package cv.acta.app.data.audio

import android.os.StatFs
import cv.acta.app.domain.modelo.TipoMarca
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class EstadoGravacaoUi(
    val ativa: Boolean = false,
    val reuniaoId: Long? = null,
    val emPausa: Boolean = false,
    val motivoPausa: TipoMarca? = null,
    /** Áudio gravado nas partes já fechadas desta sessão. */
    val acumuladoMs: Long = 0,
    /** `SystemClock.elapsedRealtime()` no início da parte em curso; null se em pausa. */
    val inicioParteRealtime: Long? = null,
    val parteAtual: Int = 0,
    val erro: String? = null,
)

/** Estado da gravação partilhado entre o serviço e a interface (o serviço corre no mesmo processo). */
object EstadoGravacao {
    private val mutavel = MutableStateFlow(EstadoGravacaoUi())
    val estado: StateFlow<EstadoGravacaoUi> = mutavel.asStateFlow()

    internal fun atualizar(f: (EstadoGravacaoUi) -> EstadoGravacaoUi) {
        mutavel.value = f(mutavel.value)
    }
}

object EspacoLivre {
    const val MINIMO_BYTES = 500L * 1024 * 1024

    fun bytesLivres(pasta: File): Long = StatFs(pasta.absolutePath).availableBytes
}
