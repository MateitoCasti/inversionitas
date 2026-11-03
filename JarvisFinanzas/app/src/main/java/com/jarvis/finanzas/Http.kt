package com.jarvis.finanzas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, val body: String) : Exception("HTTP $code ${body.take(200)}")

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** Lee un número aunque venga como texto o con otro nombre de campo. */
fun JSONObject.num(vararg keys: String): Double? {
    for (k in keys) {
        if (has(k) && !isNull(k)) {
            when (val v = opt(k)) {
                is Number -> return v.toDouble()
                is String -> v.replace(",", ".").toDoubleOrNull()?.let { return it }
            }
        }
    }
    return null
}

object Http {
    private const val UA = "Mozilla/5.0 (Linux; Android) JarvisFinanzas/1.0"

    private suspend fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        contentType: String?,
    ): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 25000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json, application/xml, text/xml, */*")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpException(code, text)
            text
        } finally {
            c.disconnect()
        }
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()) =
        request(url, "GET", headers, null, null)

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()) =
        request(
            url, "POST", headers,
            form.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" },
            "application/x-www-form-urlencoded",
        )

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()) =
        request(url, "POST", headers, json, "application/json")
}
