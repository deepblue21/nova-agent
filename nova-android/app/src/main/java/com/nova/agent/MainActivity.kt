package com.nova.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.EFFORTS
import com.nova.agent.data.MODELS
import com.nova.agent.data.Mode
import com.nova.agent.data.VoiceState
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskViewModel
import com.nova.agent.ui.Orb
import com.nova.agent.ui.theme.Azure
import com.nova.agent.ui.theme.Bg
import com.nova.agent.ui.theme.Bg2
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Cyan
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.LineBright
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Muted2
import com.nova.agent.ui.theme.Surface1
import com.nova.agent.ui.theme.Surface2
import com.nova.agent.ui.theme.TextMain
import com.nova.agent.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    private val vm: NovaViewModel by viewModels()
    private val taskVm: MobileTaskViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovaTheme { NovaApp(vm, taskVm) } }
    }
}

private val gradient = Brush.linearGradient(listOf(Cyan, Azure))

@Composable
private fun NovaApp(vm: NovaViewModel, taskVm: MobileTaskViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var micGranted by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) vm.startListening()
    }
    fun requestMicThenListen() {
        if (micGranted) vm.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(vm, onNew = { vm.newChat() }, onSettings = { showSettings = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.mode) {
                    Mode.VOICE -> VoiceView(vm) { requestMicThenListen() }
                    Mode.CHAT -> ChatView(vm)
                    Mode.TASKS -> MobileTaskWorkspace(taskVm)
                }
            }
            Dock(vm)
        }
        if (showSettings) SettingsOverlay(vm) { showSettings = false }
    }
}

@Composable
private fun MobileTaskWorkspace(vm: MobileTaskViewModel) {
    MobileTaskScreen(
        state = vm.state,
        onPromptChange = vm::updatePrompt,
        onCreateTask = vm::createTask,
        onCommand = { command ->
            when (command) {
                "pause" -> vm.pause()
                "resume" -> vm.resume()
                "cancel" -> vm.cancel()
            }
        },
        onDecision = { decision ->
            if (decision == "approve") vm.approve() else if (decision == "reject") vm.reject()
        },
    )
}

/* ----------------------------- Top bar ----------------------------- */
@Composable
private fun TopBar(vm: NovaViewModel, onNew: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(Icons.Filled.ChatBubbleOutline, "Yeni sohbet", onNew)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(gradient),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("NOVA", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(
                "${vm.currentModelName()} · ${vm.currentEffortName()}" + if (vm.settings.reasoning) " · düşünme" else "",
                color = Muted, fontSize = 11.sp
            )
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.settings.token.isNotBlank()) Cyan else Muted2))
        Spacer(Modifier.width(10.dp))
        IconBtn(Icons.Filled.Settings, "Ayarlar", onSettings)
    }
}

/* ----------------------------- Voice ----------------------------- */
@Composable
private fun VoiceView(vm: NovaViewModel, onMic: () -> Unit) {
    val level by animateFloatAsState(vm.level, tween(120), label = "lvl")
    val label = when (vm.voiceState) {
        VoiceState.IDLE -> "Hazır"
        VoiceState.LISTENING -> "Dinliyorum"
        VoiceState.THINKING -> "Düşünüyorum"
        VoiceState.SPEAKING -> "Konuşuyorum"
    }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Orb(level = level, modifier = Modifier.size(300.dp))
        Spacer(Modifier.height(26.dp))
        Text(label, color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(vm.voiceSub, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(34.dp))
        val listening = vm.voiceState == VoiceState.LISTENING
        val speaking = vm.voiceState == VoiceState.SPEAKING
        Box(
            Modifier.size(76.dp).clip(CircleShape)
                .background(if (listening) Cyan.copy(alpha = 1f) else Cyan.copy(alpha = 0.10f))
                .then(if (listening) Modifier else Modifier.border(1.dp, LineBright, CircleShape))
                .clickable {
                    when {
                        listening || speaking -> vm.stopListeningOrSpeaking()
                        else -> onMic()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (listening || speaking) Icons.Filled.Stop else Icons.Filled.Mic,
                null,
                tint = if (listening) Color(0xFF04121A) else Cyan,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/* ----------------------------- Chat ----------------------------- */
@Composable
private fun ChatView(vm: NovaViewModel) {
    Column(Modifier.fillMaxSize()) {
        if (vm.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(gradient),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.height(16.dp))
                    Text("Merhaba, ben NOVA", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Kişisel ajanın. Bir şey sor ya da bir görevi otomatikleştir.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(vm.messages.size, vm.busy) {
                if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(vm.messages) { idx, m ->
                    MessageRow(
                        role = m.role,
                        content = m.content,
                        route = m.route,
                        streaming = m.streaming,
                        isLast = idx == vm.messages.lastIndex,
                        onRegenerate = { vm.regenerate() }
                    )
                }
            }
        }
        Composer(vm)
    }
}

@Composable
private fun MessageRow(
    role: String,
    content: String,
    route: String?,
    streaming: Boolean,
    isLast: Boolean,
    onRegenerate: () -> Unit
) {
    val isUser = role == "user"
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUser) Cyan.copy(alpha = 0.14f) else Surface1)
                .border(1.dp, if (isUser) Cyan.copy(alpha = 0.22f) else Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (content.isEmpty() && streaming) {
                Text("•••", color = Cyan, fontSize = 15.sp)
            } else {
                Text(content, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        if (!isUser && route != null) {
            Spacer(Modifier.height(5.dp))
            Text("→ $route", color = Muted, fontSize = 10.sp)
        }
        if (!isUser && content.isNotEmpty() && !streaming) {
            Spacer(Modifier.height(5.dp))
            Row {
                MsgAction(Icons.Filled.ContentCopy, "Kopyala") { clipboard.setText(AnnotatedString(content)) }
                if (isLast) { Spacer(Modifier.width(4.dp)); MsgAction(Icons.Filled.Refresh, "Yeniden", onRegenerate) }
            }
        }
    }
}

@Composable
private fun Composer(vm: NovaViewModel) {
    var input by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).imePadding(),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (input.isEmpty()) Text("NOVA'ya yaz…", color = Muted2, fontSize = 15.sp)
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(10.dp))
        val canSend = input.isNotBlank() && !vm.busy
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(if (vm.busy) Surface2 else if (canSend) Color.Transparent else Surface2)
                .then(if (canSend && !vm.busy) Modifier.background(gradient, RoundedCornerShape(14.dp)) else Modifier)
                .clickable(enabled = canSend || vm.busy) {
                    if (vm.busy) vm.stop()
                    else { vm.send(input); input = "" }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (vm.busy) Icons.Filled.Stop else Icons.Filled.Send,
                null,
                tint = if (vm.busy) Coral else if (canSend) Color(0xFF04121A) else Muted2,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/* ----------------------------- Dock ----------------------------- */
@Composable
private fun Dock(vm: NovaViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp).navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // mode
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTab("Sesli", Icons.Filled.Mic, vm.mode == Mode.VOICE) { vm.mode = Mode.VOICE }
            ModeTab("Sohbet", Icons.Filled.ChatBubbleOutline, vm.mode == Mode.CHAT) { vm.mode = Mode.CHAT }
            ModeTab("Görevler", Icons.Filled.PlayArrow, vm.mode == Mode.TASKS) { vm.mode = Mode.TASKS }
        }
        ModelSelector(vm)
        EffortSegmented(vm)
        ReasoningToggle(vm)
    }
}

@Composable
private fun ModeTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .then(if (on) Modifier.background(Cyan.copy(alpha = 0.16f)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) TextMain else Muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (on) TextMain else Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ModelSelector(vm: NovaViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
                .clickable { open = true }.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("MODEL", color = Muted2, fontSize = 9.sp)
                Text(vm.currentModelName(), color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MODELS.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(m.name, color = TextMain, fontSize = 14.sp)
                            Text("${m.group} · ${m.model}", color = Muted, fontSize = 11.sp)
                        }
                    },
                    onClick = { vm.setModel(m.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun EffortSegmented(vm: NovaViewModel) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        EFFORTS.forEach { e ->
            val on = vm.settings.effort == e.id
            Box(
                Modifier.clip(RoundedCornerShape(9.dp))
                    .then(if (on) Modifier.background(gradient, RoundedCornerShape(9.dp)) else Modifier)
                    .clickable { vm.setEffort(e.id) }.padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text(e.name, color = if (on) Color(0xFF04121A) else Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ReasoningToggle(vm: NovaViewModel) {
    val on = vm.settings.reasoning
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable { vm.toggleReasoning() }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(30.dp).height(17.dp).clip(RoundedCornerShape(100.dp))
                .background(if (on) Cyan.copy(alpha = 0.35f) else Surface2),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(2.dp).size(13.dp).clip(CircleShape).background(if (on) Cyan else Muted))
        }
        Spacer(Modifier.width(8.dp))
        Text("Düşünme", color = if (on) Cyan else Muted, fontSize = 12.sp)
    }
}

/* ----------------------------- Settings ----------------------------- */
@Composable
private fun SettingsOverlay(vm: NovaViewModel, onClose: () -> Unit) {
    var baseUrl by remember { mutableStateOf(vm.settings.baseUrl) }
    var token by remember { mutableStateOf(vm.settings.token) }
    Box(
        Modifier.fillMaxSize().background(Color(0xB3030408)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(20.dp)
                .clip(RoundedCornerShape(22.dp)).background(Bg2).border(1.dp, Line, RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}.padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, null, tint = Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Gateway Bağlantısı", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text("API anahtarları gateway'de durur; burada yalnız gateway adresi ve token girilir.", color = Muted, fontSize = 13.sp)

            Spacer(Modifier.height(20.dp))
            FieldLabel("Gateway Base URL")
            Field(baseUrl, { baseUrl = it }, "http://10.0.2.2:8088/v1")
            Spacer(Modifier.height(12.dp))
            FieldLabel("Token (GATEWAY_TOKEN)")
            Field(token, { token = it }, "••••••••", password = true)

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(13.dp)).background(gradient)
                    .clickable { vm.saveConnection(baseUrl, token); onClose() },
                contentAlignment = Alignment.Center
            ) { Text("Kaydet", color = Color(0xFF04121A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Muted2, fontSize = 11.sp, modifier = Modifier.padding(bottom = 7.dp))
}

@Composable
private fun Field(value: String, onValueChange: (String) -> Unit, placeholder: String, password: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Bg)
            .border(1.dp, Line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(placeholder, color = Muted2, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 13.sp),
            cursorBrush = SolidColor(Cyan),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/* ----------------------------- shared ----------------------------- */
@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(11.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Muted, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun MsgAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Muted2, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Muted2, fontSize = 11.sp)
    }
}
