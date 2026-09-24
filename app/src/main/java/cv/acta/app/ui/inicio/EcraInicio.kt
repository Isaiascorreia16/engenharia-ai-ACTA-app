package cv.acta.app.ui.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import cv.acta.app.data.db.ResultadoPesquisa
import cv.acta.app.domain.util.Formatos
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EcraInicio(
    abrirReuniao: (Long) -> Unit,
    novaReuniao: () -> Unit,
    abrirDefinicoes: () -> Unit,
    abrirAjuda: () -> Unit,
) {
    val c = contentor()
    val reunioes by remember { c.reunioes.reunioes() }.collectAsState(initial = null)
    val demoOferecida by c.definicoes.demoOferecida.collectAsState(initial = true)
    val escopo = rememberCoroutineScope()
    var pesquisa by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<ResultadoPesquisa>>(emptyList()) }
    var aCarregarDemo by remember { mutableStateOf(false) }

    LaunchedEffect(pesquisa) {
        delay(300)
        resultados = c.reunioes.pesquisar(pesquisa)
    }

    EcraBase(
        titulo = "ACTA — Reuniões",
        aoVoltar = null,
        acoes = {
            IconButton(onClick = abrirAjuda) { Icon(Icons.Filled.Info, contentDescription = "Ajuda e limitações") }
            IconButton(onClick = abrirDefinicoes) { Icon(Icons.Filled.Settings, contentDescription = "Definições") }
        },
        botaoFlutuante = {
            ExtendedFloatingActionButton(
                onClick = novaReuniao,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova reunião") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = pesquisa,
                    onValueChange = { pesquisa = it },
                    label = { Text("Pesquisar em títulos, transcrições e actas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (pesquisa.trim().length >= 2) {
                item { Titulo("Resultados da pesquisa (${resultados.size})") }
                items(resultados) { r ->
                    Cartao(Modifier.clickable { abrirReuniao(r.reuniaoId) }) {
                        Text(r.titulo, style = MaterialTheme.typography.titleSmall)
                        Text(r.origem, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (r.excerto.isNotBlank()) Text(r.excerto, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { Spacer(Modifier.padding(8.dp)) }
            }

            val lista = reunioes
            if (lista != null && lista.isEmpty() && !demoOferecida) {
                item {
                    Cartao {
                        Titulo("Bem-vindo ao ACTA")
                        Text("Pode explorar a aplicação com uma reunião de exemplo fictícia, já transcrita e com acta em rascunho. Não precisa de chave de API.")
                        Row(Modifier.padding(top = 8.dp)) {
                            Button(
                                enabled = !aCarregarDemo,
                                onClick = {
                                    aCarregarDemo = true
                                    escopo.launch {
                                        val id = c.demo.carregar()
                                        c.definicoes.marcarDemoOferecida()
                                        aCarregarDemo = false
                                        abrirReuniao(id)
                                    }
                                },
                            ) { Text("Explorar com uma reunião de exemplo") }
                        }
                        OutlinedButton(onClick = { escopo.launch { c.definicoes.marcarDemoOferecida() } }) {
                            Text("Agora não")
                        }
                    }
                }
            }

            if (lista != null && lista.isEmpty()) {
                item {
                    Text(
                        "Ainda não há reuniões. Toque em \"Nova reunião\" para começar.",
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(lista.orEmpty(), key = { it.id }) { r ->
                Cartao(Modifier.clickable { abrirReuniao(r.id) }) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(r.titulo, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Formatos.dataHora(r.inicioPrevistoMs)} · ${r.local.ifBlank { "Sem local" }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (r.demo) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = { abrirReuniao(r.id) }, label = { Text("Fictícia") })
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(40.dp)) }
        }
    }
}
