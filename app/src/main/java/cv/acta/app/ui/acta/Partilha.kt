package cv.acta.app.ui.acta

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import java.io.File

/** Intents para e-mail, partilha nativa e calendário. O envio é feito pelas apps do telemóvel. */
object Partilha {

    fun uri(contexto: Context, ficheiro: File): Uri =
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", ficheiro)

    /** ACTION_SEND com EXTRA_EMAIL, o PDF via FileProvider e a súmula no corpo (RF-DIS-004). */
    fun email(contexto: Context, pdf: File, destinatarios: List<String>, assunto: String, corpo: String): Intent {
        val u = uri(contexto, pdf)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, destinatarios.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, assunto)
            putExtra(Intent.EXTRA_TEXT, corpo)
            putExtra(Intent.EXTRA_STREAM, u)
            clipData = ClipData.newRawUri(pdf.name, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(envio, "Enviar a acta por e-mail")
    }

    /** Folha de partilha nativa do Android (RF-DIS-010). */
    fun partilhar(contexto: Context, pdf: File, titulo: String): Intent {
        val u = uri(contexto, pdf)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, u)
            putExtra(Intent.EXTRA_SUBJECT, titulo)
            clipData = ClipData.newRawUri(pdf.name, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(envio, "Partilhar a acta")
    }

    /** ACTION_INSERT em CalendarContract.Events: a app de calendário mostra o evento para o utilizador confirmar. */
    fun calendario(titulo: String, inicioMs: Long, fimMs: Long, convidados: List<String>, descricao: String): Intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, titulo)
            .putExtra(CalendarContract.Events.DESCRIPTION, descricao)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, inicioMs)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, fimMs)
            .putExtra(Intent.EXTRA_EMAIL, convidados.joinToString(","))
}
