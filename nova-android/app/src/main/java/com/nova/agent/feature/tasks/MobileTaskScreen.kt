package com.nova.agent.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onPromptChange: (String) -> Unit,
    onCreateTask: () -> Unit,
    onCommand: (String) -> Unit,
    onDecision: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Bg)) {
        TaskComposer(
            prompt = state.prompt,
            loading = state.loading,
            onPromptChange = onPromptChange,
            onCreateTask = onCreateTask,
        )
        state.task?.let { task ->
            TaskControls(task, state.loading, onCommand)
        }
        state.error?.let { error ->
            Text(
                error,
                color = Coral,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        TaskTimeline(
            events = state.events,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        state.pendingConfirmation?.let { confirmation ->
            ConfirmationBand(confirmation, onDecision)
        }
    }
}

@Composable
private fun TaskComposer(
    prompt: String,
    loading: Boolean,
    onPromptChange: (String) -> Unit,
    onCreateTask: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(8.dp)).background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (prompt.isEmpty()) Text("Telefon görevi", color = Muted2, fontSize = 15.sp)
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                enabled = !loading,
                singleLine = true,
                textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().testTag("task_prompt"),
            )
        }
        Spacer(Modifier.width(10.dp))
        IconButton(
            onClick = onCreateTask,
            enabled = prompt.isNotBlank() && !loading,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Cyan).testTag("task_submit"),
        ) {
            Icon(Icons.Filled.Send, "Görevi başlat", tint = Color(0xFF04121A))
        }
    }
}

@Composable
private fun TaskControls(task: MobileTask, loading: Boolean, onCommand: (String) -> Unit) {
    val terminal = task.status in setOf(
        MobileTaskStatus.COMPLETED,
        MobileTaskStatus.FAILED,
        MobileTaskStatus.CANCELLED,
    )
    Row(
        Modifier.fillMaxWidth().border(width = 1.dp, color = Line).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(task.status.name.replace('_', ' '), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (!terminal) {
            if (task.status == MobileTaskStatus.PAUSED) {
                TaskIconAction(Icons.Filled.PlayArrow, "Devam et", loading) { onCommand("resume") }
            } else {
                TaskIconAction(Icons.Filled.Pause, "Duraklat", loading) { onCommand("pause") }
            }
            Spacer(Modifier.width(8.dp))
            TaskIconAction(Icons.Filled.Stop, "İptal et", loading, Coral) { onCommand("cancel") }
        }
    }
}

@Composable
private fun TaskIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    loading: Boolean,
    tint: Color = Cyan,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Surface2),
    ) {
        Icon(icon, contentDescription, tint = tint)
    }
}

@Composable
private fun TaskTimeline(events: List<MobileTaskEvent>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.testTag("task_timeline"),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(events, key = { it.id }) { event ->
            Column(
                Modifier.fillMaxWidth().border(width = 1.dp, color = Line).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(event.type, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    event.summary,
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
private fun ConfirmationBand(confirmation: MobileConfirmation, onDecision: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Surface2).border(width = 1.dp, color = Coral.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 14.dp).testTag("confirmation_panel"),
    ) {
        Text("${confirmation.riskLevel} onayı", color = Coral, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(confirmation.actionSummary, color = TextMain, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onDecision("reject") },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextMain),
                modifier = Modifier.weight(1f).testTag("confirmation_reject"),
            ) { Text("Reddet") }
            Button(
                onClick = { onDecision("approve") },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color(0xFF04121A)),
                modifier = Modifier.weight(1f).testTag("confirmation_approve"),
            ) { Text("Onayla") }
        }
    }
}
