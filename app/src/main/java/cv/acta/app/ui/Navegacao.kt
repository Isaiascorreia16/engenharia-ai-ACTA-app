package cv.acta.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cv.acta.app.ui.acta.EcraActa
import cv.acta.app.ui.acta.EcraDistribuir
import cv.acta.app.ui.ajuda.EcraAjuda
import cv.acta.app.ui.aviso.EcraAviso
import cv.acta.app.ui.definicoes.EcraDefinicoes
import cv.acta.app.ui.gravacao.EcraGravacao
import cv.acta.app.ui.inicio.EcraInicio
import cv.acta.app.ui.reuniao.EcraNovaReuniao
import cv.acta.app.ui.reuniao.EcraReuniao
import cv.acta.app.ui.revisao.EcraRevisao

private object Rotas {
    const val INICIO = "inicio"
    const val NOVA = "nova"
    const val REUNIAO = "reuniao/{id}"
    const val AVISO = "aviso/{id}"
    const val GRAVACAO = "gravacao/{id}"
    const val REVISAO = "revisao/{id}?destaque={destaque}"
    const val ACTA = "acta/{id}?acta={acta}"
    const val DISTRIBUIR = "distribuir/{acta}"
    const val DEFINICOES = "definicoes"
    const val AJUDA = "ajuda"
}

private val argId = navArgument("id") { type = NavType.LongType }

@Composable
fun Navegacao(nav: NavHostController = rememberNavController()) {
    fun voltar() {
        if (!nav.popBackStack()) nav.navigate(Rotas.INICIO)
    }

    NavHost(navController = nav, startDestination = Rotas.INICIO) {
        composable(Rotas.INICIO) {
            EcraInicio(
                abrirReuniao = { nav.navigate("reuniao/$it") },
                novaReuniao = { nav.navigate(Rotas.NOVA) },
                abrirDefinicoes = { nav.navigate(Rotas.DEFINICOES) },
                abrirAjuda = { nav.navigate(Rotas.AJUDA) },
            )
        }
        composable(Rotas.NOVA) {
            EcraNovaReuniao(aoVoltar = ::voltar, aoCriar = { id ->
                nav.navigate("reuniao/$id") { popUpTo(Rotas.INICIO) }
            })
        }
        composable(Rotas.REUNIAO, arguments = listOf(argId)) { e ->
            val id = e.arguments?.getLong("id") ?: return@composable
            EcraReuniao(
                reuniaoId = id,
                aoVoltar = ::voltar,
                prepararGravacao = { nav.navigate("aviso/$id") },
                abrirGravacao = { nav.navigate("gravacao/$id") },
                abrirRevisao = { nav.navigate("revisao/$id") },
                abrirActa = { nav.navigate("acta/$id") },
                abrirDefinicoes = { nav.navigate(Rotas.DEFINICOES) },
            )
        }
        composable(Rotas.AVISO, arguments = listOf(argId)) { e ->
            val id = e.arguments?.getLong("id") ?: return@composable
            EcraAviso(id, aoVoltar = ::voltar, aoIniciar = {
                nav.navigate("gravacao/$id") { popUpTo("reuniao/{id}") }
            })
        }
        composable(Rotas.GRAVACAO, arguments = listOf(argId)) { e ->
            val id = e.arguments?.getLong("id") ?: return@composable
            EcraGravacao(id, aoVoltar = ::voltar, aoTerminar = ::voltar)
        }
        composable(
            Rotas.REVISAO,
            arguments = listOf(argId, navArgument("destaque") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { e ->
            val id = e.arguments?.getLong("id") ?: return@composable
            EcraRevisao(
                reuniaoId = id,
                destaque = e.arguments?.getString("destaque"),
                aoVoltar = ::voltar,
                abrirActa = { nav.navigate("acta/$id") },
            )
        }
        composable(
            Rotas.ACTA,
            arguments = listOf(argId, navArgument("acta") { type = NavType.LongType; defaultValue = -1L }),
        ) { e ->
            val id = e.arguments?.getLong("id") ?: return@composable
            val acta = e.arguments?.getLong("acta")?.takeIf { it > 0 }
            EcraActa(
                reuniaoId = id,
                actaIdInicial = acta,
                aoVoltar = ::voltar,
                abrirNaTranscricao = { seg -> nav.navigate("revisao/$id?destaque=$seg") },
                abrirDistribuicao = { a -> nav.navigate("distribuir/$a") },
            )
        }
        composable(Rotas.DISTRIBUIR, arguments = listOf(navArgument("acta") { type = NavType.LongType })) { e ->
            val acta = e.arguments?.getLong("acta") ?: return@composable
            EcraDistribuir(acta, aoVoltar = ::voltar)
        }
        composable(Rotas.DEFINICOES) {
            EcraDefinicoes(aoVoltar = ::voltar, aposApagarTudo = {
                nav.navigate(Rotas.INICIO) { popUpTo(nav.graph.id) { inclusive = true } }
            })
        }
        composable(Rotas.AJUDA) { EcraAjuda(aoVoltar = ::voltar) }
    }
}
