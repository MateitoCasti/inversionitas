@file:OptIn(ExperimentalMaterial3Api::class)

package com.jarvis.finanzas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/* ---------- Tema: azul tinta con celeste, verde y rojo para subas y bajas ---------- */
private val Celeste = Color(0xFF2C78B0)
private val CelesteDark = Color(0xFF6AAEDF)
val Up = Color(0xFF17865A)
val Down = Color(0xFFC23E35)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = CelesteDark, onPrimary = Color(0xFF0F161D),
        background = Color(0xFF0F161D), surface = Color(0xFF17212B), surfaceVariant = Color(0xFF1E2A36),
        onBackground = Color(0xFFE6EDF3), onSurface = Color(0xFFE6EDF3), onSurfaceVariant = Color(0xFF93A3B2),
    ) else lightColorScheme(
        primary = Celeste, onPrimary = Color.White,
        background = Color(0xFFEEF2F5), surface = Color.White, surfaceVariant = Color(0xFFE3E9EE),
        onBackground = Color(0xFF14202B), onSurface = Color(0xFF14202B), onSurfaceVariant = Color(0xFF5B6B79),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Hoy", Icons.Filled.Home),
    Tab("Cartera", Icons.Filled.AccountBox),
    Tab("Mercado", Icons.Filled.List),
    Tab("Análisis", Icons.Filled.Search),
    Tab("Ajustes", Icons.Filled.Settings),
)

@Composable
fun JarvisApp(vm: JarvisViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(if (vm.hasIol) 0 else 4) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Jarvis", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            if (vm.loading) "Actualizando…" else "Actualizado ${hhmm(vm.snap.time)}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        val mod = Modifier.fillMaxSize().padding(pad)
        when (tab) {
            0 -> HoyScreen(vm, mod) { tab = it }
            1 -> CarteraScreen(vm, mod)
            2 -> MercadoScreen(vm, mod)
            3 -> AnalisisScreen(vm, mod)
            else -> AjustesScreen(vm, mod)
        }
    }
}

/* ---------- Piezas comunes ---------- */

@Composable
private fun Screen(modifier: Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                if (trailing != null) Text(trailing, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

private fun chgColor(v: Double?): Color? = when {
    v == null -> null
    v > 0.001 -> Up
    v < -0.001 -> Down
    else -> null
}

@Composable
private fun Chg(v: Double?, invert: Boolean = false, size: Int = 13) {
    val c = chgColor(if (invert && v != null) -v else v) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Text(pct(v), color = c, fontSize = size.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun QuoteRow(title: String, subtitle: String, value: String, chg: Double?, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontFamily = FontFamily.Monospace)
            Chg(chg)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun Muted(text: String) = Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun NewsItem(n: News) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().clickable { try { uri.openUri(n.link) } catch (_: Exception) { } }.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val ic = when (n.impact) { "alto" -> Down; "medio" -> Color(0xFFB7791F); else -> Celeste }
            Text("Impacto ${n.impact}", color = ic, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            when (NewsApi.direction(n.title)) {
                "positivo" -> Text("▲ positivo", color = Up, fontSize = 11.sp)
                "negativo" -> Text("▼ negativo", color = Down, fontSize = 11.sp)
            }
            if (n.mine) Text("Tu cartera", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(n.region, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(n.title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Muted("${n.source} · ${hhmm(n.date)}")
    }
}

/* ---------- Hoy ---------- */

@Composable
private fun HoyScreen(vm: JarvisViewModel, modifier: Modifier, go: (Int) -> Unit) {
    val s = vm.snap
    val t = s.totals()
    val hits = s.alertHits(vm.alerts)
    Screen(modifier) {
        if (hits.isNotEmpty()) item {
            Section("Alertas activas") {
                hits.forEach { (a, q) -> Text("${a.describe()} → ahora ${money(q.last, q.currency)} (${pct(q.chgPct)})", color = Down) }
            }
        }
        item {
            Section("Tu cartera") {
                if (!vm.hasIol) {
                    Muted("Conectá tu cuenta de IOL para ver tu cartera real.")
                    Button(onClick = { go(4) }) { Text("Conectar IOL") }
                } else {
                    Text(money(t.totalArs, dec = 0), fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    t.totalUsd?.let { Muted("≈ ${money(it, "USD", 0)} al dólar MEP") }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row { Muted("Hoy "); Chg(t.dayPct) }
                        Row { Muted("Total "); Chg(t.pnlPct) }
                    }
                    s.holdings.filter { it.dayPct != null }.sortedByDescending { kotlin.math.abs(it.dayPct!!) }.take(3).forEach {
                        QuoteRow(it.symbol, it.type, money(it.last, it.currency), it.dayPct)
                    }
                }
            }
        }
        item {
            Section("Mercado ahora") {
                val mep = s.fx.firstOrNull { it.name == "MEP" }
                val blue = s.fx.firstOrNull { it.name == "Blue" }
                val merv = s.indices.firstOrNull { it.name == "Merval" }
                val spx = s.indices.firstOrNull { it.name == "S&P 500" }
                Row(Modifier.fillMaxWidth()) {
                    Stat("Dólar MEP", money(mep?.venta, dec = 0), null, Modifier.weight(1f))
                    Stat("Blue", money(blue?.venta, dec = 0), null, Modifier.weight(1f))
                    Stat("Riesgo país", num(s.riesgoPais, 0), null, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat("Merval", num(merv?.last, 0), merv?.chgPct, Modifier.weight(1f))
                    Stat("S&P 500", num(spx?.last, 0), spx?.chgPct, Modifier.weight(1f))
                    val btc = s.indices.firstOrNull { it.name == "Bitcoin" }
                    Stat("Bitcoin", money(btc?.last, "USD", 0), btc?.chgPct, Modifier.weight(1f))
                }
            }
        }
        item {
            Section("Noticias del día", "${s.news.size}") {
                if (s.news.isEmpty()) Muted("Tocá actualizar para traer las noticias.")
                s.news.take(12).forEachIndexed { i, n ->
                    if (i > 0) HorizontalDivider()
                    NewsItem(n)
                }
            }
        }
        if (s.errors.isNotEmpty()) item {
            Section("Avisos de la última actualización") { s.errors.forEach { Muted("• $it") } }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, chg: Double?, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Muted(label)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        if (chg != null) Chg(chg, size = 12)
    }
}

/* ---------- Cartera ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteraScreen(vm: JarvisViewModel, modifier: Modifier) {
    val s = vm.snap
    val t = s.totals()
    Screen(modifier) {
        item {
            Section("Valor total") {
                Text(money(t.totalArs, dec = 0), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                t.totalUsd?.let { Muted("≈ ${money(it, "USD", 0)} al MEP") }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Muted("Invertido ${money(t.investedArs, dec = 0)}")
                    Muted("Disponible ${money(t.cashArs, dec = 0)}")
                }
                Row { Muted("Ganancia acumulada ${money(t.pnlArs, dec = 0)} "); Chg(t.pnlPct) }
            }
        }
        if (s.holdings.isNotEmpty()) item {
            Section("Distribución") {
                val total = t.totalArs.takeIf { it > 0 } ?: 1.0
                val by = s.holdings.groupBy { it.type }.mapValues { (_, l) -> l.sumOf { s.toArs(it.value, it.currency) } }.toMutableMap()
                if (t.cashArs > 0) by["Disponible"] = t.cashArs
                by.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                    val f = (v / total).toFloat().coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.width(96.dp), fontSize = 13.sp)
                        LinearProgressIndicator(progress = { f }, modifier = Modifier.weight(1f).height(8.dp))
                        Text(" ${num(f * 100.0, 0)}%", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        item {
            Section("Posiciones en IOL", "${s.holdings.size}") {
                if (s.holdings.isEmpty()) Muted(if (vm.hasIol) "No hay posiciones o todavía no se actualizó." else "Conectá IOL en Ajustes.")
                s.holdings.sortedByDescending { s.toArs(it.value, it.currency) }.forEachIndexed { i, h ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${h.symbol}  ·  ${h.type}", fontWeight = FontWeight.SemiBold)
                            Muted(h.name)
                            Muted("${num(h.qty, if (h.qty % 1.0 == 0.0) 0 else 2)} × ${money(h.last, h.currency)} · compra ${money(h.ppc, h.currency)}")
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(h.value, h.currency, 0), fontFamily = FontFamily.Monospace)
                            Row { Muted("hoy "); Chg(h.dayPct, size = 12) }
                            Row { Muted("total "); Chg(h.pnlPct, size = 12) }
                        }
                    }
                }
            }
        }
        item { AlertsSection(vm) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertsSection(vm: JarvisViewModel) {
    var sym by remember { mutableStateOf("") }
    var cond by remember { mutableStateOf("above") }
    var value by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Section("Alertas de precio") {
        Muted("Te aviso con una notificación (días hábiles, de 10 a 18, cada ~15 minutos).")
        vm.alerts.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.describe(), Modifier.weight(1f))
                IconButton(onClick = { vm.removeAlert(a.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar alerta", tint = Down) }
            }
        }
        OutlinedTextField(sym, { sym = it.uppercase() }, label = { Text("Ticker (como en IOL)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cond == "above", { cond = "above" }, label = { Text("Sube a") })
            FilterChip(cond == "below", { cond = "below" }, label = { Text("Baja a") })
            FilterChip(cond == "move", { cond = "move" }, label = { Text("Se mueve %") })
        }
        OutlinedTextField(
            value, { value = it }, label = { Text(if (cond == "move") "Porcentaje (ej. 5)" else "Precio") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val v = value.replace(",", ".").toDoubleOrNull()
                if (sym.isBlank() || v == null) msg = "Completá ticker y valor."
                else { vm.addAlert(sym, cond, v); sym = ""; value = ""; msg = "✓ Alerta creada." }
            }) { Text("Crear alerta") }
            Spacer(Modifier.width(12.dp))
            Muted(msg)
        }
    }
}

/* ---------- Mercado ---------- */

@Composable
private fun MercadoScreen(vm: JarvisViewModel, modifier: Modifier) {
    val s = vm.snap
    var newSym by remember { mutableStateOf("") }
    Screen(modifier) {
        item {
            Section("Dólar") {
                if (s.fx.isEmpty()) Muted("Sin datos todavía.")
                s.fx.forEach { f -> QuoteRow(f.name, "compra ${money(f.compra, dec = 2)}", money(f.venta, dec = 2), null) }
            }
        }
        item {
            Section("Índices, commodities y cripto") {
                Row { Muted("Riesgo país: "); Text(num(s.riesgoPais, 0), fontFamily = FontFamily.Monospace) }
                s.indices.forEach { q ->
                    val v = when (q.name) {
                        "Tasa 10 años EE.UU." -> num(q.last, 2) + "%"
                        "Merval" -> num(q.last, 0)
                        "VIX" -> num(q.last, 2)
                        else -> if (q.currency == "USD") money(q.last, "USD", 2) else num(q.last, 2)
                    }
                    QuoteRow(q.name, "", v, q.chgPct)
                }
            }
        }
        item {
            Section("Tasas en pesos") {
                QuoteRow("Mejor plazo fijo", s.plazoFijoBanco, s.plazoFijoTna?.let { num(it, 1) + "% TNA" } ?: "—", null)
                QuoteRow("Inflación último mes", "INDEC ${s.inflacionMes}", s.inflacion?.let { num(it, 1) + "%" } ?: "—", null)
            }
        }
        item {
            Section("Siguiendo", "${vm.watch.size}") {
                vm.watch.forEach { sym ->
                    val q = s.watch.firstOrNull { it.symbol.equals(sym, true) }
                    QuoteRow(sym, q?.name ?: "Esperando precio", money(q?.last, q?.currency ?: "ARS"), q?.chgPct) {
                        IconButton(onClick = { vm.removeWatch(sym) }) { Icon(Icons.Filled.Delete, contentDescription = "Dejar de seguir", tint = Down) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newSym, { newSym = it.uppercase() }, label = { Text("Ticker de IOL (ej. LND, GD35)") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { vm.addWatch(newSym); newSym = "" }) { Text("Seguir") }
                }
            }
        }
        listOf("Panel líder", "CEDEARs", "ETFs", "Bonos").forEach { name ->
            val list = s.panels[name].orEmpty()
            item {
                Section(name, if (list.isEmpty()) null else "IOL") {
                    if (list.isEmpty()) Muted(if (vm.hasIol) "Sin datos en la última actualización." else "Conectá IOL en Ajustes para ver este panel.")
                    list.forEach { q -> QuoteRow(q.symbol, q.name, money(q.last, q.currency), q.chgPct) }
                }
            }
        }
    }
}

/* ---------- Análisis ---------- */

@Composable
private fun AnalisisScreen(vm: JarvisViewModel, modifier: Modifier) {
    val s = vm.snap
    var question by rememberSaveable { mutableStateOf("") }
    Screen(modifier) {
        items(s.localAnalysis(vm.alerts)) { (title, body) ->
            Section(title) { Text(body, lineHeight = 21.sp) }
        }
        item {
            Section("Preguntale a Jarvis (Gemini)") {
                if (!vm.hasGemini) {
                    Muted("Para el análisis con IA, cargá tu clave gratuita de Gemini en Ajustes. Sin la clave, el análisis de arriba funciona igual.")
                } else {
                    OutlinedTextField(question, { question = it }, label = { Text("Tu pregunta (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.askAi(question) }, enabled = !vm.aiBusy) {
                        Text(if (vm.aiBusy) "Analizando…" else "Analizar mi cartera")
                    }
                    if (vm.aiText.isNotBlank()) Text(vm.aiText, lineHeight = 21.sp)
                }
            }
        }
    }
}

/* ---------- Ajustes ---------- */

@Composable
private fun AjustesScreen(vm: JarvisViewModel, modifier: Modifier) {
    val uri = LocalUriHandler.current
    Screen(modifier) {
        item {
            Section("Cuenta de IOL") {
                Muted("Tu usuario y contraseña se guardan cifrados solo en este teléfono y se usan únicamente para hablar con la API oficial de IOL.")
                OutlinedTextField(vm.iolUser, { vm.iolUser = it }, label = { Text("Usuario de IOL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    vm.iolPass, { vm.iolPass = it },
                    label = { Text(if (vm.hasIol) "Contraseña (dejá vacío para no cambiarla)" else "Contraseña de IOL") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveSettings() }) { Text("Guardar") }
                    OutlinedButton(onClick = { vm.testIol() }, enabled = vm.hasIol) { Text("Probar conexión") }
                }
                if (vm.hasIol) TextButton(onClick = { vm.forgetIol() }) { Text("Borrar mis datos de IOL", color = Down) }
            }
        }
        item {
            Section("Análisis con IA (opcional)") {
                Muted("Sacá una clave gratis en Google AI Studio con tu cuenta de Google y pegala acá.")
                TextButton(onClick = { try { uri.openUri("https://aistudio.google.com/apikey") } catch (_: Exception) { } }) { Text("Abrir Google AI Studio") }
                OutlinedTextField(
                    vm.geminiKey, { vm.geminiKey = it },
                    label = { Text(if (vm.hasGemini) "Clave guardada (pegá otra para cambiarla)" else "Clave de Gemini") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(vm.geminiModel, { vm.geminiModel = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveSettings() }) { Text("Guardar") }
                    if (vm.hasGemini) TextButton(onClick = { vm.forgetGemini() }) { Text("Borrar clave", color = Down) }
                }
            }
        }
        item {
            Section("Avisos") {
                OutlinedTextField(
                    vm.moveThreshold, { vm.moveThreshold = it },
                    label = { Text("Avisarme si un activo de mi cartera se mueve más de (%)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Muted("Además, cada día hábil desde las 9 te llega un resumen con el dólar y las noticias principales.")
                Button(onClick = { vm.saveSettings() }) { Text("Guardar") }
            }
        }
        if (vm.settingsMsg.isNotBlank()) item { Section("Estado") { Text(vm.settingsMsg) } }
        if (vm.snap.errors.isNotEmpty()) item {
            Section("Diagnóstico de la última actualización") { vm.snap.errors.forEach { Muted("• $it") } }
        }
        item { Muted("Jarvis informa y analiza; no es asesoramiento financiero. Confirmá precios en IOL antes de operar.") }
    }
}
