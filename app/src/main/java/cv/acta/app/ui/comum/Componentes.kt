package cv.acta.app.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cv.acta.app.ActaApp
import cv.acta.app.Contentor
import cv.acta.app.domain.modelo.MENCAO_IA
import cv.acta.app.ui.tema.AmbarFundo
import cv.acta.app.ui.tema.AmbarTexto

@Composable
fun contentor(): Contentor = (LocalContext.current.applicationContext as ActaApp).contentor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraBase(
    titulo: String,
    aoVoltar: (() -> Unit)?,
    acoes: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    botaoFlutuante: @Composable () -> Unit = {},
    conteudo: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo, maxLines = 2) },
                navigationIcon = {
                    if (aoVoltar != null) {
                        IconButton(onClick = aoVoltar) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = acoes,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = botaoFlutuante,
        content = conteudo,
    )
}

/** Faixa obrigatória em todas as actas (RF-ACT-010). */
@Composable
fun FaixaAvisoIA(modifier: Modifier = Modifier) {
    Text(
        text = MENCAO_IA,
        color = AmbarTexto,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .background(AmbarFundo)
            .padding(12.dp),
    )
}

@Composable
fun Cartao(modifier: Modifier = Modifier, destaque: Boolean = false, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (destaque) {
            CardDefaults.cardColors(containerColor = AmbarFundo, contentColor = AmbarTexto)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(12.dp), content = conteudo)
    }
}

@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    erro: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    linhas: Int = 1,
    ajuda: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        isError = erro != null,
        supportingText = {
            if (erro != null) {
                Text(erro, color = MaterialTheme.colorScheme.error)
            } else if (ajuda != null) {
                Text(ajuda)
            }
        },
        singleLine = linhas == 1,
        minLines = linhas,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DialogoConfirmacao(
    titulo: String,
    texto: String,
    confirmar: String,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
    cancelar: String = "Cancelar",
) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = aoConfirmar) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(cancelar) } },
    )
}
