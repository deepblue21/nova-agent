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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.ChatMessage
import com.nova.agent.data.ContentReport
import com.nova.agent.data.ContentReportReason
import com.nova.agent.data.FallbackKind
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.Surface2
import com.nova.agent.ui.theme.TextMain
import com.nova.agent.ui.theme.accentBrush
import com.nova.agent.ui.brand.NovaBrandMark
import com.nova.agent.ui.components.rememberClipboardCopy
import com.nova.agent.ui.brand.NovaThinkingIndicator

internal fun shouldShowNovaThinkingIndicator(message: ChatMessage): Boolean =
    message.role == "assistant" && message.content.isEmpty() && message.streaming

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    targetLabel: String = "PC/Gateway",
    modelLabel: String = "auto",
    pendingFallback: String? = null,
    fallbackAllowsGateway: Boolean = true,
    fallbackKind: FallbackKind = FallbackKind.LOCAL_ERROR,
    /** PC gerçekten ulaşılabilir mi. False ise "PC'ye gönder" ÖNERİLMEZ (U1). */
    gatewayReady: Boolean = false,
    showAgentHandoff: Boolean = false,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onApproveFallback: () -> Unit = {},
    onRejectFallback: () -> Unit = {},
    onOpenControl: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onHandoffToAgent: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    /** Play B6: sakıncalı yapay zekâ çıktısını bildir. */
    onReport: (ChatMessage, ContentReportReason, String) -> Unit = { _, _, _ -> },
) {
    Column(Modifier.fillMaxSize()) {
        TargetChipsRow(
            targetLabel = targetLabel,
            modelLabel = modelLabel,
            showAgentHandoff = showAgentHandoff && messages.any { it.role == "user" } && !busy,
            onOpenControl = onOpenControl,
            onOpenModels = onOpenModels,
            onHandoffToAgent = onHandoffToAgent,
            onOpenHistory = onOpenHistory,
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
                        onReport = { reason, note -> onReport(message, reason, note) },
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
                allowGateway = fallbackAllowsGateway,
                gatewayReady = gatewayReady,
                kind = fallbackKind,
                onApprove = onApproveFallback,
                onReject = onRejectFallback,
                onOpenModels = onOpenModels,
            )
        }
        ChatComposer(busy = busy, onSend = onSend, onStop = onStop)
    }
}

/**
 * Hedef/model bilgi çipleri; dokununca ilgili ekrana götürür.
 * "PC ajanına devret" yalnız devir mümkünken görünür (Çevrimdışı modda asla);
 * dokunuş = açık rıza, son soru tüm bağlamla PC ajanında yeniden yanıtlanır.
 */
@Composable
private fun TargetChipsRow(
    targetLabel: String,
    modelLabel: String,
    showAgentHandoff: Boolean,
    onOpenControl: () -> Unit,
    onOpenModels: () -> Unit,
    onHandoffToAgent: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Bu satırda ÜÇ FARKLI cins vardı ve üçü de birebir aynı görünüyordu:
        // durum göstergesi (hedef, model), gezinme (Geçmiş) ve GERÇEK BİR EYLEM
        // ("PC ajanına devret" — son soruyu tüm bağlamıyla PC'ye gönderir).
        // Sohbeti cihaz dışına çıkaran bir eylemin pasif bir durum etiketiyle
        // aynı görünmesi kabul edilemez: kullanıcı ne yaptığını göremeden
        // dokunabilir. Eylemler artık vurgulu, durumlar sönük.
        InfoChip(label = targetLabel, description = "Yürütme hedefi: $targetLabel", onClick = onOpenControl)
        InfoChip(label = modelLabel, description = "Model: $modelLabel", onClick = onOpenModels)
        InfoChip(
            label = "Geçmiş",
            description = "Sohbet geçmişini aç",
            action = true,
            onClick = onOpenHistory,
        )
        if (showAgentHandoff) {
            InfoChip(
                label = "PC ajanına devret",
                description = "Son soruyu tüm bağlamla PC'deki ajana gönder",
                action = true,
                onClick = onHandoffToAgent,
            )
        }
    }
}

/**
 * @param action true ise çip bir EYLEM yapar (bir şey gönderir/açar), false ise
 * bir durumu gösterir ve dokununca yalnız ilgili ekrana götürür. İkisi aynı
 * görünmemeli.
 */
@Composable
private fun InfoChip(
    label: String,
    description: String,
    action: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (action) accent.copy(alpha = 0.14f) else Surface1)
            .border(1.dp, if (action) accent.copy(alpha = 0.55f) else Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (action) accent else Muted,
            fontSize = 11.sp,
            fontWeight = if (action) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * Yerel model yanıt veremediğinde gösterilen kart. [allowGateway] true ise
 * istem yalnız kullanıcı onayıyla PC'deki Gateway'e gönderilir; Çevrimdışı
 * modda devir tamamen kapalıdır. Sessiz devir hiçbir modda yok.
 */
@Composable
private fun FallbackConsentCard(
    reason: String,
    allowGateway: Boolean,
    gatewayReady: Boolean,
    kind: FallbackKind,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val missingModel = kind == FallbackKind.NO_LOCAL_MODEL
    // PC'ye devir yalnızca politika izin veriyorsa VE PC gerçekten ulaşılabilirse
    // önerilir. Eskiden yalnız politikaya bakılıyordu: varsayılan kurulumda
    // gateway adresi BOŞ olduğu için kart, basıldığında kesin başarısız olacak
    // bir eylemi tek çıkış yolu olarak sunuyordu.
    val canSendToPc = allowGateway && gatewayReady
    val gradient = accentBrush()
    val onAccent = MaterialTheme.colorScheme.onPrimary
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
        Text(
            if (missingModel) "Telefonda kurulu model yok" else "Telefon modeli yanıt veremedi",
            color = TextMain,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(reason, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                canSendToPc -> "Onaylarsan bu sohbetin istemi PC'deki Gateway'e gönderilecek."
                !allowGateway ->
                    "Çevrimdışı mod: istem cihaz dışına gönderilmez. " +
                        "Modeller sekmesinden bir model indirebilirsin."
                else ->
                    "PC bağlantısı kurulmadığı için şimdilik PC'ye de gönderilemez. " +
                        "Ayarlar'dan PC'yi eşleyebilir ya da telefona model indirebilirsin."
            },
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
                        contentDescription = if (canSendToPc) "Vazgeç" else "Anladım"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (canSendToPc) "Vazgeç" else "Anladım", color = Muted, fontSize = 13.sp)
            }
            // Model yoksa asıl çözüm model indirmektir; kart eskiden bu eylemi
            // hiç sunmuyordu ve kullanıcıyı çıkışsız bırakıyordu.
            if (missingModel) {
                Box(
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (canSendToPc) Modifier.border(1.dp, Line, RoundedCornerShape(12.dp)) else Modifier.background(gradient))
                        .clickable(onClick = onOpenModels)
                        .semantics {
                            contentDescription = "Modelleri aç"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Modelleri aç",
                        color = if (canSendToPc) Muted else onAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (canSendToPc) {
                Box(
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(gradient)
                        .clickable(onClick = onApprove)
                        .semantics {
                            contentDescription = "PC'ye gönder"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("PC'ye gönder", color = onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
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
            // Tek marka görseli: Pulse Aperture.
            NovaBrandMark(modifier = Modifier.size(56.dp))
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
    onReport: (ContentReportReason, String) -> Unit,
) {
    val isUser = message.role == "user"
    val copyToClipboard = rememberClipboardCopy()
    var reporting by remember(message) { mutableStateOf(false) }

    if (reporting) {
        ReportContentDialog(
            excerpt = ContentReport.excerptOf(message.content),
            onDismiss = { reporting = false },
            onSubmit = { reason, note ->
                reporting = false
                onReport(reason, note)
            },
        )
    }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Araç izi balonun ÜSTÜNDE: yanıt gelmeden önce hangi aracın
        // çalıştığı görünsün, sonradan açıklama gibi durmasın.
        if (!isUser && message.tools.isNotEmpty()) {
            NovaToolTrace(message.tools, Modifier.widthIn(max = 320.dp))
            Spacer(Modifier.height(6.dp))
        }
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUser) accent.copy(alpha = 0.14f) else Surface1)
                .border(
                    1.dp,
                    if (isUser) accent.copy(alpha = 0.22f) else Line,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (!isUser && message.thoughts.isNotBlank()) {
                Text("Düşünme", color = Muted2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(message.thoughts, color = Muted2, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(6.dp))
            }
            when {
                shouldShowNovaThinkingIndicator(message) ->
                    NovaThinkingIndicator()

                isUser ->
                    Text(message.content, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)

                else -> AssistantBody(message.content)
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
                    copyToClipboard(message.content)
                }
                // Play B6: bildirim eylemi ÜRETİLEN İÇERİĞİN yanında durmalı.
                // Ayarlar'a gömülü bir form "içeriği bildir" değil, "bir yerde
                // şikâyet et" olurdu; kullanıcı hangi yanıtı bildirdiğini de
                // seçemezdi.
                Spacer(Modifier.width(4.dp))
                MessageAction(Icons.Filled.Flag, "Bu yanıtı bildir") { reporting = true }
                if (isLast) {
                    Spacer(Modifier.width(4.dp))
                    MessageAction(Icons.Filled.Refresh, "Yeniden oluştur", onRegenerate)
                }
            }
        }
    }
}

/**
 * Bildirim kutusu — Play B6.
 *
 * Akış CİHAZDA KAPANIR: sebep seçilir, isteğe bağlı not yazılır, kaydedilir.
 * Politika "uygulamadan çıkmadan bildirebilmeli" diyor; e-posta uygulamasına
 * atarak bitirmek bu şartı karşılamaz.
 *
 * Alıntı kullanıcıya GÖSTERİLİR: neyi bildirdiğini görmeden onaylamasını
 * istemek, "ne paylaştığını gör" ilkesinin ihlali olurdu.
 */
@Composable
private fun ReportContentDialog(
    excerpt: String,
    onDismiss: () -> Unit,
    onSubmit: (ContentReportReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf<ContentReportReason?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bu yanıtı bildir", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Sorunun ne olduğunu seç. Bildirim telefonunda kalır; " +
                        "istersen Ayarlar'dan geliştiriciye gönderirsin.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                ContentReportReason.entries.forEach { option ->
                    val selected = reason == option
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Surface1)
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else Line,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { reason = option }
                            .semantics {
                                contentDescription = option.label
                                role = Role.RadioButton
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            option.label,
                            color = if (selected) MaterialTheme.colorScheme.primary else TextMain,
                            fontSize = 13.sp,
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    placeholder = { Text("İstersen kısa bir not ekle", fontSize = 12.sp) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("report_note"),
                )
                if (excerpt.isNotBlank()) {
                    Text("Bildirilecek alıntı:", color = Muted2, fontSize = 11.sp)
                    Text(
                        excerpt,
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 4,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let { onSubmit(it, note.trim()) } },
                enabled = reason != null,
                modifier = Modifier.testTag("report_submit"),
            ) {
                Text("Bildir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç", color = Muted) }
        },
    )
}

/** Asistan gövdesi: metin + her biri ayrı kartta, blok başına Kopyala'lı kod blokları. */
@Composable
private fun AssistantBody(content: String) {
    val copyToClipboard = rememberClipboardCopy()
    val blocks = ChatMarkdown.splitBlocks(content)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ChatMarkdown.Block.Text ->
                    Text(block.content, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)

                is ChatMarkdown.Block.Code -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF04060A))
                            .border(1.dp, Line, RoundedCornerShape(10.dp)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Surface2)
                                .padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                block.language.ifBlank { "kod" },
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            MessageAction(Icons.Filled.ContentCopy, "Kopyala") {
                                copyToClipboard(block.content)
                            }
                        }
                        Text(
                            block.content,
                            color = TextMain,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
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
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val gradient = accentBrush()
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
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().testTag("chat_input"),
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
                    if (canSend) Modifier.background(gradient, RoundedCornerShape(14.dp))
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
                tint = if (busy) Coral else if (canSend) onAccent else Muted2,
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
