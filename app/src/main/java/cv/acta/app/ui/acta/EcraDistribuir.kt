package cv.acta.app.ui.acta

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cv.acta.app.data.pdf.GeradorPdf
import cv.acta.app.domain.acta.TextoActa
import cv.acta.app.domain.modelo.EstadoActa
import cv.acta.app.domain.validacao.Presencas
import cv.acta.app.domain.validacao.ValidacaoParticipante
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.FaixaAvisoIA
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Distribuição (RF-DIS-001 a 004, 007; RF-PAR-009, 010): pré-visualização dos destinatários,
 * confirmação explícita, envio pelo cliente de e-mail do telemóvel e, ao regressar,
 * a pergunta "O e-mail foi enviado?". Só com "Sim" a acta passa a DISTRIBUIDA.
 */
@Composable
fun EcraDistribuir(actaId: Long, aoVoltar: () -> Unit) {
    val c = contentor()
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val acta by remember(actaId) { c.actas.acta(actaId) }.collectAsState(initial = null)
    val a = acta
    val participantes by remember(a?.reuniaoId ?: -1L) { c.reunioes.participantes(a?.reuniaoId ?: -1L) }.collectAsState(initial = emptyList())

    val selecionados = remember { mutableStateListOf<String>() }
    val adicionais = remember { mutableStateListOf<String>() }
    var inicializado by remember { mutableStateOf(false) }
    var novo by remember { mutableStateOf("") }
    var erroNovo by remember { mutableStateOf<String?>(null) }
    var perguntar by rememberSaveable { mutableStateOf(false) }
    var enviadosTexto by rememberSaveable { mutableStateOf("") }
    val enviados = enviadosTexto.split('\n').filter { it.isNotBlank() }
    var mensagem by remember { mutableStateOf<String?>(null) }

    val porOmissao = Presencas.destinatariosPorOmissao(participantes)
    LaunchedEffect(porOmissao) {
        if (!inicializado && porOmissao.isNotEmpty()) {
            selecionados.addAll(porOmissao)
            inicializado = true
        }
    }

    // Regresso do cliente de e-mail: qualquer que seja o resultado, pergunta-se ao utilizador.
    val lancador = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { perguntar = true }

    EcraBase(titulo = "Distribuir a acta", aoVoltar = aoVoltar) { padding ->
        if (a == null) return@EcraBase
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (a.estado != EstadoActa.APROVADA) {
                Cartao(destaque = true) {
                    Text("Só é possível distribuir uma acta aprovada. Estado atual: ${a.estado.rotulo}.")
                }
                return@Column
            }
            FaixaAvisoIA()
            Titulo("Destinatários (presentes com e-mail válido)")
            val todos = (porOmissao + adicionais).distinct()
            todos.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e in selecionados, onCheckedChange = { v -> if (v) selecionados.add(e) else selecionados.remove(e) })
                    Text(e + if (e in adicionais) " (adicionado)" else "")
                }
            }
            if (todos.isEmpty()) Text("Nenhum presente tem e-mail válido. Adicione destinatários abaixo.")

            OutlinedTextField(
                value = novo,
                onValueChange = { novo = it; erroNovo = null },
                label = { Text("Adicionar destinatário") },
                isError = erroNovo != null,
                supportingText = { erroNovo?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                val e = novo.trim()
                if (!ValidacaoParticipante.emailValido(e)) {
                    erroNovo = "E-mail inválido."
                } else {
                    if (e !in adicionais) adicionais.add(e)
                    if (e !in selecionados) selecionados.add(e)
                    novo = ""
                }
            }) { Text("Adicionar") }

            Cartao {
                Titulo("Pré-visualização")
                Text("Para: ${selecionados.joinToString().ifBlank { "—" }}")
                Text("Assunto: ${TextoActa.assunto(a.documento, a.versao)}")
                Text("Anexo: acta_reuniao${a.reuniaoId}_v${a.versao}.pdf")
                Text("Corpo:", style = MaterialTheme.typography.labelMedium)
                Text(TextoActa.corpoEmail(a.documento, a.versao, a.hash), style = MaterialTheme.typography.bodySmall)
            }
            mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = selecionados.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    escopo.launch {
                        val pdf = withContext(Dispatchers.IO) { GeradorPdf.gerar(contexto, a) }
                        val lista = selecionados.toList()
                        enviadosTexto = lista.joinToString("\n")
                        try {
                            lancador.launch(
                                Partilha.email(contexto, pdf, lista,TextoActa.assunto(a.documento, a.versao), TextoActa.corpoEmail(a.documento, a.versao, a.hash))
                            )
                        } catch (e: ActivityNotFoundException) {
                            mensagem = "Não foi encontrada nenhuma app de e-mail."
                        }
                    }
                },
            ) { Text("Confirmo: enviar a ${selecionados.size} destinatário(s)") }
        }
    }

    if (perguntar && a != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("O e-mail foi enviado?") },
            text = { Text("Confirme apenas se o e-mail saiu de facto. A acta ficará registada como distribuída a ${enviados.size} destinatário(s).") },
            confirmButton = {
                TextButton(onClick = {
                    perguntar = false
                    escopo.launch {
                        c.actas.registarDistribuicao(a, enviados)
                        aoVoltar()
                    }
                }) { Text("Sim") }
            },
            dismissButton = {
                TextButton(onClick = {
                    perguntar = false
                    mensagem = "A acta continua aprovada e não distribuída."
                }) { Text("Não") }
            },
        )
    }
}
