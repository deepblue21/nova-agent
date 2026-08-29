package com.nova.agent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nova.agent.BuildConfig
import com.nova.agent.data.AppSettings
import com.nova.agent.data.EFFORTS
import com.nova.agent.data.FALLBACK_MODELS
import com.nova.agent.data.ModelOption
import com.nova.agent.data.SettingsSection
import com.nova.agent.data.UiMode
import com.nova.agent.feature.pairing.PairingForm
import com.nova.agent.feature.pairing.PairingPhase
import com.nova.agent.feature.pairing.PairingUiState
import com.nova.agent.llm.local.ActiveBackend
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.llm.local.SamplerPreset
import com.nova.agent.llm.local.SamplerSettings
import com.nova.agent.net.DiscoveredGateway
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.components.NovaSecretField
import com.nova.agent.ui.theme.Amber
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.NOVA_ACCENTS
import com.nova.agent.ui.theme.NovaAccent

@Composable
fun SettingsPanel(
    settings: AppSettings,
    connection: GatewayConnectionUiState,
    onTestConnection: (String, String) -> Unit,
    onSaveConnection: (String, String) -> Unit,
    onModelChange: (String) -> Unit,
    onEffortChange: (String) -> Unit,
    /** Gateway'den gelen canlı model listesi; boşsa yedek liste gösterilir. */
    models: List<ModelOption> = FALLBACK_MODELS,
    modelsLive: Boolean = false,
    modelsNote: String = "",
    onRefreshModels: () -> Unit = {},
    onReasoningChange: (Boolean) -> Unit,
    onThemeChange: (String) -> Unit = {},
    onHfTokenChange: (String) -> Unit = {},
    onPersonaChange: (String) -> Unit = {},
    onWipeData: (Boolean) -> Unit = {},
    /** Motorun gerçekten yüklendiği hızlandırma; tahmin değil ölçülen değer. */
    activeBackend: ActiveBackend = ActiveBackend.NONE,
    /** mDNS keşfi + kod takası durumu (Faz A). */
    pairing: PairingUiState = PairingUiState(),
    onStartDiscovery: () -> Unit = {},
    onSelectGateway: (DiscoveredGateway) -> Unit = {},
    onPairCodeChange: (String) -> Unit = {},
    onSubmitPairing: () -> Unit = {},
    onDismissPairingMessage: () -> Unit = {},
    onUiModeChange: (UiMode) -> Unit = {},
    onBackendChange: (BackendPreference) -> Unit = {},
    onSamplerPresetChange: (SamplerPreset) -> Unit = {},
    onCustomSamplerChange: (SamplerSettings) -> Unit = {},
    /** Play B6 — cihazda kayıtlı yapay zekâ içerik bildirimi sayısı. */
    reportCount: Int = 0,
    onShareReports: () -> Unit = {},
    onClearReports: () -> Unit = {},
    onClose: () -> Unit,
) {
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.token) { mutableStateOf(settings.token) }
    var hfToken by remember(settings.hfToken) { mutableStateOf(settings.hfToken) }
    var persona by remember(settings.persona) { mutableStateOf(settings.persona) }
    var showWipeDialog by remember { mutableStateOf(false) }
    val mode = settings.uiModeValue

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Ayarları kapat")
                }
            }

            UiModePicker(mode, onUiModeChange)

            if (mode.shows(SettingsSection.CONNECTION)) {
                SectionCard("PC bağlantısı") {
                    ConnectionHelp(mode)
                    // Eşleme her modda görünür: IP/anahtar yazmadan bağlanmanın
                    // yolu bu. Elle giriş yalnız Gelişmiş'te, yedek olarak kalır.
                    PairingSection(
                        state = pairing,
                        onStartDiscovery = onStartDiscovery,
                        onSelectGateway = onSelectGateway,
                        onCodeChange = onPairCodeChange,
                        onSubmit = onSubmitPairing,
                        onDismissMessage = onDismissPairingMessage,
                    )
                    if (mode.shows(SettingsSection.CONNECTION_MANUAL)) {
                        Text(
                            "Elle bağlantı (yedek)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            modifier = Modifier.fillMaxWidth().testTag("gateway_url"),
                            label = { Text("Gateway adresi") },
                            placeholder = { Text("http://192.168.1.20:18088/v1") },
                            supportingText = {
                                Text("start-horus -Lan çıktısındaki adresi birebir gir")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        NovaSecretField(
                            value = token,
                            onValueChange = { token = it },
                            label = "Erişim belirteci",
                            contentLabel = "Gateway erişim belirteci",
                            supportingText = "Web arayüzündeki Gateway “Anahtar” değeriyle aynı",
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "gateway_token",
                        )
                    } else if (settings.baseUrl.isNotBlank()) {
                        // Basit modda adres/belirteç gizli ama KAYITLI DEĞER AYNEN
                        // kullanılmaya devam eder; sadece elle düzenleme kapalıdır.
                        Text(
                            "Kayıtlı PC bağlantısı kullanılıyor. Adresi ve belirteci elle " +
                                "düzenlemek için Gelişmiş moda geçin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            modifier = Modifier.testTag("connection_simple_note"),
                        )
                    }
                    ConnectionStatus(connection)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onTestConnection(baseUrl, token) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text("Bağlantıyı test et")
                        }
                        if (mode.shows(SettingsSection.CONNECTION_MANUAL)) {
                            Button(
                                onClick = { onSaveConnection(baseUrl, token) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text("Kaydet")
                            }
                        }
                    }
                }
            }

            if (mode.shows(SettingsSection.GATEWAY_MODEL)) {
                SectionCard("Model ve çalışma biçimi") {
                    ModelDropdown(
                        settings.modelId,
                        onModelChange,
                        models,
                        modelsLive,
                        modelsNote,
                        onRefreshModels,
                    )
                    if (mode.shows(SettingsSection.GATEWAY_TUNING)) {
                        EffortControls(settings.effort, onEffortChange)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Akıl yürütme")
                                Text(
                                    "Yanıtlarda ayrıntılı düşünme kullan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                )
                            }
                            Switch(
                                checked = settings.reasoning,
                                onCheckedChange = onReasoningChange,
                                modifier = Modifier.semantics {
                                    contentDescription = "Akıl yürütme"
                                },
                            )
                        }
                    }
                }
            }

            if (mode.shows(SettingsSection.LOCAL_ENGINE)) {
                SectionCard("Cihaz motoru (telefonda çalışan model)") {
                    BackendPicker(settings.backendPreferenceValue, activeBackend, onBackendChange)
                    SamplerControls(
                        preset = settings.samplerPresetValue,
                        custom = settings.customSampler,
                        onPresetChange = onSamplerPresetChange,
                        onCustomChange = onCustomSamplerChange,
                    )
                }
            }

            if (mode.shows(SettingsSection.APPEARANCE)) {
                SectionCard("Görünüm") {
                    ThemePicker(settings.themeId, onThemeChange)
                }
            }

            if (mode.shows(SettingsSection.PERSONA)) {
                SectionCard("Yerel model kişiliği") {
                    Text(
                        "İsteğe bağlı sistem talimatı. Yalnız telefonda çalışan modele uygulanır; " +
                            "boş bırakırsan varsayılan davranış kullanılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    OutlinedTextField(
                        value = persona,
                        onValueChange = { persona = it },
                        modifier = Modifier.fillMaxWidth().testTag("persona"),
                        label = { Text("Örn. Kısa ve net yanıt ver, Türkçe konuş") },
                    )
                    OutlinedButton(
                        onClick = { onPersonaChange(persona) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Kişiliği kaydet")
                    }
                }
            }

            if (mode.shows(SettingsSection.HF_TOKEN)) {
                SectionCard("Hugging Face (kapılı modeller)") {
                    Text(
                        "Yalnız lisans onaylı model indirmede kullanılır; token cihazda kalır ve " +
                            "yalnız huggingface.co'ya gönderilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    NovaSecretField(
                        value = hfToken,
                        onValueChange = { hfToken = it },
                        label = "HF erişim token'ı (hf_…)",
                        contentLabel = "Hugging Face erişim belirteci",
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "hf_token",
                    )
                    OutlinedButton(
                        onClick = { onHfTokenChange(hfToken) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("HF token'ı kaydet")
                    }
                }
            }

            SectionCard("Veri yönetimi") {
                Text(
                    "Tüm sohbet geçmişi, notlar ve model performans kayıtları bu cihazda tutulur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                OutlinedButton(
                    onClick = { showWipeDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wipe_data"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Yerel veriyi temizle")
                }
            }

            // Play B6: bildirimlerin nerede olduğu ve gönderiminin KULLANICI
            // eylemine bağlı olduğu burada görünür. Bildirimin kendisi sohbet
            // ekranında, üretilen içeriğin yanında yapılır.
            SectionCard("İçerik bildirimleri") {
                Text(
                    if (reportCount == 0) {
                        "Sakıncalı bir yanıt gördüğünde sohbetteki bayrak simgesiyle bildir. " +
                            "Bildirimler bu telefonda kalır."
                    } else {
                        "$reportCount bildirim bu telefonda kayıtlı. Göndermeye karar veren sensin: " +
                            "gönderilecek metni paylaşım ekranında görürsün."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (reportCount > 0) {
                    OutlinedButton(
                        onClick = onShareReports,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share_reports"),
                    ) {
                        Text("Geliştiriciye gönder")
                    }
                    OutlinedButton(
                        onClick = onClearReports,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("clear_reports"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Bildirimleri sil")
                    }
                }
            }

            SectionCard("Uygulama bilgisi") {
                Text("NOVA ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("Yerel öncelikli Android kontrol merkezi", color = Muted)
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Yerel veriyi temizle") },
            text = {
                Text(
                    "Sohbet geçmişi, notlar ve performans kayıtları silinsin mi? " +
                        "Ayarların ve Gateway bağlantın korunur.",
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        onWipeData(false)
                        showWipeDialog = false
                    }) { Text("Veriyi sil (modeller kalsın)") }
                    TextButton(onClick = {
                        onWipeData(true)
                        showWipeDialog = false
                    }) { Text("Veriyi + indirilen modelleri sil") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Vazgeç") }
            },
        )
    }
}

/** Bölümleri görsel olarak ayıran hafif kart; başlık metni test çapaları için korunur. */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                // Ayarlar uzun bir liste; başlık semantiği olmadan
                // ekran okuyucuyla bölüm bulmak tek tek gezmek demek.
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/**
 * Eşleme bölümü: yerel ağdaki PC'leri listeler, 8 karakterlik kodu alır.
 *
 * Kullanıcı hiçbir yere IP ya da anahtar yazmaz. Hiçbir şey bulunamazsa
 * bunun **neden** olabileceği ve elle yolun var olduğu dürüstçe yazılır —
 * sonsuza kadar dönen bir "aranıyor…" göstermez.
 */
@Composable
private fun PairingSection(
    state: PairingUiState,
    onStartDiscovery: () -> Unit,
    onSelectGateway: (DiscoveredGateway) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    // Panel açıldığında taramayı bir kez başlat; kapanınca çağıran durdurur.
    LaunchedEffect(Unit) { onStartDiscovery() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PC'yi bul ve eşle",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStartDiscovery) { Text("Yeniden tara") }
        }

        if (state.found.isEmpty()) {
            Text(
                PairingForm.emptyMessage(state.scanning),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.testTag("pairing_empty"),
            )
        } else {
            state.found.forEach { gateway ->
                val selected = gateway.baseUrl == state.selectedBaseUrl
                val modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("pairing_gateway_${gateway.host}")
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = gateway.displayName
                    }
                if (selected) {
                    Button(onClick = { onSelectGateway(gateway) }, modifier = modifier) {
                        GatewayLabel(gateway)
                    }
                } else {
                    OutlinedButton(onClick = { onSelectGateway(gateway) }, modifier = modifier) {
                        GatewayLabel(gateway)
                    }
                }
            }
        }

        // Alan iki şeyi birden kabul eder: 8 karakterlik kod ya da QR'ın
        // içerdiği tam horus://pair… bağlantısı. İkincisi adresi de taşıdığı
        // için keşif başarısız olsa bile tek hamlede bağlanmayı sağlar.
        val pastedLink = PairingForm.looksLikeUri(state.code)
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth().testTag("pairing_code"),
            label = { Text("Eşleme kodu veya QR bağlantısı") },
            placeholder = { Text("ABCD-2345  ·  horus://pair?…") },
            supportingText = { Text(PairingForm.codeHelp(state.code)) },
            singleLine = !pastedLink,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                // Bağlantı yapıştırıldıysa büyük harfe zorlamak URI'yi bozar.
                capitalization = if (pastedLink) {
                    KeyboardCapitalization.None
                } else {
                    KeyboardCapitalization.Characters
                },
            ),
        )

        // Gönderimden ÖNCE nereye bağlanılacağını yaz: yapıştırılan bir
        // bağlantı listede olmayan bir PC'yi işaret edebilir.
        PairingForm.resolveTarget(state)?.let { target ->
            Text(
                "Bağlanılacak: ${target.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("pairing_target"),
            )
        }

        Button(
            onClick = onSubmit,
            enabled = PairingForm.canSubmit(state),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pairing_submit"),
        ) {
            Text(if (state.busy) "Eşleniyor…" else "Eşle")
        }

        when (val phase = state.phase) {
            is PairingPhase.Paired -> PairingMessage(
                icon = Icons.Default.CheckCircle,
                tint = MaterialTheme.colorScheme.primary,
                message = "Eşlendi: ${phase.label}",
                hint = "Bağlantı kaydedildi. Artık adres ya da anahtar girmen gerekmiyor.",
                onDismiss = onDismissMessage,
                testTag = "pairing_success",
            )

            is PairingPhase.Failed -> PairingMessage(
                icon = Icons.Default.Error,
                tint = MaterialTheme.colorScheme.error,
                message = phase.message,
                hint = phase.hint,
                onDismiss = onDismissMessage,
                testTag = "pairing_error",
            )

            else -> Unit
        }
    }
}

@Composable
private fun GatewayLabel(gateway: DiscoveredGateway) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(gateway.displayName, maxLines = 1)
        Text(
            "${gateway.host}:${gateway.port}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun PairingMessage(
    icon: ImageVector,
    tint: Color,
    message: String,
    hint: String,
    onDismiss: () -> Unit,
    testTag: String,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(8.dp))
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Tamam") }
        }
        if (hint.isNotBlank()) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

/**
 * Basit / Gelişmiş seçici.
 *
 * Metin bilerek "gizlenen ayarlar çalışmaya devam eder" der: kullanıcı mod
 * değiştirmenin bir şeyi kapattığını sanmamalı, çünkü sanmıyor da — kapatmıyor.
 */
@Composable
private fun UiModePicker(mode: UiMode, onChange: (UiMode) -> Unit) {
    SectionCard("Arayüz") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UiMode.entries.forEach { option ->
                val selected = option == mode
                val modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("ui_mode_${option.id}")
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = option.label
                    }
                if (selected) {
                    Button(onClick = { onChange(option) }, modifier = modifier) {
                        Text(option.label, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { onChange(option) }, modifier = modifier) {
                        Text(option.label, maxLines = 1)
                    }
                }
            }
        }
        Text(mode.summary, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

/**
 * Hızlandırma seçici. Seçilen tercihin yanında motorun GERÇEKTEN yüklendiği
 * backend yazar — "Otomatik" seçiliyken sonucu görmenin tek dürüst yolu bu.
 */
@Composable
private fun BackendPicker(
    preference: BackendPreference,
    active: ActiveBackend,
    onChange: (BackendPreference) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hızlandırma", style = MaterialTheme.typography.titleSmall)
        BackendPreference.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { option ->
                    val selected = option == preference
                    val modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("backend_${option.id}")
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = option.label
                        }
                    if (selected) {
                        Button(onClick = { onChange(option) }, modifier = modifier) {
                            Text(option.label, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onChange(option) }, modifier = modifier) {
                            Text(option.label, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(preference.note, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(
            if (active == ActiveBackend.NONE) {
                "Şu an yüklü motor yok. Seçim bir sonraki yanıtta uygulanır."
            } else {
                "Şu an çalışan: ${active.label}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (active == ActiveBackend.NONE) Muted else MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("active_backend"),
        )
    }
}

/**
 * Örnekleme ayarları. Varsayılan "Model varsayılanı"dır ve o seçiliyken motora
 * hiçbir sampler değeri gönderilmez — yani güncelleme kimsenin yanıtlarını
 * kendiliğinden değiştirmez.
 */
@Composable
private fun SamplerControls(
    preset: SamplerPreset,
    custom: SamplerSettings,
    onPresetChange: (SamplerPreset) -> Unit,
    onCustomChange: (SamplerSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Yanıt üslubu (örnekleme)", style = MaterialTheme.typography.titleSmall)
        SamplerPreset.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { option ->
                    val selected = option == preset
                    val modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("sampler_${option.id}")
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = option.label
                        }
                    if (selected) {
                        Button(onClick = { onPresetChange(option) }, modifier = modifier) {
                            Text(option.label, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onPresetChange(option) }, modifier = modifier) {
                            Text(option.label, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(preset.note, style = MaterialTheme.typography.bodySmall, color = Muted)

        if (preset.isCustom) {
            // Sürükleme sırasında yalnız yerel Compose durumu değişir; diske
            // yazma parmak kalkınca bir kez yapılır. Aksi halde tek bir
            // sürüklemede onlarca DataStore yazımı tetiklenirdi.
            SamplerSlider(
                label = "topK",
                value = custom.topK.toFloat(),
                range = SamplerSettings.TOP_K_MIN.toFloat()..SamplerSettings.TOP_K_MAX.toFloat(),
                testTag = "sampler_top_k",
                format = { it.toInt().toString() },
                onCommit = { onCustomChange(custom.copy(topK = it.toInt())) },
            )
            SamplerSlider(
                label = "topP",
                value = custom.topP.toFloat(),
                range = SamplerSettings.TOP_P_MIN.toFloat()..SamplerSettings.TOP_P_MAX.toFloat(),
                testTag = "sampler_top_p",
                format = { formatTwo(it.toDouble()) },
                onCommit = { onCustomChange(custom.copy(topP = it.toDouble())) },
            )
            SamplerSlider(
                label = "Sıcaklık",
                value = custom.temperature.toFloat(),
                range = SamplerSettings.TEMPERATURE_MIN.toFloat()..
                    SamplerSettings.TEMPERATURE_MAX.toFloat(),
                testTag = "sampler_temperature",
                format = { formatTwo(it.toDouble()) },
                onCommit = { onCustomChange(custom.copy(temperature = it.toDouble())) },
            )
        }
    }
}

/**
 * Kaydırıcı: sürüklerken akıcı (yerel durum), bırakınca kalıcı ([onCommit]).
 * [value] dışarıdan değişirse (hazır ayara geçip geri dönmek gibi) yerel
 * durum `remember(value)` ile yeniden tohumlanır.
 */
@Composable
private fun SamplerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    testTag: String,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(format(draft), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth().testTag(testTag).semantics {
                contentDescription = label
            },
        )
    }
}

/** Locale'den bağımsız iki ondalık; cihaz diline göre virgül/nokta oynamasın. */
private fun formatTwo(value: Double): String {
    val scaled = kotlin.math.round(value * 100).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

/**
 * Katlanabilir bağlantı rehberi. Basit modda elle alan doldurma adımı yoktur,
 * bu yüzden yönergeler moda göre değişir.
 */
@Composable
private fun ConnectionHelp(mode: UiMode) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { open = !open }) {
                Text("Nasıl bağlanırım?")
                Icon(
                    if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (open) {
            Text(
                if (mode.isAdvanced) {
                    "1) PC'de PowerShell: .\\scripts\\start-horus.ps1 -Lan " +
                        "(dışarıdan erişim için -Tailscale ekle)\n" +
                        "2) Ekrandaki TELEFON AYARLARI kutusundan adresi ve API anahtarını " +
                        "buraya gir\n" +
                        "3) \"Bağlantıyı test et\" → \"PC hazır\" görünce Kaydet"
                } else {
                    "PC bağlantısı yalnız PC'deki modelleri kullanmak için gerekir. " +
                        "Telefonda çalışan model için bağlantı gerekmez: Modeller " +
                        "sekmesinden bir model indirmen yeterli.\n\n" +
                        "PC'ye bağlanmak istersen Gelişmiş moda geç."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

/**
 * Tema seçici. Satır başına 2 seçenek: tek satırda 4 tema sıkışıp adları
 * kırpılıyordu; ızgara düzeni yeni temalar eklendikçe de okunur kalır.
 */
@Composable
private fun ThemePicker(selectedId: String, onThemeChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NOVA_ACCENTS.chunked(2).forEach { rowAccents ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowAccents.forEach { accent ->
                    val selected = accent.id == selectedId
                    val modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("theme_${accent.id}")
                    if (selected) {
                        Button(onClick = { onThemeChange(accent.id) }, modifier = modifier) {
                            ThemeSwatch(accent)
                            Spacer(Modifier.width(6.dp))
                            Text(accent.name, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onThemeChange(accent.id) }, modifier = modifier) {
                            ThemeSwatch(accent)
                            Spacer(Modifier.width(6.dp))
                            Text(accent.name, maxLines = 1)
                        }
                    }
                }
                // Tek sayıda tema kalırsa son satır hizalı dursun.
                if (rowAccents.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeSwatch(accent: NovaAccent) {
    Box(
        Modifier
            .width(12.dp)
            .heightIn(min = 12.dp)
            .clip(CircleShape)
            .background(accent.primary),
    )
}

@Composable
private fun ConnectionStatus(connection: GatewayConnectionUiState) {
    val icon: ImageVector = when (connection.status) {
        GatewayConnectionStatus.READY -> Icons.Default.CheckCircle
        GatewayConnectionStatus.CHECKING -> Icons.Default.Sync
        GatewayConnectionStatus.AUTH_REQUIRED -> Icons.Default.Warning
        GatewayConnectionStatus.UNREACHABLE,
        GatewayConnectionStatus.INVALID_URL,
        -> Icons.Default.Error
        GatewayConnectionStatus.UNKNOWN -> Icons.Default.Info
    }
    val tint = when (connection.status) {
        GatewayConnectionStatus.READY -> MaterialTheme.colorScheme.primary
        GatewayConnectionStatus.AUTH_REQUIRED -> Amber
        GatewayConnectionStatus.UNREACHABLE,
        GatewayConnectionStatus.INVALID_URL,
        -> MaterialTheme.colorScheme.error
        else -> Muted
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(10.dp))
            Text(connection.message)
        }
        if (connection.hint.isNotBlank()) {
            Text(
                connection.hint,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.testTag("connection_hint"),
            )
        }
    }
}

@Composable
private fun ModelDropdown(
    selectedId: String,
    onModelChange: (String) -> Unit,
    models: List<ModelOption>,
    live: Boolean,
    note: String,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val list = models.ifEmpty { FALLBACK_MODELS }
    val selected = list.find { it.id == selectedId } ?: list.first()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (live) "Canlı liste · gateway" else "Yedek liste · gateway'e ulaşılamadı",
                style = MaterialTheme.typography.bodySmall,
                color = if (live) MaterialTheme.colorScheme.primary else Muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) { Text("Yenile") }
        }
        if (note.isNotBlank()) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = Amber)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("model_dropdown"),
            ) {
                Text(selected.name, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                list.forEach { model ->
                    DropdownMenuItem(
                        enabled = model.available,
                        text = {
                            Column {
                                Text(model.name)
                                Text(
                                    // Anahtar yoksa nedeni dürüstçe yaz.
                                    if (!model.available && model.reason.isNotBlank()) model.reason
                                    else listOf(model.group, model.desc).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onModelChange(model.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EffortControls(selectedId: String, onEffortChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EFFORTS.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { effort ->
                    val selected = effort.id == selectedId
                    if (selected) {
                        Button(
                            onClick = { onEffortChange(effort.id) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(effort.name)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onEffortChange(effort.id) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(effort.name)
                        }
                    }
                }
            }
        }
    }
}
