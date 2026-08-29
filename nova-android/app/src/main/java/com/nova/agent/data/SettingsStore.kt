package com.nova.agent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.llm.local.SamplerPreset
import com.nova.agent.llm.local.SamplerSettings
import com.nova.agent.ui.theme.DEFAULT_ACCENT_ID
import com.nova.agent.ui.theme.normalizeThemeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

internal const val THEME_ALIGNMENT_VERSION_KEY = "theme_alignment_version"
internal const val THEME_ALIGNMENT_VERSION = 1

internal const val UI_MODE_MIGRATION_KEY = "ui_mode_migration"
internal const val UI_MODE_MIGRATION_VERSION = 1

data class AppSettings(
    // Varsayılan BOŞ (Play kararı B5). Mağazadan indiren kullanıcının PC'si
    // yoktur; "http://10.0.2.2:8088/v1" emülatör loopback'i Ayarlar'da onun
    // için anlamsız ve yanıltıcıydı (üstelik gateway artık 18088'de). Adres ya eşlemeden (Faz 10A: mDNS + kod /
    // horus:// bağlantısı) ya da Gelişmiş modda elle gelir.
    // Açılış sondası zaten belirteç yoksa atılmıyor
    // (GatewayConnectionUiState.shouldProbeOnStart), yani boş adres ilk ekranda
    // hata üretmez — nötr "PC bağlantısı kurulmadı" durumu gösterilir.
    // Emülatörde geliştirirken adresi Ayarlar > PC bağlantısı'ndan gir.
    val baseUrl: String = "",
    val token: String = "",
    val modelId: String = "auto",
    val effort: String = "balanced",
    val reasoning: Boolean = true,
    // --- Faz 1: yerel öncelikli ---
    // Anahtar diskte yoksa varsayılanlar uygulanır; eski kurulumlarda anahtar
    // diskte olduğu için onlar Gateway'de kalmaya devam eder (sessiz taşıma yok).
    //
    // Play kararı (B5): TEMİZ kurulumun varsayılanı LOCAL_FIRST. "gateway_only"
    // varsayılanıyla mağazadan indiren kullanıcı için hiçbir şey çalışmıyordu —
    // politika PC istiyor, PC yok. Bu, Play'in "asgari işlevsellik" reddi
    // demekti. Telefonda model de yoksa EngineRouter LocalNeedsSetup döndürür
    // ve FirstRunGuide kartı kullanıcıyı Modeller'e yönlendirir; sessiz devir
    // yine YOK.
    val executionPolicy: String = "local_first", // gateway_only | local_first | local_only | hybrid
    val localModelId: String = "qwen3-0.6b-int4",
    val localThinking: Boolean = false,
    val localTools: Boolean = true, // Faz 2: çevrimdışı araç seti (deneysel)
    // Geçerli kimlikler design/nova-tokens.json > accents ile üretilir.
    val themeId: String = DEFAULT_ACCENT_ID,
    // Faz 2 D3: kapılı (Gemma) model indirmeleri için HF erişim token'ı.
    // Cihazda kalır; yalnız huggingface.co'ya gönderilir.
    val hfToken: String = "",
    // Faz 3 D1: hibritte yerel hata sonrası otomatik PC devri (false = her seferinde sor).
    val hybridAutoFallback: Boolean = false,
    // Faz 8: yerel model için sistem talimatı (persona). Boşsa gönderilmez.
    val persona: String = "",
    // --- Faz 9: arayüz yoğunluğu + cihaz motoru ayarları ---
    // Yalnız görünürlük; hiçbir ayarın değerini değiştirmez (bkz. UiMode).
    val uiMode: String = UiMode.SIMPLE.id,
    // Hızlandırma tercihi. "auto" = önce GPU, olmazsa CPU.
    val backendPreference: String = BackendPreference.AUTO.id,
    // Örnekleme. Varsayılan "model_default" = motora samplerConfig GÖNDERİLMEZ,
    // yani mevcut kurulumların üretim davranışı birebir korunur.
    val samplerPreset: String = SamplerPreset.MODEL_DEFAULT.id,
    // İlk açılış model kartı kapatıldı mı (bkz. FirstRunGuide). Kalıcı:
    // kullanıcı "şimdilik atla" derse kart bir daha çıkmaz.
    val firstRunGuideDismissed: Boolean = false,
    val samplerTopK: Int = SamplerPreset.CUSTOM_SEED.topK,
    val samplerTopP: Double = SamplerPreset.CUSTOM_SEED.topP,
    val samplerTemperature: Double = SamplerPreset.CUSTOM_SEED.temperature,
) {
    val uiModeValue: UiMode get() = UiMode.fromId(uiMode)
    val backendPreferenceValue: BackendPreference get() = BackendPreference.fromId(backendPreference)
    val samplerPresetValue: SamplerPreset get() = SamplerPreset.fromId(samplerPreset)

    /** Elle ayarlanmış değerler; hazır ayar seçiliyken kullanılmaz ama saklanır. */
    val customSampler: SamplerSettings
        get() = SamplerSettings(samplerTopK, samplerTopP, samplerTemperature)

    /**
     * Motora gönderilecek örnekleme ayarı. null = hiç gönderme
     * (model kendi varsayılanıyla çalışır).
     */
    val effectiveSampler: SamplerSettings?
        get() = SamplerPreset.resolve(samplerPresetValue, customSampler)
}

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
        val localTools = booleanPreferencesKey("local_tools")
        val themeId = stringPreferencesKey("theme_id")
        val themeAlignmentVersion = intPreferencesKey(THEME_ALIGNMENT_VERSION_KEY)
        val hfToken = stringPreferencesKey("hf_token")
        val hybridAutoFallback = booleanPreferencesKey("hybrid_auto_fallback")
        val persona = stringPreferencesKey("persona")
        val uiMode = stringPreferencesKey("ui_mode")
        val uiModeMigration = intPreferencesKey(UI_MODE_MIGRATION_KEY)
        val backendPreference = stringPreferencesKey("backend_preference")
        val samplerPreset = stringPreferencesKey("sampler_preset")
        val firstRunGuideDismissed = booleanPreferencesKey("first_run_guide_dismissed")
        val samplerTopK = intPreferencesKey("sampler_top_k")
        val samplerTopP = doublePreferencesKey("sampler_top_p")
        val samplerTemperature = doublePreferencesKey("sampler_temperature")
    }

    val flow = context.dataStore.data.map { p ->
        val def = AppSettings()
        AppSettings(
            baseUrl = p[Keys.baseUrl] ?: def.baseUrl,
            token = p[Keys.token] ?: def.token,
            modelId = p[Keys.modelId] ?: def.modelId,
            effort = p[Keys.effort] ?: def.effort,
            reasoning = p[Keys.reasoning] ?: def.reasoning,
            executionPolicy = p[Keys.executionPolicy] ?: def.executionPolicy,
            localModelId = p[Keys.localModelId] ?: def.localModelId,
            localThinking = p[Keys.localThinking] ?: def.localThinking,
            localTools = p[Keys.localTools] ?: def.localTools,
            themeId = normalizeThemeId(p[Keys.themeId] ?: def.themeId),
            hfToken = p[Keys.hfToken] ?: def.hfToken,
            hybridAutoFallback = p[Keys.hybridAutoFallback] ?: def.hybridAutoFallback,
            persona = p[Keys.persona] ?: def.persona,
            uiMode = p[Keys.uiMode] ?: def.uiMode,
            backendPreference = p[Keys.backendPreference] ?: def.backendPreference,
            samplerPreset = p[Keys.samplerPreset] ?: def.samplerPreset,
            firstRunGuideDismissed =
                p[Keys.firstRunGuideDismissed] ?: def.firstRunGuideDismissed,
            samplerTopK = p[Keys.samplerTopK] ?: def.samplerTopK,
            samplerTopP = p[Keys.samplerTopP] ?: def.samplerTopP,
            samplerTemperature = p[Keys.samplerTemperature] ?: def.samplerTemperature,
        )
    }

    suspend fun load(): AppSettings {
        context.dataStore.edit { preferences ->
            // "Temiz kurulum mu" kararı, HERHANGİ bir göç yazmadan ÖNCE alınmalı:
            // tema göçü bir anahtar yazdıktan sonra bakılırsa her kurulum
            // "mevcut" görünürdü.
            val freshInstall = preferences.asMap().isEmpty()

            val appliedVersion = preferences[Keys.themeAlignmentVersion] ?: 0
            if (appliedVersion < THEME_ALIGNMENT_VERSION) {
                preferences[Keys.themeId] = DEFAULT_ACCENT_ID
                preferences[Keys.themeAlignmentVersion] = THEME_ALIGNMENT_VERSION
            }

            val uiModeApplied = preferences[Keys.uiModeMigration] ?: 0
            if (uiModeApplied < UI_MODE_MIGRATION_VERSION) {
                preferences[Keys.uiMode] = initialUiModeFor(freshInstall).id
                preferences[Keys.uiModeMigration] = UI_MODE_MIGRATION_VERSION
            }
        }
        return flow.first()
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.baseUrl] = s.baseUrl
            p[Keys.token] = s.token
            p[Keys.modelId] = s.modelId
            p[Keys.effort] = s.effort
            p[Keys.reasoning] = s.reasoning
            p[Keys.executionPolicy] = s.executionPolicy
            p[Keys.localModelId] = s.localModelId
            p[Keys.localThinking] = s.localThinking
            p[Keys.localTools] = s.localTools
            p[Keys.themeId] = normalizeThemeId(s.themeId)
            p[Keys.hfToken] = s.hfToken
            p[Keys.hybridAutoFallback] = s.hybridAutoFallback
            p[Keys.persona] = s.persona
            p[Keys.uiMode] = UiMode.fromId(s.uiMode).id
            p[Keys.backendPreference] = BackendPreference.fromId(s.backendPreference).id
            p[Keys.samplerPreset] = SamplerPreset.fromId(s.samplerPreset).id
            p[Keys.firstRunGuideDismissed] = s.firstRunGuideDismissed
            // Kaydederken de kırp: bozuk bir değer diske hiç yazılmasın.
            val sampler = s.customSampler.clamped()
            p[Keys.samplerTopK] = sampler.topK
            p[Keys.samplerTopP] = sampler.topP
            p[Keys.samplerTemperature] = sampler.temperature
        }
    }
}
