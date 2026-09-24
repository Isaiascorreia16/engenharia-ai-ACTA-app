package cv.acta.app.data

import android.content.Context
import android.util.Log
import cv.acta.app.data.audio.ConversorAudio
import cv.acta.app.data.db.ActaDao
import cv.acta.app.data.db.OradorEntity
import cv.acta.app.data.db.ParteEntity
import cv.acta.app.data.db.paraEntidade
import cv.acta.app.data.definicoes.Definicoes
import cv.acta.app.data.openai.ClienteOpenAI
import cv.acta.app.data.openai.ErroApi
import cv.acta.app.data.openai.TipoErroApi
import cv.acta.app.domain.modelo.EstadoParte
import cv.acta.app.domain.transcricao.MontagemTranscricao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Transcrição em lote, depois da reunião (RF-ASR-001). Um pedido por parte gravada.
 * Corre no escopo da aplicação para não ser cancelada se o utilizador sair do ecrã.
 */
class ServicoTranscricao(
    private val contexto: Context,
    private val dao: ActaDao,
    private val definicoes: Definicoes,
    private val cliente: ClienteOpenAI,
    private val escopo: CoroutineScope,
) {
    private val progressoMutavel = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** Reuniões com transcrição em curso → mensagem de progresso. */
    val progresso: StateFlow<Map<Long, String>> = progressoMutavel.asStateFlow()

    private val trabalhos = mutableMapOf<Long, Job>()

    fun transcreverPendentes(reuniaoId: Long) = lancar(reuniaoId) {
        val pendentes = dao.partes(reuniaoId).filter { it.estado != EstadoParte.TRANSCRITA.name }
        for ((i, parte) in pendentes.withIndex()) {
            val continuar = processar(parte, "Parte ${parte.numero} (${i + 1} de ${pendentes.size})")
            if (!continuar) break
        }
    }

    /** Repete só a parte que falhou. */
    fun repetirParte(reuniaoId: Long, parteId: Long) = lancar(reuniaoId) {
        dao.parte(parteId)?.let { processar(it, "Parte ${it.numero}") }
    }

    @Synchronized
    private fun lancar(reuniaoId: Long, bloco: suspend () -> Unit) {
        if (trabalhos[reuniaoId]?.isActive == true) return
        trabalhos[reuniaoId] = escopo.launch {
            try {
                bloco()
            } finally {
                progressoMutavel.update { it - reuniaoId }
            }
        }
    }

    private fun mostrar(reuniaoId: Long, texto: String) = progressoMutavel.update { it + (reuniaoId to texto) }

    /** Devolve false se o erro impede as partes seguintes (sem rede, chave inválida, sem saldo). */
    private suspend fun processar(parte: ParteEntity, rotulo: String): Boolean {
        val reuniaoId = parte.reuniaoId
        val m4a = File(contexto.cacheDir, String.format(Locale.ROOT, "m4a/reuniao_%d/parte_%03d.m4a", reuniaoId, parte.numero))
        return try {
            val chave = definicoes.chave()
                ?: throw ErroApi(TipoErroApi.CHAVE_INVALIDA, "Não há chave da API configurada. Abra Definições.")
            mostrar(reuniaoId, "$rotulo: a converter o áudio…")
            val conversao = ConversorAudio.adtsParaM4a(File(parte.ficheiro), m4a)
            mostrar(reuniaoId, "$rotulo: a transcrever (pode demorar alguns minutos)…")
            val brutos = cliente.transcrever(chave, m4a)
            val segmentos = MontagemTranscricao.montar(reuniaoId, parte.numero, parte.inicioMs, brutos)
            val oradores = segmentos.map { it.orador }.distinct().map { OradorEntity(reuniaoId, it, null) }
            dao.substituirSegmentosDaParte(
                reuniaoId,
                MontagemTranscricao.prefixo(parte.numero),
                segmentos.map { it.paraEntidade() },
                oradores,
            )
            dao.atualizarParte(parte.copy(estado = EstadoParte.TRANSCRITA.name, erro = null, duracaoMs = conversao.duracaoMs))
            true
        } catch (e: ErroApi) {
            dao.atualizarParte(parte.copy(estado = EstadoParte.ERRO.name, erro = e.message))
            !e.bloqueiaRestantes
        } catch (e: Exception) {
            Log.e("ServicoTranscricao", "Falha na parte ${parte.numero}", e)
            dao.atualizarParte(parte.copy(estado = EstadoParte.ERRO.name, erro = "Falha ao processar o áudio: ${e.message}"))
            true
        } finally {
            m4a.delete()
        }
    }
}
