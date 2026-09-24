package cv.acta.app.ui.reuniao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cv.acta.app.data.audio.EstadoGravacao
import cv.acta.app.domain.modelo.EstadoParte
import cv.acta.app.domain.modelo.EstadoSessao
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.util.Formatos
import cv.acta.app.domain.validacao.Presencas
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.DialogoConfirmacao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EcraReuniao(
    reuniaoId: Long,
    aoVoltar: () -> Unit,
    prepararGravacao: () -> Unit,
    abrirGravacao: () -> Unit,
    abrirRevisao: () -> Unit,
    abrirActa: () -> Unit,
    abrirDefinicoes: () -> Unit,
) {
    val c = contentor()
    val escopo = rememberCoroutineScope()
    val reuniao by remember(reuniaoId) { c.reunioes.reuniao(reuniaoId) }.collectAsState(initial = null)
    val participantes by remember(reuniaoId) { c.reunioes.participantes(reuniaoId) }.collectAsState(initial = emptyList())
    val partes by remember(reuniaoId) { c.gravacao.partes(reuniaoId) }.collectAsState(initial = emptyList())
    val sessoes by remember(reuniaoId) { c.gravacao.sessoes(reuniaoId) }.collectAsState(initial = emptyList())
    val segmentos by remember(reuniaoId) { c.revisao.segmentos(reuniaoId) }.collectAsState(initial = emptyList())
    val actas by remember(reuniaoId) { c.actas.actas(reuniaoId) }.collectAsState(initial = emptyList())
    val temChave by c.definicoes.temChave.collectAsState(initial = false)
    val progresso by c.transcricao.progresso.collectAsState()
    val gravacao by EstadoGravacao.estado.collectAsState()

    var editar by remember { mutableStateOf<ParticipanteNaReuniao?>(null) }
    var novo by remember { mutableStateOf(false) }
    var confirmarApagar by remember { mutableStateOf(false) }
    var remover by remember { mutableStateOf<ParticipanteNaReuniao?>(null) }

    val r = reuniao
    EcraBase(
        titulo = r?.titulo ?: "Reunião",
        aoVoltar = aoVoltar,
        acoes = {
            IconButton(onClick = { confirmarApagar = true }) { Icon(Icons.Filled.Delete, contentDescription = "Apagar reunião") }
        },
    ) { padding ->
        if (r == null) return@EcraBase
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (r.demo) {
                    Cartao(destaque = true) {
                        Text("Reunião FICTÍCIA de exemplo. Nomes, falas e decisões foram inventados para demonstração. Pode apagá-la a qualquer momento.")
                    }
                }
                Cartao {
                    Text("Data: ${Formatos.dataHora(r.inicioPrevistoMs)}")
                    Text("Local: ${r.local.ifBlank { "—" }}")
                    Text("Língua: ${r.lingua}")
                }
            }

            // ---------- Participantes ----------
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Titulo("Participantes (${participantes.size})", Modifier.weight(1f))
                    TextButton(onClick = { novo = true }) { Text("Adicionar") }
                }
                val semConsentimento = Presencas.presentesSemConsentimento(participantes)
                if (semConsentimento.isNotEmpty()) {
                    Cartao(destaque = true) {
                        Text("Atenção: sem consentimento registado — ${semConsentimento.joinToString()}.")
                    }
                }
            }
            items(participantes, key = { it.participante.id }) { np ->
                Cartao {
                    Text(np.participante.nome, style = MaterialTheme.typography.titleSmall)
                    Text("${np.participante.funcao} · ${np.participante.email}", style = MaterialTheme.typography.bodySmall)
                    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeletorPresenca(np.participacao.presenca) { p -> escopo.launch { c.reunioes.alterarPresenca(np, p) } }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = np.participacao.consentimento,
                                onCheckedChange = { v -> escopo.launch { c.reunioes.alterarConsentimento(np, v, System.currentTimeMillis()) } },
                            )
                            Text("Consentiu")
                        }
                        TextButton(onClick = { editar = np }) { Text("Editar") }
                        TextButton(onClick = { remover = np }) { Text("Remover") }
                    }
                    np.participacao.consentimentoEmMs?.let {
                        Text("Consentimento registado em ${Formatos.dataHora(it)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ---------- Gravação ----------
            item {
                Titulo("Gravação")
                val aGravarAqui = gravacao.ativa && gravacao.reuniaoId == reuniaoId
                when {
                    aGravarAqui -> Button(onClick = abrirGravacao, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao ecrã de gravação") }
                    gravacao.ativa -> Text("Está a decorrer uma gravação noutra reunião.")
                    else -> Button(onClick = prepararGravacao, modifier = Modifier.fillMaxWidth()) {
                        Text(if (partes.isEmpty()) "Preparar gravação" else "Gravar nova sessão")
                    }
                }
                if (sessoes.any { it.estado == EstadoSessao.INTERROMPIDA }) {
                    Cartao(destaque = true) {
                        Text("Uma sessão foi interrompida de forma anómala (por exemplo, permissão revogada). As partes já gravadas foram recuperadas.")
                    }
                }
            }
            if (partes.isNotEmpty()) {
                item { Titulo("Partes gravadas (${partes.size})") }
                items(partes, key = { it.id }) { p ->
                    Cartao(destaque = p.estado == EstadoParte.ERRO) {
                        val estado = when (p.estado) {
                            EstadoParte.GRAVADA -> "por transcrever"
                            EstadoParte.TRANSCRITA -> "transcrita"
                            EstadoParte.ERRO -> "erro"
                        }
                        Text("Parte ${p.numero} · início ${Formatos.duracao(p.inicioMs)} · ${p.duracaoMs?.let { Formatos.duracao(it) } ?: "duração desconhecida"} · $estado")
                        p.erro?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (p.estado == EstadoParte.ERRO && progresso[reuniaoId] == null && temChave) {
                            OutlinedButton(onClick = { c.transcricao.repetirParte(reuniaoId, p.id) }) { Text("Repetir esta parte") }
                        }
                    }
                }
                item {
                    val pendentes = partes.count { it.estado != EstadoParte.TRANSCRITA }
                    val emCurso = progresso[reuniaoId]
                    when {
                        emCurso != null -> Cartao { Text(emCurso) }
                        pendentes > 0 && !temChave -> Cartao(destaque = true) {
                            Text("Para transcrever é preciso configurar a chave da API da OpenAI.")
                            TextButton(onClick = abrirDefinicoes) { Text("Abrir Definições") }
                        }
                        pendentes > 0 && !(gravacao.ativa && gravacao.reuniaoId == reuniaoId) -> Button(
                            onClick = { c.transcricao.transcreverPendentes(reuniaoId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Transcrever $pendentes parte(s)") }
                    }
                }
            }

            // ---------- Revisão e acta ----------
            item {
                Titulo("Transcrição e acta")
                if (segmentos.isEmpty()) {
                    Text("Ainda não há transcrição.")
                } else {
                    Button(onClick = abrirRevisao, modifier = Modifier.fillMaxWidth()) { Text("Rever transcrição (${segmentos.size} segmentos)") }
                }
                val ultima = actas.firstOrNull()
                if (ultima != null) {
                    OutlinedButton(onClick = abrirActa, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir acta (v${ultima.versao} · ${ultima.estado.rotulo})")
                    }
                }
                Spacer(Modifier.padding(24.dp))
            }
        }
    }

    if (novo) DialogoParticipante(reuniaoId, null) { novo = false }
    editar?.let { DialogoParticipante(reuniaoId, it) { editar = null } }
    remover?.let { np ->
        DialogoConfirmacao(
            titulo = "Remover participante",
            texto = "Remover ${np.participante.nome} desta reunião?",
            confirmar = "Remover",
            aoConfirmar = {
                escopo.launch { c.reunioes.removerParticipante(reuniaoId, np.participante.id) }
                remover = null
            },
            aoCancelar = { remover = null },
        )
    }
    if (confirmarApagar) {
        DialogoConfirmacao(
            titulo = "Apagar reunião",
            texto = "Apaga a reunião, as gravações, a transcrição e todas as versões da acta. Não pode ser desfeito.",
            confirmar = "Apagar",
            aoConfirmar = {
                confirmarApagar = false
                escopo.launch {
                    c.gravacao.apagarReuniaoComFicheiros(reuniaoId)
                    aoVoltar()
                }
            },
            aoCancelar = { confirmarApagar = false },
        )
    }
}

