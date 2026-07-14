package com.nova.agent.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            ChatEmptyState(Modifier.weight(1f))
        } else {
            val listState = rememberLazyListState()
            val lastMessageContent = messages.lastOrNull()?.content
            LaunchedEffect(messages.size, lastMessageContent, busy) {
                listState.animateScrollToItem(messages.lastIndex)
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
            }
        }
        ChatComposer(busy = busy, onSend = onSend, onStop = onStop)
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
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUser) Cyan.copy(alpha = 0.14f) else Surface1)
                .border(
                    1.dp,
                    if (isUser) Cyan.copy(alpha = 0.22f) else Line,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (message.content.isEmpty() && message.streaming) {
                Text("•••", color = Cyan, fontSize = 15.sp)
            } else {
                Text(message.content, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        if (!isUser && message.route != null) {
            Spacer(Modifier.height(5.dp))
            Text("→ ${message.route}", color = Muted, fontSize = 10.sp)
        }
        if (!isUser && message.content.isNotEmpty() && !message.streaming) {
            Spacer(Modifier.height(5.dp))
            Row {
                MessageAction(Icons.Filled.ContentCopy, "Kopyala") {
                    clipboard.setText(AnnotatedString(message.content))
                }
                if (isLast) {
                    Spacer(Modifier.width(4.dp))
                    MessageAction(Icons.Filled.Refresh, "Yeniden oluştur", onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    busy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).imePadding(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (draft.isEmpty()) Text("NOVA'ya yaz…", color = Muted2, fontSize = 15.sp)
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        val canSend = draft.isNotBlank() && !busy
        val actionDescription = if (busy) "Yanıtı durdur" else "Mesaj gönder"
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (busy) Surface2 else if (canSend) Color.Transparent else Surface2)
                .then(
                    if (canSend) Modifier.background(chatGradient, RoundedCornerShape(14.dp))
                    else Modifier,
                )
                .clickable(enabled = canSend || busy) {
                    if (busy) {
                        onStop()
                    } else {
                        onSend(draft)
                        draft = ""
                    }
                }
                .semantics {
                    contentDescription = actionDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (busy) Coral else if (canSend) Color(0xFF04121A) else Muted2,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MessageAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Muted2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Muted2, fontSize = 11.sp)
    }
}
