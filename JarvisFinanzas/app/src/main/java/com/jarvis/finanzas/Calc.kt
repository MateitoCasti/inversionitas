package com.jarvis.finanzas

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

val AR_TZ: TimeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
private val SYM = DecimalFormatSymbols(Locale("es", "AR"))

fun num(v: Double?, dec: Int = 2): String =
    if (v == null || v.isNaN()) "—" else DecimalFormat(if (dec == 0) "#,##0" else "#,##0." + "0".repeat(dec), SYM).format(v)

fun money(v: Double?, cur: String = "ARS", dec: Int = 2): String =
    if (v == null) "—" else (if (cur == "USD") "US$ " else "$ ") + num(v, dec)

fun pct(v: Double?): String = if (v == null) "—" else (if (v > 0) "+" else "") + num(v, 2) + "%"

fun dayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = AR_TZ }.format(Date())

fun hhmm(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale.US).apply { timeZone = AR_TZ }.format(Date(ms))

fun Snapshot.mep(): Double? = fx.firstOrNull { it.name == "MEP" }?.venta
fun Snapshot.toArs(v: Double, cur: String): Double = if (cur == "USD") v * (mep() ?: 0.0) else v

data class Totals(
    val totalArs: Double, val totalUsd: Double?, val investedArs: Double, val cashArs: Double,
    val dayArs: Double, val dayPct: Double?, val pnlArs: Double, val pnlPct: Double?,
)

fun Snapshot.totals(): Totals {
    var v = 0.0; var day = 0.0; var pnl = 0.0
    holdings.forEach { h ->
        val va = toArs(h.value, h.currency)
        v += va
        h.dayPct?.let { p -> if (p > -100) day += va - va / (1 + p / 100) }
        h.pnl?.let { pnl += toArs(it, h.currency) }
    }
    val cash = cashArs + toArs(cashUsd, "USD")
    val total = v + cash
    val cost = v - pnl
    return Totals(
        totalArs = total,
        totalUsd = mep()?.takeIf { it > 0 }?.let { total / it },
        investedArs = v, cashArs = cash,
        dayArs = day, dayPct = if (v - day > 0) day / (v - day) * 100 else null,
        pnlArs = pnl, pnlPct = if (cost > 0) pnl / cost * 100 else null,
    )
}

/** Todos los precios conocidos por símbolo (cartera, paneles y seguimiento). */
fun Snapshot.priceMap(): Map<String, Quote> {
    val m = linkedMapOf<String, Quote>()
    panels.values.flatten().forEach { m[it.symbol.uppercase()] = it }
    watch.forEach { m[it.symbol.uppercase()] = it }
    holdings.forEach { m[it.symbol.uppercase()] = Quote(it.symbol, it.name, it.last, it.dayPct, it.currency) }
    return m
}

fun Alert.describe(): String = when (cond) {
    "above" -> "Si $symbol sube a ${num(value)}"
    "below" -> "Si $symbol baja a ${num(value)}"
    else -> "Si $symbol se mueve más de ${num(value)}% en el día"
}

fun Alert.hit(q: Quote?): Boolean {
    val p = q?.last ?: return false
    return when (cond) {
        "above" -> p >= value
        "below" -> p <= value
        else -> q.chgPct?.let { abs(it) >= value } ?: false
    }
}

fun Snapshot.alertHits(alerts: List<Alert>): List<Pair<Alert, Quote>> {
    val pm = priceMap()
    return alerts.mapNotNull { a -> pm[a.symbol.uppercase()]?.let { q -> if (a.hit(q)) a to q else null } }
}

/** Análisis automático, sin IA: resumen, distribución, concentración, pesos vs inflación y alertas. */
fun Snapshot.localAnalysis(alerts: List<Alert>): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    if (holdings.isEmpty() && cashArs == 0.0 && cashUsd == 0.0) {
        out += "Tu cartera" to "Conectá tu cuenta de IOL en Ajustes para ver tu cartera real y su análisis."
    } else {
        val t = totals()
        val sb = StringBuilder()
        sb.append("• Valor total: ${money(t.totalArs, dec = 0)}")
        t.totalUsd?.let { sb.append(" (≈ ${money(it, "USD", 0)} al MEP)") }
        sb.append("\n• Hoy: ${pct(t.dayPct)} (${money(t.dayArs, dec = 0)})")
        sb.append("\n• Ganancia acumulada: ${money(t.pnlArs, dec = 0)} (${pct(t.pnlPct)})")
        val best = holdings.filter { it.dayPct != null }.maxByOrNull { it.dayPct!! }
        val worst = holdings.filter { it.dayPct != null }.minByOrNull { it.dayPct!! }
        if (best != null && worst != null && best != worst) {
            sb.append("\n• Mejor del día: ${best.symbol} ${pct(best.dayPct)} · Peor: ${worst.symbol} ${pct(worst.dayPct)}")
        }
        out += "Cómo viene la cartera" to sb.toString()

        val total = t.totalArs.takeIf { it > 0 } ?: 1.0
        val byType = holdings.groupBy { it.type }.mapValues { (_, l) -> l.sumOf { toArs(it.value, it.currency) } }.toMutableMap()
        if (t.cashArs > 0) byType["Disponible"] = t.cashArs
        val rv = (byType["Acción"] ?: 0.0) + (byType["CEDEAR"] ?: 0.0) + (byType["Opción"] ?: 0.0)
        val rf = (byType["Bono"] ?: 0.0) + (byType["ON"] ?: 0.0) + (byType["Letra"] ?: 0.0)
        val liq = (byType["FCI"] ?: 0.0) + (byType["Caución"] ?: 0.0) + (byType["Disponible"] ?: 0.0)
        val usdShare = holdings.filter { it.currency == "USD" || it.type == "CEDEAR" }.sumOf { toArs(it.value, it.currency) } + toArs(cashUsd, "USD")
        val d = StringBuilder()
        byType.entries.sortedByDescending { it.value }.forEach { (k, v) -> d.append("• $k: ${num(v / total * 100, 1)}%\n") }
        d.append("• Renta variable ${num(rv / total * 100, 0)}% · Renta fija ${num(rf / total * 100, 0)}% · Liquidez ${num(liq / total * 100, 0)}%\n")
        d.append("• Dolarizado (USD + CEDEARs): ${num(usdShare / total * 100, 0)}% · En pesos: ${num(100 - usdShare / total * 100, 0)}%")
        out += "Distribución" to d.toString()

        val top = holdings.maxByOrNull { toArs(it.value, it.currency) }
        if (top != null) {
            val share = toArs(top.value, top.currency) / total * 100
            val txt = "• Tu mayor posición es ${top.symbol}, con el ${num(share, 1)}% de la cartera." +
                when {
                    share >= 40 -> "\n• Concentración alta: una mala noticia de ese activo pesa mucho en tu total."
                    share >= 25 -> "\n• Concentración moderada. Vigilá ese activo."
                    else -> "\n• Concentración razonable."
                } + "\n• Activos distintos: ${holdings.size}."
            out += "Riesgo y concentración" to txt
        }
    }

    val pf = StringBuilder()
    inflacion?.let { pf.append("• Inflación del último mes (INDEC, $inflacionMes): ${num(it, 1)}%\n") }
    plazoFijoTna?.let {
        val monthly = it / 12
        pf.append("• Mejor plazo fijo: ${num(it, 1)}% TNA ($plazoFijoBanco) ≈ ${num(monthly, 2)}% por mes\n")
        inflacion?.let { inf ->
            pf.append(if (monthly >= inf) "• Hoy el plazo fijo le gana a la última inflación." else "• Hoy el plazo fijo queda por debajo de la última inflación.")
        }
    }
    if (pf.isNotEmpty()) out += "Pesos: ¿le ganan a la inflación?" to pf.toString().trim()

    val hits = alertHits(alerts)
    out += "Alertas" to if (hits.isEmpty()) "Ninguna alerta disparada." else hits.joinToString("\n") { (a, q) -> "• ${a.describe()} → ahora ${money(q.last, q.currency)} (${pct(q.chgPct)})" }

    val mine = news.filter { it.mine }.take(4)
    if (mine.isNotEmpty()) out += "Noticias que tocan tu cartera" to mine.joinToString("\n") { "• ${it.title}" }

    out += "Aclaración" to "Jarvis informa y analiza; no es asesoramiento financiero."
    return out
}
