package com.nova.agent.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nova.agent.ui.brand.NovaBrandMark
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
import com.nova.agent.ui.theme.Line
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.Success
import com.nova.agent.ui.theme.Surface2
import com.nova.agent.ui.theme.TextMain

private data class Destination(
    val mode: Mode,
    val label: String,
    val icon: ImageVector,
)

/**
 * "İşler" (telefon kontrolü) sekmesi görünsün mü — TEK ANAHTAR.
 *
 * Kapalı, çünkü özellik DONDURULDU ve üç ayrı gerekçe aynı yeri gösteriyor:
 *
 * 1. Ürün kararı: telefon kontrolü (AccessibilityService / mobilerun) ilk
 *    sürüme girmiyor.
 * 2. Play politikası (28 Ocak 2026): AccessibilityService ile özerk eylem
 *    yasak. Sekmenin hızlı komutları ("Ayarlar'ı aç", "Bir uygulamayı aç")
 *    tam olarak bu davranışı tarif ediyor; mağaza incelemesinde doğrudan
 *    ret gerekçesi.
 * 3. Kullanılabilirlik: görevi PC'deki çalışan yürütüyor. Mağazadan indiren
 *    kullanıcının PC'si yok, dolayısıyla dört ana sekmeden biri onun için
 *    hiçbir koşulda çalışmıyor.
 *
 * Kod SİLİNMEDİ, yalnız gezinmeden çıkarıldı: bu satırı `true` yapmak sekmeyi
 * olduğu gibi geri getirir.
 */
const val PHONE_TASKS_TAB_ENABLED = false

/** Ses sekmesi kaldırılmadı: Sohbet üst çubuğundaki mikrofonla açılır. */
private val destinations = listOfNotNull(
    Destination(Mode.KONTROL, "Kontrol", Icons.Filled.Dashboard),
    Destination(Mode.TASKS, "İşler", Icons.Filled.Checklist)
        .takeIf { PHONE_TASKS_TAB_ENABLED },
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
    /** Kısa onay mesajı (bildirim kaydedildi vb.). Null ise gösterilmez. */
    notice: String? = null,
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
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            content()
            notice?.let { message ->
                Text(
                    message,
                    color = TextMain,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("app_notice"),
                )
            }
        }
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
        // Tek marka görseli: Pulse Aperture. Jenerik "parıltı" ikonu kullanılmaz.
        NovaBrandMark(modifier = Modifier.size(36.dp))
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
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (mode == Mode.CHAT || mode == Mode.VOICE) {
            IconButton(onClick = onToggleVoice) {
                if (mode == Mode.VOICE) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Sohbete dön",
                        tint = Muted,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Ses moduna geç",
                        tint = Muted,
                    )
                }
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
