package cv.acta.app.domain.validacao

import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Presenca

data class ErroCampo(val campo: String, val mensagem: String)

/** Validação de participantes (RF-PAR-001, 002, 003). */
object ValidacaoParticipante {
    const val CAMPO_NOME = "nome"
    const val CAMPO_FUNCAO = "funcao"
    const val CAMPO_EMAIL = "email"
    const val CAMPO_TELEFONE = "telefone"

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$")
    private val E164 = Regex("^\\+[1-9]\\d{1,14}$")

    fun emailValido(email: String): Boolean = EMAIL.matches(email.trim()) && !email.contains("..")

    fun telefoneValido(telefone: String): Boolean = E164.matches(telefone.trim())

    /** Devolve a lista de erros, cada um com o campo em causa. Lista vazia = registo válido. */
    fun validar(nome: String, funcao: String, email: String, telefone: String?): List<ErroCampo> {
        val erros = mutableListOf<ErroCampo>()
        if (nome.isBlank()) erros += ErroCampo(CAMPO_NOME, "O nome é obrigatório.")
        if (funcao.isBlank()) erros += ErroCampo(CAMPO_FUNCAO, "A função é obrigatória.")
        if (email.isBlank()) {
            erros += ErroCampo(CAMPO_EMAIL, "O e-mail é obrigatório.")
        } else if (!emailValido(email)) {
            erros += ErroCampo(CAMPO_EMAIL, "E-mail inválido (exemplo: nome@dominio.cv).")
        }
        if (!telefone.isNullOrBlank() && !telefoneValido(telefone)) {
            erros += ErroCampo(CAMPO_TELEFONE, "Telefone inválido. Use o formato internacional E.164, por exemplo +2389912345.")
        }
        return erros
    }
}

object ValidacaoReuniao {
    private val DATA = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val HORA = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun validar(titulo: String, data: String, hora: String): List<ErroCampo> {
        val erros = mutableListOf<ErroCampo>()
        if (titulo.isBlank()) erros += ErroCampo("titulo", "O título é obrigatório.")
        if (!DATA.matches(data.trim()) || !Datas.dataValida(data.trim())) erros += ErroCampo("data", "Data inválida. Use AAAA-MM-DD.")
        if (!HORA.matches(hora.trim())) erros += ErroCampo("hora", "Hora inválida. Use HH:MM.")
        return erros
    }
}

object Datas {
    fun dataValida(iso: String): Boolean = try {
        java.time.LocalDate.parse(iso)
        true
    } catch (e: java.time.format.DateTimeParseException) {
        false
    }
}

/** Presença e consentimento (RF-PAR-004, 006, 007). */
object Presencas {
    /** Quem está na sala: presente ou representado. */
    fun estaPresente(p: Presenca): Boolean = p == Presenca.PRESENTE || p == Presenca.REPRESENTADO

    fun presentesSemConsentimento(lista: List<ParticipanteNaReuniao>): List<String> =
        lista.filter { estaPresente(it.participacao.presenca) && !it.participacao.consentimento }
            .map { it.participante.nome }

    /** Lista de distribuição por omissão (RF-PAR-009): presentes com e-mail válido. */
    fun destinatariosPorOmissao(lista: List<ParticipanteNaReuniao>): List<String> =
        lista.filter { estaPresente(it.participacao.presenca) && ValidacaoParticipante.emailValido(it.participante.email) }
            .map { it.participante.email.trim() }
            .distinct()
}
