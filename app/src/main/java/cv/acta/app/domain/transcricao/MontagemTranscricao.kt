package cv.acta.app.domain.transcricao

import cv.acta.app.domain.modelo.Segmento
import java.util.Locale
import kotlin.math.roundToLong

/** Segmento tal como vem da API (tempos em segundos, relativos ao início do ficheiro enviado). */
data class SegmentoBruto(
    val orador: String,
    val inicioS: Double,
    val fimS: Double,
    val texto: String,
)

object MontagemTranscricao {
    /** Mínimo de fala para gerar acta (RF-ACT-015). */
    const val FALA_MINIMA_MS = 60_000L

    fun prefixo(numeroParte: Int): String = "P$numeroParte-"

    fun rotulo(numeroParte: Int, oradorApi: String): String {
        val limpo = oradorApi.trim().ifBlank { "?" }
        return prefixo(numeroParte) + limpo
    }

    /**
     * Converte os segmentos de UM pedido em segmentos da reunião:
     * soma o início da parte aos tempos e prefixa os rótulos de orador ("A" → "P2-A"),
     * porque os rótulos só são válidos dentro de cada pedido.
     */
    fun montar(reuniaoId: Long, numeroParte: Int, inicioParteMs: Long, brutos: List<SegmentoBruto>): List<Segmento> =
        brutos
            .filter { it.texto.isNotBlank() }
            .sortedBy { it.inicioS }
            .mapIndexed { i, b ->
                val inicio = inicioParteMs + (b.inicioS * 1000).roundToLong()
                val fim = inicioParteMs + (b.fimS * 1000).roundToLong()
                Segmento(
                    id = String.format(Locale.ROOT, "%s%03d", prefixo(numeroParte), i + 1),
                    reuniaoId = reuniaoId,
                    inicioMs = inicio,
                    fimMs = maxOf(fim, inicio),
                    orador = rotulo(numeroParte, b.orador),
                    texto = b.texto.trim(),
                )
            }

    /** Tempo total de fala: união dos intervalos (sobreposições não contam a dobrar). */
    fun tempoDeFalaMs(segmentos: List<Segmento>): Long {
        var total = 0L
        var fimAtual = Long.MIN_VALUE
        var inicioAtual = Long.MIN_VALUE
        for (s in segmentos.sortedBy { it.inicioMs }) {
            if (s.inicioMs > fimAtual) {
                if (fimAtual > inicioAtual) total += fimAtual - inicioAtual
                inicioAtual = s.inicioMs
                fimAtual = s.fimMs
            } else if (s.fimMs > fimAtual) {
                fimAtual = s.fimMs
            }
        }
        if (fimAtual > inicioAtual) total += fimAtual - inicioAtual
        return total
    }

    fun falaSuficiente(segmentos: List<Segmento>): Boolean = tempoDeFalaMs(segmentos) >= FALA_MINIMA_MS

    /** Tempo de intervenção por orador (RF-DIA-011). [nomeDe] resolve o rótulo para o nome atribuído. */
    fun tempoPorOrador(segmentos: List<Segmento>, nomeDe: (String) -> String): List<Pair<String, Long>> =
        segmentos.groupBy { nomeDe(it.orador) }
            .mapValues { (_, l) -> l.sumOf { it.fimMs - it.inicioMs } }
            .toList()
            .sortedByDescending { it.second }
}
