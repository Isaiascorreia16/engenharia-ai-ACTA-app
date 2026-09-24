package cv.acta.app.ui.definicoes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.DialogoConfirmacao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun EcraDefinicoes(aoVoltar: () -> Unit, aposApagarTudo: () -> Unit) {
    val c = contentor()
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val temChave by c.definicoes.temChave.collectAsState(initial = false)
    var chave by remember { mutableStateOf("") }
    var modelo by remember { mutableStateOf("") }
    var autor by remember { mutableStateOf("") }
    var passoApagar by remember { mutableStateOf(0) }
    var confirmarDemo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        modelo = c.definicoes.modeloTexto.first()
        autor = c.definicoes.autorEdicoes.first()
    }

    EcraBase(titulo = "Definições", aoVoltar = aoVoltar, snackbar = snackbar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cartao {
                Titulo("Chave da API da OpenAI")
                Text(
                    if (temChave) "Estado: chave configurada (guardada cifrada com o Android Keystore)."
                    else "Estado: sem chave. A gravação e a transcrição precisam de uma chave de uma conta OpenAI com saldo.",
                )
                OutlinedTextField(
                    value = chave,
                    onValueChange = { chave = it },
                    label = { Text(if (temChave) "Substituir chave" else "Chave (sk-…)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = chave.isNotBlank(), onClick = {
                        escopo.launch {
                            c.definicoes.guardarChave(chave)
                            chave = ""
                            snackbar.showSnackbar("Chave guardada e cifrada.")
                        }
                    }) { Text("Guardar") }
                    if (temChave) {
                        OutlinedButton(onClick = {
                            escopo.launch {
                                c.definicoes.apagarChave()
                                snackbar.showSnackbar("Chave removida.")
                            }
                        }) { Text("Remover chave") }
                    }
                }
                Text(
                    "A chave nunca é mostrada, registada em logs nem incluída em cópias de segurança.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Cartao {
                Titulo("Modelo de texto (acta)")
                OutlinedTextField(value = modelo, onValueChange = { modelo = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Por omissão: gpt-6-sol (equilíbrio entre qualidade e custo).", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    escopo.launch {
                        c.definicoes.guardarModelo(modelo)
                        snackbar.showSnackbar("Modelo guardado.")
                    }
                }) { Text("Guardar modelo") }
            }

            Cartao {
                Titulo("Autor das edições")
                Text("Nome registado quando edita segmentos da transcrição (RF-ASR-013).", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = autor, onValueChange = { autor = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    escopo.launch {
                        c.definicoes.guardarAutor(autor)
                        snackbar.showSnackbar("Autor guardado.")
                    }
                }) { Text("Guardar autor") }
            }

            Cartao {
                Titulo("Dados")
                OutlinedButton(onClick = { confirmarDemo = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Apagar dados de exemplo (fictícios)")
                }
                OutlinedButton(onClick = {
                    escopo.launch {
                        val id = c.demo.carregar()
                        snackbar.showSnackbar("Reunião de exemplo carregada (id $id).")
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Carregar reunião de exemplo") }
                Button(
                    onClick = { passoApagar = 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Apagar todos os dados") }
            }
        }
    }

    if (confirmarDemo) {
        DialogoConfirmacao(
            titulo = "Apagar dados de exemplo",
            texto = "Apaga todas as reuniões marcadas como fictícias.",
            confirmar = "Apagar",
            aoConfirmar = {
                confirmarDemo = false
                escopo.launch {
                    val n = c.demo.apagar()
                    snackbar.showSnackbar("Reuniões de exemplo apagadas: $n.")
                }
            },
            aoCancelar = { confirmarDemo = false },
        )
    }
    // Dupla confirmação (RF-SEG-012).
    when (passoApagar) {
        1 -> DialogoConfirmacao(
            titulo = "Apagar todos os dados?",
            texto = "Vai apagar todas as reuniões, gravações, transcrições, actas, a chave da API e as definições.",
            confirmar = "Continuar",
            aoConfirmar = { passoApagar = 2 },
            aoCancelar = { passoApagar = 0 },
        )
        2 -> DialogoConfirmacao(
            titulo = "Confirmação final",
            texto = "Esta ação é definitiva e não pode ser desfeita. Apagar mesmo tudo?",
            confirmar = "Apagar tudo",
            aoConfirmar = {
                passoApagar = 0
                escopo.launch {
                    c.apagarTodosOsDados()
                    aposApagarTudo()
                }
            },
            aoCancelar = { passoApagar = 0 },
        )
    }
}
