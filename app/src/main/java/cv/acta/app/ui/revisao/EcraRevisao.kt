package cv.acta.app.ui.revisao

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cv.acta.app.data.EstadoGeracao
import cv.acta.app.domain.modelo.MarcaTemporal
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Segmento
import cv.acta.app.domain.transcricao.MontagemTranscricao
import cv.acta.app.domain.util.Formatos
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.DialogoConfirmacao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface Linha {
    val inicioMs: Long

    data class DeSegmento(val s: Segmento) : Linha {
        override val inicioMs get() = s.inicioMs
    }

    data class DeMarca(val m: MarcaTemporal) : Linha {
        override val inicioMs get() = m.inicioMs
    }
}

/** Revisão da transcrição: filtro por orador, edição com registo, associação de rótulos, tempos (Fase 5). */
@Composable
fun EcraRevisao(reuniaoId: Long, destaque: String?, aoVoltar: () -> Unit, abrirActa: () -> Unit) {
    val c = contentor()
    val escopo = rememberCoroutineScope()
    val segmentos by remember(reuniaoId) { c.revisao.segmentos(reuniaoId) }.collectAsState(initial = emptyList())
    val oradores by remember(reuniaoId) { c.revisao.oradores(reuniaoId) }.collectAsState(initial = emptyList())
    val edicoes by remember(reuniaoId) { c.revisao.edicoes(reuniaoId) }.collectAsState(initial = emptyList())
    val marcas by remember(reuniaoId) { c.gravacao.marcas(reuniaoId) }.collectAsState(initial = emptyList())
    val participantes by remember(reuniaoId) { c.reunioes.participantes(reuniaoId) }.collectAsState(initial = emptyList())
    val actas by remember(reuniaoId) { c.actas.actas(reuniaoId) }.collectAsState(initial = emptyList())
    val geracao by c.actas.geracao.collectAsState()

    var filtro by remember { mutableStateOf<String?>(null) }
    var aEditar by remember { mutableStateOf<Segmento?>(null) }
    var confirmarGerar by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val estadoLista = rememberLazyListState()

    val nomePorId = participantes.associate { it.participante.id to it.participante.nome }
    val associacao = oradores.associate { it.rotulo to it.participanteId }
    fun nomeDe(rotulo: String): String = associacao[rotulo]?.let { nomePorId[it] } ?: rotulo

    val linhas: List<Linha> = remember(segmentos, marcas, filtro, oradores, participantes) {
        val segs = segmentos.filter { filtro == null || nomeDe(it.orador) == filtro }.map { Linha.DeSegmento(it) }
        val mks = if (filtro == null) marcas.filter { it.tipo != cv.acta.app.domain.modelo.TipoMarca.MUDANCA_PARTE }.map { Linha.DeMarca(it) } else emptyList()
        (segs + mks).sortedBy { it.inicioMs }
    }
    val cabecalhos = 2 // cartão de oradores + filtros

    // Abrir um segmento de origem vindo da acta: desloca a lista até ele.
    LaunchedEffect(destaque, linhas.size) {
        if (destaque != null) {
            val i = linhas.indexOfFirst { it is Linha.DeSegmento && it.s.id == destaque }
            if (i >= 0) estadoLista.scrollToItem(i + cabecalhos)
        }
    }

    val estadoGeracao = geracao[reuniaoId]
    LaunchedEffect(estadoGeracao) {
        when (estadoGeracao) {
            is EstadoGeracao.Concluida -> {
                c.actas.limparEstado(reuniaoId)
                abrirActa()
            }
            is EstadoGeracao.Erro -> aviso = estadoGeracao.mensagem
            else -> Unit
        }
    }

    EcraBase(titulo = "Revisão da transcrição", aoVoltar = aoVoltar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = estadoLista,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Cartao {
                        Titulo("Oradores")
                        Text(
                            "Os rótulos só valem dentro de cada parte: \"P1-A\" e \"P2-A\" podem ser pessoas diferentes. " +
                                "Associe cada rótulo a um participante; a mudança aplica-se a todos os segmentos com esse rótulo.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        oradores.forEach { o ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(o.rotulo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
                                SeletorParticipante(
                                    atual = o.participanteId?.let { nomePorId[it] } ?: "Anónimo",
                                    participantes = participantes,
                                    modifier = Modifier.weight(0.7f),
                                ) { pid -> escopo.launch { c.revisao.associar(reuniaoId, o.rotulo, pid) } }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Titulo("Tempo de intervenção")
                        MontagemTranscricao.tempoPorOrador(segmentos, ::nomeDe).forEach { (nome, ms) ->
                            Text("$nome: ${Formatos.duracao(ms)}")
                        }
                    }
                }
                item {
                    val nomes = segmentos.map { nomeDe(it.orador) }.distinct().sorted()
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filtro == null, onClick = { filtro = null }, label = { Text("Todos") })
                        nomes.forEach { n -> FilterChip(selected = filtro == n, onClick = { filtro = n }, label = { Text(n) }) }
                    }
                }
                items(linhas, key = { l -> if (l is Linha.DeSegmento) "s" + l.s.id else "m" + (l as Linha.DeMarca).m.id }) { l ->
                    when (l) {
                        is Linha.DeMarca -> Text(
                            "— ${l.m.tipo.rotulo} às ${Formatos.duracao(l.m.inicioMs)}" +
                                (l.m.fimMs?.takeIf { it > l.m.inicioMs }?.let { " até ${Formatos.duracao(it)}" } ?: "") + " —",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        is Linha.DeSegmento -> {
                            val s = l.s
                            val editado = edicoes.any { it.segmentoId == s.id }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (s.id == destaque) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                                    .clickable { aEditar = s }
                                    .padding(8.dp),
                            ) {
                                Text(
                                    "${Formatos.duracao(s.inicioMs)} · ${nomeDe(s.orador)}" +
                                        (if (nomeDe(s.orador) != s.orador) " (${s.orador})" else "") +
                                        (if (editado) " · editado" else ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(s.texto)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                aviso?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                if (estadoGeracao is EstadoGeracao.EmCurso) {
                    Text(estadoGeracao.mensagem)
                } else {
                    Button(
                        enabled = segmentos.isNotEmpty(),
                        onClick = {
                            aviso = null
                            if (!MontagemTranscricao.falaSuficiente(segmentos)) {
                                aviso = "A transcrição tem menos de 60 segundos de fala " +
                                    "(${Formatos.duracao(MontagemTranscricao.tempoDeFalaMs(segmentos))}). " +
                                    "Não há conteúdo suficiente para uma acta fiável."
                            } else {
                                escopo.launch {
                                    if (!c.definicoes.temChave.first()) {
                                        aviso = "Para gerar a acta é preciso configurar a chave da API em Definições."
                                    } else if (actas.isNotEmpty()) {
                                        confirmarGerar = true
                                    } else {
                                        c.actas.gerar(reuniaoId)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Gerar acta") }
                    if (actas.isNotEmpty()) {
                        OutlinedButton(onClick = abrirActa, modifier = Modifier.fillMaxWidth()) { Text("Abrir acta existente") }
                    }
                }
            }
        }
    }

    aEditar?.let { s ->
        DialogoEdicao(
            segmento = s,
            historico = edicoes.filter { it.segmentoId == s.id },
            rotulos = oradores.map { it.rotulo },
            nomeDe = ::nomeDe,
            aoFechar = { aEditar = null },
            aoGuardar = { texto, rotulo ->
                escopo.launch {
                    c.revisao.editarTexto(s, texto, c.definicoes.autorEdicoes.first(), System.currentTimeMillis())
                    if (rotulo != s.orador) c.revisao.reatribuirSegmento(s.copy(texto = texto.trim().ifEmpty { s.texto }), rotulo)
                }
                aEditar = null
            },
        )
    }

    if (confirmarGerar) {
        val ultima = actas.first()
        DialogoConfirmacao(
            titulo = "Gerar nova acta",
            texto = if (ultima.estado.name == "RASCUNHO" || ultima.estado.name == "EM_REVISAO") {
                "A acta v${ultima.versao} ainda está em ${ultima.estado.rotulo.lowercase()} e será substituída pelo novo resultado. As edições feitas nela perdem-se."
            } else {
                "A acta v${ultima.versao} está ${ultima.estado.rotulo.lowercase()} e fica preservada. O resultado será a versão ${ultima.versao + 1}, em rascunho."
            },
            confirmar = "Gerar",
            aoConfirmar = {
                confirmarGerar = false
                c.actas.gerar(reuniaoId)
            },
            aoCancelar = { confirmarGerar = false },
        )
    }
}

@Composable
private fun SeletorParticipante(
    atual: String,
    participantes: List<ParticipanteNaReuniao>,
    modifier: Modifier = Modifier,
    aoEscolher: (Long?) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { aberto = true }, modifier = Modifier.fillMaxWidth()) { Text(atual) }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(text = { Text("Anónimo") }, onClick = {
                aberto = false
                aoEscolher(null)
            })
            participantes.forEach { p ->
                DropdownMenuItem(text = { Text(p.participante.nome) }, onClick = {
                    aberto = false
                    aoEscolher(p.participante.id)
                })
            }
        }
    }
}

@Composable
private fun DialogoEdicao(
    segmento: Segmento,
    historico: List<cv.acta.app.domain.modelo.EdicaoSegmento>,
    rotulos: List<String>,
    nomeDe: (String) -> String,
    aoFechar: () -> Unit,
    aoGuardar: (String, String) -> Unit,
) {
    var texto by remember { mutableStateOf(segmento.texto) }
    var rotulo by remember { mutableStateOf(segmento.orador) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Segmento ${segmento.id} · ${Formatos.duracao(segmento.inicioMs)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = texto, onValueChange = { texto = it }, label = { Text("Texto") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Orador: ")
                    Box {
                        TextButton(onClick = { menu = true }) { Text("${nomeDe(rotulo)} ($rotulo)") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            rotulos.forEach { r ->
                                DropdownMenuItem(text = { Text("${nomeDe(r)} ($r)") }, onClick = {
                                    rotulo = r
                                    menu = false
                                })
                            }
                        }
                    }
                }
                if (historico.isNotEmpty()) {
                    Titulo("Histórico de edições")
                    historico.forEach { e ->
                        Text("${Formatos.dataHora(e.emMs)} · ${e.autor}", style = MaterialTheme.typography.labelSmall)
                        Text("Antes: ${e.textoAnterior}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { aoGuardar(texto, rotulo) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
