package com.nova.agent.feature.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.userLabel
import com.nova.agent.llm.EngineRouter
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.LocalEngineUi
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Success
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.TextMain

/**
 * Komuta merkezi: yürütme politikası, aktif hedef durumu ve aktif iş.
 * Ağ çağrısı yapmaz; yalnız durum sunar ve yönlendirir.
 */
@Composable
fun ControlScreen(
    policy: ExecutionPolicy,
    localModelName: String,
    localInstalled: Boolean,
    localVerified: Boolean,
    engineState: LocalEngineUi,
    connection: GatewayConnectionUiState,
    activeTask: MobileTask?,
    chatBusy: Boolean,
    hybridAutoFallback: Boolean,
    onHybridAutoFallback: (Boolean) -> Unit,
    onPolicyChange: (ExecutionPolicy) -> Unit,
    onNewTask: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionLabel("YÜRÜTME POLİTİKASI")
        PolicyPicker(policy, onPolicyChange)

        TargetCard(
            policy = policy,
            localModelName = localModelName,
            localInstalled = localInstalled,
            localVerified = localVerified,
            engineState = engineState,
            connection = connection,
            onNewTask = onNewTask,
            onOpenChat = onOpenChat,
            onOpenModels = onOpenModels,
        )

        if (policy == ExecutionPolicy.HYBRID) {
            HybridRulesCard(hybridAutoFallback, onHybridAutoFallback)
        }

        SectionLabel("AKTİF İŞ")
        ActiveWorkCard(activeTask, chatBusy, engineState)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted2, fontSize = 11.sp, letterSpacing = 1.2.sp)
}

@Composable
private fun PolicyPicker(policy: ExecutionPolicy, onPolicyChange: (ExecutionPolicy) -> Unit) {
    val ordered = listOf(
        ExecutionPolicy.LOCAL_FIRST,
        ExecutionPolicy.GATEWAY_ONLY,
        ExecutionPolicy.LOCAL_ONLY,
        ExecutionPolicy.HYBRID,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ordered.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val selected = option == policy
                    val enabled = option.selectableNow
                    val phaseNote: String? = null // Faz 3 D1 ile tüm politikalar açık.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    Surface1
                                },
                            )
                            .border(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    Line
                                },
                                RoundedCornerShape(14.dp),
                            )
                            .clickable(enabled = enabled) { onPolicyChange(option) }
                            .semantics {
                                role = Role.RadioButton
                                contentDescription = option.label +
                                    if (phaseNote != null) " ($phaseNote — henüz açık değil)" else ""
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("policy_${option.id}"),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            option.label,
                            color = if (enabled) TextMain else Muted2,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (phaseNote != null) {
                            Text("$phaseNote — henüz açık değil", color = Muted2, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetCard(
    policy: ExecutionPolicy,
    localModelName: String,
    localInstalled: Boolean,
    localVerified: Boolean,
    engineState: LocalEngineUi,
    connection: GatewayConnectionUiState,
    onNewTask: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val localMode = policy.runsOnDevice
    val offlineMode = policy == ExecutionPolicy.LOCAL_ONLY
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.06f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    policy.label,
                    color = TextMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = if (localMode) {
                    when {
                        engineState is LocalEngineUi.Loading -> "$localModelName · yükleniyor…"
                        !localInstalled -> "$localModelName · indirilmedi"
                        localVerified -> "$localModelName · cihazda hazır"
                        else -> "$localModelName · doğrulanmadı"
                    }
                } else {
                    connection.message
                }
                Text(subtitle, color = accent, fontSize = 13.sp)
            }
        }

        if (localMode) {
            val hybridMode = policy == ExecutionPolicy.HYBRID
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        when {
                            offlineMode -> "Yalnız telefonda çalışır · devir kapalı"
                            hybridMode -> "Kısa işler telefonda · uzun işler ve düşük pil PC'de"
                            else -> "Önce telefonda çalışır · gerekirse izin ister"
                        },
                        color = TextMain,
                        fontSize = 13.sp,
                    )
                    Text(
                        when {
                            offlineMode -> "İstemler hiçbir koşulda cihaz dışına gönderilmez."
                            hybridMode -> "Hibrit seçimi PC kullanımına açık rızadır; kural kartından yönetilir."
                            else -> "Verileriniz cihazınızda kalır. Onaysız hiçbir istem dışarı çıkmaz."
                        },
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        } else {
            Text(
                "Bulut modelleri de PC'deki Gateway üzerinden çağrılır; anahtarlar telefona gelmez.",
                color = Muted,
                fontSize = 11.sp,
            )
        }

        if (localMode && !localInstalled) {
            Button(
                onClick = onOpenModels,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("cta_download_model"),
            ) {
                Text("Model indir")
            }
        } else {
            Button(
                onClick = onNewTask,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("cta_new_task"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Yeni görev")
            }
        }
        TextButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Sohbet başlat", color = Muted)
        }
    }
}

/** Hibrit kuralları: sabit, şeffaf kurallar + tek kullanıcı anahtarı. */
@Composable
private fun HybridRulesCard(autoFallback: Boolean, onAutoFallback: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("hybrid_rules_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Hibrit kuralları", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "• ${EngineRouter.LONG_PROMPT_CHARS}+ karakterlik istemler PC'ye gider\n" +
                "• Pil ≤ %${EngineRouter.LOW_BATTERY_PERCENT} ve şarjda değilken PC tercih edilir\n" +
                "• Cihaz ciddi ısınmışsa PC tercih edilir\n" +
                "• Telefon modeli kurulu değilse istekler PC'de çalışır",
            color = Muted,
            fontSize = 12.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Yerel hata olursa otomatik PC'ye devret", color = TextMain, fontSize = 13.sp)
                Text(
                    if (autoFallback) "Açık: devir bildirimsiz yapılır (rota rozetinde görünür)."
                    else "Kapalı: her seferinde izin kartı sorulur.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = autoFallback,
                onCheckedChange = onAutoFallback,
                modifier = Modifier.semantics { contentDescription = "Otomatik PC devri" },
            )
        }
    }
}

@Composable
private fun ActiveWorkCard(task: MobileTask?, chatBusy: Boolean, engineState: LocalEngineUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("active_work_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            task != null -> {
                Text(
                    task.prompt,
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusTint(task)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(task.status.userLabel, color = Muted, fontSize = 12.sp)
                }
            }

            chatBusy -> {
                val label = when (engineState) {
                    is LocalEngineUi.Loading -> "Model yükleniyor: ${engineState.modelName}…"
                    is LocalEngineUi.Ready -> "Telefonda yanıt üretiliyor (${engineState.modelName})…"
                    else -> "Sohbet yanıtı üretiliyor…"
                }
                Text(label, color = TextMain, fontSize = 13.sp)
            }

            else -> Text("Aktif iş yok", color = Muted2, fontSize = 13.sp)
        }
    }
}

@Composable
private fun statusTint(task: MobileTask) = when (task.status) {
    MobileTaskStatus.COMPLETED -> Success
    MobileTaskStatus.FAILED, MobileTaskStatus.CANCELLED -> Coral
    else -> MaterialTheme.colorScheme.primary
}
