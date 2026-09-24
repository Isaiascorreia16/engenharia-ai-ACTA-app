package cv.acta.app.domain.acta

import cv.acta.app.domain.modelo.Accao
import cv.acta.app.domain.modelo.DocumentoActa
import cv.acta.app.domain.modelo.MENCAO_IA

/** Textos derivados da acta: leitura em voz, assunto e corpo do e-mail. */
object TextoActa {

    fun assunto(doc: DocumentoActa, versao: Int): String =
        "Acta — ${doc.cabecalho.titulo} (${doc.cabecalho.data}) — versão $versao"

    fun corpoEmail(doc: DocumentoActa, versao: Int, hash: String?): String = buildString {
        appendLine("Segue em anexo a acta da reunião \"${doc.cabecalho.titulo}\" de ${doc.cabecalho.data} (versão $versao).")
        appendLine()
        appendLine("Súmula executiva:")
        appendLine(doc.conteudo.sumulaExecutiva)
        appendLine()
        hash?.let { appendLine("SHA-256 da acta aprovada: $it") }
        appendLine()
        append(MENCAO_IA)
    }

    fun sumulaParaVoz(doc: DocumentoActa): String =
        "Súmula executiva da reunião ${doc.cabecalho.titulo}. ${doc.conteudo.sumulaExecutiva} $MENCAO_IA"

    /** Texto corrido para TextToSpeech, dividido em parágrafos curtos. */
    fun actaParaVoz(doc: DocumentoActa): List<String> = buildList {
        val c = doc.cabecalho
        add("$MENCAO_IA")
        add("Acta da reunião ${c.titulo}, realizada em ${c.data}, das ${c.horaInicio} às ${c.horaFim}, em ${c.local}.")
        if (c.presentes.isNotEmpty()) add("Presentes: ${c.presentes.joinToString(", ")}.")
        if (c.ausentes.isNotEmpty()) add("Ausentes: ${c.ausentes.joinToString(", ")}.")
        val conteudo = doc.conteudo
        if (conteudo.ordemTrabalhos.isNotEmpty()) {
            add("Ordem de trabalhos.")
            conteudo.ordemTrabalhos.forEachIndexed { i, p -> add("Ponto ${i + 1}: ${p.titulo}. ${p.sumula}") }
        }
        if (conteudo.deliberacoes.isNotEmpty()) {
            add("Deliberações.")
            conteudo.deliberacoes.forEachIndexed { i, d ->
                add("Deliberação ${i + 1}: ${d.texto}." + (d.votacao?.let { " Votação: $it." } ?: "") + if (!d.verificado) " Atenção: item não verificado." else "")
            }
        }
        if (conteudo.accoes.isNotEmpty()) {
            add("Plano de ações.")
            conteudo.accoes.forEachIndexed { i, a ->
                val resp = if (a.responsavel == Accao.POR_ATRIBUIR) "responsável por atribuir" else "responsável ${a.responsavel}"
                add("Ação ${i + 1}: ${a.descricao}; $resp" + (a.prazo?.let { "; prazo $it" } ?: "; sem prazo") + ".")
            }
        }
        conteudo.proximaReuniao?.let { add("Próxima reunião: ${it.replace('T', ' ')}.") }
        add("Súmula executiva. ${conteudo.sumulaExecutiva}")
    }
}
