package com.jarvis.finanzas

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Todo queda guardado solo en el teléfono, cifrado. */
class Store(ctx: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "jarvis_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("jarvis_plain", Context.MODE_PRIVATE)
    }

    private fun str(k: String, d: String = "") = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getString(k, d) ?: d
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            prefs.edit().putString(k, value).apply()
        }
    }

    private fun lng(k: String) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getLong(k, 0L)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            prefs.edit().putLong(k, value).apply()
        }
    }

    var iolUser by str("iol_user")
    var iolPass by str("iol_pass")
    var accessToken by str("iol_access")
    var refreshToken by str("iol_refresh")
    var tokenExpires by lng("iol_expires")

    var geminiKey by str("gemini_key")
    var geminiModel by str("gemini_model", "gemini-2.5-flash")
    var moveThreshold by str("move_threshold", "5")

    var snapshotJson by str("snapshot")
    var alertsJson by str("alerts", "[]")
    var watchJson by str("watch", "[]")
    var firedJson by str("fired", "{}")
    var lastBriefDay by str("last_brief")

    var snapshot: Snapshot
        get() = try { J.decodeFromString<Snapshot>(snapshotJson) } catch (e: Exception) { Snapshot() }
        set(v) { snapshotJson = J.encodeToString(v) }

    var alerts: List<Alert>
        get() = try { J.decodeFromString<List<Alert>>(alertsJson) } catch (e: Exception) { emptyList() }
        set(v) { alertsJson = J.encodeToString(v) }

    var watch: List<String>
        get() = try { J.decodeFromString<List<String>>(watchJson) } catch (e: Exception) { emptyList() }
        set(v) { watchJson = J.encodeToString(v) }

    /** clave de aviso -> día en que se avisó (para no repetir el mismo aviso en el día) */
    var fired: Map<String, String>
        get() = try { J.decodeFromString<Map<String, String>>(firedJson) } catch (e: Exception) { emptyMap() }
        set(v) { firedJson = J.encodeToString(v) }

    fun clearToken() {
        accessToken = ""; refreshToken = ""; tokenExpires = 0
    }
}
