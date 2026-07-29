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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nova.agent.BuildConfig
import com.nova.agent.data.AppSettings
import com.nova.agent.data.EFFORTS
import com.nova.agent.data.FALLBACK_MODELS
import com.nova.agent.data.ModelOption
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
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
    onClose: () -> Unit,
) {
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.token) { mutableStateOf(settings.token) }
    var hfToken by remember(settings.hfToken) { mutableStateOf(settings.hfToken) }
    var persona by remember(settings.persona) { mutableStateOf(settings.persona) }
    var showWipeDialog by remember { mutableStateOf(false) }

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

            SectionCard("PC bağlantısı") {
                ConnectionHelp()
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth().testTag("gateway_url"),
                    label = { Text("Gateway adresi") },
                    placeholder = { Text("http://192.168.1.20:8088/v1") },
                    supportingText = { Text("start-horus -Lan çıktısındaki adresi birebir gir") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gateway_token")
                        .semantics {
                            contentDescription = "Gateway erişim belirteci"
                            password()
                        },
                    label = { Text("Erişim belirteci") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
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
                    Button(
                        onClick = { onSaveConnection(baseUrl, token) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Kaydet")
                    }
                }
            }

            SectionCard("Model ve çalışma biçimi") {
                ModelDropdown(settings.modelId, onModelChange, models, modelsLive, modelsNote, onRefreshModels)
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

            SectionCard("Görünüm") {
                ThemePicker(settings.themeId, onThemeChange)
            }

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

            SectionCard("Hugging Face (kapılı modeller)") {
                Text(
                    "Yalnız lisans onaylı model indirmede kullanılır; token cihazda kalır ve " +
                        "yalnız huggingface.co'ya gönderilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                OutlinedTextField(
                    value = hfToken,
                    onValueChange = { hfToken = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hf_token")
                        .semantics {
                            contentDescription = "Hugging Face erişim belirteci"
                            password()
                        },
                    label = { Text("HF erişim token'ı (hf_…)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedButton(
                    onClick = { onHfTokenChange(hfToken) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("HF token'ı kaydet")
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Katlanabilir 3 adımlık bağlantı rehberi — yeni kullanıcı için tek bakışta kurulum. */
@Composable
private fun ConnectionHelp() {
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
                "1) PC'de PowerShell: .\\scripts\\start-horus.ps1 -Lan " +
                    "(dışarıdan erişim için -Tailscale ekle)\n" +
                    "2) Ekrandaki TELEFON AYARLARI kutusundan adresi ve API anahtarını buraya gir\n" +
                    "3) \"Bağlantıyı test et\" → \"PC hazır\" görünce Kaydet",
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
