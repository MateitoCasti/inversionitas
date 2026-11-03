package com.jarvis.finanzas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    private val st get() = repo.st

    var snap by mutableStateOf(repo.load())
        private set
    var loading by mutableStateOf(false)
        private set
    var alerts by mutableStateOf(st.alerts)
        private set
    var watch by mutableStateOf(st.watch)
        private set

    var aiText by mutableStateOf("")
        private set
    var aiBusy by mutableStateOf(false)
        private set
    var settingsMsg by mutableStateOf("")
        private set

    // Ajustes (se muestran en pantalla; la contraseña no se vuelve a mostrar)
    var iolUser by mutableStateOf(st.iolUser)
    var iolPass by mutableStateOf("")
    var geminiKey by mutableStateOf("")
    var geminiModel by mutableStateOf(st.geminiModel)
    var moveThreshold by mutableStateOf(st.moveThreshold)

    val hasIol get() = st.iolUser.isNotBlank() && st.iolPass.isNotBlank()
    val hasGemini get() = st.geminiKey.isNotBlank()

    init {
        if (System.currentTimeMillis() - snap.time > 5 * 60_000) refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try { snap = repo.refresh() } finally { loading = false }
        }
    }

    fun saveSettings() {
        st.iolUser = iolUser.trim()
        if (iolPass.isNotBlank()) st.iolPass = iolPass
        if (geminiKey.isNotBlank()) st.geminiKey = geminiKey.trim()
        st.geminiModel = geminiModel.trim().ifBlank { "gemini-2.5-flash" }
        st.moveThreshold = moveThreshold.trim().ifBlank { "5" }
        st.clearToken()
        iolPass = ""; geminiKey = ""
        settingsMsg = "Guardado."
        refresh()
    }

    fun testIol() {
        settingsMsg = "Probando conexión con IOL…"
        viewModelScope.launch {
            settingsMsg = try { repo.iol.test() } catch (e: Exception) { e.message ?: "No se pudo conectar." }
        }
    }

    fun forgetIol() {
        st.iolUser = ""; st.iolPass = ""; st.clearToken(); iolUser = ""
        settingsMsg = "Se borraron tus datos de IOL del teléfono."
    }

    fun forgetGemini() {
        st.geminiKey = ""
        settingsMsg = "Se borró la clave de Gemini."
    }

    fun addAlert(symbol: String, cond: String, value: Double) {
        val a = Alert(UUID.randomUUID().toString().take(8), symbol.trim().uppercase(), cond, value)
        st.alerts = st.alerts + a
        alerts = st.alerts
    }

    fun removeAlert(id: String) {
        st.alerts = st.alerts.filterNot { it.id == id }
        alerts = st.alerts
    }

    fun addWatch(symbol: String) {
        val s = symbol.trim().uppercase()
        if (s.isEmpty() || s in st.watch) return
        st.watch = st.watch + s
        watch = st.watch
        refresh()
    }

    fun removeWatch(symbol: String) {
        st.watch = st.watch - symbol
        watch = st.watch
        snap = snap.copy(watch = snap.watch.filterNot { it.symbol.equals(symbol, true) })
    }

    fun askAi(question: String) {
        if (!hasGemini || aiBusy) return
        aiBusy = true
        aiText = ""
        viewModelScope.launch {
            aiText = try {
                Gemini.ask(st.geminiKey, st.geminiModel, repo.aiPrompt(snap, question))
            } catch (e: Exception) {
                e.message ?: "No se pudo completar el análisis."
            } finally {
                aiBusy = false
            }
        }
    }
}
