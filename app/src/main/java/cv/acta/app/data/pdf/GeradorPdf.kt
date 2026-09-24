package cv.acta.app.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import cv.acta.app.domain.modelo.Accao
import cv.acta.app.domain.modelo.ActaVersao
import cv.acta.app.domain.modelo.MENCAO_IA
import cv.acta.app.domain.util.Formatos
import java.io.File
import java.io.FileOutputStream

/**
 * PDF da acta com `android.graphics.pdf.PdfDocument` (RF-ACT-012, parcial: não é PDF/A).
 * Cada página tem no rodapé a menção de IA e o SHA-256 (ou a indicação de que é rascunho).
 */
object GeradorPdf {
    private const val LARGURA_PAGINA = 595 // A4 em pontos
    private const val ALTURA_PAGINA = 842
    private const val MARGEM = 50f
    private const val ALTURA_RODAPE = 70f
    private val LARGURA_TEXTO = (LARGURA_PAGINA - 2 * MARGEM).toInt()

    private fun pincel(tamanho: Float, negrito: Boolean = false, cor: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tamanho
        color = cor
        typeface = if (negrito) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private val titulo = pincel(16f, negrito = true)
    private val seccao = pincel(12.5f, negrito = true, cor = Color.rgb(0x1B, 0x4F, 0x72))
    private val corpo = pincel(10.5f)
    private val pequeno = pincel(9f, cor = Color.DKGRAY)
    private val alerta = pincel(9.5f, negrito = true, cor = Color.rgb(0x8A, 0x55, 0x00))
    private val rodape = pincel(7.5f, cor = Color.DKGRAY)

    private class Escritor(private val doc: PdfDocument, private val desenharRodape: (Canvas, Int) -> Unit) {
        private var pagina: PdfDocument.Page? = null
        private var numero = 0
        private var y = MARGEM

        fun novaPagina() {
            terminar()
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA_PAGINA, ALTURA_PAGINA, numero).create())
            y = MARGEM
        }

        fun terminar() {
            pagina?.let {
                desenharRodape(it.canvas, numero)
                doc.finishPage(it)
            }
            pagina = null
        }

        fun paragrafo(textoOriginal: String, p: TextPaint, depois: Float = 6f, recuo: Float = 0f) {
            if (pagina == null) novaPagina()
            val texto = textoOriginal.replace('\n', ' ').trim()
            if (texto.isEmpty()) return
            val largura = (LARGURA_TEXTO - recuo).toInt()
            val layout = StaticLayout.Builder.obtain(texto, 0, texto.length, p, largura)
                .setLineSpacing(0f, 1.15f)
                .build()
            for (i in 0 until layout.lineCount) {
                val altura = (layout.getLineBottom(i) - layout.getLineTop(i)).toFloat()
                if (y + altura > ALTURA_PAGINA - ALTURA_RODAPE) novaPagina()
                val base = y + (layout.getLineBaseline(i) - layout.getLineTop(i))
                pagina!!.canvas.drawText(texto, layout.getLineStart(i), layout.getLineEnd(i), MARGEM + recuo, base, p)
                y += altura
            }
            y += depois
        }

        fun espaco(pts: Float) {
            y += pts
        }
    }

    fun gerar(contexto: Context, acta: ActaVersao): File {
        val pasta = File(contexto.cacheDir, "pdf").apply { mkdirs() }
        val ficheiro = File(pasta, "acta_reuniao${acta.reuniaoId}_v${acta.versao}.pdf")
        val doc = PdfDocument()
        val linhaHash = acta.hash?.let { "SHA-256: $it" } ?: "Versão não aprovada (${acta.estado.rotulo}): sem hash."

        val e = Escritor(doc) { canvas, n ->
            val base = ALTURA_PAGINA - ALTURA_RODAPE + 18f
            canvas.drawLine(MARGEM, base - 12f, LARGURA_PAGINA - MARGEM, base - 12f, rodape)
            canvas.drawText(MENCAO_IA, MARGEM, base, rodape)
            canvas.drawText(linhaHash, MARGEM, base + 11f, rodape)
            canvas.drawText("Acta v${acta.versao} · página $n", MARGEM, base + 22f, rodape)
        }

        val c = acta.documento.cabecalho
        val conteudo = acta.documento.conteudo
        e.paragrafo("ACTA — ${c.titulo}", titulo, depois = 4f)
        e.paragrafo("Versão ${acta.versao} · ${acta.estado.rotulo}" + (acta.aprovadaEmMs?.let { " · aprovada em ${Formatos.dataHora(it)}" } ?: ""), pequeno)
        e.paragrafo(MENCAO_IA, alerta, depois = 10f)

        e.paragrafo("Identificação", seccao)
        e.paragrafo("Data: ${c.data}   ·   Hora: ${c.horaInicio} – ${c.horaFim}   ·   Local: ${c.local}", corpo)
        e.paragrafo("Presentes: ${c.presentes.joinToString(", ").ifBlank { "—" }}", corpo)
        e.paragrafo("Ausentes: ${c.ausentes.joinToString(", ").ifBlank { "—" }}", corpo, depois = 10f)

        e.paragrafo("Ordem de trabalhos", seccao)
        if (conteudo.ordemTrabalhos.isEmpty()) e.paragrafo("—", corpo)
        conteudo.ordemTrabalhos.forEachIndexed { i, p ->
            e.paragrafo("${i + 1}. ${p.titulo}", pincel(10.5f, negrito = true), depois = 2f)
            e.paragrafo(p.sumula, corpo, recuo = 12f)
        }
        e.espaco(4f)

        e.paragrafo("Deliberações", seccao)
        if (conteudo.deliberacoes.isEmpty()) e.paragrafo("Não foram registadas deliberações.", corpo)
        conteudo.deliberacoes.forEachIndexed { i, d ->
            e.paragrafo("${i + 1}. ${d.texto}", corpo, depois = 2f)
            d.votacao?.let { e.paragrafo("Votação: $it", corpo, depois = 2f, recuo = 12f) }
            e.paragrafo(origem(d.ancoraInicioMs, d.ancoraFimMs, d.segmentoIds, d.citacao), pequeno, depois = 2f, recuo = 12f)
            if (!d.verificado) e.paragrafo("NÃO VERIFICADO: ${d.motivoNaoVerificado.orEmpty()}", alerta, recuo = 12f)
            e.espaco(4f)
        }

        e.paragrafo("Plano de ações", seccao)
        if (conteudo.accoes.isEmpty()) e.paragrafo("Não foram registadas ações.", corpo)
        conteudo.accoes.forEachIndexed { i, a ->
            val resp = if (a.responsavel == Accao.POR_ATRIBUIR) "POR ATRIBUIR" else a.responsavel
            e.paragrafo("${i + 1}. ${a.descricao}", corpo, depois = 2f)
            e.paragrafo("Responsável: $resp   ·   Prazo: ${a.prazo ?: "sem prazo"}", corpo, depois = 2f, recuo = 12f)
            e.paragrafo(origem(a.ancoraInicioMs, a.ancoraFimMs, a.segmentoIds, a.citacao), pequeno, depois = 2f, recuo = 12f)
            if (!a.verificado) e.paragrafo("NÃO VERIFICADO: ${a.motivoNaoVerificado.orEmpty()}", alerta, recuo = 12f)
            e.espaco(4f)
        }

        e.paragrafo("Próxima reunião", seccao)
        e.paragrafo(conteudo.proximaReuniao?.replace('T', ' ') ?: "Não foi marcada.", corpo, depois = 10f)

        e.paragrafo("Súmula executiva", seccao)
        e.paragrafo(conteudo.sumulaExecutiva, corpo, depois = 10f)

        acta.hash?.let {
            e.paragrafo("Integridade", seccao)
            e.paragrafo("Conteúdo fixado na aprovação. Resumo criptográfico SHA-256 do documento: $it", pequeno)
        }
        e.terminar()

        FileOutputStream(ficheiro).use { doc.writeTo(it) }
        doc.close()
        return ficheiro
    }

    private fun origem(inicio: Long?, fim: Long?, ids: List<String>, citacao: String): String {
        val tempo = if (inicio != null && fim != null) "${Formatos.duracao(inicio)}–${Formatos.duracao(fim)}" else "?"
        return "Origem: [$tempo] segmentos ${ids.joinToString(", ")} — «$citacao»"
    }
}
