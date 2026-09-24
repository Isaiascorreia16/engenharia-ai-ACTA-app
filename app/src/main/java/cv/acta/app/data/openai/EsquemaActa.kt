package cv.acta.app.data.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON Schema da saída estruturada (modo strict): todos os campos obrigatórios,
 * `additionalProperties: false` em todos os objetos e campos opcionais como ["string", "null"].
 * Não há campos de cabeçalho nem de tempos: esses dados nunca vêm do LLM.
 */
object EsquemaActa {

    private fun texto(descricao: String? = null) = buildJsonObject {
        put("type", "string")
        descricao?.let { put("description", it) }
    }

    private fun textoOuNulo(descricao: String) = buildJsonObject {
        put("type", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("string")); add(kotlinx.serialization.json.JsonPrimitive("null")) })
        put("description", descricao)
    }

    private fun listaDeTextos(descricao: String) = buildJsonObject {
        put("type", "array")
        put("description", descricao)
        put("items", texto())
    }

    private fun objeto(propriedades: Map<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", buildJsonArray { propriedades.keys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        put("properties", buildJsonObject { propriedades.forEach { (k, v) -> put(k, v) } })
    }

    private fun lista(itens: JsonObject) = buildJsonObject {
        put("type", "array")
        put("items", itens)
    }

    val ESQUEMA: JsonObject = objeto(
        linkedMapOf(
            "ordemTrabalhos" to lista(
                objeto(linkedMapOf("titulo" to texto(), "sumula" to texto()))
            ),
            "deliberacoes" to lista(
                objeto(
                    linkedMapOf(
                        "texto" to texto("Deliberação tomada"),
                        "votacao" to textoOuNulo("Resultado da votação, se mencionado"),
                        "segmentoIds" to listaDeTextos("Ids dos segmentos de origem"),
                        "citacao" to texto("Excerto literal curto de um dos segmentos"),
                    )
                )
            ),
            "accoes" to lista(
                objeto(
                    linkedMapOf(
                        "responsavel" to textoOuNulo("Nome exato de um participante, ou null se não for claro"),
                        "descricao" to texto(),
                        "prazo" to textoOuNulo("AAAA-MM-DD ou null"),
                        "segmentoIds" to listaDeTextos("Ids dos segmentos de origem"),
                        "citacao" to texto("Excerto literal curto de um dos segmentos"),
                    )
                )
            ),
            "proximaReuniao" to textoOuNulo("AAAA-MM-DD ou AAAA-MM-DDTHH:MM, ou null"),
            "sumulaExecutiva" to texto("Máximo de 250 palavras"),
        )
    )
}
