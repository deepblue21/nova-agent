package com.nova.agent.feature.models

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.ModelOption
import com.nova.agent.llm.LocalModelUi
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.ui.theme.Amber
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Success
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.TextMain

/**
 * Modeller: cihazdaki indirme merkezi + PC Gateway model listesi.
 * İndirme sabit sürümlü ve SHA-256 doğrulamalıdır; doğrulanmadan kurulmaz.
 */
@Composable
fun ModelsScreen(
    models: List<LocalModelUi>,
    activeLocalId: String,
    localThinking: Boolean,
    localTools: Boolean,
    toolSummary: String,
    storageUsedBytes: Long,
    storageFreeBytes: Long,
    deviceRamGb: Double,
    offlineReady: Boolean,
    gatewayModels: List<ModelOption>,
    gatewaySelectedId: String,
    onDownload: (LocalModelUi) -> Unit,
    onCancelDownload: (LocalModelUi) -> Unit,
    onDelete: (LocalModelUi) -> Unit,
    onVerify: (LocalModelUi) -> Unit,
    onSelectLocal: (String) -> Unit,
    onLocalThinking: (Boolean) -> Unit,
    onLocalTools: (Boolean) -> Unit,
    onSelectGateway: (String) -> Unit,
    onStartLocalChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OfflineReadinessCard(
            offlineReady = offlineReady,
            deviceRamGb = deviceRamGb,
            storageUsedBytes = storageUsedBytes,
            storageFreeBytes = storageFreeBytes,
            onStartLocalChat = onStartLocalChat,
        )

        SectionLabel("CİHAZDAKİ MODELLER")
        models.forEach { ui ->
            LocalModelRow(
                ui = ui,
                active = ui.spec.id == activeLocalId,
                onDownload = { onDownload(ui) },
                onCancel = { onCancelDownload(ui) },
                onDelete = { onDelete(ui) },
                onVerify = { onVerify(ui) },
                onSelect = { onSelectLocal(ui.spec.id) },
            )
        }

        ThinkingRow(localThinking, onLocalThinking)
        ToolsRow(localTools, toolSummary, onLocalTools)

        SectionLabel("PC GATEWAY MODELLERİ")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(16.dp)),
        ) {
            gatewayModels.forEachIndexed { index, model ->
                GatewayModelRow(
                    model = model,
                    selected = model.id == gatewaySelectedId,
                    onSelect = { onSelectGateway(model.id) },
                )
                if (index != gatewayModels.lastIndex) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Line),
                    )
                }
            }
        }
        Text(
            "Gateway modelleri PC'de çalışır; bulut anahtarları telefona gelmez.",
            color = Muted2,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted2, fontSize = 11.sp, letterSpacing = 1.2.sp)
}

@Composable
private fun OfflineReadinessCard(
    offlineReady: Boolean,
    deviceRamGb: Double,
    storageUsedBytes: Long,
    storageFreeBytes: Long,
    onStartLocalChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("offline_readiness"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (offlineReady) Success else Muted2,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (offlineReady) "Çevrimdışı kullanılabilir" else "Çevrimdışı için model gerekli",
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            if (offlineReady) {
                "Seçili model doğrulandı. Yerel sohbet Gateway olmadan çalışır."
            } else {
                "Aşağıdan bir model indirip doğrulanmasını bekleyin."
            },
            color = Muted,
            fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cihaz RAM: ${"%.1f".format(deviceRamGb)} GB",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Yerel istekler bu cihazdan çıkmaz", color = Muted, fontSize = 12.sp)
        }
        Text(
            "Modeller: ${storageUsedBytes / 1_048_576} MB · Boş depolama: " +
                "${"%.1f".format(storageFreeBytes / 1_073_741_824.0)} GB",
            color = Muted,
            fontSize = 12.sp,
        )
        if (offlineReady) {
            Button(
                onClick = onStartLocalChat,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start_local_chat"),
            ) {
                Text("Yerel sohbet başlat")
            }
        }
    }
}

@Composable
private fun LocalModelRow(
    ui: LocalModelUi,
    active: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onVerify: () -> Unit,
    onSelect: () -> Unit,
) {
    val spec = ui.spec
    val installed = ui.disk is LocalModelDiskState.Installed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("local_model_${spec.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (installed) {
                RadioButton(
                    selected = active,
                    onClick = onSelect,
                    modifier = Modifier.semantics {
                        contentDescription = "${spec.displayName} aktif yerel model"
                    },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(spec.displayName, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${spec.quantization} · ${spec.sizeLabel} · ${spec.licenseName} · önerilen ≥${spec.recommendedRamGb} GB RAM",
                    color = Muted,
                    fontSize = 11.sp,
                )
                if (spec.gated && !installed) {
                    Text(
                        "Kapılı model: HF hesabında lisans onayı + Ayarlar'da HF token gerekir.",
                        color = Amber,
                        fontSize = 11.sp,
                    )
                }
            }
            StatusChip(ui)
        }

        if (ui.downloading) {
            val fraction = if (spec.sizeBytes > 0) {
                (ui.downloadedBytes.toFloat() / spec.sizeBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "%${(fraction * 100).toInt()} · ${ui.downloadedBytes / 1_048_576} MB / ${spec.sizeBytes / 1_048_576} MB",
                color = Muted,
                fontSize = 11.sp,
            )
        }

        ui.error?.let { Text(it, color = Coral, fontSize = 12.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                ui.downloading -> {
                    TextButton(onClick = onCancel) { Text("Duraklat", color = Coral) }
                }

                installed -> {
                    if (ui.verifying) {
                        Text("Doğrulanıyor…", color = Muted, fontSize = 12.sp)
                    } else {
                        TextButton(onClick = onVerify) { Text("Doğrula") }
                        TextButton(onClick = onDelete) { Text("Sil", color = Coral) }
                    }
                }

                ui.disk is LocalModelDiskState.Partial -> {
                    Button(onClick = onDownload, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text("Sürdür")
                    }
                    TextButton(onClick = onDelete) { Text("Sil", color = Coral) }
                }

                else -> {
                    Button(onClick = onDownload, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text("İndir (${spec.sizeLabel})")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(ui: LocalModelUi) {
    val (label, tint) = when {
        ui.downloading -> "İndiriliyor" to MaterialTheme.colorScheme.primary
        ui.verifying -> "Doğrulanıyor" to MaterialTheme.colorScheme.primary
        ui.disk is LocalModelDiskState.Installed ->
            if ((ui.disk as LocalModelDiskState.Installed).verified) {
                "Hazır" to Success
            } else {
                "Doğrulanmadı" to Coral
            }
        ui.disk is LocalModelDiskState.Partial -> "Yarım kaldı" to Coral
        else -> "Cihazda yok" to Muted2
    }
    Text(
        label,
        color = tint,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ThinkingRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Yerel düşünme (Qwen3)", color = TextMain, fontSize = 14.sp)
            Text(
                "Modelin gerçek enable_thinking anahtarı: Açık/Kapalı. Kademeli seviye bu motorda yok.",
                color = Muted,
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = "Yerel düşünme" },
        )
    }
}

@Composable
private fun ToolsRow(enabled: Boolean, summary: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Yerel araçlar (deneysel)", color = TextMain, fontSize = 14.sp)
            Text(
                "Tamamı çevrimdışı: $summary. Araç çağrısı model kararına bağlıdır; " +
                    "küçük modellerde her istemde tetiklenmeyebilir.",
                color = Muted,
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = "Yerel araçlar" },
        )
    }
}

@Composable
private fun GatewayModelRow(model: ModelOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(model.name, color = TextMain, fontSize = 14.sp)
            Text(model.group, color = Muted2, fontSize = 11.sp)
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "${model.name} seçili",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
