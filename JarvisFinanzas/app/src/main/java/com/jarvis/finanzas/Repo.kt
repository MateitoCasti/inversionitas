package com.jarvis.finanzas

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Collections

class Repo(private val ctx: Context) {
    val st = Store(ctx)
    val iol = IolApi(st)

    fun load(): Snapshot = st.snapshot

    /** Trae todo en paralelo. Si una fuente falla, conserva el último dato bueno y anota el error. */
    suspend fun refresh(): Snapshot = coroutineScope {
        val prev = st.snapshot
        val errors = Collections.synchronizedList(mutableListOf<String>())
        suspend fun <T> safe(label: String, block: suspend () -> T): T? = try {
            block()
        } catch (e: Exception) {
            errors.add("$label: ${e.message?.take(180) ?: e.javaClass.simpleName}"); null
        }
        val useIol = iol.configured

        val fxD = async { safe("Dólar") { MarketApi.dolares() } }
        val rpD = async { safe("Riesgo país") { MarketApi.riesgoPais() } }
        val infD = async { safe("Inflación") { MarketApi.inflacion() } }
        val pfD = async { safe("Plazo fijo") { MarketApi.plazoFijo() } }
        val ixD = async { safe("Índices") { MarketApi.indices() } }
        val crD = async { safe("Cripto") { MarketApi.crypto() } }

        val holdD = async { if (useIol) safe("IOL cartera") { iol.portfolio() } else null }
        val cashD = async { if (useIol) safe("IOL cuenta") { iol.cash() } else null }
        val liderD = async { if (useIol) safe("IOL panel líder") { iol.panel(listOf("Acciones" to "Merval", "acciones" to "merval", "Acciones" to "Merval 25")) } else null }
        val cedD = async { if (useIol) safe("IOL CEDEARs") { iol.panel(listOf("Acciones" to "CEDEARs", "CEDEARs" to "Todos", "cedears" to "todos")) } else null }
        val bonD = async { if (useIol) safe("IOL bonos") { iol.panel(listOf("Bonos" to "Todos", "TitulosPublicos" to "Todos", "titulosPublicos" to "todos", "Bonos" to "Soberanos en dólares")) } else null }

        val holdings = holdD.await() ?: if (useIol) prev.holdings else emptyList()
        val newsD = async { safe("Noticias") { NewsApi.fetch(holdings) } }

        val panels = linkedMapOf<String, List<Quote>>()
        (liderD.await() ?: prev.panels["Panel líder"])?.let { panels["Panel líder"] = it }
        val ced = cedD.await()
        if (ced != null) {
            panels["CEDEARs"] = Ref.CEDEARS.mapNotNull { s -> ced.firstOrNull { it.symbol.equals(s, true) } }
            panels["ETFs"] = Ref.ETFS.mapNotNull { s -> ced.firstOrNull { it.symbol.equals(s, true) } }
        } else {
            prev.panels["CEDEARs"]?.let { panels["CEDEARs"] = it }
            prev.panels["ETFs"]?.let { panels["ETFs"] = it }
        }
        val bon = bonD.await()
        if (bon != null) panels["Bonos"] = Ref.BONOS.mapNotNull { s -> bon.firstOrNull { it.symbol.equals(s, true) } }
        else prev.panels["Bonos"]?.let { panels["Bonos"] = it }

        // Seguimiento: primero busca en lo que ya se trajo, después pregunta a IOL uno por uno.
        val known = (panels.values.flatten() + (ced ?: emptyList()) + (bon ?: emptyList())).associateBy { it.symbol.uppercase() }
        val watch = st.watch.mapNotNull { s ->
            known[s.uppercase()]
                ?: (if (useIol) safe("IOL $s") { iol.quote(s) } else null)
                ?: prev.watch.firstOrNull { it.symbol.equals(s, true) }
        }

        val cash = cashD.await()
        val inf = infD.await()
        val pf = pfD.await()
        val snap = Snapshot(
            time = System.currentTimeMillis(),
            holdings = holdings,
            cashArs = cash?.first ?: if (useIol) prev.cashArs else 0.0,
            cashUsd = cash?.second ?: if (useIol) prev.cashUsd else 0.0,
            fx = fxD.await() ?: prev.fx,
            indices = (ixD.await().orEmpty() + crD.await().orEmpty()).ifEmpty { prev.indices },
            riesgoPais = rpD.await() ?: prev.riesgoPais,
            inflacion = inf?.first ?: prev.inflacion,
            inflacionMes = inf?.second ?: prev.inflacionMes,
            plazoFijoTna = pf?.first ?: prev.plazoFijoTna,
            plazoFijoBanco = pf?.second ?: prev.plazoFijoBanco,
            panels = panels,
            watch = watch,
            news = newsD.await() ?: prev.news,
            errors = errors.toList(),
        )
        st.snapshot = snap
        snap
    }

    /** Texto con los datos para que Gemini analice. */
    fun aiPrompt(s: Snapshot, question: String): String {
        val t = s.totals()
        val data = JSONObject()
            .put("fecha", hhmm(s.time))
            .put("totalPesos", t.totalArs).put("totalDolaresMEP", t.totalUsd).put("variacionHoyPct", t.dayPct)
            .put("gananciaAcumuladaPesos", t.pnlArs).put("disponiblePesos", s.cashArs).put("disponibleDolares", s.cashUsd)
            .put("cartera", JSONArray(s.holdings.map {
                JSONObject().put("ticker", it.symbol).put("nombre", it.name).put("tipo", it.type).put("moneda", it.currency)
                    .put("cantidad", it.qty).put("precioCompra", it.ppc).put("precioActual", it.last)
                    .put("variacionHoyPct", it.dayPct).put("valor", it.value).put("gananciaPct", it.pnlPct)
            }))
            .put("dolar", JSONArray(s.fx.map { JSONObject().put("tipo", it.name).put("venta", it.venta) }))
            .put("indices", JSONArray(s.indices.map { JSONObject().put("nombre", it.name).put("valor", it.last).put("variacionPct", it.chgPct) }))
            .put("riesgoPais", s.riesgoPais).put("inflacionMensual", s.inflacion).put("mejorPlazoFijoTNA", s.plazoFijoTna)
            .put("noticias", JSONArray(s.news.take(14).map { JSONObject().put("titulo", it.title).put("region", it.region).put("tocaMiCartera", it.mine) }))
        return """
Sos Jarvis, un asistente financiero personal para un inversor argentino que opera en InvertirOnline (IOL).
Hablás en español rioplatense, claro y directo.
${if (question.isNotBlank()) "Primero respondé esta pregunta del usuario: $question\n" else ""}
Después hacé un análisis breve (máximo 300 palabras) con estos títulos, cada uno con 1 a 3 viñetas que empiecen con "• ":
CÓMO VIENE LA CARTERA / IMPACTO DE LAS NOTICIAS DE HOY / RIESGO Y DIVERSIFICACIÓN / PESOS VS INFLACIÓN / PARA MIRAR.
Usá solo los números de los datos; si falta algo, decilo. No des órdenes de compra o venta: presentá escenarios.
Terminá con una línea que diga que no es asesoramiento financiero.
No uses formato markdown con asteriscos.
DATOS: $data
        """.trimIndent()
    }

    /** Lo que corre en segundo plano cada ~15 minutos: resumen de la mañana y alertas. */
    suspend fun backgroundCheck() {
        val cal = Calendar.getInstance(AR_TZ)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekday = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val today = dayKey()

        // Resumen de la mañana (una vez por día hábil, desde las 9)
        if (weekday && hour >= 9 && st.lastBriefDay != today) {
            try {
                val fx = MarketApi.dolares()
                val news = NewsApi.fetch(st.snapshot.holdings).take(3)
                val mep = fx.firstOrNull { it.name == "MEP" }?.venta
                val blue = fx.firstOrNull { it.name == "Blue" }?.venta
                val body = buildString {
                    append("Dólar MEP ${money(mep, dec = 0)} · Blue ${money(blue, dec = 0)}")
                    news.forEach { append("\n• ").append(it.title) }
                }
                Notifier.show(ctx, 1, "Jarvis · Tu resumen del día", body)
                st.lastBriefDay = today
            } catch (_: Exception) { }
        }

        // Alertas y movimientos fuertes (días hábiles de 10 a 18)
        if (!weekday || hour < 10 || hour > 18) return
        val alerts = st.alerts
        val fired = st.fired.filterValues { it == today }.toMutableMap()
        val prices = mutableMapOf<String, Quote>()

        if (iol.configured) {
            val threshold = st.moveThreshold.replace(",", ".").toDoubleOrNull() ?: 5.0
            try {
                val hold = iol.portfolio()
                hold.forEach { prices[it.symbol.uppercase()] = Quote(it.symbol, it.name, it.last, it.dayPct, it.currency) }
                hold.filter { (it.dayPct ?: 0.0).let { p -> kotlin.math.abs(p) >= threshold } }.forEach { h ->
                    val key = "move:${h.symbol}"
                    if (fired[key] == null) {
                        Notifier.show(ctx, key.hashCode(), "${h.symbol} ${pct(h.dayPct)} hoy", "Está en ${money(h.last, h.currency)}. Movimiento fuerte en tu cartera.")
                        fired[key] = today
                    }
                }
            } catch (_: Exception) { }
        }
        for (a in alerts) {
            val key = "alert:${a.id}"
            if (fired[key] != null) continue
            val q = prices[a.symbol.uppercase()] ?: (if (iol.configured) try { iol.quote(a.symbol) } catch (_: Exception) { null } else null)
            if (a.hit(q)) {
                Notifier.show(ctx, key.hashCode(), "Alerta: ${a.symbol}", "${a.describe()} → ahora ${money(q?.last, q?.currency ?: "ARS")} (${pct(q?.chgPct)})")
                fired[key] = today
            }
        }
        st.fired = fired
    }
}
