package cv.acta.app.ui.reuniao

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cv.acta.app.domain.modelo.Reuniao
import cv.acta.app.domain.util.Formatos
import cv.acta.app.domain.validacao.ErroCampo
import cv.acta.app.domain.validacao.ValidacaoReuniao
import cv.acta.app.ui.comum.CampoTexto
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.launch

@Composable
fun EcraNovaReuniao(aoVoltar: () -> Unit, aoCriar: (Long) -> Unit) {
    val c = contentor()
    val escopo = rememberCoroutineScope()
    val agora = remember { System.currentTimeMillis() }
    var titulo by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(Formatos.dataIso(agora)) }
    var hora by remember { mutableStateOf(Formatos.hora(agora)) }
    var local by remember { mutableStateOf("") }
    var lingua by remember { mutableStateOf("Português") }
    var erros by remember { mutableStateOf<List<ErroCampo>>(emptyList()) }
    fun erro(campo: String) = erros.firstOrNull { it.campo == campo }?.mensagem

    EcraBase(titulo = "Nova reunião", aoVoltar = aoVoltar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            CampoTexto(titulo, { titulo = it }, "Título", erro = erro("titulo"))
            CampoTexto(data, { data = it }, "Data (AAAA-MM-DD)", erro = erro("data"))
            CampoTexto(hora, { hora = it }, "Hora de início (HH:MM)", erro = erro("hora"))
            CampoTexto(local, { local = it }, "Local")
            CampoTexto(
                lingua, { lingua = it }, "Língua falada",
                ajuda = "Fica registada como metadado. O modelo de transcrição deteta a língua automaticamente.",
            )
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                onClick = {
                    erros = ValidacaoReuniao.validar(titulo, data, hora)
                    if (erros.isEmpty()) {
                        escopo.launch {
                            val id = c.reunioes.criar(
                                Reuniao(
                                    titulo = titulo.trim(),
                                    inicioPrevistoMs = Formatos.paraMs(data, hora),
                                    local = local.trim(),
                                    lingua = lingua.trim(),
                                )
                            )
                            aoCriar(id)
                        }
                    }
                },
            ) { Text("Criar reunião") }
        }
    }
}
