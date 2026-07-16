package com.nova.agent.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.Mode
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.Amber
import com.nova.agent.ui.theme.Bg
import com.nova.agent.ui.theme.Bg2
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Success
import com.nova.agent.ui.theme.TextMain

private data class Destination(
    val mode: Mode,
    val label: String,
    val icon: ImageVector,
)

/** Ses sekmesi kaldırılmadı: Sohbet üst çubuğundaki mikrofonla açılır. */
private val destinations = listOf(
    Destination(Mode.KONTROL, "Kontrol", Icons.Filled.Dashboard),
    Destination(Mode.TASKS, "İşler", Icons.Filled.Checklist),
    Destination(Mode.CHAT, "Sohbet", Icons.Filled.ChatBubbleOutline),
    Destination(Mode.MODELLER, "Modeller", Icons.Filled.ViewInAr),
)

@Composable
fun NovaAppShell(
    mode: Mode,
    connection: GatewayConnectionUiState,
    onModeChange: (Mode) -> Unit,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    localSubtitle: String? = null,
    onToggleVoice: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            NovaTopBar(mode, connection, localSubtitle, onSettings, onNewChat, onToggleVoice)
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("primary_navigation"),
                containerColor = Bg2,
            ) {
                val selectedMode = if (mode == Mode.VOICE) Mode.CHAT else mode
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedMode == destination.mode,
                        onClick = { onModeChange(destination.mode) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF04121A),
                            selectedTextColor = TextMain,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@Composable
private fun NovaTopBar(
    mode: Mode,
    connection: GatewayConnectionUiState,
    localSubtitle: String?,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    onToggleVoice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "NOVA",
                color = TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (localSubtitle != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                connection.status.tint()
                            },
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = localSubtitle ?: connection.message,
                    color = Mute