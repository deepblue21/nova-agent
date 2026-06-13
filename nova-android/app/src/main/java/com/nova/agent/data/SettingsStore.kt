package com.nova.agent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

data class AppSettings(
    // Emülatör host'u 10.0.2.2 = makinenin localhost'u. Gerçek cihazda Tailscale/LAN IP'si kullan.
    val baseUrl: String = "http://10.0.2.2:8088/v1",
    val token: String = "",
    val modelId: String = "auto",
    val effort: String = "balanced",
    val reasoning: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token")
        val modelId = stringPreferencesKey("model_id")
        val effort = stringPreferencesKey("effort")
        val reasoning = booleanPreferencesKey("reasoning")
    }

    val flow = context.dataStore.data.map { p ->
        val def = AppSettings()
        AppSettings(
            baseUrl = p[Keys.baseUrl] ?: def.baseUrl,
            token = p[Keys.token] ?: def.token,
            modelId = p[Keys.modelId] ?: def.modelId,
            effort = p[Keys.effort] ?: def.effort,
            reasoning = p[Keys.reasoning] ?: def.reasoning,
        )
    }

    suspend fun load(): AppSettings = flow.first()

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.baseUrl] = s.baseUrl
            p[Keys.token] = s.token
            p[Keys.modelId] = s.modelId
            p[Keys.effort] = s.effort
            p[Keys.reasoning] = s.reasoning
        }
    }
}
