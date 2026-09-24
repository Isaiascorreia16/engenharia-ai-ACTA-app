package cv.acta.app.data

import android.util.Log
import cv.acta.app.data.db.ActaDao
import cv.acta.app.data.db.ExpedicaoEntity
import cv.acta.app.data.db.paraDominio
import cv.acta.app.data.db.paraEntidade
import cv.acta.app.data.definicoes.Definicoes
import cv.acta.app.data.openai.ClienteOpenAI
import cv.acta.app.data.openai.ErroApi
import cv.acta.app.data.openai.EsquemaActa
import cv.acta.app.domain.acta.ActaGerada
import cv.acta.app.domain.acta.Cabecalho
import cv.acta.app.domain.acta.PromptActa
import cv.acta.app.domain.acta.Verificador
import cv.acta.app.domain.ciclo.CicloActa
import cv.acta.app.domain.ciclo.Versionamento
import cv.acta.app.domain.modelo.ActaVersao
import cv.acta.app.domain.modelo.ConteudoActa
import cv.acta.app.domain.modelo.DocumentoActa
import cv.acta.app.domain.modelo.EstadoActa
import cv.acta.app.domain.modelo.Expedicao
import cv.acta.app.domain.modelo.Segmento
import cv.acta.app.domain.transcricao.MontagemTranscricao
import cv.acta.app.domain.util.Formatos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

sealed interface EstadoGeracao {
    data class EmCurso(val mensagem: String) : EstadoGeracao
    data class Concluida(val actaId: Long) : EstadoGeracao
    data class Erro(val mensagem: String) : EstadoGeracao
}

class RepositorioActas(
    private val dao: ActaDao,
    private val reunioes: RepositorioReunioes,
    private val revisao: RepositorioRevisao,
    private val definicoes: Definicoes,
    private val cliente: ClienteOpenAI,
    private val escopo: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val estadoMutavel = MutableStateFlow<Map<Long, EstadoGeracao>>(emptyMap())
    val geracao: StateFlow<Map<Long, EstadoGeracao>> = estadoMutavel.asStateFlow()

    fun actas(reuniaoId: Long): Flow<List<ActaVersao>> = dao.observarActas(reuniaoId).map { l -> l.map { it.paraDominio() } }
    fun acta(id: Long): Flow<ActaVersao?> = dao.observarActa(id).map { it?.paraDominio() }
    suspend fun actaAgora(id: Long): ActaVersao? = dao.acta(id)?.paraDominio()
    fun expedicoes(actaId: Long): Flow<List<Expedicao>> = dao.observarExpedicoes(actaId).map { l -> l.map { it.paraDominio() } }

    fun limparEstado(reuniaoId: Long) = estadoMutavel.update { it - reuniaoId }

    private fun estado(reuniaoId: Long, e: EstadoGeracao) = estadoMutavel.update { it + (reuniaoId to e) }

    /** Geração a pedido (RF-ACT-001), no escopo da aplicação. */
    fun gerar(reuniaoId: Long) {
        if (geracao.value[reuniaoId] is EstadoGeracao.EmCurso) return
        estado(reuniaoId, EstadoGeracao.EmCurso("A preparar o pedido…"))
        escopo.launch {
            try {
                estado(reuniaoId, EstadoGeracao.Concluida(gerarAgora(reuniaoId)))
            } catch (e: ErroApi) {
                estado(reuniaoId, EstadoGeracao.Erro(e.message ?: "Erro na API."))
            } catch (e: Exception) {
                Log.e("RepositorioActas", "Falha na geração", e)
                estado(reuniaoId, EstadoGeracao.Erro("Não foi possível gerar a acta: ${e.message}"))
            }
        }
    }

    private suspend fun gerarAgora(reuniaoId: Long): Long {
        val reuniao = reunioes.obter(reuniaoId) ?: error("Reunião inexistente.")
        val participantes = reunioes.participantesAgora(reuniaoId)
        val segmentos = revisao.segmentosAgora(reuniaoId)
        if (!MontagemTranscricao.falaSuficiente(segmentos)) {
            throw IllegalStateException("A transcrição tem menos de 60 segundos de fala.")
        }
        val chave = definicoes.chave() ?: throw IllegalStateException("Configure a chave da API em Definições.")
        val modelo = definicoes.modeloTexto.first()
        val nomeDe = nomeador(reuniaoId, participantes.map { it.participante.id to it.participante.nome })
        val cabecalho = Cabecalho.construir(reuniao, participantes)

        estado(reuniaoId, EstadoGeracao.EmCurso("A gerar a acta com $modelo (pode demorar um ou dois minutos)…"))
        val entrada = PromptActa.entrada(LocalDate.parse(cabecalho.data), participantes, segmentos, nomeDe)
        val resposta = cliente.gerarEstruturado(chave, modelo, PromptActa.INSTRUCOES, entrada, "acta", EsquemaActa.ESQUEMA)
        val gerada = try {
            json.decodeFromString(ActaGerada.serializer(), resposta)
        } catch (e: Exception) {
            throw IllegalStateException("O modelo devolveu um JSON fora do esquema.")
        }

        estado(reuniaoId, EstadoGeracao.EmCurso("A verificar citações e responsáveis…"))
        val conteudo = Verificador.verificar(gerada, segmentos, participantes.map { it.participante })
        val documento = DocumentoActa(cabecalho, conteudo)
        val agora = System.currentTimeMillis()

        val ultima = dao.ultimaActa(reuniaoId)?.paraDominio()
        return if (ultima == null) {
            dao.inserirActa(ActaVersao(reuniaoId = reuniaoId, versao = 1, estado = EstadoActa.RASCUNHO, documento = documento, criadaEmMs = agora).paraEntidade())
        } else {
            guardar(Versionamento.alterar(ultima, documento, agora))
        }
    }

    private suspend fun nomeador(reuniaoId: Long, nomes: List<Pair<Long, String>>): (String) -> String {
        val porId = nomes.toMap()
        val associacao = revisao.oradoresAgora(reuniaoId).associate { it.rotulo to it.participanteId }
        return { rotulo -> associacao[rotulo]?.let { porId[it] } ?: rotulo }
    }

    private suspend fun guardar(r: Versionamento.Resultado): Long = when (r) {
        is Versionamento.Resultado.Atualizada -> {
            val guardada = dao.acta(r.acta.id)?.paraDominio()
            if (guardada != null) Versionamento.garantirEditavel(guardada)
            dao.atualizarActa(r.acta.paraEntidade())
            r.acta.id
        }
        is Versionamento.Resultado.NovaVersao -> {
            // O número segue a versão mais recente (pode editar-se uma versão antiga).
            val maior = dao.ultimaActa(r.nova.reuniaoId)?.versao ?: r.anterior.versao
            dao.inserirActa(r.nova.copy(versao = maxOf(maior, r.anterior.versao) + 1).paraEntidade())
        }
    }

    /** Guarda edições. Numa acta aprovada ou distribuída cria uma nova versão (RF-ACT-016). Devolve o id resultante. */
    suspend fun guardarConteudo(acta: ActaVersao, novo: ConteudoActa): Long {
        val participantes = reunioes.participantesAgora(acta.reuniaoId)
        val segmentos = revisao.segmentosAgora(acta.reuniaoId)
        val verificado = Verificador.reverificar(novo, segmentos, participantes.map { it.participante })
        val cabecalho = if (CicloActa.editavel(acta.estado)) {
            reunioes.obter(acta.reuniaoId)?.let { Cabecalho.construir(it, participantes) } ?: acta.documento.cabecalho
        } else {
            acta.documento.cabecalho
        }
        return guardar(Versionamento.alterar(acta, DocumentoActa(cabecalho, verificado), System.currentTimeMillis()))
    }

    suspend fun enviarParaRevisao(acta: ActaVersao) = atualizarEstado(CicloActa.enviarParaRevisao(acta))

    suspend fun voltarARascunho(acta: ActaVersao) = atualizarEstado(CicloActa.voltarARascunho(acta))

    /** Aprovação humana: fixa o conteúdo (cabeçalho atualizado da BD) e calcula o SHA-256. */
    suspend fun aprovar(acta: ActaVersao) {
        val participantes = reunioes.participantesAgora(acta.reuniaoId)
        val reuniao = reunioes.obter(acta.reuniaoId) ?: return
        val segmentos = revisao.segmentosAgora(acta.reuniaoId)
        val definitivo = DocumentoActa(
            Cabecalho.construir(reuniao, participantes),
            Verificador.reverificar(acta.documento.conteudo, segmentos, participantes.map { it.participante }),
        )
        atualizarEstado(CicloActa.aprovar(acta, definitivo, System.currentTimeMillis()))
    }

    /** Só depois do "Sim" do utilizador: DISTRIBUIDA + registo da expedição (RF-DIS-007). */
    suspend fun registarDistribuicao(acta: ActaVersao, destinatarios: List<String>) {
        val distribuida = CicloActa.distribuir(acta)
        dao.registarDistribuicao(
            distribuida.paraEntidade(),
            ExpedicaoEntity(actaId = acta.id, versao = acta.versao, emMs = System.currentTimeMillis(), destinatarios = destinatarios.joinToString("\n")),
        )
    }

    private suspend fun atualizarEstado(nova: ActaVersao) {
        dao.atualizarActa(nova.paraEntidade())
    }

    suspend fun segmentosCitados(reuniaoId: Long, ids: List<String>): List<Segmento> {
        val todos = revisao.segmentosAgora(reuniaoId).associateBy { it.id }
        return ids.mapNotNull { todos[it] }
    }

    fun descreverVersao(a: ActaVersao): String =
        "v${a.versao} · ${a.estado.rotulo}" + (a.aprovadaEmMs?.let { " · aprovada em ${Formatos.dataHora(it)}" } ?: "")
}
