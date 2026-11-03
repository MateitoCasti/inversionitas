package com.jarvis.finanzas

import org.json.JSONArray
import org.json.JSONObject

/** Fuentes públicas y gratuitas, sin cuenta. */
object MarketApi {

    suspend fun dolares(): List<Fx> {
        val arr = JSONArray(Http.get("https://dolarapi.com/v1/dolares"))
        val names = linkedMapOf(
            "oficial" to "Oficial", "bolsa" to "MEP", "contadoconliqui" to "CCL",
            "blue" to "Blue", "cripto" to "Cripto", "tarjeta" to "Tarjeta", "mayorista" to "Mayorista",
        )
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val n = names[o.optString("casa")] ?: return@mapNotNull null
            Fx(n, o.num("compra"), o.num("venta"))
        }
        return list.sortedBy { names.values.indexOf(it.name) }
    }

    suspend fun riesgoPais(): Double? =
        JSONObject(Http.get("https://api.argentinadatos.com/v1/finanzas/indices/riesgo-pais/ultimo")).num("valor")

    /** Última inflación mensual (INDEC): valor y fecha. */
    suspend fun inflacion(): Pair<Double?, String> {
        val arr = JSONArray(Http.get("https://api.argentinadatos.com/v1/finanzas/indices/inflacion"))
        if (arr.length() == 0) return null to ""
        val o = arr.getJSONObject(arr.length() - 1)
        return o.num("valor") to o.optString("fecha")
    }

    /** Mejor TNA de plazo fijo para clientes. */
    suspend fun plazoFijo(): Pair<Double?, String> {
        val arr = JSONArray(Http.get("https://api.argentinadatos.com/v1/finanzas/tasas/plazoFijo"))
        var best: Double? = null
        var bank = ""
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.num("tnaClientes") ?: continue
            if (best == null || t > best) { best = t; bank = o.optString("entidad") }
        }
        return best?.let { if (it < 1.5) it * 100 else it } to bank
    }

    /** Índices de EE.UU. y commodities desde Yahoo Finance. */
    suspend fun yahoo(sym: String, name: String, currency: String = "USD"): Quote? = try {
        val o = JSONObject(Http.get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(sym)}?range=1d&interval=5m"))
        val m = o.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
        val p = m.num("regularMarketPrice")
        val prev = m.num("chartPreviousClose", "previousClose")
        Quote(name, name, p, if (p != null && prev != null && prev != 0.0) (p / prev - 1) * 100 else null, currency)
    } catch (e: Exception) {
        null
    }

    suspend fun crypto(): List<Quote> {
        val o = JSONObject(Http.get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=usd&include_24hr_change=true"))
        return listOf("bitcoin" to "Bitcoin", "ethereum" to "Ethereum").mapNotNull { (id, n) ->
            val c = o.optJSONObject(id) ?: return@mapNotNull null
            Quote(n, n, c.num("usd"), c.num("usd_24h_change"), "USD")
        }
    }

    suspend fun indices(): List<Quote> {
        val defs = listOf(
            Triple("^MERV", "Merval", "ARS"),
            Triple("^GSPC", "S&P 500", "USD"),
            Triple("^IXIC", "Nasdaq", "USD"),
            Triple("^DJI", "Dow Jones", "USD"),
            Triple("^VIX", "VIX", "USD"),
            Triple("^TNX", "Tasa 10 años EE.UU.", "USD"),
            Triple("GC=F", "Oro", "USD"),
            Triple("CL=F", "Petróleo WTI", "USD"),
            Triple("ZS=F", "Soja", "USD"),
        )
        return defs.mapNotNull { (s, n, c) -> yahoo(s, n, c) }
    }
}
