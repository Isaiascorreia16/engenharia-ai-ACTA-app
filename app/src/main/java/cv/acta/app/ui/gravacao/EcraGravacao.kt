package cv.acta.app.ui.gravacao

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cv.acta.app.data.audio.EstadoGravacao
import cv.acta.app.data.audio.ServicoGravacao
import cv.acta.app.domain.modelo.TipoMarca
import cv.acta.app.domain.util.Formatos
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.DialogoConfirmacao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.tema.VermelhoGravacao
import kotlinx.coroutines.delay

@Composable
fun EcraGravacao(reuniaoId: Long, aoVoltar: () -> Unit, aoTerminar: () -> Unit) {
    val contexto = LocalContext.current
    val estado by EstadoGravacao.estado.collectAsState()
    var agora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmarFim by remember { mutableStateOf(false) }
    var jaIniciou by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            agora = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    // Quando a sessão acaba (terminada ou falhada), volta ao ecrã da reunião.
    LaunchedEffect(estado.ativa) {
        if (estado.ativa) jaIniciou = true
        else if (jaIniciou && estado.erro == null) aoTerminar()
    }

    val decorrido = estado.acumuladoMs + (estado.inicioParteRealtime?.let { agora - it } ?: 0)

    EcraBase(titulo = "Gravação", aoVoltar = aoVoltar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val outraReuniao = estado.ativa && estado.reuniaoId != reuniaoId
            when {
                estado.erro != null -> Cartao(destaque = true) { Text(estado.erro!!) }
                outraReuniao -> Text("Está a decorrer uma gravação noutra reunião.")
                !estado.ativa -> Text("A iniciar…")
                estado.emPausa -> Text(
                    if (estado.motivoPausa == TipoMarca.CHAMADA) "Em pausa: chamada em curso. Retoma automaticamente no fim da chamada."
                    else "Em pausa",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                else -> Text(
                    "● A gravar",
                    color = VermelhoGravacao,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Text(Formatos.duracao(decorrido), style = MaterialTheme.typography.displayLarge)
            if (estado.ativa) Text("Parte ${estado.parteAtual} · cada parte tem até 5 minutos", style = MaterialTheme.typography.bodySmall)

            if (estado.ativa && !outraReuniao) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (estado.emPausa) {
                        OutlinedButton(onClick = { ServicoGravacao.enviar(contexto, ServicoGravacao.ACAO_RETOMAR) }) { Text("Retomar") }
                    } else {
                        OutlinedButton(onClick = { ServicoGravacao.enviar(contexto, ServicoGravacao.ACAO_PAUSAR) }) { Text("Pausar") }
                    }
                    Button(
                        onClick = { confirmarFim = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VermelhoGravacao),
                    ) { Text("Terminar") }
                }
            }
            if (!estado.ativa && estado.erro != null) {
                Button(onClick = aoTerminar, modifier = Modifier.fillMaxWidth()) { Text("Voltar à reunião") }
            }
            Text(
                "Pode bloquear o ecrã ou mudar de app: a gravação continua e o indicador fica na barra de notificações.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (confirmarFim) {
        DialogoConfirmacao(
            titulo = "Terminar gravação",
            texto = "Terminar a gravação desta sessão? Depois pode transcrevê-la no ecrã da reunião.",
            confirmar = "Terminar",
            aoConfirmar = {
                confirmarFim = false
                ServicoGravacao.enviar(contexto, ServicoGravacao.ACAO_TERMINAR)
            },
            aoCancelar = { confirmarFim = false },
        )
    }
}
