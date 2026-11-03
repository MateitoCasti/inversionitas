package com.jarvis.finanzas

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale

/** Noticias de Google Noticias (Argentina), filtradas por lo que mueve el mercado. */
object NewsApi {
    private fun gn(q: String) =
        "https://news.google.com/rss/search?q=${enc(q)}&hl=es-419&gl=AR&ceid=AR:es-419"

    private val HIGH = listOf(
        "bcra", "banco central", "fed", "reserva federal", "tasa", "inflación", "riesgo país", "default",
        "devaluación", "cepo", "licitación", "fmi", "bandas", "reservas", "aranceles", "recesión",
    )
    private val UP = listOf("sube", "suben", "subió", "trepa", "rebota", "récord", "gana", "ganan", "alza", "dispara", "mejora", "baja el riesgo")
    private val DOWN = listOf("cae", "caen", "cayó", "se hunde", "desploma", "baja", "pierde", "pierden", "derrumbe", "alerta", "tensión")

    private val STOP = setOf("grupo", "banco", "compania", "compañía", "inc", "corp", "sociedad", "anonima", "argentina", "holding", "financiero", "class", "clase")

    /** Palabras para reconocer noticias de tu cartera: ticker y primera palabra del nombre. */
    fun keywords(h: List<Holding>): List<String> = h
        .filter { it.type == "Acción" || it.type == "CEDEAR" || it.type == "ON" }
        .flatMap { x ->
            val word = x.name.split(" ", ".", ",").map { it.trim().lowercase() }
                .firstOrNull { it.length >= 4 && it !in STOP }
            listOfNotNull(x.symbol.lowercase().takeIf { it.length >= 3 }, word)
        }.distinct()

    suspend fun fetch(holdings: List<Holding>): List<News> {
        val kw = keywords(holdings)
        val feeds = mutableListOf(
            "Argentina" to "(Merval OR BCRA OR \"riesgo país\" OR \"dólar MEP\" OR bonos OR inflación OR acciones argentinas) when:1d",
            "EE.UU./Global" to "(\"Wall Street\" OR \"Reserva Federal\" OR \"S&P 500\" OR Nasdaq OR petróleo OR bitcoin) when:1d",
        )
        val names = holdings.sortedByDescending { it.value }
            .filter { it.type == "Acción" || it.type == "CEDEAR" }
            .mapNotNull { h -> h.name.split(" ").firstOrNull { it.length >= 4 && it.lowercase() !in STOP } }
            .distinct().take(6)
        if (names.isNotEmpty()) feeds += "Tu cartera" to "(${names.joinToString(" OR ")}) acciones when:2d"

        val all = mutableListOf<News>()
        for ((region, q) in feeds) {
            try { all += parse(Http.get(gn(q)), region) } catch (_: Exception) { }
        }
        return all
            .distinctBy { it.title.lowercase().take(60) }
            .map { n -> tag(n, kw) }
            .sortedWith(compareByDescending<News> { it.mine }.thenByDescending { it.date })
            .take(30)
    }

    private fun tag(n: News, kw: List<String>): News {
        val t = n.title.lowercase()
        val mine = n.region == "Tu cartera" || kw.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(t) }
        val impact = when {
            HIGH.any { it in t } -> "alto"
            mine || UP.any { it in t } || DOWN.any { it in t } -> "medio"
            else -> "bajo"
        }
        return n.copy(mine = mine, impact = impact)
    }

    fun direction(title: String): String {
        val t = title.lowercase()
        val u = UP.count { it in t }
        val d = DOWN.count { it in t }
        return when { u > d -> "positivo"; d > u -> "negativo"; else -> "neutral" }
    }

    private fun parse(xml: String, region: String): List<News> {
        val out = mutableListOf<News>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        var inItem = false
        var tag = ""
        var title = ""; var link = ""; var date = ""; var source = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") { inItem = true; title = ""; link = ""; date = ""; source = "" }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val tx = p.text ?: ""
                    when (tag) {
                        "title" -> title += tx
                        "link" -> link += tx
                        "pubDate" -> date += tx
                        "source" -> source += tx
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" && inItem) {
                        inItem = false
                        val src = source.trim()
                        var t = title.trim()
                        if (src.isNotEmpty() && t.endsWith(" - $src")) t = t.removeSuffix(" - $src")
                        val ms = try { fmt.parse(date.trim())?.time ?: 0L } catch (e: Exception) { 0L }
                        if (t.isNotEmpty()) out += News(t, link.trim(), src, ms, region)
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
        return out
    }
}
