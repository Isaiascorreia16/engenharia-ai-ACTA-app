package cv.acta.app.ui.acta

import android.content.ActivityNotFoundException
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cv.acta.app.data.pdf.GeradorPdf
import cv.acta.app.domain.acta.TextoActa
import cv.acta.app.domain.ciclo.CicloActa
import cv.acta.app.domain.modelo.Accao
import cv.acta.app.domain.modelo.ActaVersao
import cv.acta.app.domain.modelo.ConteudoActa
import cv.acta.app.domain.modelo.EstadoActa
import cv.acta.app.domain.modelo.PontoOrdem
import cv.acta.app.domain.util.Formatos
import cv.acta.app.domain.validacao.Presencas
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.DialogoConfirmacao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.FaixaAvisoIA
import cv.acta.app.ui.comum.Titulo
import cv.acta.app.ui.comum.contentor
import cv.acta.app.ui.tema.AmbarTexto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Edicao {
    data class Ponto(val i: Int) : Edicao
    data class Delib(val i: Int) : Edicao
    data class DeAccao(val i: Int) : Edicao
    data object Sumula : Edicao
    data object Proxima : Edicao
}

private data class Origem(val citacao: String, val ids: List<String>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EcraActa(
    reuniaoId: Long,
    actaIdInicial: Long?,
    aoVoltar: () -> Unit,
    abrirNaTranscricao: (String) -> Unit,
    abrirDistribuicao: (Long) -> Unit,
) {
    val c = contentor()
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val actas by remember(reuniaoId) { c.actas.actas(reuniaoId) }.collectAsState(initial = emptyList())
    val participantes by remember(reuniaoId) { c.reunioes.participantes(reuniaoId) }.collectAsState(initial = emptyList())
    val segmentos by remember(reuniaoId) { c.revisao.segmentos(reuniaoId) }.collectAsState(initial = emptyList())
    val oradores by remember(reuniaoId) { c.revisao.oradores(reuniaoId) }.collectAsState(initial = emptyList())

    var selecionadaId by rememberSaveable { mutableStateOf(actaIdInicial) }
    var edicao by remember { mutableStateOf<Edicao?>(null) }
    var pendente by remember { mutableStateOf<ConteudoActa?>(null) }
    var confirmarAprovar by remember { mutableStateOf(false) }
    var origem by remember { mutableStateOf<Origem?>(null) }
    var calendario by remember { mutableStateOf(false) }
    var voz by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    val acta: ActaVersao? = actas.firstOrNull { it.id == selecionadaId } ?: actas.firstOrNull()
    val nomePorId = participantes.associate { it.participante.id to it.participante.nome }
    val associacao = oradores.associate { it.rotulo to it.participanteId }
    fun nomeDe(rotulo: String) = associacao[rotulo]?.let { nomePorId[it] } ?: rotulo

    fun mostrar(msg: String) {
        escopo.launch { snackbar.showSnackbar(msg) }
    }

    fun guardar(a: ActaVersao, novo: ConteudoActa) {
        if (CicloActa.editavel(a.estado)) {
            escopo.launch { c.actas.guardarConteudo(a, novo) }
        } else {
            pendente = novo
        }
    }

    fun comPdf(a: ActaVersao, acao: (java.io.File) -> Unit) {
        escopo.launch {
            val f = withContext(Dispatchers.IO) { GeradorPdf.gerar(contexto, a) }
            acao(f)
        }
    }

    EcraBase(titulo = "Acta", aoVoltar = aoVoltar, snackbar = snackbar) { padding ->
        if (acta == null) {
            Text("Ainda não há acta para esta reunião.", Modifier.padding(padding).padding(16.dp))
            return@EcraBase
        }
        val doc = acta.documento
        val conteudo = doc.conteudo
        val editavel = CicloActa.editavel(acta.estado)

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FaixaAvisoIA() }

            // Versões (RF-ACT-016)
            if (actas.size > 1) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        actas.forEach { a ->
                            FilterChip(
                                selected = a.id == acta.id,
                                onClick = { selecionadaId = a.id },
                                label = { Text("v${a.versao} · ${a.estado.rotulo}") },
                            )
                        }
                    }
                }
            }

            item {
                Cartao {
                    Text("Versão ${acta.versao} · ${acta.estado.rotulo}", fontWeight = FontWeight.Bold)
                    acta.aprovadaEmMs?.let { Text("Aprovada em ${Formatos.dataHora(it)}") }
                    acta.hash?.let { Text("SHA-256: $it", style = MaterialTheme.typography.bodySmall) }
                    if (!editavel) Text("Conteúdo fixado. Qualquer alteração cria uma nova versão.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (acta.estado) {
                            EstadoActa.RASCUNHO -> Button(onClick = { escopo.launch { c.actas.enviarParaRevisao(acta) } }) { Text("Enviar para revisão") }
                            EstadoActa.EM_REVISAO -> {
                                OutlinedButton(onClick = { escopo.launch { c.actas.voltarARascunho(acta) } }) { Text("Voltar a rascunho") }
                                Button(onClick = { confirmarAprovar = true }) { Text("Aprovar") }
                            }
                            EstadoActa.APROVADA -> Button(onClick = { abrirDistribuicao(acta.id) }) { Text("Distribuir por e-mail") }
                            EstadoActa.DISTRIBUIDA -> Unit
                        }
                        OutlinedButton(onClick = {
                            comPdf(acta) { f ->
                                try {
                                    contexto.startActivity(Partilha.partilhar(contexto, f, TextoActa.assunto(doc, acta.versao)))
                                } catch (e: ActivityNotFoundException) {
                                    mostrar("Não há nenhuma app para partilhar o PDF.")
                                }
                            }
                        }) { Text("PDF / Partilhar") }
                        OutlinedButton(onClick = { calendario = true }) { Text("Agendar próxima reunião") }
                        OutlinedButton(onClick = { voz = "Ouvir a acta" to TextoActa.actaParaVoz(doc) }) { Text("Ouvir acta") }
                        OutlinedButton(onClick = { voz = "Ouvir a súmula" to listOf(TextoActa.sumulaParaVoz(doc)) }) { Text("Ouvir súmula") }
                    }
                }
            }

            // Cabeçalho: dados da BD, não do LLM.
            item {
                Cartao {
                    Titulo(doc.cabecalho.titulo)
                    Text("Data: ${doc.cabecalho.data} · ${doc.cabecalho.horaInicio}–${doc.cabecalho.horaFim}")
                    Text("Local: ${doc.cabecalho.local}")
                    Text("Presentes: ${doc.cabecalho.presentes.joinToString().ifBlank { "—" }}")
                    Text("Ausentes: ${doc.cabecalho.ausentes.joinToString().ifBlank { "—" }}")
                    Text("Dados preenchidos a partir do registo da reunião.", style = MaterialTheme.typography.labelSmall)
                }
            }

            item { Titulo("Ordem de trabalhos") }
            itemsIndexed(conteudo.ordemTrabalhos) { i, p ->
                Cartao {
                    Text("${i + 1}. ${p.titulo}", fontWeight = FontWeight.SemiBold)
                    Text(p.sumula)
                    TextButton(onClick = { edicao = Edicao.Ponto(i) }) { Text("Editar") }
                }
            }

            item { Titulo("Deliberações") }
            if (conteudo.deliberacoes.isEmpty()) item { Text("Não foram identificadas deliberações.") }
            itemsIndexed(conteudo.deliberacoes) { i, d ->
                Cartao(destaque = !d.verificado) {
                    if (!d.verificado) Text("⚠ Não verificado: ${d.motivoNaoVerificado.orEmpty()}", color = AmbarTexto, fontWeight = FontWeight.Bold)
                    Text("${i + 1}. ${d.texto}")
                    d.votacao?.let { Text("Votação: $it") }
                    Text(
                        "«${d.citacao}» [${ancora(d.ancoraInicioMs, d.ancoraFimMs)}]",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = { origem = Origem(d.citacao, d.segmentoIds) }) { Text("Ver origem") }
                        TextButton(onClick = { edicao = Edicao.Delib(i) }) { Text("Editar") }
                    }
                }
            }

            item { Titulo("Plano de ações") }
            if (conteudo.accoes.isEmpty()) item { Text("Não foram identificadas ações.") }
            itemsIndexed(conteudo.accoes) { i, a ->
                Cartao(destaque = !a.verificado || a.responsavel == Accao.POR_ATRIBUIR) {
                    if (!a.verificado) Text("⚠ Não verificado: ${a.motivoNaoVerificado.orEmpty()}", color = AmbarTexto, fontWeight = FontWeight.Bold)
                    Text("${i + 1}. ${a.descricao}")
                    Text("Responsável: ${a.responsavel}", fontWeight = if (a.responsavel == Accao.POR_ATRIBUIR) FontWeight.Bold else FontWeight.Normal)
                    Text("Prazo: ${a.prazo ?: "sem prazo"}")
                    Text("«${a.citacao}» [${ancora(a.ancoraInicioMs, a.ancoraFimMs)}]", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { origem = Origem(a.citacao, a.segmentoIds) }) { Text("Ver origem") }
                        TextButton(onClick = { edicao = Edicao.DeAccao(i) }) { Text("Editar") }
                    }
                }
            }

            item {
                Titulo("Próxima reunião")
                Row {
                    Text(conteudo.proximaReuniao?.replace('T', ' ') ?: "Não foi marcada.", Modifier.weight(1f))
                    TextButton(onClick = { edicao = Edicao.Proxima }) { Text("Editar") }
                }
            }

            item {
                Cartao {
                    Titulo("Súmula executiva")
                    Text(conteudo.sumulaExecutiva)
                    Text("${Formatos.contarPalavras(conteudo.sumulaExecutiva)} / 250 palavras", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { edicao = Edicao.Sumula }) { Text("Editar") }
                }
            }

            item {
                val expedicoes by remember(acta.id) { c.actas.expedicoes(acta.id) }.collectAsState(initial = emptyList())
                if (expedicoes.isNotEmpty()) {
                    Cartao {
                        Titulo("Distribuição")
                        expedicoes.forEach { e ->
                            Text("v${e.versao} · ${Formatos.dataHora(e.emMs)} · ${e.destinatarios.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                FaixaAvisoIA(Modifier.padding(vertical = 16.dp))
            }
        }

        // ---------- Diálogos de edição ----------
        when (val ed = edicao) {
            is Edicao.Ponto -> {
                val p = conteudo.ordemTrabalhos[ed.i]
                DialogoCampos("Ponto ${ed.i + 1}", listOf("Título" to p.titulo, "Súmula" to p.sumula), { edicao = null }) { v ->
                    edicao = null
                    guardar(acta, conteudo.copy(ordemTrabalhos = conteudo.ordemTrabalhos.toMutableList().also { it[ed.i] = PontoOrdem(v[0], v[1]) }))
                }
            }
            is Edicao.Delib -> {
                val d = conteudo.deliberacoes[ed.i]
                DialogoCampos("Deliberação ${ed.i + 1}", listOf("Deliberação" to d.texto, "Votação (vazio se não houve)" to d.votacao.orEmpty()), { edicao = null }) { v ->
                    edicao = null
                    val nova = d.copy(texto = v[0].trim(), votacao = v[1].trim().ifEmpty { null })
                    guardar(acta, conteudo.copy(deliberacoes = conteudo.deliberacoes.toMutableList().also { it[ed.i] = nova }))
                }
            }
            is Edicao.DeAccao -> DialogoAccao(conteudo.accoes[ed.i], participantes, { edicao = null }) { nova ->
                edicao = null
                guardar(acta, conteudo.copy(accoes = conteudo.accoes.toMutableList().also { it[ed.i] = nova }))
            }
            Edicao.Sumula -> DialogoCampos("Súmula executiva (máx. 250 palavras)", listOf("Súmula" to conteudo.sumulaExecutiva), { edicao = null }) { v ->
                edicao = null
                guardar(acta, conteudo.copy(sumulaExecutiva = v[0]))
            }
            Edicao.Proxima -> DialogoCampos(
                "Próxima reunião",
                listOf("AAAA-MM-DD ou AAAA-MM-DDTHH:MM (vazio = não marcada)" to conteudo.proximaReuniao.orEmpty()),
                { edicao = null },
            ) { v ->
                edicao = null
                guardar(acta, conteudo.copy(proximaReuniao = v[0].trim().ifEmpty { null }))
            }
            null -> Unit
        }

        pendente?.let { novo ->
            DialogoConfirmacao(
                titulo = "Criar nova versão",
                texto = "A versão ${acta.versao} está ${acta.estado.rotulo.lowercase()} e é imutável. A alteração cria uma nova versão em rascunho; a atual fica preservada.",
                confirmar = "Criar nova versão",
                aoConfirmar = {
                    pendente = null
                    escopo.launch {
                        selecionadaId = c.actas.guardarConteudo(acta, novo)
                        snackbar.showSnackbar("Nova versão criada.")
                    }
                },
                aoCancelar = { pendente = null },
            )
        }

        if (confirmarAprovar) {
            val naoVerificados = conteudo.deliberacoes.count { !it.verificado } + conteudo.accoes.count { !it.verificado }
            DialogoConfirmacao(
                titulo = "Aprovar a acta",
                texto = (if (naoVerificados > 0) "Atenção: há $naoVerificados item(ns) não verificado(s). " else "") +
                    "Ao aprovar, o conteúdo fica fixado e é calculado o SHA-256. Alterações posteriores criam uma nova versão.",
                confirmar = "Aprovar",
                aoConfirmar = {
                    confirmarAprovar = false
                    escopo.launch { c.actas.aprovar(acta) }
                },
                aoCancelar = { confirmarAprovar = false },
            )
        }

        origem?.let { o ->
            val porId = segmentos.associateBy { it.id }
            DialogoOrigem(
                citacao = o.citacao,
                segmentos = o.ids.mapNotNull { porId[it] },
                idsEmFalta = o.ids.filter { it !in porId },
                nomeDe = ::nomeDe,
                aoFechar = { origem = null },
                abrirNaTranscricao = { id ->
                    origem = null
                    abrirNaTranscricao(id)
                },
            )
        }

        if (calendario) {
            val proxima = conteudo.proximaReuniao
            DialogoCalendario(
                dataInicial = proxima?.take(10).orEmpty(),
                horaInicial = proxima?.takeIf { it.length >= 16 }?.substring(11, 16) ?: "10:00",
                aoFechar = { calendario = false },
            ) { inicio ->
                calendario = false
                val convidados = Presencas.destinatariosPorOmissao(participantes)
                try {
                    contexto.startActivity(
                        Partilha.calendario(
                            titulo = "Próxima reunião — ${doc.cabecalho.titulo}",
                            inicioMs = inicio,
                            fimMs = inicio + 60 * 60 * 1000,
                            convidados = convidados,
                            descricao = "Agendada a partir da acta v${acta.versao} de ${doc.cabecalho.data}.",
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    mostrar("Não foi encontrada uma app de calendário.")
                }
            }
        }

        voz?.let { (titulo, paragrafos) -> DialogoVoz(titulo, paragrafos) { voz = null } }
    }
}

private fun ancora(inicio: Long?, fim: Long?): String =
    if (inicio != null && fim != null) "${Formatos.duracao(inicio)}–${Formatos.duracao(fim)}" else "sem âncora"
