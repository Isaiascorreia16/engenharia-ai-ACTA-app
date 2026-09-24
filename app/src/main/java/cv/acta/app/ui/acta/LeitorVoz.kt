package cv.acta.app.ui.acta

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class EstadoVoz { A_INICIAR, PRONTO, A_FALAR, SEM_PORTUGUES, INDISPONIVEL }

/**
 * Leitura em voz com o TextToSpeech nativo (RF-TTS-001, 002).
 * Usa o canal de multimédia (USAGE_MEDIA) e só fala a pedido, para não interferir
 * com o leitor de ecrã, que usa o canal de acessibilidade (RF-TTS-006).
 */
class LeitorVoz(contexto: Context) : TextToSpeech.OnInitListener {

    private val estadoMutavel = MutableStateFlow(EstadoVoz.A_INICIAR)
    val estado: StateFlow<EstadoVoz> = estadoMutavel.asStateFlow()

    private val tts = TextToSpeech(contexto.applicationContext, this)
    private var pendentes = 0

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            estadoMutavel.value = EstadoVoz.INDISPONIVEL
            return
        }
        val candidatas = listOf(Locale.forLanguageTag("pt-PT"), Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("pt"))
        val escolhida = candidatas.firstOrNull { l ->
            val r = tts.setLanguage(l)
            r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (escolhida == null) {
            estadoMutavel.value = EstadoVoz.SEM_PORTUGUES
            return
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                estadoMutavel.value = EstadoVoz.A_FALAR
            }

            override fun onDone(utteranceId: String?) {
                pendentes--
                if (pendentes <= 0) estadoMutavel.value = EstadoVoz.PRONTO
            }

            @Deprecated("Obrigatório pela classe base")
            override fun onError(utteranceId: String?) {
                pendentes = 0
                estadoMutavel.value = EstadoVoz.PRONTO
            }
        })
        estadoMutavel.value = EstadoVoz.PRONTO
    }

    fun falar(paragrafos: List<String>, velocidade: Float, volume: Float) {
        if (estadoMutavel.value != EstadoVoz.PRONTO && estadoMutavel.value != EstadoVoz.A_FALAR) return
        tts.setSpeechRate(velocidade)
        val parametros = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        val limite = TextToSpeech.getMaxSpeechInputLength() - 1
        val blocos = paragrafos.flatMap { it.chunked(limite) }.filter { it.isNotBlank() }
        pendentes = blocos.size
        blocos.forEachIndexed { i, b ->
            tts.speak(b, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, parametros, "acta-$i")
        }
    }

    fun parar() {
        tts.stop()
        pendentes = 0
        if (estadoMutavel.value == EstadoVoz.A_FALAR) estadoMutavel.value = EstadoVoz.PRONTO
    }

    fun libertar() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        /** Abre o ecrã do motor de voz para instalar os dados de português. */
        fun intentInstalarVoz(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }
}
