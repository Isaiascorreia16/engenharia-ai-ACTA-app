package cv.acta.app.data

import cv.acta.app.data.db.ActaDao
import cv.acta.app.data.db.EdicaoSegmentoEntity
import cv.acta.app.data.db.OradorEntity
import cv.acta.app.data.db.paraDominio
import cv.acta.app.data.db.paraEntidade
import cv.acta.app.domain.modelo.EdicaoSegmento
import cv.acta.app.domain.modelo.Orador
import cv.acta.app.domain.modelo.Segmento
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RepositorioRevisao(private val dao: ActaDao) {

    fun segmentos(reuniaoId: Long): Flow<List<Segmento>> = dao.observarSegmentos(reuniaoId).map { l -> l.map { it.paraDominio() } }
    fun oradores(reuniaoId: Long): Flow<List<Orador>> = dao.observarOradores(reuniaoId).map { l -> l.map { it.paraDominio() } }
    fun edicoes(reuniaoId: Long): Flow<List<EdicaoSegmento>> = dao.observarEdicoes(reuniaoId).map { l -> l.map { it.paraDominio() } }

    suspend fun segmentosAgora(reuniaoId: Long): List<Segmento> = dao.segmentos(reuniaoId).map { it.paraDominio() }
    suspend fun oradoresAgora(reuniaoId: Long): List<Orador> = dao.oradores(reuniaoId).map { it.paraDominio() }

    /** Edição manual com registo de autor, instante e conteúdo anterior (RF-ASR-012, RF-ASR-013). */
    suspend fun editarTexto(segmento: Segmento, novoTexto: String, autor: String, agoraMs: Long) {
        val limpo = novoTexto.trim()
        if (limpo == segmento.texto || limpo.isEmpty()) return
        dao.editarSegmento(
            segmento.copy(texto = limpo).paraEntidade(),
            EdicaoSegmentoEntity(
                reuniaoId = segmento.reuniaoId,
                segmentoId = segmento.id,
                autor = autor,
                emMs = agoraMs,
                textoAnterior = segmento.texto,
                textoNovo = limpo,
            ),
        )
    }

    /**
     * Associa um rótulo ("P2-A") a um participante (ou volta a anónimo com null).
     * A propagação é automática: todos os segmentos com esse rótulo passam a mostrar o nome (RF-DIA-007).
     */
    suspend fun associar(reuniaoId: Long, rotulo: String, participanteId: Long?) {
        dao.guardarOradores(listOf(OradorEntity(reuniaoId, rotulo, participanteId)))
    }

    /** Reatribui um segmento isolado a outro rótulo (quando a diarização se enganou num segmento). */
    suspend fun reatribuirSegmento(segmento: Segmento, novoRotulo: String) {
        if (novoRotulo == segmento.orador) return
        dao.atualizarSegmento(segmento.copy(orador = novoRotulo).paraEntidade())
    }
}
