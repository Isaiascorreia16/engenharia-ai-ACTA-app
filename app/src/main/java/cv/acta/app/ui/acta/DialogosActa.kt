package cv.acta.app.ui.acta

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cv.acta.app.domain.modelo.Accao
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Segmento
import cv.acta.app.domain.util.Formatos
import cv.acta.app.domain.validacao.ErroCampo
import cv.acta.app.domain.validacao.ValidacaoReuniao
import cv.acta.app.ui.comum.CampoTexto
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Diálogo genérico para editar um ou mais campos de texto. */
@Composable
fun DialogoCampos(
    titulo: String,
    campos: List<Pair<String, String>>,
    aoFechar: () -> Unit,
    aoGuardar: (List<String>) -> Unit,
) {
    val valores = remember { campos.map { mutableStateOf(it.second) } }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                campos.forEachIndexed { i, (rotulo, _) ->
                    OutlinedTextField(
                        value = valores[i].value,
                        onValueChange = { valores[i].value = it },
                        label = { Text(rotulo) },
                        minLines = if (campos.size == 1) 6 else 2,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                if (campos.size == 1) {
                    Text("${Formatos.contarPalavras(valores[0].value)} palavras", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { aoGuardar(valores.map { it.value }) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Edição de uma ação: descrição, responsável (só participantes ou "Por atribuir") e prazo ISO. */
@Composable
fun DialogoAccao(
    accao: Accao,
    participantes: List<ParticipanteNaReuniao>,
    aoFechar: () -> Unit,
    aoGuardar: (Accao) -> Unit,
) {
    var descricao by remember { mutableStateOf(accao.descricao) }
    var prazo by remember { mutableStateOf(accao.prazo.orEmpty()) }
    var responsavelId by remember { mutableStateOf(accao.responsavelId) }
    var responsavel by remember { mutableStateOf(accao.responsavel) }
    var menu by remember { mutableStateOf(false) }
    var erroPrazo by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Editar ação") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CampoTexto(descricao, { descricao = it }, "Descrição", linhas = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Responsável: ")
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text(responsavel) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(Accao.POR_ATRIBUIR) }, onClick = {
                                responsavelId = null
                                responsavel = Accao.POR_ATRIBUIR
                                menu = false
                            })
                            participantes.forEach { p ->
                                DropdownMenuItem(text = { Text(p.participante.nome) }, onClick = {
                                    responsavelId = p.participante.id
                                    responsavel = p.participante.nome
                                    menu = false
                                })
                            }
                        }
                    }
                }
                CampoTexto(prazo, { prazo = it }, "Prazo (AAAA-MM-DD, vazio = sem prazo)", erro = erroPrazo)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = prazo.trim()
                if (p.isNotEmpty() && ValidacaoReuniao.validar("x", p, "00:00").any { it.campo == "data" }) {
                    erroPrazo = "Data inválida. Use AAAA-MM-DD."
                } else {
                    aoGuardar(accao.copy(descricao = descricao.trim(), prazo = p.ifEmpty { null }, responsavel = responsavel, responsavelId = responsavelId))
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Mostra os segmentos citados por um item, com o botão para os abrir na transcrição. */
@Composable
fun DialogoOrigem(
    citacao: String,
    segmentos: List<Segmento>,
    idsEmFalta: List<String>,
    nomeDe: (String) -> String,
    aoFechar: () -> Unit,
    abrirNaTranscricao: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Origem na transcrição") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Citação: «$citacao»", style = MaterialTheme.typography.bodyMedium)
                if (idsEmFalta.isNotEmpty()) {
                    Text("Segmentos inexistentes: ${idsEmFalta.joinToString()}", color = MaterialTheme.colorScheme.error)
                }
                segmentos.forEach { s ->
                    Text("${s.id} · ${Formatos.duracao(s.inicioMs)} · ${nomeDe(s.orador)}", style = MaterialTheme.typography.labelMedium)
                    Text(s.texto)
                    TextButton(onClick = { abrirNaTranscricao(s.id) }) { Text("Abrir na transcrição") }
                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

/** Pede ou confirma a data da próxima reunião e abre o calendário. */
@Composable
fun DialogoCalendario(
    dataInicial: String,
    horaInicial: String,
    aoFechar: () -> Unit,
    aoConfirmar: (inicioMs: Long) -> Unit,
) {
    var data by remember { mutableStateOf(dataInicial) }
    var hora by remember { mutableStateOf(horaInicial) }
    var erros by remember { mutableStateOf<List<ErroCampo>>(emptyList()) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Agendar próxima reunião") },
        text = {
            Column {
                if (dataInicial.isEmpty()) Text("A acta não indica a data da próxima reunião. Indique-a:")
                CampoTexto(data, { data = it }, "Data (AAAA-MM-DD)", erro = erros.firstOrNull { it.campo == "data" }?.mensagem)
                CampoTexto(hora, { hora = it }, "Hora (HH:MM)", erro = erros.firstOrNull { it.campo == "hora" }?.mensagem)
                Text("Duração prevista: 1 hora. Pode ajustar na app de calendário.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                erros = ValidacaoReuniao.validar("x", data, hora)
                if (erros.isEmpty()) aoConfirmar(Formatos.paraMs(data, hora))
            }) { Text("Abrir calendário") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Leitura em voz com controlos de velocidade e volume (RF-TTS-001, 002). */
@Composable
fun DialogoVoz(titulo: String, paragrafos: List<String>, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val c = contentor()
    val escopo = rememberCoroutineScope()
    val leitor = remember { LeitorVoz(contexto) }
    val estado by leitor.estado.collectAsState()
    var velocidade by remember { mutableFloatStateOf(1f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var aviso by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        velocidade = c.definicoes.ttsVelocidade.first()
        volume = c.definicoes.ttsVolume.first()
    }
    DisposableEffect(Unit) { onDispose { leitor.libertar() } }

    AlertDialog(
        onDismissRequest = {
            leitor.parar()
            aoFechar()
        },
        title = { Text(titulo) },
        text = {
            Column {
                when (estado) {
                    EstadoVoz.A_INICIAR -> Text("A preparar a voz…")
                    EstadoVoz.INDISPONIVEL -> Text("O motor de voz do telemóvel não está disponível.")
                    EstadoVoz.SEM_PORTUGUES -> {
                        Text(
                            "Não há voz em português instalada. Instale-a em Definições do Android > Sistema > Idiomas > " +
                                "Saída de texto para voz (ou nas definições do \"Serviços de voz da Google\").",
                        )
                        TextButton(onClick = {
                            try {
                                contexto.startActivity(LeitorVoz.intentInstalarVoz())
                            } catch (e: ActivityNotFoundException) {
                                aviso = "Abra as definições de texto para voz do Android manualmente."
                            }
                        }) { Text("Instalar voz") }
                    }
                    else -> {
                        Titulo("Velocidade: ${"%.1f".format(velocidade)}×")
                        Slider(
                            value = velocidade, onValueChange = { velocidade = it }, valueRange = 0.5f..2f, steps = 5,
                            modifier = Modifier.semantics { contentDescription = "Velocidade da voz" },
                        )
                        Titulo("Volume: ${(volume * 100).toInt()}%")
                        Slider(
                            value = volume, onValueChange = { volume = it }, valueRange = 0f..1f,
                            modifier = Modifier.semantics { contentDescription = "Volume da voz" },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                escopo.launch { c.definicoes.guardarVoz(velocidade, volume) }
                                leitor.falar(paragrafos, velocidade, volume)
                            }) { Text(if (estado == EstadoVoz.A_FALAR) "Recomeçar" else "Ouvir") }
                            OutlinedButton(enabled = estado == EstadoVoz.A_FALAR, onClick = { leitor.parar() }) { Text("Parar") }
                        }
                    }
                }
                aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                leitor.parar()
                aoFechar()
            }) { Text("Fechar") }
        },
    )
}
