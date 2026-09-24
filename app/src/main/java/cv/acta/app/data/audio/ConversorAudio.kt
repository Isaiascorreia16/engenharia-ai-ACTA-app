package cv.acta.app.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Converte uma parte AAC ADTS (.aac) num contentor MPEG-4 (.m4a) SEM recodificar:
 * o MediaExtractor lê os blocos AAC um a um e o MediaMuxer escreve-os no novo contentor.
 * Nunca se corta o ficheiro por bytes: trabalha-se sempre com blocos de áudio completos.
 * Se o ADTS terminar a meio de um bloco (processo morto), o bloco incompleto é ignorado.
 */
object ConversorAudio {
    private const val TAG = "ConversorAudio"

    /** Duração de um bloco AAC-LC: 1024 amostras. */
    private const val AMOSTRAS_POR_BLOCO = 1024

    data class Resultado(val ficheiro: File, val duracaoMs: Long, val blocos: Int)

    @Throws(IOException::class)
    fun adtsParaM4a(origem: File, destino: File): Resultado {
        if (!origem.exists() || origem.length() == 0L) throw IOException("O ficheiro da parte não existe ou está vazio.")
        destino.parentFile?.mkdirs()
        if (destino.exists()) destino.delete()

        val extrator = MediaExtractor()
        var muxer: MediaMuxer? = null
        var blocos = 0
        var ultimoUs = 0L
        var taxa = 16_000
        try {
            extrator.setDataSource(origem.absolutePath)
            val faixa = (0 until extrator.trackCount).firstOrNull {
                extrator.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("A parte não contém áudio reconhecível.")
            extrator.selectTrack(faixa)
            val formato = extrator.getTrackFormat(faixa)
            if (formato.containsKey(MediaFormat.KEY_SAMPLE_RATE)) taxa = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val capacidade = if (formato.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxOf(formato.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 64 * 1024)
            } else {
                64 * 1024
            }
            val buffer = ByteBuffer.allocate(capacidade)
            val info = MediaCodec.BufferInfo()

            val m = MediaMuxer(destino.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val pista = m.addTrack(formato)
            m.start()

            while (true) {
                buffer.clear()
                val lidos = try {
                    extrator.readSampleData(buffer, 0)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Bloco final ilegível ignorado", e)
                    -1
                }
                if (lidos < 0) break
                val tempo = extrator.sampleTime
                info.set(0, lidos, tempo, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                m.writeSampleData(pista, buffer, info)
                ultimoUs = tempo
                blocos++
                if (!extrator.advance()) break
            }
            if (blocos == 0) throw IOException("A parte não tem blocos de áudio.")
            m.stop()
        } finally {
            try {
                muxer?.release()
            } catch (e: RuntimeException) {
                Log.w(TAG, "release do muxer falhou", e)
            }
            extrator.release()
        }
        val duracaoUltimoBlocoMs = AMOSTRAS_POR_BLOCO * 1000L / taxa
        return Resultado(destino, ultimoUs / 1000 + duracaoUltimoBlocoMs, blocos)
    }
}
