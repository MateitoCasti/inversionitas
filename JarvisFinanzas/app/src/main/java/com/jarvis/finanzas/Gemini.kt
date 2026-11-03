package com.jarvis.finanzas

import org.json.JSONArray
import org.json.JSONObject

/** Análisis con Google Gemini (clave gratuita de https://aistudio.google.com/apikey). Opcional. */
object Gemini {
    suspend fun ask(key: String, model: String, prompt: String): String {
        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
        )
        val m = model.ifBlank { "gemini-2.5-flash" }
        val raw = try {
            Http.postJson(
                "https://generativelanguage.googleapis.com/v1beta/models/${enc(m)}:generateContent",
                body.toString(),
                mapOf("x-goog-api-key" to key),
            )
        } catch (e: HttpException) {
            val msg = try { JSONObject(e.body).getJSONObject("error").optString("message") } catch (_: Exception) { e.body.take(200) }
            throw IllegalStateException(
                when (e.code) {
                    400, 403 -> "Gemini rechazó la clave o el modelo: $msg"
                    404 -> "No existe el modelo \"$m\". Probá con otro en Ajustes (por ejemplo gemini-2.5-flash)."
                    429 -> "Llegaste al límite gratuito de Gemini por ahora. Probá en un rato."
                    else -> "Error de Gemini (${e.code}): $msg"
                }
            )
        }
        val r = JSONObject(raw)
        val cands = r.optJSONArray("candidates") ?: throw IllegalStateException("Gemini no devolvió respuesta.")
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
    }
}
