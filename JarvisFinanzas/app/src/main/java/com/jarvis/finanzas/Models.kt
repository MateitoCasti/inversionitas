package com.jarvis.finanzas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val J = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class Quote(
    val symbol: String,
    val name: String = "",
    val last: Double? = null,
    val chgPct: Double? = null,
    val currency: String = "ARS",
)

@Serializable
data class Holding(
    val symbol: String,
    val name: String,
    val type: String,
    val currency: String,
    val qty: Double,
    val ppc: Double?,
    val last: Double?,
    val dayPct: Double?,
    val value: Double,
    val pnl: Double?,
    val pnlPct: Double?,
)

@Serializable
data class Fx(val name: String, val compra: Double?, val venta: Double?)

@Serializable
data class News(
    val title: String,
    val link: String,
    val source: String,
    val date: Long,
    val region: String,
    val mine: Boolean = false,
    val impact: String = "bajo",
)

/** cond: "above" (sube a), "below" (baja a), "move" (se mueve más de value % en el día) */
@Serializable
data class Alert(val id: String, val symbol: String, val cond: String, val value: Double)

@Serializable
data class Snapshot(
    val time: Long = 0,
    val holdings: List<Holding> = emptyList(),
    val cashArs: Double = 0.0,
    val cashUsd: Double = 0.0,
    val fx: List<Fx> = emptyList(),
    val indices: List<Quote> = emptyList(),
    val riesgoPais: Double? = null,
    val inflacion: Double? = null,
    val inflacionMes: String = "",
    val plazoFijoTna: Double? = null,
    val plazoFijoBanco: String = "",
    val panels: Map<String, List<Quote>> = emptyMap(),
    val watch: List<Quote> = emptyList(),
    val news: List<News> = emptyList(),
    val errors: List<String> = emptyList(),
)

/** Activos de referencia, como en IOL. */
object Ref {
    val CEDEARS = listOf("AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "TSLA", "MELI", "KO", "BRKB", "VIST", "NU")
    val ETFS = listOf("SPY", "QQQ", "DIA", "IWM", "EEM", "EWZ", "XLE", "GLD")
    val BONOS = listOf("AL30", "GD30", "AL35", "GD35", "AE38", "GD41", "GD46")
}
