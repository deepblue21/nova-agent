package com.nova.agent.feature.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.agent.data.VoiceState
import com.nova.agent.ui.Orb
import com.nova.agent.ui.theme.Cyan
import com.nova.agent.ui.theme.LineBright
import com.nova.agent.ui.theme.Muted
import com.nova.agent.ui.theme.TextMain

@Composable
fun VoiceScreen(
    state: VoiceState,
    subtitle: String,
    level: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val animatedLevel by animateFloatAsState(level, tween(120), label = "voice_level")
    val stateLabel = when (state) {
        VoiceState.IDLE -> "Hazır"
        VoiceState.LISTENING -> "Dinliyorum"
        VoiceState.THINKING -> "Düşünüyorum"
        VoiceState.SPEAKING -> "Konuşuyorum"
    }
    val actionDescription = when (state) {
        VoiceState.LISTENING -> "Dinlemeyi durdur"
        VoiceState.SPEAKING -> "Konuşmayı durdur"
        VoiceState.IDLE,
        VoiceState.THINKING,
        -> "Dinlemeyi başlat"
    }
    val stopsCurrentAction = state == VoiceState.LISTENING || state == VoiceState.SPEAKING

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Orb(
            level = animatedLevel,
            modifier = Modifier.widthIn(max = 280.dp).aspectRatio(1f),
        )
        Spacer(Modifier.height(26.dp))
        Text(stateLabel, color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(34.dp))
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (state == VoiceState.LISTENING) Cyan
                    else Cyan.copy(alpha = 0.10f),
                )
                .then(
                    if (state == VoiceState.LISTENING) Modifier
                    else Modifier.border(1.dp, LineBright, CircleShape),
                )
                .clickable {
                    if (stopsCurrentAction) onStop() else onStart()
                }
                .semantics {
                    contentDescription = actionDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (stopsCurrentAction) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (state == VoiceState.LISTENING) Color(0xFF04121A) else Cyan,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
