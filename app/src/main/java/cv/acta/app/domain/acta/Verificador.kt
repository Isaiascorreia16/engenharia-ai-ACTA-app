package cv.acta.app.domain.acta

import cv.acta.app.domain.modelo.Accao
import cv.acta.app.domain.modelo.ConteudoActa
import cv.acta.app.domain.modelo.Deliberacao
import cv.acta.app.domain.modelo.Participante
import cv.acta.app.domain.modelo.PontoOrdem
import cv.acta.app.domain.modelo.Segmento
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

// ---------- Forma da resposta do LLM (sem tempos, sem verificação) ----------

@Serializable
data class DeliberacaoGerada(
    val texto: String,
    val votacao: String? = null,
    val segmentoIds: List<String> = emptyList(),
    val citacao: String = "",
)

@Serializable
data class AccaoGerada(
    val responsavel: String? = null,
    val descricao: String,
    val prazo: String? = null,
    val segmentoIds: List<String> = emptyList(),
    val citacao: String = "",
)

@Serializable
data class ActaGerada(
    val ordemTrabalhos: List<PontoOrdem> = emptyList(),
    val deliberacoes: List<DeliberacaoGerada> = emptyList(),
    val accoes: List<AccaoGerada> = emptyList(),
    val proximaReuniao: String? = null,
    val sumulaExecutiva: String = "",
)

/**
 * Verificação obrigatória em código (RF-ACT-005, RF-ACT-006, RF-ACT-004).
 *
 * Garante: cada deliberação/ação aponta para segmentos que existem e contém uma citação que aparece,
 * literalmente (após normalização), no texto desses segmentos; os tempos da âncora vêm dos segmentos,
 * nunca do LLM; o responsável é um participante da lista ou fica "Por atribuir".
 *
 * NÃO garante: que o resumo/descrição do item diga o mesmo que a citação (o LLM pode citar bem e resumir mal);
 * que a transcrição esteja correta; que a citação seja a mais relevante. Por isso a acta precisa de revisão humana.
 */
object Verificador {
    const val LIMITE_PALAVRAS_SUMULA = 250

    private val PONTUACAO = Regex("\\p{P}+")
    private val ESPACOS = Regex("\\s+")

    /** Minúsculas, sem pontuação, espaços colapsados. */
    fun normalizar(texto: String): String =
        texto.lowercase(Locale.ROOT)
            .replace(PONTUACAO, " ")
            .replace(ESPACOS, " ")
            .trim()

    data class Resultado(
        val verificado: Boolean,
        val motivo: String?,
        val ancoraInicioMs: Long?,
        val ancoraFimMs: Long?,
    )

    fun verificarCitacao(ids: List<String>, citacao: String, segmentos: Map<String, Segmento>): Resultado {
        if (ids.isEmpty()) return Resultado(false, "Não indica o segmento de origem.", null, null)
        val inexistentes = ids.filter { it !in segmentos }
        if (inexistentes.isNotEmpty()) {
            return Resultado(false, "Segmento inexistente: ${inexistentes.joinToString()}.", null, null)
        }
        val citados = ids.distinct().map { segmentos.getValue(it) }.sortedBy { it.inicioMs }
        val inicio = citados.minOf { it.inicioMs }
        val fim = citados.maxOf { it.fimMs }
        val c = normalizar(citacao)
        if (c.isEmpty()) return Resultado(false, "Sem citação.", inicio, fim)
        val texto = normalizar(citados.joinToString(" ") { it.texto })
        if (!texto.contains(c)) {
            return Resultado(false, "A citação não consta do texto dos segmentos indicados.", inicio, fim)
        }
        return Resultado(true, null, inicio, fim)
    }

    /**
     * O responsável tem de corresponder a UM participante. Aceita o nome completo ou um nome parcial
     * que identifique uma única pessoa ("Maria" → "Maria Tavares" se não houver outra Maria).
     * Ambíguo, desconhecido ou vazio → null ("Por atribuir").
     */
    fun resolverResponsavel(nome: String?, participantes: List<Participante>): Participante? {
        if (nome.isNullOrBlank()) return null
        val n = normalizar(nome)
        if (n.isEmpty() || n == normalizar(Accao.POR_ATRIBUIR)) return null
        participantes.filter { normalizar(it.nome) == n }.let { if (it.size == 1) return it.first() }
        val tokens = n.split(' ')
        return participantes.filter { p ->
            val doParticipante = normalizar(p.nome).split(' ').toSet()
            tokens.all { it in doParticipante }
        }.singleOrNull()
    }

    fun prazoIso(prazo: String?): String? {
        val p = prazo?.trim().orEmpty()
        if (p.isEmpty()) return null
        return try {
            LocalDate.parse(p).toString()
        } catch (e: Exception) {
            null
        }
    }

    /** Aceita AAAA-MM-DD ou AAAA-MM-DDTHH:MM[:SS]; qualquer outra coisa é descartada. */
    fun dataProximaReuniao(texto: String?): String? {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t).toString()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(t).toString()
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun limitarPalavras(texto: String, limite: Int = LIMITE_PALAVRAS_SUMULA): String {
        val palavras = texto.trim().split(ESPACOS).filter { it.isNotEmpty() }
        return if (palavras.size <= limite) texto.trim() else palavras.take(limite).joinToString(" ") + "…"
    }

    /** Converte a resposta do LLM em conteúdo verificado. Nada é descartado: o que falha fica "Não verificado". */
    fun verificar(gerada: ActaGerada, segmentos: List<Segmento>, participantes: List<Participante>): ConteudoActa {
        val porId = segmentos.associateBy { it.id }
        val deliberacoes = gerada.deliberacoes.map { d ->
            val r = verificarCitacao(d.segmentoIds, d.citacao, porId)
            Deliberacao(
                texto = d.texto.trim(),
                votacao = d.votacao?.trim()?.ifBlank { null },
                segmentoIds = d.segmentoIds,
                citacao = d.citacao.trim(),
                ancoraInicioMs = r.ancoraInicioMs,
                ancoraFimMs = r.ancoraFimMs,
                verificado = r.verificado,
                motivoNaoVerificado = r.motivo,
            )
        }
        val accoes = gerada.accoes.map { a ->
            val r = verificarCitacao(a.segmentoIds, a.citacao, porId)
            val resp = resolverResponsavel(a.responsavel, participantes)
            Accao(
                responsavel = resp?.nome ?: Accao.POR_ATRIBUIR,
                responsavelId = resp?.id,
                descricao = a.descricao.trim(),
                prazo = prazoIso(a.prazo),
                segmentoIds = a.segmentoIds,
                citacao = a.citacao.trim(),
                ancoraInicioMs = r.ancoraInicioMs,
                ancoraFimMs = r.ancoraFimMs,
                verificado = r.verificado,
                motivoNaoVerificado = r.motivo,
            )
        }
        return ConteudoActa(
            ordemTrabalhos = gerada.ordemTrabalhos.map { PontoOrdem(it.titulo.trim(), it.sumula.trim()) },
            deliberacoes = deliberacoes,
            accoes = accoes,
            proximaReuniao = dataProximaReuniao(gerada.proximaReuniao),
            sumulaExecutiva = limitarPalavras(gerada.sumulaExecutiva),
        )
    }

    /**
     * Volta a verificar um conteúdo já existente (depois de edições na acta ou na transcrição).
     * O responsável mantém-se se o participante ainda existir; caso contrário é resolvido pelo nome.
     */
    fun reverificar(conteudo: ConteudoActa, segmentos: List<Segmento>, participantes: List<Participante>): ConteudoActa {
        val porId = segmentos.associateBy { it.id }
        return conteudo.copy(
            deliberacoes = conteudo.deliberacoes.map { d ->
                val r = verificarCitacao(d.segmentoIds, d.citacao, porId)
                d.copy(ancoraInicioMs = r.ancoraInicioMs, ancoraFimMs = r.ancoraFimMs, verificado = r.verificado, motivoNaoVerificado = r.motivo)
            },
            accoes = conteudo.accoes.map { a ->
                val r = verificarCitacao(a.segmentoIds, a.citacao, porId)
                val resp = participantes.firstOrNull { it.id == a.responsavelId } ?: resolverResponsavel(a.responsavel, participantes)
                a.copy(
                    responsavel = resp?.nome ?: Accao.POR_ATRIBUIR,
                    responsavelId = resp?.id,
                    prazo = prazoIso(a.prazo),
                    ancoraInicioMs = r.ancoraInicioMs,
                    ancoraFimMs = r.ancoraFimMs,
                    verificado = r.verificado,
                    motivoNaoVerificado = r.motivo,
                )
            },
            proximaReuniao = dataProximaReuniao(conteudo.proximaReuniao),
            sumulaExecutiva = limitarPalavras(conteudo.sumulaExecutiva),
        )
    }
}
