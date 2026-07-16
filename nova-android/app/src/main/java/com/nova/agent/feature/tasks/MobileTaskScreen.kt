package com.nova.agent.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.Bg
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Cyan
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.Surface2
import com.nova.agent.ui.theme.TextMain

@Composable
fun MobileTaskScreen(
    state: MobileTaskUiState,
    connection: GatewayConnectionUiState = GatewayConnectionUiState(),
    onPromptChange: (String) -> Unit,
    onCreateTask: () -> Unit,
    onCommand: (String) -> Unit,
    onDecision: (String) -> Unit,
    onNewTask: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRetryConnection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val connected = connection.status == GatewayConnectionStatus.READY
    val confirmationPending = state.pendingConfirmation != null
    Box(modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier.fillMaxSize().then(
                if (confirmationPending) Modifier.clearAndSetSemantics {} else Modifier,
            ),
        ) {
            if (state.task == null && state.events.isEmpty()) {
                TaskEmptyState(
                    prompt = state.prompt,
                    connection = connection,
                    connected = connected,
                    loading = state.loading,
                    actionsEnabled = !confirmationPending,
                    error = state.error,
                    onPromptChange = onPromptChange,
                    onQuickPrompt = onPromptChange,
                    onCreateTask = onCreateTask,
                    onOpenSettings = onOpenSettings,
                    onRetryConnection = onRetryConnection,
                )
            } else {
                ActiveTaskContent(
                    state = state,
                    actionsEnabled = !confirmationPending,
                    onCommand = onCommand,
                    onNewTask = onNewTask,
                )
            }
        }

        state.pendingConfirmation?.let { confirmation ->
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.66f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
            val taskPrompt = state.task?.prompt
            val summary = state.events.asReversed()
                .firstOrNull { it.confirmation?.id == confirmation.id }
                ?.userSummary(taskPrompt)
                ?: taskPrompt?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Onay bekleniyor"
            ConfirmationPanel(
                confirmation = confirmation,
                actionSummary = summary,
                decisionEnabled = state.canResolveConfirmation,
                onDecision = onDecision,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun TaskEmptyState(
    prompt: String,
    connection: GatewayConnectionUiState,
    connected: Boolean,
    loading: Boolean,
    actionsEnabled: Boolean,
    error: String?,
    onPromptChange: (String) -> Unit,
    onQuickPrompt: (String) -> Unit,
    onCreateTask: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryConnection: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Telefonunda ne yapmamı istersin?",
                color = TextMain,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(connection.message, color = if (connected) Cyan else Muted, fontSize = 13.sp)

            QuickPrompt("Android sürümünü bul", actionsEnabled, onQuickPrompt)
            QuickPrompt("Ayarlar'ı aç", actionsEnabled, onQuickPrompt)
            QuickPrompt("Bir uygulamayı aç", actionsEnabled, onQuickPrompt)
        }

        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (prompt.isEmpty()) {
                    Text("Görevi ayrıntılarıyla yaz", color = Muted2, fontSize = 15.sp)
                }
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    enabled = actionsEnabled && !loading,
                    minLines = 3,
                    maxLines = 5,
                    textStyle = TextStyle(color = TextMain, fontSize = 15.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(Cyan),
                    modifier = Modifier.fillMaxWidth().testTag("task_prompt"),
                )
            }

            error?.let { ErrorText(it) }

            when {
                connected -> Button(
                    onClick = onCreateTask,
                    enabled = actionsEnabled && prompt.isNotBlank() && !loading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan,
                        contentColor = Color(0xFF04121A),
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("task_submit"),
                ) {
                    Text("Görevi başlat", fontWeight = FontWeight.Bold)
                }

                connection.status in SETTINGS_CONNECTION_STATUSES -> {
                    Button(
                        onClick = onOpenSettings,
                        enabled = actionsEnabled,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("Bağlantıyı ayarla")
                    }
                    if (connection.status == GatewayConnectionStatus.UNREACHABLE) {
                        OutlinedButton(
                            onClick = onRetryConnection,
                            enabled = actionsEnabled,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Tekrar dene")
                        }
                    }
                }

                else -> Button(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Bağlantı kontrol ediliyor")
                }
            }
        }
    }
}

@Composable
private fun QuickPrompt(label: String, enabled: Boolean, onQuickPrompt: (String) -> Unit) {
    OutlinedButton(
        onClick = { onQuickPrompt(label) },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun ActiveTaskContent(
    state: MobileTaskUiState,
    actionsEnabled: Boolean,
    onCommand: (String) -> Unit,
    onNewTask: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        state.task?.let { task ->
            TaskStatusCard(
                task = task,
                loading = state.loading,
                actionsEnabled = actionsEnabled,
                onCommand = onCommand,
                onNewTask = onNewTask,
            )
        }
        state.error?.let { error ->
            ErrorText(error, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        TaskTimeline(
            events = state.events,
            taskPrompt = state.task?.prompt,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun TaskStatusCard(
    task: MobileTask,
    loading: Boolean,
    actionsEnabled: Boolean,
    onCommand: (String) -> Unit,
    onNewTask: () -> Unit,
) {
    val terminal = task.status.isTerminal()
    Column(
        Modifier.fillMaxWidth()
            .background(Surface1)
            .border(1.dp, Line)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Durum: ${task.status.userLabel}",
            color = if (terminal) Cyan else TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            task.prompt,
            color = Muted,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (terminal) {
            Button(
                onClick = onNewTask,
                enabled = actionsEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = Color(0xFF04121A),
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Yeni görev", fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        onCommand(if (task.status == MobileTaskStatus.PAUSED) "resume" else "pause")
                    },
                    enabled = actionsEnabled && !loading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(if (task.status == MobileTaskStatus.PAUSED) "Devam et" else "Duraklat")
                }
                OutlinedButton(
                    onClick = { onCommand("cancel") },
                    enabled = actionsEnabled && !loading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("İptal et")
                }
            }
        }
    }
}

@Composable
private fun TaskTimeline(
    events: List<MobileTaskEvent>,
    taskPrompt: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("task_timeline"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
    ) {
        items(events, key = { it.id }) { event ->
            Column(
                Modifier.fillMaxWidth()
                    .border(1.dp, Line)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(event.userLabel, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    event.userSummary(taskPrompt),
                    color = TextMain,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationPanel(
    confirmation: MobileConfirmation,
    actionSummary: String,
    decisionEnabled: Boolean,
    onDecision: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .background(Surface2)
            .border(1.dp, Coral.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("confirmation_panel"),
    ) {
        Text(
            "${confirmation.riskLevel} onayı",
            color = Coral,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            actionSummary,
            color = TextMain,
            fontSize = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onDecision("reject") },
                enabled = decisionEnabled,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("confirmation_reject"),
            ) {
                Text("Reddet")
            }
            Button(
                onClick = { onDecision("approve") },
                enabled = decisionEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = Color(0xFF04121A),
                ),
                modifier = Modifier.weight(1f).height(48.dp).testTag("confirmation_approve"),
            ) {
                Text("Onayla")
            }
        }
    }
}

@Composable
private fun ErrorText(message: String, modifier: Modifier = Modifier) {
    Text(message, color = Coral, fontSize = 13.sp, modifier = modifier.fillMaxWidth())
}

private fun MobileTaskStatus.isTerminal(): Boolean = this in setOf(
    MobileTaskStatus.COMPLETED,
    MobileTaskStatus.FAILED,
    MobileTaskStatus.CANCELLED,
)

private val SETTINGS_CONNECTION_STATUSES = setOf(
    GatewayConnectionStatus.UNKNOWN,
    GatewayConnectionStatus.AUTH_REQUIRED,
    GatewayConnectionStatus.INVALID_URL,
    GatewayConnectionStatus.UNREACHABLE,
)
