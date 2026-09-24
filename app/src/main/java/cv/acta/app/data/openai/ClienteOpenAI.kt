package cv.acta.app.data.openai

import cv.acta.app.domain.transcricao.SegmentoBruto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class TipoErroApi {
    SEM_REDE, TEMPO_ESGOTADO, CHAVE_INVALIDA, SEM_SALDO, LIMITE_EXCEDIDO, FICHEIRO_GRANDE, PEDIDO_INVALIDO, SERVIDOR, RECUSA, RESPOSTA_INVALIDA,
}

class ErroApi(val tipo: TipoErroApi, mensagem: String) : Exception(mensagem) {
    /** Erros que afetam todos os pedidos: não vale a pena continuar com as partes seguintes. */
    val bloqueiaRestantes: Boolean get() = tipo in setOf(TipoErroApi.SEM_REDE, TipoErroApi.CHAVE_INVALIDA, TipoErroApi.SEM_SALDO)
}

/** Resposta `diarized_json`: só os campos documentados que usamos; os restantes são ignorados. */
@Serializable
data class RespostaDiarizada(
    val text: String? = null,
    val duration: Double? = null,
    val segments: List<SegmentoDiarizado> = emptyList(),
)

@Serializable
data class SegmentoDiarizado(
    val speaker: String,
    val start: Double,
    val end: Double,
    val text: String,
)

/**
 * Cliente mínimo da API da OpenAI (OkHttp + kotlinx.serialization).
 * A chave é passada em cada chamada e nunca é registada em logs.
 */
class ClienteOpenAI {
    companion object {
        private const val BASE = "https://api.openai.com/v1"
        const val MODELO_TRANSCRICAO = "gpt-4o-transcribe-diarize"
        const val LIMITE_BYTES = 25L * 1024 * 1024
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    /** POST /v1/audio/transcriptions com diarização. Um pedido por parte gravada. */
    suspend fun transcrever(chave: String, ficheiroM4a: File): List<SegmentoBruto> = withContext(Dispatchers.IO) {
        if (ficheiroM4a.length() > LIMITE_BYTES) {
            throw ErroApi(TipoErroApi.FICHEIRO_GRANDE, "A parte tem mais de 25 MB, o limite da API.")
        }
        val corpo = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", ficheiroM4a.name, ficheiroM4a.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("model", MODELO_TRANSCRICAO)
            .addFormDataPart("response_format", "diarized_json")
            .addFormDataPart("chunking_strategy", "auto")
            .build()
        val pedido = Request.Builder()
            .url("$BASE/audio/transcriptions")
            .header("Authorization", "Bearer $chave")
            .post(corpo)
            .build()
        val texto = executar(pedido)
        val resposta = try {
            json.decodeFromString(RespostaDiarizada.serializer(), texto)
        } catch (e: Exception) {
            throw ErroApi(TipoErroApi.RESPOSTA_INVALIDA, "A resposta da transcrição não tem o formato esperado.")
        }
        resposta.segments.map { SegmentoBruto(it.speaker, it.start, it.end, it.text) }
    }

    /**
     * POST /v1/responses com saída estruturada (JSON Schema, strict). Devolve o texto JSON gerado.
     * `store=false`: a OpenAI não guarda a resposta para consulta posterior.
     */
    suspend fun gerarEstruturado(chave: String, modelo: String, instrucoes: String, entrada: String, nomeEsquema: String, esquema: JsonObject): String =
        withContext(Dispatchers.IO) {
            val corpo = buildJsonObject {
                put("model", modelo)
                put("instructions", instrucoes)
                put("input", entrada)
                put("store", false)
                put("text", buildJsonObject {
                    put("format", buildJsonObject {
                        put("type", "json_schema")
                        put("name", nomeEsquema)
                        put("strict", true)
                        put("schema", esquema)
                    })
                })
            }
            val pedido = Request.Builder()
                .url("$BASE/responses")
                .header("Authorization", "Bearer $chave")
                .post(corpo.toString().toRequestBody("application/json".toMediaType()))
                .build()
            extrairTexto(executar(pedido))
        }

    private fun extrairTexto(corpo: String): String {
        val raiz = try {
            json.parseToJsonElement(corpo).jsonObject
        } catch (e: Exception) {
            throw ErroApi(TipoErroApi.RESPOSTA_INVALIDA, "Resposta do modelo ilegível.")
        }
        val estado = raiz["status"]?.jsonPrimitive?.contentOrNull
        if (estado == "incomplete") {
            val motivo = raiz["incomplete_details"]?.let { d -> (d as? JsonObject)?.get("reason")?.jsonPrimitive?.contentOrNull }
            throw ErroApi(TipoErroApi.RESPOSTA_INVALIDA, "Resposta incompleta do modelo${motivo?.let { " ($it)" } ?: ""}. Tente de novo.")
        }
        val saida = raiz["output"]?.jsonArray ?: throw ErroApi(TipoErroApi.RESPOSTA_INVALIDA, "Resposta sem conteúdo.")
        for (item in saida) {
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "message") continue
            for (c in obj["content"]?.jsonArray.orEmpty()) {
                val co = c.jsonObject
                when (co["type"]?.jsonPrimitive?.contentOrNull) {
                    "output_text" -> co["text"]?.jsonPrimitive?.contentOrNull?.let { return it }
                    "refusal" -> throw ErroApi(TipoErroApi.RECUSA, "O modelo recusou o pedido: ${co["refusal"]?.jsonPrimitive?.contentOrNull.orEmpty()}")
                }
            }
        }
        throw ErroApi(TipoErroApi.RESPOSTA_INVALIDA, "Resposta sem texto.")
    }

    private fun executar(pedido: Request): String {
        val resposta = try {
            http.newCall(pedido).execute()
        } catch (e: UnknownHostException) {
            throw ErroApi(TipoErroApi.SEM_REDE, "Sem ligação à Internet. Verifique a rede e tente de novo.")
        } catch (e: ConnectException) {
            throw ErroApi(TipoErroApi.SEM_REDE, "Sem ligação à Internet. Verifique a rede e tente de novo.")
        } catch (e: SocketTimeoutException) {
            throw ErroApi(TipoErroApi.TEMPO_ESGOTADO, "O servidor demorou demasiado a responder. Tente de novo.")
        } catch (e: IOException) {
            throw ErroApi(TipoErroApi.SEM_REDE, "Falha de comunicação: ${e.message ?: "erro de rede"}.")
        }
        resposta.use { r ->
            val corpo = r.body?.string().orEmpty()
            if (r.isSuccessful) return corpo
            val (codigo, mensagem) = lerErro(corpo)
            throw when {
                r.code == 401 -> ErroApi(TipoErroApi.CHAVE_INVALIDA, "Chave da API inválida ou revogada. Corrija-a em Definições.")
                r.code == 429 && codigo == "insufficient_quota" ->
                    ErroApi(TipoErroApi.SEM_SALDO, "A conta OpenAI não tem saldo (quota esgotada). Carregue saldo em platform.openai.com.")
                r.code == 429 -> ErroApi(TipoErroApi.LIMITE_EXCEDIDO, "Limite de pedidos excedido. Aguarde um pouco e tente de novo.")
                r.code == 413 -> ErroApi(TipoErroApi.FICHEIRO_GRANDE, "Ficheiro demasiado grande para a API.")
                r.code in 500..599 -> ErroApi(TipoErroApi.SERVIDOR, "Erro no servidor da OpenAI (${r.code}). Tente mais tarde.")
                else -> ErroApi(TipoErroApi.PEDIDO_INVALIDO, "Pedido recusado (${r.code})${mensagem?.let { ": $it" } ?: "."}")
            }
        }
    }

    private fun lerErro(corpo: String): Pair<String?, String?> = try {
        val erro = json.parseToJsonElement(corpo).jsonObject["error"]?.jsonObject
        erro?.get("code")?.jsonPrimitive?.contentOrNull to erro?.get("message")?.jsonPrimitive?.contentOrNull
    } catch (e: Exception) {
        null to null
    }
}
