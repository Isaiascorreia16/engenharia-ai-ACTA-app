package cv.acta.app.domain

import cv.acta.app.domain.modelo.Participacao
import cv.acta.app.domain.modelo.Participante
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Presenca
import cv.acta.app.domain.validacao.Presencas
import cv.acta.app.domain.validacao.ValidacaoParticipante
import cv.acta.app.domain.validacao.ValidacaoReuniao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacaoTest {

    @Test
    fun emailsValidosEInvalidos() {
        assertTrue(ValidacaoParticipante.emailValido("ana.silva@example.org"))
        assertTrue(ValidacaoParticipante.emailValido("rui+acta@gov.cv"))
        assertFalse(ValidacaoParticipante.emailValido("ana@"))
        assertFalse(ValidacaoParticipante.emailValido("ana.example.org"))
        assertFalse(ValidacaoParticipante.emailValido("ana@exemplo"))
        assertFalse(ValidacaoParticipante.emailValido("ana..silva@example.org"))
    }

    @Test
    fun telefoneE164() {
        assertTrue(ValidacaoParticipante.telefoneValido("+2389912345"))
        assertFalse(ValidacaoParticipante.telefoneValido("9912345"))
        assertFalse(ValidacaoParticipante.telefoneValido("+0123"))
    }

    @Test
    fun registoInvalidoIndicaOCampo() {
        val erros = ValidacaoParticipante.validar("", "Tesoureira", "errado", "123")
        assertEquals(
            setOf(ValidacaoParticipante.CAMPO_NOME, ValidacaoParticipante.CAMPO_EMAIL, ValidacaoParticipante.CAMPO_TELEFONE),
            erros.map { it.campo }.toSet(),
        )
    }

    @Test
    fun telefoneOpcional() {
        assertTrue(ValidacaoParticipante.validar("Ana", "Presidente", "ana@example.org", null).isEmpty())
        assertTrue(ValidacaoParticipante.validar("Ana", "Presidente", "ana@example.org", "").isEmpty())
    }

    @Test
    fun validacaoDaReuniao() {
        assertTrue(ValidacaoReuniao.validar("Reunião", "2026-09-24", "10:30").isEmpty())
        assertEquals(3, ValidacaoReuniao.validar("", "2026-02-30", "25:00").size)
    }

    private fun p(id: Long, nome: String, presenca: Presenca, consentiu: Boolean, email: String = "$nome@example.org") =
        ParticipanteNaReuniao(
            Participante(id, nome, "Membro", email),
            Participacao(1, id, presenca, consentiu),
        )

    @Test
    fun presentesSemConsentimento() {
        val lista = listOf(
            p(1, "ana", Presenca.PRESENTE, true),
            p(2, "rui", Presenca.PRESENTE, false),
            p(3, "eva", Presenca.AUSENTE, false),
            p(4, "leo", Presenca.REPRESENTADO, false),
        )
        assertEquals(listOf("rui", "leo"), Presencas.presentesSemConsentimento(lista))
    }

    @Test
    fun destinatariosSaoPresentesComEmailValido() {
        val lista = listOf(
            p(1, "ana", Presenca.PRESENTE, true),
            p(2, "rui", Presenca.AUSENTE, true),
            p(3, "eva", Presenca.PRESENTE, true, email = "invalido"),
        )
        assertEquals(listOf("ana@example.org"), Presencas.destinatariosPorOmissao(lista))
    }
}
