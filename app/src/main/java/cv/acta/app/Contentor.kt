package cv.acta.app

import android.app.Application
import cv.acta.app.data.RepositorioActas
import cv.acta.app.data.RepositorioGravacao
import cv.acta.app.data.RepositorioReunioes
import cv.acta.app.data.RepositorioRevisao
import cv.acta.app.data.ServicoTranscricao
import cv.acta.app.data.db.BaseDados
import cv.acta.app.data.definicoes.Definicoes
import cv.acta.app.data.demo.DadosDemo
import cv.acta.app.data.openai.ClienteOpenAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File

/** Injeção de dependências manual: um único objeto com os repositórios da app. */
class Contentor(val app: Application) {
    /** Escopo da aplicação, para trabalho que deve sobreviver à saída de um ecrã. */
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val baseDados: BaseDados = BaseDados.criar(app)
    val dao = baseDados.dao()
    val definicoes = Definicoes(app)
    private val openAi = ClienteOpenAI()

    val reunioes = RepositorioReunioes(dao)
    val gravacao = RepositorioGravacao(app, dao)
    val revisao = RepositorioRevisao(dao)
    val transcricao = ServicoTranscricao(app, dao, definicoes, openAi, escopo)
    val actas = RepositorioActas(dao, reunioes, revisao, definicoes, openAi, escopo)
    val demo = DadosDemo(dao, gravacao)

    /** "Apagar todos os dados" (RF-SEG-012): BD, gravações, PDF, definições e chave do Keystore. */
    suspend fun apagarTodosOsDados() = withContext(Dispatchers.IO) {
        baseDados.clearAllTables()
        File(app.filesDir, "gravacoes").deleteRecursively()
        File(app.cacheDir, "m4a").deleteRecursively()
        File(app.cacheDir, "pdf").deleteRecursively()
        definicoes.apagarTudo()
    }
}
