package com.jarvis.finanzas

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * API oficial de InvertirOnline. Usa tu usuario y contraseña de IOL.
 * Documentación: https://api.invertironline.com
 */
class IolApi(private val st: Store) {
    private val base = "https://api.invertironline.com"
    private val lock = Mutex()
    private var fciCache: List<Quote>? = null

    val configured get() = st.iolUser.isNotBlank() && st.iolPass.isNotBlank()

    private suspend fun token(): String = lock.withLock {
        val now = System.currentTimeMillis()
        if (st.accessToken.isNotBlank() && st.tokenExpires > now + 60_000) return@withLock st.accessToken
        if (st.refreshToken.isNotBlank()) {
            try {
                return@withLock saveToken(
                    Http.postForm("$base/token", mapOf("refresh_token" to st.refreshToken, "grant_type" to "refresh_token"))
                )
            } catch (_: Exception) { /* si falla, vuelve a iniciar sesión */ }
        }
        if (!configured) throw IllegalStateException("Cargá tu usuario y contraseña de IOL en Ajustes")
        try {
            saveToken(
                Http.postForm(
                    "$base/token",
                    mapOf("username" to st.iolUser, "password" to st.iolPass, "grant_type" to "password"),
                )
            )
        } catch (e: HttpException) {
            if (e.code == 400 || e.code == 401) throw IllegalStateException(
                "IOL rechazó el usuario o la contraseña. Revisalos en Ajustes y que tengas habilitado el uso de la API en tu cuenta de IOL."
            )
            throw e
        }
    }

    private fun saveToken(body: String): String {
        val o = JSONObject(body)
        val t = o.getString("access_token")
        st.accessToken = t
        st.refreshToken = o.optString("refresh_token", "")
        val secs = o.optLong("expires_in", 900L)
        st.tokenExpires = System.currentTimeMillis() + secs * 1000
        return t
    }

    suspend fun get(path: String): String {
        val t = token()
        return try {
            Http.get(base + path, mapOf("Authorization" to "Bearer $t"))
        } catch (e: HttpException) {
            if (e.code == 401) {
                st.accessToken = ""; st.tokenExpires = 0
                Http.get(base + path, mapOf("Authorization" to "Bearer ${token()}"))
            } else throw e
        }
    }

    /** Prueba la conexión y devuelve un texto para mostrar. */
    suspend fun test(): String {
        st.clearToken()
        val h = portfolio()
        return "Conectado a IOL. Tu cartera tiene ${h.size} activos."
    }

    suspend fun portfolio(): List<Holding> {
        val o = JSONObject(get("/api/v2/portafolio/argentina"))
        val arr = o.optJSONArray("activos") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            val t = a.optJSONObject("titulo") ?: JSONObject()
            Holding(
                symbol = t.optString("simbolo"),
                name = t.optString("descripcion"),
                type = mapType(t.optString("tipo")),
                currency = cur(t.optString("moneda")),
                qty = a.num("cantidad") ?: 0.0,
                ppc = a.num("ppc"),
                last = a.num("ultimoPrecio"),
                dayPct = a.num("variacionDiaria"),
                value = a.num("valorizado") ?: 0.0,
                pnl = a.num("gananciaDinero"),
                pnlPct = a.num("gananciaPorcentaje"),
            )
        }.filter { it.symbol.isNotBlank() }
    }

    /** Plata disponible en la cuenta: pesos y dólares. */
    suspend fun cash(): Pair<Double, Double> {
        val o = JSONObject(get("/api/v2/estadocuenta"))
        val arr = o.optJSONArray("cuentas") ?: JSONArray()
        var ars = 0.0
        var usd = 0.0
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val d = c.num("disponible") ?: 0.0
            if (cur(c.optString("moneda")) == "USD") usd += d else ars += d
        }
        return ars to usd
    }

    /** Panel de cotizaciones. Prueba varios nombres porque IOL los escribe distinto según el instrumento. */
    suspend fun panel(candidates: List<Pair<String, String>>): List<Quote> {
        var last: Exception? = null
        for ((instr, panel) in candidates) {
            try {
                val body = get("/api/v2/Cotizaciones/${enc(instr)}/${enc(panel)}/argentina")
                val arr = JSONObject(body).optJSONArray("titulos") ?: continue
                val list = (0 until arr.length()).map { i ->
                    val q = arr.getJSONObject(i)
                    Quote(
                        symbol = q.optString("simbolo"),
                        name = q.optString("descripcion"),
                        last = q.num("ultimoPrecio", "ultimoCierre"),
                        chgPct = q.num("variacionPorcentual", "variacion"),
                        currency = cur(q.optString("moneda")),
                    )
                }.filter { it.symbol.isNotBlank() }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                last = e
            }
        }
        if (last != null) throw last
        return emptyList()
    }

    suspend fun quote(sym: String): Quote? {
        try {
            val q = JSONObject(get("/api/v2/bCBA/Titulos/${enc(sym)}/Cotizacion"))
            val p = q.num("ultimoPrecio") ?: return fci(sym)
            return Quote(sym, q.optString("descripcionTitulo"), p, q.num("variacion", "variacionPorcentual"), cur(q.optString("moneda")))
        } catch (e: Exception) {
            return fci(sym)
        }
    }

    /** Fondos comunes de inversión listados en IOL. */
    private suspend fun fci(sym: String): Quote? {
        val list = fciCache ?: try {
            val arr = JSONArray(get("/api/v2/Titulos/FCI"))
            (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                Quote(
                    symbol = f.optString("simbolo"),
                    name = f.optString("descripcion"),
                    last = f.num("ultimoOperado", "valorCuotaparte", "ultimoPrecio"),
                    chgPct = f.num("variacion", "variacionDiaria"),
                    currency = cur(f.optString("moneda")),
                )
            }.also { fciCache = it }
        } catch (e: Exception) {
            emptyList()
        }
        return list.firstOrNull { it.symbol.equals(sym, ignoreCase = true) }
    }

    companion object {
        fun cur(m: String): String {
            val s = m.lowercase()
            return if ("dolar" in s || "dólar" in s || s == "usd" || s == "us$") "USD" else "ARS"
        }

        fun mapType(t: String): String {
            val s = t.lowercase()
            return when {
                "cedear" in s -> "CEDEAR"
                "accion" in s || "acción" in s -> "Acción"
                "fondo" in s || "fci" in s -> "FCI"
                "obligacion" in s -> "ON"
                "letra" in s -> "Letra"
                "titulospublicos" in s || "bono" in s || "publico" in s -> "Bono"
                "caucion" in s -> "Caución"
                "opcion" in s -> "Opción"
                else -> t.ifBlank { "Otro" }
            }
        }
    }
}
