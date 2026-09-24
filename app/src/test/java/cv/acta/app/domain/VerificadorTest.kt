package cv.acta.app.domain

import cv.acta.app.domain.acta.AccaoGerada
import cv.acta.app.domain.acta.ActaGerada
import cv.acta.app.domain.acta.DeliberacaoGerada
import cv.acta.app.domain.acta.Verificador
import cv.acta.app.domain.modelo.Accao
import cv.acta.app.domain.modelo.Participante
import cv.acta.app.domain.modelo.Segmento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificadorTest {

    private val segmentos = listOf(
        Segmento("P1-001", 1, 1_000, 5_000, "P1-A", "Bom dia a todos. Vamos começar pela aprovação do orçamento."),
        Segmento("P1-002", 1, 5_000, 9_000, "P1-B", "Proponho que o orçamento de 2027 seja aprovado tal como está!"),
        Segmento("P1-003", 1, 9_000, 14_000, "P1-A", "Então fica aprovado. A Maria envia o documento até sexta-feira."),
    )
    private val participantes = listOf(
        Participante(1, "Maria Tavares", "Secretária", "maria@example.org"),
        Participante(2, "João Lopes", "Presidente", "joao@example.org"),
        Participante(3, "João Semedo", "Tesoureiro", "semedo@example.org"),
    )

    private fun gerada(d: DeliberacaoGerada? = null, a: AccaoGerada? = null) = ActaGerada(
        deliberacoes = listOfNotNull(d),
        accoes = listOfNotNull(a),
        sumulaExecutiva = "Resumo.",
    )

    @Test
    fun normalizacao() {
        assertEquals("proponho que o orçamento de 2027 seja aprovado", Verificador.normalizar("  Proponho, que o ORÇAMENTO de 2027 — seja   aprovado!"))
    }

    @Test
    fun itemValidoFicaVerificadoComAncoraCalculadaEmCodigo() {
        val c = Verificador.verificar(
            gerada(d = DeliberacaoGerada("Orçamento aprovado", null, listOf("P1-002", "P1-003"), "seja aprovado tal como está Então fica aprovado")),
            segmentos, participantes,
        )
        val d = c.deliberacoes.single()
        assertTrue(d.verificado)
        assertNull(d.motivoNaoVerificado)
        assertEquals(5_000L, d.ancoraInicioMs)
        assertEquals(14_000L, d.ancoraFimMs)
    }

    @Test
    fun idInexistenteFicaNaoVerificadoENaoEDescartado() {
        val c = Verificador.verificar(
            gerada(d = DeliberacaoGerada("Orçamento aprovado", null, listOf("P1-002", "P9-999"), "seja aprovado tal como está")),
            segmentos, participantes,
        )
        assertEquals(1, c.deliberacoes.size)
        assertFalse(c.deliberacoes.single().verificado)
        assertTrue(c.deliberacoes.single().motivoNaoVerificado!!.contains("P9-999"))
    }

    @Test
    fun citacaoInexistenteFicaNaoVerificada() {
        val c = Verificador.verificar(
            gerada(d = DeliberacaoGerada("Orçamento aprovado", null, listOf("P1-002"), "o orçamento foi aprovado por unanimidade")),
            segmentos, participantes,
        )
        val d = c.deliberacoes.single()
        assertFalse(d.verificado)
        assertEquals(5_000L, d.ancoraInicioMs)
    }

    @Test
    fun citacaoDeOutroSegmentoNaoConta() {
        // A citação existe na transcrição, mas não nos segmentos indicados.
        val c = Verificador.verificar(
            gerada(d = DeliberacaoGerada("x", null, listOf("P1-001"), "Então fica aprovado")),
            segmentos, participantes,
        )
        assertFalse(c.deliberacoes.single().verificado)
    }

    @Test
    fun responsavelQueNaoEParticipanteFicaPorAtribuir() {
        val c = Verificador.verificar(
            gerada(a = AccaoGerada("Carlos Pina", "Enviar o documento", "2026-09-25", listOf("P1-003"), "A Maria envia o documento até sexta-feira")),
            segmentos, participantes,
        )
        val a = c.accoes.single()
        assertEquals(Accao.POR_ATRIBUIR, a.responsavel)
        assertNull(a.responsavelId)
        assertTrue("a citação continua verificada", a.verificado)
    }

    @Test
    fun responsavelParcialUnicoEResolvido() {
        val c = Verificador.verificar(
            gerada(a = AccaoGerada("Maria", "Enviar o documento", "2026-09-25", listOf("P1-003"), "A Maria envia o documento até sexta-feira")),
            segmentos, participantes,
        )
        assertEquals("Maria Tavares", c.accoes.single().responsavel)
        assertEquals(1L, c.accoes.single().responsavelId)
    }

    @Test
    fun responsavelAmbiguoFicaPorAtribuir() {
        assertNull(Verificador.resolverResponsavel("João", participantes))
        assertNull(Verificador.resolverResponsavel(null, participantes))
        assertNull(Verificador.resolverResponsavel("Por atribuir", participantes))
        assertEquals(3L, Verificador.resolverResponsavel("joão semedo", participantes)?.id)
    }

    @Test
    fun prazoNaoIsoEDescartado() {
        val c = Verificador.verificar(
            gerada(a = AccaoGerada("Maria Tavares", "Enviar", "sexta-feira", listOf("P1-003"), "envia o documento até sexta-feira")),
            segmentos, participantes,
        )
        assertNull(c.accoes.single().prazo)
    }

    @Test
    fun sumulaLimitadaA250Palavras() {
        val longa = (1..300).joinToString(" ") { "palavra$it" }
        val c = Verificador.verificar(ActaGerada(sumulaExecutiva = longa), segmentos, participantes)
        assertEquals(250, c.sumulaExecutiva.removeSuffix("…").split(" ").size)
    }

    @Test
    fun reverificarDetetaSegmentoEditadoDepois() {
        val c = Verificador.verificar(
            gerada(d = DeliberacaoGerada("Orçamento aprovado", null, listOf("P1-002"), "seja aprovado tal como está")),
            segmentos, participantes,
        )
        val editados = segmentos.map { if (it.id == "P1-002") it.copy(texto = "Proponho adiar o orçamento.") else it }
        assertFalse(Verificador.reverificar(c, editados, participantes).deliberacoes.single().verificado)
    }
}
