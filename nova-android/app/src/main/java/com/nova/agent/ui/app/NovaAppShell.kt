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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.nova.agent.ui.theme.Azure
import com.nova.agent.ui.theme.Bg
import com.nova.agent.ui.theme.Bg2
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Cyan
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Success
import com.nova.agent.ui.theme.TextMain

private data class Destination(
    val mode: Mode,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    Destination(Mode.TASKS, "Görevler", Icons.Filled.PlayArrow),
    Destination(Mode.CHAT, "Sohbet", Icons.Filled.ChatBubbleOutline),
    Destination(Mode.VOICE, "Ses", Icons.Filled.Mic),
)

@Composable
fun NovaAppShell(
    mode: Mode,
    connection: GatewayConnectionUiState,
    onModeChange: (Mode) -> Unit,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Bg,
        topBar = { NovaTopBar(mode, connection, onSettings, onNewChat) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("primary_navigation"),
                containerColor = Bg2,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = mode == destination.mode,
                        onClick = { onModeChange(destination.mode) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF04121A),
                            selectedTextColor = TextMain,
                            indicatorColor = Cyan,
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
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
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
                .background(Brush.linearGradient(listOf(Cyan, Azure))),
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
                        .background(connection.status.tint()),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = connection.message,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (mode == Mode.CHAT) {
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = "Yeni sohbet",
                    tint = Muted,
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Ayarlar",
                tint = Muted,
            )
        }
    }
}

private fun GatewayConnectionStatus.tint(): Color = when (this) {
    GatewayConnectionStatus.READY -> Success
    GatewayConnectionStatus.CHECKING -> Amber
    GatewayConnectionStatus.AUTH_REQUIRED,
    GatewayConnectionStatus.UNREACHABLE,
    GatewayConnectionStatus.INVALID_URL,
    -> Coral
    GatewayConnectionStatus.UNKNOWN -> Muted
}
