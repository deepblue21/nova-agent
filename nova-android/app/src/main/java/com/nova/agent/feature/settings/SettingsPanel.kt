package com.nova.agent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.nova.agent.BuildConfig
import com.nova.agent.data.AppSettings
import com.nova.agent.data.EFFORTS
import com.nova.agent.data.MODELS
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
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
    onReasoningChange: (Boolean) -> Unit,
    onThemeChange: (String) -> Unit = {},
    onHfTokenChange: (String) -> Unit = {},
    onClose: () -> Unit,
) {
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.token) { mutableStateOf(settings.token) }
    var hfToken by remember(settings.hfToken) { mutableStateOf(settings.hfToken) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            SectionHeading("PC bağlantısı")
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth().testTag("gateway_url"),
                label = { Text("Gateway adresi") },
                singleLine = true,
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

            SectionHeading("Model ve çalışma biçimi")
            ModelDropdown(settings.modelId, onModelChange)
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

            SectionHeading("Hugging Face (kapılı modeller)")
            Text(
                "Yalnız lisans onaylı model indirmede kullanılır; token cihazda kalır ve " +
                    "yalnız huggingface.co'ya gönderilir.",
                style = MaterialTheme.typography.bodySmall,
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

            SectionHeading("Görünüm")
            ThemePicker(settings.themeId, onThemeChange)

            SectionHeading("Uygulama bilgisi")
            Text("NOVA ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text("Yerel öncelikli Android kontrol merkezi")
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ThemePicker(selectedId: String, onThemeChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NOVA_ACCENTS.forEach { accent ->
            val selected = accent.id == selectedId
            if (selected) {
                Button(
                    onClick = { onThemeChange(accent.id) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    ThemeSwatch(accent)
                    Spacer(Modifier.width(6.dp))
                    Text(accent.name)
                }
            } else {
                OutlinedButton(
                    onClick = { onThemeChange(accent.id) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    ThemeSwatch(accent)
                    Spacer(Modifier.width(6.dp))
                    Text(accent.name)
                }
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
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(connection.message)
    }
}

@Composable
private fun ModelDropdown(selectedId: String, onModelChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = MODELS.find { it.id == selectedId } ?: MODELS.first()
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(selected.name, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MODELS.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name)
                            Text(model.group, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick =