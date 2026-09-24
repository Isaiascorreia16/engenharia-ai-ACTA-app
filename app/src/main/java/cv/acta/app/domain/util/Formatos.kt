package cv.acta.app.domain.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Formatos {
    /** 65_000 → "01:05"; acima de uma hora → "1:01:05". */
    fun duracao(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private val DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm")
    private val DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun local(ms: Long, zona: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zona)

    fun dataIso(ms: Long, zona: ZoneId = ZoneId.systemDefault()): String = local(ms, zona).format(DATA)
    fun hora(ms: Long, zona: ZoneId = ZoneId.systemDefault()): String = local(ms, zona).format(HORA)
    fun dataHora(ms: Long, zona: ZoneId = ZoneId.systemDefault()): String = local(ms, zona).format(DATA_HORA)

    fun paraMs(dataIso: String, hora: String, zona: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.parse("${dataIso.trim()}T${hora.trim()}").atZone(zona).toInstant().toEpochMilli()

    fun contarPalavras(texto: String): Int = texto.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
