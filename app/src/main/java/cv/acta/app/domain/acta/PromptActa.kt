package cv.acta.app.domain.acta

import cv.acta.app.domain.modelo.CabecalhoActa
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Presenca
import cv.acta.app.domain.modelo.Reuniao
import cv.acta.app.domain.modelo.Segmento
import cv.acta.app.domain.util.Formatos
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Construção do pedido ao LLM. Os dados de identificação (data, horas, local, presenças) vêm da BD
 * e são enviados só como CONTEXTO; o LLM não os devolve, para não ser convidado a inventá-los.
 */
object PromptActa {

    val INSTRUCOES: String = """
        És o secretário de uma reunião. Produz o conteúdo de uma acta em português europeu a partir da transcrição fornecida.
        Regras obrigatórias:
        1. Usa apenas informação presente na transcrição. Não inventes factos, nomes, datas, números nem decisões.
        2. Não produzas cabeçalho (título, data, horas, local, presenças): esses dados são preenchidos pelo sistema.
        3. ordemTrabalhos: infere os pontos discutidos, pela ordem em que surgem; para cada um, uma súmula objetiva.
        4. deliberacoes: só decisões efetivamente tomadas. Em votacao indica o resultado se tiver sido mencionado
           (por exemplo "Aprovado por unanimidade" ou "A favor: 3; contra: 0; abstenções: 1"); caso contrário, null.
        5. accoes: tarefas atribuídas. Em responsavel escreve exatamente o nome de um participante da lista; se não for claro
           quem é o responsável, usa null. Em prazo usa a data ISO 8601 (AAAA-MM-DD), resolvendo expressões relativas
           ("até sexta-feira", "na próxima semana") a partir da data da reunião indicada; se não houver prazo, null.
        6. Cada deliberação e cada ação tem segmentoIds (os ids dos segmentos em que se apoia; pelo menos um) e citacao:
           um excerto LITERAL e curto (entre 5 e 25 palavras) copiado exatamente de um desses segmentos, sem alterar palavras.
        7. Não indiques tempos nem minutos: identifica a origem só pelos ids dos segmentos.
        8. proximaReuniao: data (AAAA-MM-DD) ou data e hora (AAAA-MM-DDTHH:MM) se for mencionada; caso contrário, null.
        9. sumulaExecutiva: resumo da reunião com no máximo 250 palavras.
        10. Não infiras emoções, estados de espírito nem traços de personalidade dos participantes.
    """.trimIndent()

    private val DIAS = mapOf(
        DayOfWeek.MONDAY to "segunda-feira",
        DayOfWeek.TUESDAY to "terça-feira",
        DayOfWeek.WEDNESDAY to "quarta-feira",
        DayOfWeek.THURSDAY to "quinta-feira",
        DayOfWeek.FRIDAY to "sexta-feira",
        DayOfWeek.SATURDAY to "sábado",
        DayOfWeek.SUNDAY to "domingo",
    )

    fun entrada(
        dataReuniao: LocalDate,
        participantes: List<ParticipanteNaReuniao>,
        segmentos: List<Segmento>,
        nomeDe: (String) -> String,
    ): String = buildString {
        appendLine("Data da reunião: $dataReuniao (${DIAS.getValue(dataReuniao.dayOfWeek)})")
        appendLine()
        appendLine("Participantes (nome — função — presença):")
        participantes.forEach {
            appendLine("- ${it.participante.nome} — ${it.participante.funcao} — ${it.participacao.presenca.rotulo}")
        }
        appendLine()
        appendLine("Transcrição (cada linha: [id] orador: texto):")
        segmentos.sortedBy { it.inicioMs }.forEach { s ->
            appendLine("[${s.id}] ${nomeDe(s.orador)}: ${s.texto}")
        }
    }
}

/** Cabeçalho preenchido pelo código a partir da BD (nunca pelo LLM). */
object Cabecalho {
    fun construir(reuniao: Reuniao, participantes: List<ParticipanteNaReuniao>, zona: ZoneId = ZoneId.systemDefault()): CabecalhoActa {
        val inicio = reuniao.origemGravacaoMs ?: reuniao.inicioPrevistoMs
        val presentes = participantes.filter { it.participacao.presenca == Presenca.PRESENTE || it.participacao.presenca == Presenca.REPRESENTADO }
            .map { if (it.participacao.presenca == Presenca.REPRESENTADO) "${it.participante.nome} (representado)" else it.participante.nome }
        val ausentes = participantes.filter { it.participacao.presenca == Presenca.AUSENTE || it.participacao.presenca == Presenca.JUSTIFICADO }
            .map { if (it.participacao.presenca == Presenca.JUSTIFICADO) "${it.participante.nome} (falta justificada)" else it.participante.nome }
        return CabecalhoActa(
            reuniaoId = reuniao.id,
            titulo = reuniao.titulo,
            data = Formatos.dataIso(inicio, zona),
            horaInicio = Formatos.hora(inicio, zona),
            horaFim = reuniao.fimGravacaoMs?.let { Formatos.hora(it, zona) } ?: "—",
            local = reuniao.local.ifBlank { "—" },
            presentes = presentes,
            ausentes = ausentes,
        )
    }
}
