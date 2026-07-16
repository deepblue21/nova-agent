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
    // --- Faz 1: yerel öncelikli ---
    // Anahtar diskte yoksa varsayılanlar uygulanır; eski kurulumlar Gateway'de kalır.
    val executionPolicy: String = "gateway_only", // gateway_only | local_first (local_only=Faz2, hybrid=Faz3)
    val localModelId: String = "qwen3-0.6b-int4",
    val localThinking: Boolean = false,
    val themeId: String = "nova", // nova | aurora | amber
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token")
        val modelId = stringPreferencesKey("model_id")
        val effort = stringPreferencesKey("effort")
        val reasoning = booleanPreferencesKey("reasoning")
        val executionPolicy = stringPreferencesKey("execution_policy")
        val localModelId = stringPreferencesKey("local_model_id")
        val localThinking = booleanPreferencesKey("local_thinking")
        val themeId = stringPreferencesKey("theme_id")
    }

    val flow = context.dataStore.data.map { p ->
        val def = AppSettings()
        AppSettings(
            baseUrl = p[Keys.baseUr