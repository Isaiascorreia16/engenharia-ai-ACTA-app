package cv.acta.app.data

import cv.acta.app.data.db.ActaDao
import cv.acta.app.data.db.ParticipacaoEntity
import cv.acta.app.data.db.ResultadoPesquisa
import cv.acta.app.data.db.paraDominio
import cv.acta.app.data.db.paraEntidade
import cv.acta.app.domain.modelo.Participante
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Presenca
import cv.acta.app.domain.modelo.Reuniao
import cv.acta.app.domain.validacao.ErroCampo
import cv.acta.app.domain.validacao.ValidacaoParticipante
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RepositorioReunioes(private val dao: ActaDao) {

    fun reunioes(): Flow<List<Reuniao>> = dao.observarReunioes().map { l -> l.map { it.paraDominio() } }

    fun reuniao(id: Long): Flow<Reuniao?> = dao.observarReuniao(id).map { it?.paraDominio() }

    suspend fun obter(id: Long): Reuniao? = dao.reuniao(id)?.paraDominio()

    suspend fun criar(r: Reuniao): Long = dao.inserirReuniao(r.paraEntidade())

    suspend fun atualizar(r: Reuniao) = dao.atualizarReuniao(r.paraEntidade())

    suspend fun apagar(id: Long) {
        dao.apagarReuniao(id)
        dao.apagarParticipantesOrfaos()
    }

    suspend fun existemReunioes(): Boolean = dao.contarReunioes() > 0

    fun participantes(reuniaoId: Long): Flow<List<ParticipanteNaReuniao>> =
        dao.observarParticipantes(reuniaoId).map { l -> l.map { it.paraDominio() } }

    suspend fun participantesAgora(reuniaoId: Long): List<ParticipanteNaReuniao> =
        dao.participantes(reuniaoId).map { it.paraDominio() }

    /**
     * Cria ou atualiza um participante e a sua participação. Devolve os erros de validação
     * (RF-PAR-003: o registo inválido é recusado e cada erro indica o campo).
     */
    suspend fun guardarParticipante(
        reuniaoId: Long,
        participante: Participante,
        presenca: Presenca,
        consentimento: Boolean,
        consentimentoEmMsAnterior: Long?,
        agoraMs: Long,
    ): List<ErroCampo> {
        val erros = ValidacaoParticipante.validar(participante.nome, participante.funcao, participante.email, participante.telefone)
        if (erros.isNotEmpty()) return erros
        val limpo = participante.copy(
            nome = participante.nome.trim(),
            funcao = participante.funcao.trim(),
            email = participante.email.trim(),
            telefone = participante.telefone?.trim()?.ifBlank { null },
            organizacao = participante.organizacao?.trim()?.ifBlank { null },
        )
        val id = if (limpo.id == 0L) {
            dao.inserirParticipante(limpo.paraEntidade())
        } else {
            dao.atualizarParticipante(limpo.paraEntidade())
            limpo.id
        }
        val consentimentoEm = if (consentimento) consentimentoEmMsAnterior ?: agoraMs else null
        dao.guardarParticipacao(ParticipacaoEntity(reuniaoId, id, presenca.name, consentimento, consentimentoEm))
        return emptyList()
    }

    suspend fun alterarPresenca(np: ParticipanteNaReuniao, presenca: Presenca) {
        dao.guardarParticipacao(np.participacao.copy(presenca = presenca).paraEntidade())
    }

    /** Regista o consentimento com o instante em que foi dado (RF-PAR-006). */
    suspend fun alterarConsentimento(np: ParticipanteNaReuniao, consentiu: Boolean, agoraMs: Long) {
        dao.guardarParticipacao(
            np.participacao.copy(consentimento = consentiu, consentimentoEmMs = if (consentiu) agoraMs else null).paraEntidade()
        )
    }

    suspend fun removerParticipante(reuniaoId: Long, participanteId: Long) {
        dao.removerParticipacao(reuniaoId, participanteId)
        dao.apagarParticipantesOrfaos()
    }

    suspend fun pesquisar(texto: String): List<ResultadoPesquisa> {
        val q = texto.trim().replace("%", "").replace("_", "")
        if (q.length < 2) return emptyList()
        return dao.pesquisar(q)
    }
}
