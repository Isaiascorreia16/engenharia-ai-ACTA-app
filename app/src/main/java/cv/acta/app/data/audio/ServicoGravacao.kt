package cv.acta.app.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cv.acta.app.ActaApp
import cv.acta.app.MainActivity
import cv.acta.app.R
import cv.acta.app.data.RepositorioGravacao
import cv.acta.app.domain.modelo.EstadoSessao
import cv.acta.app.domain.modelo.TipoMarca
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Serviço em primeiro plano do tipo "microfone" (RF-PLT-003, RF-CAP-003, RF-CAP-004).
 *
 * - Grava em AAC ADTS: se o processo morrer, o ficheiro é legível até ao último bloco escrito.
 * - Grava em partes de [DURACAO_PARTE_MS]. O `setNextOutputFile` não funciona em ADTS (o AACWriter
 *   do Android não o implementa), por isso a rotação faz stop + novo MediaRecorder.
 * - Pausa (manual ou por chamada) fecha a parte atual e abre uma marca temporal; retomar abre nova parte.
 *   Assim cada parte é contínua e "início da parte + tempo no ficheiro" dá o tempo real na reunião.
 * - Chamadas: deteção pelo modo do AudioManager, sem READ_PHONE_STATE (RF-CAP-008).
 */
class ServicoGravacao : Service() {

    companion object {
        private const val TAG = "ServicoGravacao"
        const val ACAO_INICIAR = "cv.acta.app.gravacao.INICIAR"
        const val ACAO_PAUSAR = "cv.acta.app.gravacao.PAUSAR"
        const val ACAO_RETOMAR = "cv.acta.app.gravacao.RETOMAR"
        const val ACAO_TERMINAR = "cv.acta.app.gravacao.TERMINAR"
        const val EXTRA_REUNIAO = "reuniaoId"

        /** 5 minutos: margem face ao limite de 2 000 tokens de saída do gpt-4o-transcribe-diarize e aos 25 MB. */
        const val DURACAO_PARTE_MS = 5L * 60 * 1000

        private const val CANAL = "gravacao"
        private const val ID_NOTIFICACAO = 1001
        private const val INTERVALO_MODO_MS = 1000L

        /** Modos do AudioManager que indicam chamada (telefónica ou VoIP) a tocar ou em curso. */
        private val MODOS_CHAMADA = setOf(
            AudioManager.MODE_RINGTONE,        // 1
            AudioManager.MODE_IN_CALL,         // 2
            AudioManager.MODE_IN_COMMUNICATION, // 3
            4, // MODE_CALL_SCREENING (API 30)
            5, // MODE_CALL_REDIRECT (API 33)
            6, // MODE_COMMUNICATION_REDIRECT (API 33)
        )

        fun iniciar(contexto: Context, reuniaoId: Long) {
            val i = Intent(contexto, ServicoGravacao::class.java).setAction(ACAO_INICIAR).putExtra(EXTRA_REUNIAO, reuniaoId)
            ContextCompat.startForegroundService(contexto, i)
        }

        fun enviar(contexto: Context, acao: String) {
            contexto.startService(Intent(contexto, ServicoGravacao::class.java).setAction(acao))
        }
    }

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: RepositorioGravacao
    private lateinit var audioManager: AudioManager

    private var gravador: MediaRecorder? = null
    private var reuniaoId: Long = 0
    private var sessaoId: Long = 0
    private var origemMs: Long = 0
    private var parteId: Long = 0
    private var parteInicioRealtime: Long = 0
    private var marcaPausaId: Long? = null
    private var motivoPausa: TipoMarca? = null
    private var ativo = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var ouvinteModo: Any? = null

    private val rodarParte = Runnable { escopo.launch { mutex.withLock { rodar() } } }

    private val verificarModo = object : Runnable {
        override fun run() {
            tratarModo(audioManager.mode)
            handler.postDelayed(this, INTERVALO_MODO_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = (application as ActaApp).contentor.gravacao
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        criarCanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACAO_INICIAR -> {
                // Tem de entrar em primeiro plano logo (limite de poucos segundos após startForegroundService).
                entrarEmPrimeiroPlano("A preparar a gravação…")
                val id = intent.getLongExtra(EXTRA_REUNIAO, 0)
                escopo.launch { mutex.withLock { iniciarSessao(id) } }
            }
            ACAO_PAUSAR -> escopo.launch { mutex.withLock { pausar(TipoMarca.PAUSA) } }
            ACAO_RETOMAR -> escopo.launch { mutex.withLock { retomar() } }
            ACAO_TERMINAR -> escopo.launch { mutex.withLock { terminar() } }
        }
        // Se o sistema matar o processo, não se reinicia sozinho: a recuperação é feita ao reabrir a app.
        return START_NOT_STICKY
    }

    // ---------- Ciclo da sessão ----------

    private suspend fun iniciarSessao(id: Long) {
        if (ativo) return
        try {
            val sessao = repo.iniciarSessao(id, System.currentTimeMillis())
            reuniaoId = id
            sessaoId = sessao.sessaoId
            origemMs = sessao.origemMs
            ativo = true
            adquirirWakeLock()
            EstadoGravacao.atualizar { EstadoGravacaoUi(ativa = true, reuniaoId = id) }
            iniciarParte()
            sinalSonoro()
            registarDetecaoChamadas()
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar a gravação", e)
            falhar("Não foi possível iniciar a gravação: ${e.message}")
        }
    }

    private suspend fun iniciarParte() {
        val agora = System.currentTimeMillis()
        val (novaParteId, ficheiro) = repo.novaParte(reuniaoId, sessaoId, agora - origemMs)
        parteId = novaParteId
        val g = try {
            criarGravador(ficheiro).also { novo ->
                try {
                    novo.start()
                } catch (e: RuntimeException) {
                    novo.release()
                    throw e
                }
            }
        } catch (e: Exception) {
            repo.descartarParte(novaParteId)
            throw e
        }
        gravador = g
        parteInicioRealtime = SystemClock.elapsedRealtime()
        handler.postDelayed(rodarParte, DURACAO_PARTE_MS)
        EstadoGravacao.atualizar {
            it.copy(emPausa = false, motivoPausa = null, inicioParteRealtime = parteInicioRealtime, parteAtual = it.parteAtual + 1, erro = null)
        }
        atualizarNotificacao()
    }

    /** Fecha a parte em curso. Devolve a duração gravada. */
    private suspend fun terminarParte(): Long {
        handler.removeCallbacks(rodarParte)
        val g = gravador ?: return 0
        gravador = null
        val duracao = SystemClock.elapsedRealtime() - parteInicioRealtime
        var comDados = true
        try {
            g.stop()
        } catch (e: RuntimeException) {
            // stop() falha se ainda não foi escrito nenhum bloco de áudio.
            comDados = false
        } finally {
            g.release()
        }
        if (comDados) repo.fecharParte(parteId, duracao) else repo.descartarParte(parteId)
        EstadoGravacao.atualizar { it.copy(acumuladoMs = it.acumuladoMs + if (comDados) duracao else 0, inicioParteRealtime = null) }
        return duracao
    }

    private suspend fun rodar() {
        if (!ativo || gravador == null) return
        terminarParte()
        val agoraRel = System.currentTimeMillis() - origemMs
        val m = repo.abrirMarca(reuniaoId, TipoMarca.MUDANCA_PARTE, agoraRel)
        try {
            iniciarParte()
            repo.fecharMarca(m, System.currentTimeMillis() - origemMs)
        } catch (e: Exception) {
            Log.e(TAG, "Falha na mudança de parte", e)
            falhar("A gravação parou ao mudar de parte: ${e.message}")
        }
    }

    private suspend fun pausar(motivo: TipoMarca) {
        if (!ativo || gravador == null) return
        terminarParte()
        marcaPausaId = repo.abrirMarca(reuniaoId, motivo, System.currentTimeMillis() - origemMs)
        motivoPausa = motivo
        repo.alterarEstadoSessao(sessaoId, EstadoSessao.EM_PAUSA)
        EstadoGravacao.atualizar { it.copy(emPausa = true, motivoPausa = motivo) }
        atualizarNotificacao()
    }

    private suspend fun retomar() {
        if (!ativo || gravador != null) return
        marcaPausaId?.let { repo.fecharMarca(it, System.currentTimeMillis() - origemMs) }
        marcaPausaId = null
        motivoPausa = null
        repo.alterarEstadoSessao(sessaoId, EstadoSessao.A_GRAVAR)
        try {
            iniciarParte()
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao retomar", e)
            falhar("Não foi possível retomar a gravação: ${e.message}")
        }
    }

    private suspend fun terminar() {
        if (ativo) {
            terminarParte()
            marcaPausaId?.let { repo.fecharMarca(it, System.currentTimeMillis() - origemMs) }
            repo.terminarSessao(sessaoId, System.currentTimeMillis())
        }
        encerrar()
        EstadoGravacao.atualizar { EstadoGravacaoUi() }
    }

    private suspend fun falhar(mensagem: String) {
        if (ativo) {
            try {
                terminarParte()
                repo.terminarSessao(sessaoId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao fechar a sessão", e)
            }
        }
        encerrar()
        EstadoGravacao.atualizar { EstadoGravacaoUi(erro = mensagem) }
    }

    private fun encerrar() {
        ativo = false
        marcaPausaId = null
        motivoPausa = null
        handler.removeCallbacks(rodarParte)
        removerDetecaoChamadas()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(rodarParte)
        removerDetecaoChamadas()
        gravador?.let {
            // Destruição inesperada: fecha o ficheiro para não perder o último bloco.
            try {
                it.stop()
            } catch (e: RuntimeException) {
                Log.w(TAG, "stop() falhou no onDestroy", e)
            }
            it.release()
        }
        gravador = null
        wakeLock?.let { if (it.isHeld) it.release() }
        escopo.cancel()
        super.onDestroy()
    }

    // ---------- MediaRecorder ----------

    private fun criarGravador(ficheiro: File): MediaRecorder {
        val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        g.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        g.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        g.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        g.setAudioChannels(1)
        g.setAudioSamplingRate(16_000)
        g.setAudioEncodingBitRate(64_000)
        g.setOutputFile(ficheiro.absolutePath)
        g.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "Erro do MediaRecorder: $what/$extra")
            escopo.launch { mutex.withLock { falhar("Erro do gravador ($what). O áudio gravado até aqui foi preservado.") } }
        }
        try {
            g.prepare()
        } catch (e: Exception) {
            g.release()
            throw e
        }
        return g
    }

    /** Sinal sonoro curto ao iniciar (RF-SEG-002). */
    private fun sinalSonoro() {
        try {
            val tom = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tom.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            handler.postDelayed({ tom.release() }, 400)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Sinal sonoro indisponível", e)
        }
    }

    private fun adquirirWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "acta:gravacao").apply {
            setReferenceCounted(false)
            acquire(6L * 60 * 60 * 1000)
        }
    }

    // ---------- Chamadas (RF-CAP-008) ----------

    private fun registarDetecaoChamadas() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ouvinte = AudioManager.OnModeChangedListener { modo -> tratarModo(modo) }
            audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(this), ouvinte)
            ouvinteModo = ouvinte
        } else {
            handler.post(verificarModo)
        }
    }

    private fun removerDetecaoChamadas() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ouvinteModo as? AudioManager.OnModeChangedListener)?.let { audioManager.removeOnModeChangedListener(it) }
        } else {
            handler.removeCallbacks(verificarModo)
        }
        ouvinteModo = null
    }

    private fun tratarModo(modo: Int) {
        if (!ativo) return
        val emChamada = modo in MODOS_CHAMADA
        escopo.launch {
            mutex.withLock {
                if (emChamada && gravador != null) {
                    pausar(TipoMarca.CHAMADA)
                } else if (!emChamada && motivoPausa == TipoMarca.CHAMADA) {
                    // Só retoma sozinho se foi a chamada que pausou (não uma pausa manual).
                    retomar()
                }
            }
        }
    }

    // ---------- Notificação ----------

    private fun criarCanal() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Gravação em curso", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indicador persistente enquanto o ACTA grava"
                setShowBadge(false)
            }
        )
    }

    private fun construirNotificacao(texto: String): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun acao(acao: String, codigo: Int) = PendingIntent.getService(
            this, codigo, Intent(this, ServicoGravacao::class.java).setAction(acao),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ACTA")
            .setContentText(texto)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(abrir)
        if (ativo) {
            if (gravador != null) b.addAction(0, "Pausar", acao(ACAO_PAUSAR, 1))
            else b.addAction(0, "Retomar", acao(ACAO_RETOMAR, 2))
            b.addAction(0, "Terminar", acao(ACAO_TERMINAR, 3))
        }
        return b.build()
    }

    private fun entrarEmPrimeiroPlano(texto: String) {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, ID_NOTIFICACAO, construirNotificacao(texto), tipo)
    }

    private fun atualizarNotificacao() {
        val texto = when {
            gravador != null -> "● A gravar — reunião em curso"
            motivoPausa == TipoMarca.CHAMADA -> "Em pausa: chamada em curso. Retoma automaticamente."
            else -> "Em pausa"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ID_NOTIFICACAO, construirNotificacao(texto))
    }
}
