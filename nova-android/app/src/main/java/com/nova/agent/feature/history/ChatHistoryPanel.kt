package com.nova.agent.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.ConversationSummary
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.TextMain
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatHistoryPanel(
    summaries: List<ConversationSummary>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sohbet Geçmişi", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Geçmişi kapat")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("history_search"),
                label = { Text("Sohbetlerde ara") },
                singleLine = true,
            )

            if (summaries.isEmpty()) {
                Text(
                    if (query.isBlank()) "Henüz kayıtlı sohbet yok." else "Eşleşen sohbet bulunamadı.",
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().testTag("history_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(summaries, key = { it.id }) { summary ->
                        HistoryRow(
                            summary = summary,
                            onOpen = { onOpen(summary.id) },
                            onDelete = { onDelete(summary.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    summary: ConversationSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp)
            .testTag("history_row_${summary.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                summary.title,
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.snippet.isNotBlank()) {
                Text(
                    summary.snippet,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${formatDate(summary.updatedAt)} · ${summary.messageCount} mesaj",
                color = Muted2,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = "Sohbeti sil" },
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatDate(epochMs: Long): String {
    if (epochMs <= 0) return ""
    return SimpleDateFormat("d MMM yyyy HH:mm", Locale("tr", "TR")).format(Date(epochMs))
}
