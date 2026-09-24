package cv.acta.app.ui.aviso

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cv.acta.app.data.audio.EspacoLivre
import cv.acta.app.data.audio.ServicoGravacao
import cv.acta.app.domain.validacao.Presencas
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor

private val CAIXAS = listOf(
    "Informei todos os presentes",
    "Todos consentiram",
    "Os presentes sabem que o áudio será enviado para processamento na OpenAI",
    "Sei que gravar sem consentimento é ilícito",
)

/** Aviso e confirmação antes de gravar (RF-SEG-001), com alerta de consentimento (RF-PAR-007). */
@Composable
fun EcraAviso(reuniaoId: Long, aoVoltar: () -> Unit, aoIniciar: () -> Unit) {
    val c = contentor()
    val contexto = LocalContext.current
    val participantes by remember(reuniaoId) { c.reunioes.participantes(reuniaoId) }.collectAsState(initial = emptyList())
    val marcadas = remember { mutableStateListOf(false, false, false, false) }
    var mensagem by remember { mutableStateOf<String?>(null) }

    val permissoes = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun iniciar() {
        val livres = EspacoLivre.bytesLivres(contexto.filesDir)
        if (livres < EspacoLivre.MINIMO_BYTES) {
            mensagem = "Espaço livre insuficiente: ${livres / (1024 * 1024)} MB. São precisos pelo menos 500 MB para gravar."
            return
        }
        ServicoGravacao.iniciar(contexto, reuniaoId)
        aoIniciar()
    }

    val pedido = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        if (resultado[Manifest.permission.RECORD_AUDIO] == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && resultado[Manifest.permission.POST_NOTIFICATIONS] != true) {
                mensagem = "Sem permissão de notificações o indicador de gravação pode não aparecer na barra. A gravação continua a funcionar."
            }
            iniciar()
        } else {
            mensagem = "Sem acesso ao microfone não é possível gravar. Pode conceder a permissão em Definições do Android > Apps > ACTA > Permissões."
        }
    }

    EcraBase(titulo = "Antes de gravar", aoVoltar = aoVoltar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Gravar uma reunião envolve dados pessoais (a voz das pessoas). Confirme cada ponto antes de começar.",
                style = MaterialTheme.typography.bodyLarge,
            )
            val semConsentimento = Presencas.presentesSemConsentimento(participantes)
            if (semConsentimento.isNotEmpty()) {
                Cartao(Modifier.padding(vertical = 8.dp), destaque = true) {
                    Titulo("Consentimento em falta")
                    Text("Os seguintes presentes não têm consentimento registado: ${semConsentimento.joinToString()}.")
                }
            }
            CAIXAS.forEachIndexed { i, texto ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = marcadas[i], role = Role.Checkbox, onValueChange = { marcadas[i] = it })
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = marcadas[i], onCheckedChange = null)
                    Text(texto)
                }
            }
            Cartao(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Permissões: o ACTA vai pedir acesso ao microfone (para gravar) e, no Android 13 ou superior, " +
                        "a notificações (para mostrar o indicador de gravação enquanto a app está em segundo plano).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            Button(
                enabled = marcadas.all { it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    val emFalta = permissoes.filter {
                        ContextCompat.checkSelfPermission(contexto, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (emFalta.isEmpty()) iniciar() else pedido.launch(emFalta.toTypedArray())
                },
            ) { Text("Iniciar gravação") }
        }
    }
}
