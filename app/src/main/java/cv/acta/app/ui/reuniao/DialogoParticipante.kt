package cv.acta.app.ui.reuniao

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import cv.acta.app.domain.modelo.Participante
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Presenca
import cv.acta.app.domain.validacao.ErroCampo
import cv.acta.app.domain.validacao.ValidacaoParticipante
import cv.acta.app.ui.comum.CampoTexto
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.launch

@Composable
fun SeletorPresenca(atual: Presenca, aoEscolher: (Presenca) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { aberto = true }) { Text(atual.rotulo) }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            Presenca.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.rotulo) }, onClick = {
                    aberto = false
                    aoEscolher(p)
                })
            }
        }
    }
}

/** Criação e edição de participante, com validação por campo (RF-PAR-001 a 004, 006). */
@Composable
fun DialogoParticipante(reuniaoId: Long, existente: ParticipanteNaReuniao?, aoFechar: () -> Unit) {
    val c = contentor()
    val escopo = rememberCoroutineScope()
    val p = existente?.participante
    var nome by remember { mutableStateOf(p?.nome.orEmpty()) }
    var funcao by remember { mutableStateOf(p?.funcao.orEmpty()) }
    var email by remember { mutableStateOf(p?.email.orEmpty()) }
    var telefone by remember { mutableStateOf(p?.telefone.orEmpty()) }
    var organizacao by remember { mutableStateOf(p?.organizacao.orEmpty()) }
    var presenca by remember { mutableStateOf(existente?.participacao?.presenca ?: Presenca.PRESENTE) }
    var consentimento by remember { mutableStateOf(existente?.participacao?.consentimento ?: false) }
    var erros by remember { mutableStateOf<List<ErroCampo>>(emptyList()) }
    fun erro(campo: String) = erros.firstOrNull { it.campo == campo }?.mensagem

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (existente == null) "Novo participante" else "Editar participante") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CampoTexto(nome, { nome = it }, "Nome", erro = erro(ValidacaoParticipante.CAMPO_NOME))
                CampoTexto(funcao, { funcao = it }, "Função", erro = erro(ValidacaoParticipante.CAMPO_FUNCAO))
                CampoTexto(email, { email = it }, "E-mail", erro = erro(ValidacaoParticipante.CAMPO_EMAIL), teclado = KeyboardType.Email)
                CampoTexto(
                    telefone, { telefone = it }, "Telefone (opcional, E.164)",
                    erro = erro(ValidacaoParticipante.CAMPO_TELEFONE), teclado = KeyboardType.Phone, ajuda = "Exemplo: +2389912345",
                )
                CampoTexto(organizacao, { organizacao = it }, "Organização (opcional)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Presença: ")
                    SeletorPresenca(presenca) { presenca = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentimento, onCheckedChange = { consentimento = it })
                    Text("Consentiu a gravação")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                escopo.launch {
                    val resultado = c.reunioes.guardarParticipante(
                        reuniaoId = reuniaoId,
                        participante = Participante(
                            id = p?.id ?: 0,
                            nome = nome,
                            funcao = funcao,
                            email = email,
                            telefone = telefone,
                            organizacao = organizacao,
                        ),
                        presenca = presenca,
                        consentimento = consentimento,
                        consentimentoEmMsAnterior = existente?.participacao?.consentimentoEmMs,
                        agoraMs = System.currentTimeMillis(),
                    )
                    erros = resultado
                    if (resultado.isEmpty()) aoFechar()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
