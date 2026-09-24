package cv.acta.app.domain.ciclo

import cv.acta.app.domain.modelo.ActaVersao
import cv.acta.app.domain.modelo.DocumentoActa
import cv.acta.app.domain.modelo.EstadoActa
import cv.acta.app.domain.modelo.EstadoActa.APROVADA
import cv.acta.app.domain.modelo.EstadoActa.DISTRIBUIDA
import cv.acta.app.domain.modelo.EstadoActa.EM_REVISAO
import cv.acta.app.domain.modelo.EstadoActa.RASCUNHO
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class TransicaoInvalidaException(val de: EstadoActa, val para: EstadoActa) :
    IllegalStateException("Transição inválida: ${de.rotulo} → ${para.rotulo}")

class ActaImutavelException :
    IllegalStateException("A acta aprovada está fixada e não pode ser alterada. As alterações criam uma nova versão.")

/**
 * Ciclo de vida: RASCUNHO → EM_REVISAO → APROVADA → DISTRIBUIDA.
 * De EM_REVISAO pode voltar-se a RASCUNHO. Tudo o resto é inválido (RF-ACT-008, RF-DIS-003).
 */
object CicloActa {

    private val permitidas: Map<EstadoActa, Set<EstadoActa>> = mapOf(
        RASCUNHO to setOf(EM_REVISAO),
        EM_REVISAO to setOf(RASCUNHO, APROVADA),
        APROVADA to setOf(DISTRIBUIDA),
        DISTRIBUIDA to emptySet(),
    )

    fun podeTransitar(de: EstadoActa, para: EstadoActa): Boolean = para in permitidas.getValue(de)

    fun validar(de: EstadoActa, para: EstadoActa) {
        if (!podeTransitar(de, para)) throw TransicaoInvalidaException(de, para)
    }

    fun editavel(estado: EstadoActa): Boolean = estado == RASCUNHO || estado == EM_REVISAO

    fun enviarParaRevisao(acta: ActaVersao): ActaVersao = transitar(acta, EM_REVISAO)

    fun voltarARascunho(acta: ActaVersao): ActaVersao = transitar(acta, RASCUNHO)

    /** Aprovação: fixa o conteúdo e calcula o SHA-256 (RF-ACT-009, RF-PER-010). */
    fun aprovar(acta: ActaVersao, documentoFinal: DocumentoActa, agoraMs: Long): ActaVersao {
        validar(acta.estado, APROVADA)
        return acta.copy(
            estado = APROVADA,
            documento = documentoFinal,
            hash = HashActa.sha256(documentoFinal),
            aprovadaEmMs = agoraMs,
        )
    }

    /** Distribuir só a partir de APROVADA (RF-DIS-003). */
    fun distribuir(acta: ActaVersao): ActaVersao = transitar(acta, DISTRIBUIDA)

    private fun transitar(acta: ActaVersao, para: EstadoActa): ActaVersao {
        validar(acta.estado, para)
        return acta.copy(estado = para)
    }
}

/** Versionamento (RF-ACT-016): alterar uma acta fixada cria uma nova versão; a anterior fica intacta. */
object Versionamento {

    sealed interface Resultado {
        /** A mesma versão, com o novo conteúdo (acta ainda editável). */
        data class Atualizada(val acta: ActaVersao) : Resultado

        /** Uma versão nova em RASCUNHO; a anterior não é tocada. */
        data class NovaVersao(val anterior: ActaVersao, val nova: ActaVersao) : Resultado
    }

    fun alterar(acta: ActaVersao, novoDocumento: DocumentoActa, agoraMs: Long): Resultado =
        if (CicloActa.editavel(acta.estado)) {
            Resultado.Atualizada(acta.copy(documento = novoDocumento))
        } else {
            Resultado.NovaVersao(
                anterior = acta,
                nova = ActaVersao(
                    id = 0,
                    reuniaoId = acta.reuniaoId,
                    versao = acta.versao + 1,
                    estado = RASCUNHO,
                    documento = novoDocumento,
                    hash = null,
                    criadaEmMs = agoraMs,
                    aprovadaEmMs = null,
                ),
            )
        }

    /** Garante que uma acta fixada nunca é sobrescrita em armazenamento. */
    fun garantirEditavel(acta: ActaVersao) {
        if (!CicloActa.editavel(acta.estado)) throw ActaImutavelException()
    }
}

object HashActa {
    /** JSON canónico: ordem dos campos fixa pela declaração das classes e valores por omissão incluídos. */
    val json = Json {
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = true
    }

    fun canonico(documento: DocumentoActa): String = json.encodeToString(DocumentoActa.serializer(), documento)

    fun sha256(documento: DocumentoActa): String = sha256(canonico(documento))

    fun sha256(texto: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(texto.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
