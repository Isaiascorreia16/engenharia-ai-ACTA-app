package cv.acta.app.data

import android.content.Context
import cv.acta.app.data.db.ActaDao
import cv.acta.app.data.db.MarcaEntity
import cv.acta.app.data.db.ParteEntity
import cv.acta.app.data.db.SessaoEntity
import cv.acta.app.data.db.paraDominio
import cv.acta.app.domain.modelo.EstadoParte
import cv.acta.app.domain.modelo.EstadoSessao
import cv.acta.app.domain.modelo.MarcaTemporal
import cv.acta.app.domain.modelo.ParteGravada
import cv.acta.app.domain.modelo.Sessao
import cv.acta.app.domain.modelo.TipoMarca
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale

/**
 * Sessões, partes e marcas temporais. Todos os tempos guardados em partes e marcas são
 * relativos à origem da gravação da reunião (início da primeira sessão).
 */
class RepositorioGravacao(private val contexto: Context, private val dao: ActaDao) {

    data class SessaoIniciada(val sessaoId: Long, val origemMs: Long)

    fun partes(reuniaoId: Long): Flow<List<ParteGravada>> = dao.observarPartes(reuniaoId).map { l -> l.map { it.paraDominio() } }
    fun sessoes(reuniaoId: Long): Flow<List<Sessao>> = dao.observarSessoes(reuniaoId).map { l -> l.map { it.paraDominio() } }
    fun marcas(reuniaoId: Long): Flow<List<MarcaTemporal>> = dao.observarMarcas(reuniaoId).map { l -> l.map { it.paraDominio() } }

    suspend fun partesAgora(reuniaoId: Long): List<ParteGravada> = dao.partes(reuniaoId).map { it.paraDominio() }

    fun pastaReuniao(reuniaoId: Long): File = File(contexto.filesDir, "gravacoes/reuniao_$reuniaoId").apply { mkdirs() }

    suspend fun iniciarSessao(reuniaoId: Long, agoraMs: Long): SessaoIniciada {
        val r = dao.reuniao(reuniaoId) ?: error("Reunião inexistente: $reuniaoId")
        val origem = r.origemGravacaoMs ?: agoraMs
        if (r.origemGravacaoMs == null) dao.atualizarReuniao(r.copy(origemGravacaoMs = origem))
        val id = dao.inserirSessao(SessaoEntity(reuniaoId = reuniaoId, inicioMs = agoraMs, fimMs = null, estado = EstadoSessao.A_GRAVAR.name))
        return SessaoIniciada(id, origem)
    }

    suspend fun novaParte(reuniaoId: Long, sessaoId: Long, inicioRelativoMs: Long): Pair<Long, File> {
        val numero = dao.ultimoNumeroParte(reuniaoId) + 1
        val ficheiro = File(pastaReuniao(reuniaoId), String.format(Locale.ROOT, "parte_%03d.aac", numero))
        val id = dao.inserirParte(
            ParteEntity(
                sessaoId = sessaoId, reuniaoId = reuniaoId, numero = numero, ficheiro = ficheiro.absolutePath,
                inicioMs = inicioRelativoMs, duracaoMs = null, estado = EstadoParte.GRAVADA.name, erro = null,
            )
        )
        return id to ficheiro
    }

    suspend fun fecharParte(parteId: Long, duracaoMs: Long) {
        val p = dao.parte(parteId) ?: return
        dao.atualizarParte(p.copy(duracaoMs = duracaoMs))
    }

    /** Remove uma parte sem áudio útil (por exemplo, parada antes de o codificador escrever dados). */
    suspend fun descartarParte(parteId: Long) {
        val p = dao.parte(parteId) ?: return
        File(p.ficheiro).delete()
        dao.apagarParte(parteId)
    }

    suspend fun abrirMarca(reuniaoId: Long, tipo: TipoMarca, inicioRelativoMs: Long): Long =
        dao.inserirMarca(MarcaEntity(reuniaoId = reuniaoId, tipo = tipo.name, inicioMs = inicioRelativoMs, fimMs = null))

    suspend fun fecharMarca(marcaId: Long, fimRelativoMs: Long) {
        val m = dao.marca(marcaId) ?: return
        dao.atualizarMarca(m.copy(fimMs = fimRelativoMs))
    }

    suspend fun alterarEstadoSessao(sessaoId: Long, estado: EstadoSessao) {
        val s = dao.sessao(sessaoId) ?: return
        dao.atualizarSessao(s.copy(estado = estado.name))
    }

    suspend fun terminarSessao(sessaoId: Long, agoraMs: Long) {
        val s = dao.sessao(sessaoId) ?: return
        dao.atualizarSessao(s.copy(estado = EstadoSessao.TERMINADA.name, fimMs = agoraMs))
        dao.reuniao(s.reuniaoId)?.let { dao.atualizarReuniao(it.copy(fimGravacaoMs = agoraMs)) }
    }

    /**
     * Recuperação após terminação anómala (RF-CAP-007, RNF-007). Chamado no arranque do processo:
     * se há sessões abertas, o processo anterior morreu a meio da gravação. O ADTS é legível até ao
     * último bloco escrito, por isso as partes com dados mantêm-se; as vazias são descartadas.
     * Devolve o número de sessões recuperadas.
     */
    suspend fun recuperarSessoesInterrompidas(): Int {
        val abertas = dao.sessoesAbertas()
        for (s in abertas) {
            val reuniao = dao.reuniao(s.reuniaoId)
            val origem = reuniao?.origemGravacaoMs ?: s.inicioMs
            var ultimoInstante = s.inicioMs
            for (p in dao.partesDaSessao(s.id)) {
                val f = File(p.ficheiro)
                if (!f.exists() || f.length() == 0L) {
                    f.delete()
                    dao.apagarParte(p.id)
                    continue
                }
                val fimEstimado = f.lastModified()
                ultimoInstante = maxOf(ultimoInstante, fimEstimado)
                if (p.duracaoMs == null) {
                    // Estimativa pela hora da última escrita; a conversão para M4A calcula o valor exato.
                    val inicioAbsoluto = origem + p.inicioMs
                    dao.atualizarParte(p.copy(duracaoMs = (fimEstimado - inicioAbsoluto).coerceAtLeast(0)))
                }
            }
            val fimRelativo = ultimoInstante - origem
            dao.inserirMarca(MarcaEntity(reuniaoId = s.reuniaoId, tipo = TipoMarca.INTERRUPCAO_ANOMALA.name, inicioMs = fimRelativo, fimMs = fimRelativo))
            dao.atualizarSessao(s.copy(estado = EstadoSessao.INTERROMPIDA.name, fimMs = ultimoInstante))
            reuniao?.let { dao.atualizarReuniao(it.copy(fimGravacaoMs = ultimoInstante)) }
        }
        return abertas.size
    }

    suspend fun apagarReuniaoComFicheiros(reuniaoId: Long) {
        File(contexto.filesDir, "gravacoes/reuniao_$reuniaoId").deleteRecursively()
        File(contexto.cacheDir, "m4a/reuniao_$reuniaoId").deleteRecursively()
        dao.apagarReuniao(reuniaoId)
        dao.apagarParticipantesOrfaos()
    }
}
