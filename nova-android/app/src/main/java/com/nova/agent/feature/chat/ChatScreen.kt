package com.nova.agent.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.ChatMessage
import com.nova.agent.ui.theme.Azure
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Cyan
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.Surface2
import com.nova.agent.ui.theme.TextMain

private val chatGradient = Brush.linearGradient(listOf(Cyan, Azure))

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    targetLabel: String = "PC/Gateway",
    modelLabel: String = "auto",
    pendingFallback: String? = null,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onApproveFallback: () -> Unit = {},
    onRejectFallback: () -> Unit = {},
    onOpenControl: () -> Unit = {},
    onOpenModels: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TargetChipsRow(
            targetLabel = targetLabel,
            modelLabel = modelLabel,
            onOpenControl = onOpenControl,
            onOpenModels = onOpenModels,
        )
        if (messages.isEmpty()) {
            ChatEmptyState(Modifier.weight(1f))
        } else {
            val listState = rememberLazyListState()
            val lastMessageContent = messages.lastOrNull()?.content
            LaunchedEffect(messages.size, lastMessageContent, busy) {
                listState.animateScrollToItem(messages.size)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(messages) { index, message ->
                    ChatMessageRow(
                        message = message,
                        isLast = index == messages.lastIndex,
                        onRegenerate = onRegenerate,
                    )
                }
                item {
                    Spacer(
                        Modifier.fillMaxWidth().height(1.dp).testTag("chat_stream_tail"),
                    )
                }
            }
        }
        if (pendingFallback != null) {
            FallbackConsentCard(
                reason = pendingFallback,
                onApprove = onApproveFallback,
                onReject = onRejectFallback,
            )
        }
        ChatComposer(busy = busy, onSend = onSend, onStop = onStop)
    }
}

/** Hedef/model bilgi çipleri; dokununca ilgili ekrana götürür. */
@Composable
private fun TargetChipsRow(
    targetLabel: String,
    modelLabel: String,
    onOpenControl: () -> Unit,
    onOpenModels: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfoChip(label = targetLabel, description = "Yürütme hedefi: $targetLabel", onClick = onOpenControl)
        InfoChip(label = modelLabel, description = "Model: $modelLabel", onClick = onOpenModels)
    }
}

@Composable
private fun InfoChip(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * İzinli devir kartı: yerel model yanıt veremediğinde istem yalnız
 * kullanıcı onayıyla PC'deki Gateway'e gönderilir. Sessiz devir yok.
 */
@Composable
private fun FallbackConsentCard(
    reason: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Coral.copy(alpha = 0.10f))
            .border(1.dp, Coral.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("fallback_consent"),
    ) {
        Text("Telefon modeli yanıt veremedi", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(reason, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Onaylarsan bu sohbetin istemi PC'deki Gateway'e gönderilecek.",
            color = Muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .clickable(onClick = onReject)
                    .semantics {
                        contentDescription = "Vazgeç"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Vazgeç", color = Muted, fontSize = 13.sp)
            }
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(chatGradient)
                    .clickable(onClick = onApprove)
                    .semantics {
                        contentDescription = "PC'ye gönder"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("PC'ye gönder", color = Color(0xFF04121A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(chatGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Merhaba, ben NOVA",
                color = TextMain,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Kişisel ajanın. Bir şey sor ya da bir görevi otomatikleştir.",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isLast: Boolean,
    onRegenerate: () -> Unit,
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
       