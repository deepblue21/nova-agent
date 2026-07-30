package com.nova.agent.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.ToolStep
import com.nova.agent.ui.theme.Ember
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.TextMain

/**
 * Ajan araç izi — web'deki `ToolTrace` bileşeninin Android karşılığı.
 * Hangi aracın hangi sorguyla çalıştığını ve kullanılan kaynakları gösterir;
 * "model bir şey uydurdu mu, gerçekten arattı mı" ayrımı kullanıcıda kalsın diye.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovaToolTrace(tools: List<ToolStep>, modifier: Modifier = Modifier) {
    if (tools.isEmpty()) return

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Ember.copy(alpha = 0.05f))
            .border(1.dp, Ember.copy(alpha = 0.22f), RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp)
            .testTag("nova_tool_trace"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "Araç kullanıldı",
            color = Ember,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )

        for (step in tools) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    toolLabel(step.name),
                    color = TextMain,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (step.query.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        step.query,
                        color = Muted2,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (step.sources.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    step.sources.take(4).forEach { src ->
                        Text(
                            buildString {
                                if (src.index > 0) append("[${src.index}] ")
                                append(src.title)
                            },
                            color = Muted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.dp, Line, RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Araç kimliği → kullanıcı etiketi. Web `TOOL_LABEL` ile aynı sözlük. */
internal fun toolLabel(name: String): String = when (name) {
    "web_search" -> "Web araması"
    "weather_forecast" -> "Hava tahmini"
    "calculator" -> "Hesaplama"
    "current_time" -> "Saat"
    "code_run" -> "Kod sandbox"
    "fetch_url" -> "Web sayfası"
    "subtask" -> "Alt-ajan"
    "synthesis" -> "Sentez"
    "doc_search" -> "Belge araması"
    else -> if (name.startsWith("mcp__")) {
        name.removePrefix("mcp__").replaceFirst("__", " · ")
    } else {
        name
    }
}
