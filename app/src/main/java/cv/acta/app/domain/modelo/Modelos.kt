package cv.acta.app.domain.modelo

import kotlinx.serialization.Serializable

// Modelos do domínio: Kotlin puro, sem dependências Android.
// Os tempos (inicioMs, fimMs) são relativos à origem da gravação da reunião,
// isto é, ao instante em que começou a primeira sessão.

enum class Presenca(val rotulo: String) {
    PRESENTE("Presente"),
    AUSENTE("Ausente"),
    JUSTIFICADO("Justificado"),
    REPRESENTADO("Representado"),
}

enum class EstadoActa(val rotulo: String) {
    RASCUNHO("Rascunho"),
    EM_REVISAO("Em revisão"),
    APROVADA("Aprovada"),
    DISTRIBUIDA("Distribuída"),
}

enum class EstadoSessao { A_GRAVAR, EM_PAUSA, TERMINADA, INTERROMPIDA }

enum class EstadoParte { GRAVADA, TRANSCRITA, ERRO }

enum class TipoMarca(val rotulo: String) {
    PAUSA("Pausa"),
    CHAMADA("Chamada telefónica"),
    MUDANCA_PARTE("Mudança de parte"),
    INTERRUPCAO_ANOMALA("Terminação anómala"),
}

data class Reuniao(
    val id: Long = 0,
    val titulo: String,
    val inicioPrevistoMs: Long,
    val local: String,
    val lingua: String,
    /** Instante (epoch ms) em que começou a primeira gravação; origem da linha temporal. */
    val origemGravacaoMs: Long? = null,
    val fimGravacaoMs: Long? = null,
    val demo: Boolean = false,
)

data class Participante(
    val id: Long = 0,
    val nome: String,
    val funcao: String,
    val email: String,
    val telefone: String? = null,
    val organizacao: String? = null,
)

data class Participacao(
    val reuniaoId: Long,
    val participanteId: Long,
    val presenca: Presenca,
    val consentimento: Boolean,
    val consentimentoEmMs: Long? = null,
)

/** Participante com os dados da sua participação numa reunião concreta. */
data class ParticipanteNaReuniao(
    val participante: Participante,
    val participacao: Participacao,
)

data class Sessao(
    val id: Long = 0,
    val reuniaoId: Long,
    val inicioMs: Long,
    val fimMs: Long? = null,
    val estado: EstadoSessao,
)

data class ParteGravada(
    val id: Long = 0,
    val sessaoId: Long,
    val reuniaoId: Long,
    /** Número global da parte na reunião (1, 2, 3…). Define o prefixo dos rótulos: "P1-A". */
    val numero: Int,
    val ficheiro: String,
    val inicioMs: Long,
    val duracaoMs: Long? = null,
    val estado: EstadoParte = EstadoParte.GRAVADA,
    val erro: String? = null,
)

data class MarcaTemporal(
    val id: Long = 0,
    val reuniaoId: Long,
    val tipo: TipoMarca,
    val inicioMs: Long,
    val fimMs: Long? = null,
)

data class Segmento(
    val id: String,
    val reuniaoId: Long,
    val inicioMs: Long,
    val fimMs: Long,
    /** Rótulo do orador, válido só dentro da parte: "P1-A", "P2-B"… */
    val orador: String,
    val texto: String,
)

/** Associação de um rótulo de orador a um participante (null = anónimo). */
data class Orador(
    val reuniaoId: Long,
    val rotulo: String,
    val participanteId: Long? = null,
)

data class EdicaoSegmento(
    val id: Long = 0,
    val reuniaoId: Long,
    val segmentoId: String,
    val autor: String,
    val emMs: Long,
    val textoAnterior: String,
    val textoNovo: String,
)

data class Expedicao(
    val id: Long = 0,
    val actaId: Long,
    val versao: Int,
    val emMs: Long,
    val destinatarios: List<String>,
)

// ---------- Conteúdo da acta ----------

/** Dados que vêm SEMPRE da base de dados, nunca do LLM. */
@Serializable
data class CabecalhoActa(
    val reuniaoId: Long,
    val titulo: String,
    val data: String,
    val horaInicio: String,
    val horaFim: String,
    val local: String,
    val presentes: List<String>,
    val ausentes: List<String>,
)

@Serializable
data class PontoOrdem(
    val titulo: String,
    val sumula: String,
)

@Serializable
data class Deliberacao(
    val texto: String,
    val votacao: String? = null,
    val segmentoIds: List<String>,
    val citacao: String,
    val ancoraInicioMs: Long? = null,
    val ancoraFimMs: Long? = null,
    val verificado: Boolean = false,
    val motivoNaoVerificado: String? = null,
)

@Serializable
data class Accao(
    /** Nome do participante responsável, ou [POR_ATRIBUIR]. */
    val responsavel: String,
    val responsavelId: Long? = null,
    val descricao: String,
    /** Prazo em ISO 8601 (AAAA-MM-DD), ou null se não houver prazo. */
    val prazo: String? = null,
    val segmentoIds: List<String>,
    val citacao: String,
    val ancoraInicioMs: Long? = null,
    val ancoraFimMs: Long? = null,
    val verificado: Boolean = false,
    val motivoNaoVerificado: String? = null,
) {
    companion object {
        const val POR_ATRIBUIR = "Por atribuir"
    }
}

@Serializable
data class ConteudoActa(
    val ordemTrabalhos: List<PontoOrdem>,
    val deliberacoes: List<Deliberacao>,
    val accoes: List<Accao>,
    val proximaReuniao: String? = null,
    val sumulaExecutiva: String,
)

/** Documento completo: cabeçalho (BD) + conteúdo (LLM, verificado e editado). É isto que se fixa e se assina com SHA-256. */
@Serializable
data class DocumentoActa(
    val cabecalho: CabecalhoActa,
    val conteudo: ConteudoActa,
)

data class ActaVersao(
    val id: Long = 0,
    val reuniaoId: Long,
    val versao: Int,
    val estado: EstadoActa,
    val documento: DocumentoActa,
    val hash: String? = null,
    val criadaEmMs: Long,
    val aprovadaEmMs: Long? = null,
)

const val MENCAO_IA = "Documento gerado com recurso a inteligência artificial. Carece de validação humana."
