package cv.acta.app.domain

import cv.acta.app.domain.modelo.Segmento
import cv.acta.app.domain.transcricao.MontagemTranscricao
import cv.acta.app.domain.transcricao.SegmentoBruto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MontagemTranscricaoTest {

    @Test
    fun somaOInicioDaParteEPrefixaOsRotulos() {
        val brutos = listOf(
            SegmentoBruto("A", 0.0, 2.5, " Bom dia. "),
            SegmentoBruto("B", 2.5, 4.0, "Olá."),
            SegmentoBruto("A", 4.0, 5.0, "   "),
        )
        val s = MontagemTranscricao.montar(9, 2, 300_000, brutos)
        assertEquals(2, s.size)
        assertEquals("P2-001", s[0].id)
        assertEquals("P2-A", s[0].orador)
        assertEquals(300_000L, s[0].inicioMs)
        assertEquals(302_500L, s[0].fimMs)
        assertEquals("Bom dia.", s[0].texto)
        assertEquals("P2-B", s[1].orador)
        assertEquals("P2-002", s[1].id)
    }

    private fun seg(ini: Long, fim: Long, orador: String = "P1-A") = Segmento("x$ini", 1, ini, fim, orador, "t")

    @Test
    fun tempoDeFalaNaoContaSobreposicoesADobrar() {
        val l = listOf(seg(0, 10_000), seg(5_000, 15_000), seg(20_000, 30_000))
        assertEquals(25_000L, MontagemTranscricao.tempoDeFalaMs(l))
    }

    @Test
    fun menosDe60SegundosDeFalaNaoChega() {
        assertFalse(MontagemTranscricao.falaSuficiente(listOf(seg(0, 59_000))))
        assertTrue(MontagemTranscricao.falaSuficiente(listOf(seg(0, 30_000), seg(40_000, 70_000))))
    }

    @Test
    fun tempoPorOradorUsaONomeAtribuido() {
        val l = listOf(seg(0, 10_000, "P1-A"), seg(10_000, 15_000, "P2-B"), seg(15_000, 45_000, "P1-B"))
        val nomes = mapOf("P1-A" to "Ana", "P2-B" to "Ana", "P1-B" to "Rui")
        val t = MontagemTranscricao.tempoPorOrador(l) { nomes[it] ?: it }
        assertEquals(listOf("Rui" to 30_000L, "Ana" to 15_000L), t)
    }
}
