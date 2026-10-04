package com.nova.agent.feature.models

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.ModelOption
import com.nova.agent.data.UiMode
import com.nova.agent.llm.LocalModelUi
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.LocalThinkingSupport
import com.nova.agent.llm.local.ModelMetrics
import com.nova.agent.llm.local.ModelRecommender
import com.nova.agent.ui.theme.Amber
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Success
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.TextMain
import com.nova.agent.ui.components.novaGlass

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
    recommendedId: String,
    metrics: Map<String, ModelMetrics>,
    gatewayModels: List<ModelOption>,
    gatewaySelectedId: String,
    /**
     * Basit modda kapılı (lisans onaylı) modeller listelenmez: indirmeleri
     * yalnız Gelişmiş modda görünen HF belirtecine bağlıdır, dolayısıyla
     * gösterilseler kaçınılmaz bir çıkmaz sokak olurlardı.
     */
    uiMode: UiMode = UiMode.ADVANCED,
    showGatewayModels: Boolean = uiMode.isAdvanced,
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
    var catalogExpanded by rememberSaveable { mutableStateOf(false) }
    var modelSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var detailModelId by rememberSaveable { mutableStateOf<String?>(null) }
    // Resolve from current state, never retain a stale download/verification snapshot.
    models.firstOrNull { it.spec.id == detailModelId }?.let { ui ->
        ModelDetailsDialog(
            ui = ui,
            active = ui.spec.id == activeLocalId,
            recommended = ui.spec.id == recommendedId,
            fit = ModelRecommender.fit(ui.spec, deviceRamGb),
            metrics = metrics[ui.spec.id],
            onDismiss = { detailModelId = null },
            onDownload = { onDownload(ui) },
            onCancel = { onCancelDownload(ui) },
            onDelete = { onDelete(ui) },
            onVerify = { onVerify(ui) },
            onSelect = { onSelectLocal(ui.spec.id) },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OfflineReadinessCard(
            offlineReady = offlineReady,
            activeModel = models.firstOrNull { it.spec.id == activeLocalId },
            deviceRamGb = deviceRamGb,
            storageUsedBytes = storageUsedBytes,
            storageFreeBytes = storageFreeBytes,
            onStartLocalChat = onStartLocalChat,
            onVerify = onVerify,
        )

        // Basit modda kapılı modeller gizlenir; ancak kullanıcı zaten birini
        // indirmiş ya da seçmişse satırı SAKLAMAYIZ — sahip olduğu modeli
        // yönetemez duruma düşürmek sadeleştirme değil, veri kaybı hissi olur.
        val eligibleModels = models.filter { ui ->
            !ui.spec.gated ||
                uiMode.isAdvanced ||
                ui.spec.id == activeLocalId ||
                ui.disk !is LocalModelDiskState.NotInstalled || ui.downloading || ui.error != null
        }
        val hiddenGatedCount = models.size - eligibleModels.size
        val visibleModels = eligibleModels.filter { ui ->
            uiMode.isAdvanced || catalogExpanded || ui.spec.id == activeLocalId ||
                ui.spec.id == recommendedId || ui.disk !is LocalModelDiskState.NotInstalled ||
                ui.downloading || ui.error != null
        }

        // Öneri afişi YALNIZ model henüz cihazda değilken. Afiş bir çağrıdır
        // ("şunu indir"); model indikten sonra aynı model hem afişte hem listede
        // görünüyordu — ekranda arka arkaya iki özdeş kart. Afiş gösterilirken o
        // modelin satırı listeden çıkarılır: afiş zaten o satırın işini görür ve
        // daha fazla bağlam taşır. Model kurulduğunda afiş kapanır, satır
        // listeye döner ve silme/doğrulama yönetimi orada yapılır.
        // İndirme başlamışsa veya hata varsa yönetim satırı kalmalı: afişte
        // duraklatma, ilerleme ve hata alanları yok.
        val recommended = visibleModels.firstOrNull {
            it.spec.id == recommendedId && it.disk is LocalModelDiskState.NotInstalled &&
                !it.downloading && it.error == null
        }
        if (recommended != null) {
            CompactModelRow(
                ui = recommended,
                active = false,
                recommended = true,
                tag = "recommendation_banner",
                onClick = { detailModelId = recommended.spec.id },
            )
        }

        // "CİHAZDAKİ MODELLER" YANLIŞTI: liste kataloğun tamamını gösteriyor,
        // yani henüz indirilmemiş modelleri de. Başlık, hiçbir şey indirmemiş
        // kullanıcıya "bunlar cihazında" diyordu.
        SectionLabel("TELEFON MODELLERİ")
        Text("Özet ve indirme seçenekleri için bir modele dokunun.", color = Muted, fontSize = 12.sp)
        visibleModels.filter { it.spec.id != recommended?.spec?.id }.forEach { ui ->
            CompactModelRow(
                ui = ui,
                active = ui.spec.id == activeLocalId,
                recommended = ui.spec.id == recommendedId,
                onClick = { detailModelId = ui.spec.id },
            )
        }
        if (!uiMode.isAdvanced && (catalogExpanded || visibleModels.size < eligibleModels.size)) {
            TextButton(
                onClick = { catalogExpanded = !catalogExpanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle_model_catalog"),
            ) {
                Text(
                    if (catalogExpanded) "Daha az model göster" else "Diğer telefon modelleri (${eligibleModels.size - visibleModels.size})",
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        if (hiddenGatedCount > 0 && (uiMode.isAdvanced || catalogExpanded)) {
            Text(
                "$hiddenGatedCount model lisans onayı ve Hugging Face belirteci gerektirdiği " +
                    "için Basit modda listelenmiyor. Ayarlar > Arayüz > Gelişmiş ile açılır.",
                color = Muted2,
                fontSize = 11.sp,
                modifier = Modifier.testTag("hidden_gated_note"),
            )
        }

        if (!uiMode.isAdvanced) {
            TextButton(onClick = { modelSettingsExpanded = !modelSettingsExpanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle_model_settings")) {
                Text(if (modelSettingsExpanded) "Model ayarlarını gizle" else "Model ayarları")
            }
        }
        AnimatedVisibility(visible = uiMode.isAdvanced || modelSettingsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThinkingRow(
                    activeSpec = models.firstOrNull { it.spec.id == activeLocalId }?.spec,
                    enabled = localThinking,
                    onChange = onLocalThinking,
                )
                ToolsRow(localTools, toolSummary, onLocalTools)
            }
        }

        if (showGatewayModels) {
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
}

@Composable
private fun SectionLabel(text: String) {
    // heading(): TalkBack kullanıcısı bölümler arasında tek hareketle
    // gezinebilsin. Görsel olarak hiçbir şey değişmez.
    Text(
        text,
        color = Muted2,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun OfflineReadinessCard(
    offlineReady: Boolean,
    activeModel: LocalModelUi?,
    deviceRamGb: Double,
    storageUsedBytes: Long,
    storageFreeBytes: Long,
    onStartLocalChat: () -> Unit,
    onVerify: (LocalModelUi) -> Unit,
) {
    val needsVerification = activeModel?.disk is LocalModelDiskState.Installed && !offlineReady
    val verifying = activeModel?.verifying == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .novaGlass()
            .padding(16.dp)
            .testTag("offline_readiness"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Hazır DEĞİLKEN onay işareti kullanılmaz: ✓ "tamamlandı" demektir ve
            // "model gerekli" cümlesiyle taban tabana zıttır. Yalnız renkle
            // ayırmak da yetmez — renk körlüğünde iki durum aynı görünür.
            Icon(
                if (offlineReady) Icons.Filled.CheckCircle else Icons.Filled.Download,
                contentDescription = null,
                tint = if (offlineReady) Success else Muted2,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    verifying -> "Model doğrulanıyor"
                    offlineReady -> "Çevrimdışı kullanılabilir"
                    needsVerification -> "Model doğrulaması gerekli"
                    else -> "Çevrimdışı için model gerekli"
                },
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            when {
                verifying -> "Dosya telefonda kontrol ediliyor. İnternet bağlantısı gerekmez."
                offlineReady -> "Seçili model doğrulandı. Yerel sohbet Gateway olmadan çalışır."
                needsVerification -> "Model zaten telefonda. Yeniden indirmeden dosyayı doğrulayabilirsiniz."
                else -> "Aşağıdan bir model indirip doğrulanmasını bekleyin."
            },
            color = Muted,
            fontSize = 12.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Cihaz RAM: ${"%.1f".format(deviceRamGb)} GB",
                color = Muted,
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Yerel istekler bu cihazdan çıkmaz", color = Muted, fontSize = 12.sp)
            }
        }
        Text(
            "Modeller: ${storageUsedBytes / 1_048_576} MB · Boş depolama: " +
                "${"%.1f".format(storageFreeBytes / 1_073_741_824.0)} GB",
            color = Muted,
            fontSize = 12.sp,
        )
        if (verifying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (needsVerification) {
            Button(
                onClick = { onVerify(activeModel) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("verify_local_model"),
            ) {
                Text("Modeli doğrula")
            }
        } else if (offlineReady) {
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
private fun CompactModelRow(
    ui: LocalModelUi,
    active: Boolean,
    recommended: Boolean,
    tag: String = "local_model_${ui.spec.id}",
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().novaGlass(emphasized = recommended)
            .clickable(role = Role.Button, onClickLabel = "Model ayrıntılarını aç", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (recommended) Text("Önerilen model", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                Text(ui.spec.displayName, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${ui.spec.sizeLabel} · ${ui.spec.quantization}${if (active) " · Seçili" else ""}",
                    color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        }
        // Status stays visible even when details are closed; large summaries stay in the dialog.
        if (ui.downloading || ui.verifying || ui.error != null || ui.disk !is LocalModelDiskState.NotInstalled) {
            StatusChip(ui)
        }
        if (ui.downloading) ModelDownloadProgress(ui, showBytes = false)
    }
}

@Composable
private fun ModelDetailsDialog(
    ui: LocalModelUi,
    active: Boolean,
    recommended: Boolean,
    fit: ModelRecommender.Fit,
    metrics: ModelMetrics?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onVerify: () -> Unit,
    onSelect: () -> Unit,
) {
    val spec = ui.spec
    val installed = ui.disk is LocalModelDiskState.Installed
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .novaGlass(shape = RoundedCornerShape(24.dp), emphasized = true)
                    .padding(18.dp).testTag("model_details"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Model ayrıntıları", color = Muted, fontSize = 13.sp,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_model_details")) {
                        Icon(Icons.Default.Close, contentDescription = "Model ayrıntılarını kapat", tint = TextMain)
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(spec.displayName, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    StatusChip(ui)
                    Text(spec.note ?: "Bu model için ek açıklama bulunmuyor.", color = TextMain, fontSize = 14.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("İndirme: ${spec.sizeLabel} · ${spec.quantization}", color = Muted, fontSize = 13.sp)
                        Text("Önerilen cihaz RAM'i: ${spec.recommendedRamGb} GB ve üzeri (tahmini)",
                            color = Muted, fontSize = 13.sp)
                        FitAndPerfLine(fit, metrics)
                        Text("Lisans: ${spec.licenseName}", color = Muted, fontSize = 13.sp)
                    }
                    if (spec.recommendedRamGb >= 12 || fit == ModelRecommender.Fit.RISKY) {
                        Text("Dosya boyutu, çalışma belleği değildir. Android ve konuşma bağlamı da RAM kullanır; " +
                            "bu model cihazınızda yavaş çalışabilir veya bellek yetersizliğinden açılamayabilir.",
                            color = Amber, fontSize = 12.sp)
                    }
                    if (spec.gated && !installed) {
                        Text("Hugging Face hesabında lisans onayı ve Ayarlar'da HF token gerekir.",
                            color = Amber, fontSize = 12.sp)
                    }
                    if (ui.downloading) ModelDownloadProgress(ui, showBytes = true)
                    ui.error?.let { Text(it, color = Coral, fontSize = 13.sp) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            ui.downloading -> TextButton(onClick = onCancel) { Text("Duraklat", color = Coral) }
                            ui.verifying -> Text("Doğrulanıyor…", color = Muted)
                            installed -> {
                                Button(onClick = onSelect, modifier = Modifier.semantics {
                                    contentDescription = "${spec.displayName} aktif yerel model"
                                }) { Text(if (active) "Seçili model" else "Bu modeli seç") }
                                TextButton(onClick = onVerify) { Text("Doğrula") }
                                TextButton(onClick = onDelete) { Text("Sil", color = Coral) }
                            }
                            else -> {
                                Button(onClick = onDownload, modifier = Modifier.heightIn(min = 48.dp)
                                    .testTag(if (recommended) "recommend_download" else "model_download")) {
                                    Text(when {
                                        ui.disk is LocalModelDiskState.Partial -> "Sürdür"
                                        spec.gated -> "İndir (HF token gerekli)"
                                        else -> "İndir (${spec.sizeLabel})"
                                    })
                                }
                                if (ui.disk is LocalModelDiskState.Partial) {
                                    TextButton(onClick = onDelete) { Text("Sil", color = Coral) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDownloadProgress(ui: LocalModelUi, showBytes: Boolean) {
    val fraction = if (ui.spec.sizeBytes > 0) {
        (ui.downloadedBytes.toFloat() / ui.spec.sizeBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    if (showBytes) {
        Text("%${(fraction * 100).toInt()} · ${ui.downloadedBytes / 1_048_576} MB / ${ui.spec.sizeBytes / 1_048_576} MB",
            color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun FitAndPerfLine(fit: ModelRecommender.Fit, metrics: ModelMetrics?) {
    val fitColor = when (fit) {
        ModelRecommender.Fit.COMFORTABLE -> Success
        ModelRecommender.Fit.TIGHT -> Amber
        ModelRecommender.Fit.RISKY -> Coral
        ModelRecommender.Fit.UNKNOWN -> Muted2
    }
    val perf = metrics?.summary()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (fit != ModelRecommender.Fit.UNKNOWN) {
            Text("Uygunluk: ", color = Muted2, fontSize = 11.sp)
            Text(fit.label, color = fitColor, fontSize = 11.sp)
        }
        if (perf != null) {
            Text(perf, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusChip(ui: LocalModelUi) {
    val (label, tint) = when {
        ui.error != null -> "İşlem tamamlanamadı" to Coral
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
private fun ThinkingRow(
    activeSpec: LocalModelSpec?,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    // Anahtar artık seçili modele bağlı. Desteklemeyen modellerde GİZLENMEZ,
    // pasif çizilir ve nedeni yazar — projenin "desteklenmeyeni taklit etme,
    // pasif göster ve açıkla" değişmezi.
    val support = LocalThinkingSupport.forModel(activeSpec)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("thinking_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                support.title,
                color = if (support.interactive) TextMain else Muted,
                fontSize = 14.sp,
            )
            Text(support.explanation, color = Muted, fontSize = 11.sp)
        }
        Switch(
            checked = enabled && support.interactive,
            onCheckedChange = onChange,
            enabled = support.interactive,
            modifier = Modifier
                .testTag("thinking_switch")
                .semantics { contentDescription = "Yerel düşünme" },
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
    // Sağlayıcı anahtarı yoksa satır görünür ama seçilemez ve nedeni yazılır.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = model.available, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.name,
                    color = if (model.available) TextMain else Muted2,
                    fontSize = 14.sp,
                )
                // Araç desteği rozeti: ajan modu yalnız bu modellerde açılır.
                // Ölçülen ("probe"/"provider") ile tahmin ("family") ayrı yazılır;
                // tahmin, ölçümmüş gibi sunulmaz.
                if (model.tools) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (model.toolsVerified) "araç destekli" else "araç destekli?",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .testTag("tools_badge_${model.id}"),
                    )
                }
            }
            Text(
                when {
                    !model.available && model.reason.isNotBlank() -> model.reason
                    model.desc.isNotBlank() -> model.desc
                    else -> model.group
                },
                color = Muted2,
                fontSize = 11.sp,
            )
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
