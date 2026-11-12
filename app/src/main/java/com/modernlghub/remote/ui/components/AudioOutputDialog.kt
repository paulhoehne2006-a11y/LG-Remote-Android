package com.modernlghub.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.modernlghub.remote.data.AudioStatus
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.DenonGreen
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite

@Composable
fun AudioOutputDialog(
    currentAudio: AudioStatus,
    onSelectOutput: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceCard)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audioausgabe",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Der Denon AVR-1803 empfängt digitalen Ton über das optische Kabel (festes Signal). Die Lautstärke wird direkt am Verstärker geregelt oder durch Umschalten auf die internen TV-Lautsprecher.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                AudioOptionItem(
                    title = "Denon AVR-1803",
                    subtitle = "Optischer Digitalausgang",
                    icon = "🔊",
                    isSelected = currentAudio.isDenonOptical,
                    onClick = {
                        onSelectOutput("external_optical")
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                AudioOptionItem(
                    title = "TV-Lautsprecher",
                    subtitle = "Intern (Lautstärke direkt über Handy steuerbar)",
                    icon = "🔈",
                    isSelected = currentAudio.isTvSpeaker,
                    onClick = {
                        onSelectOutput("tv_speaker")
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                AudioOptionItem(
                    title = "TV & Denon AVR gleichzeitig",
                    subtitle = "Optisch + TV-Lautsprecher parallel",
                    icon = "🔊",
                    isSelected = currentAudio.soundOutput == "tv_speaker_and_external_optical",
                    onClick = {
                        onSelectOutput("tv_speaker_and_external_optical")
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                AudioOptionItem(
                    title = "Bluetooth-Audio",
                    subtitle = "Kopfhörer / Drahtlos",
                    icon = "🎧",
                    isSelected = currentAudio.soundOutput.contains("bt"),
                    onClick = {
                        onSelectOutput("bt_soundbar")
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun AudioOptionItem(
    title: String,
    subtitle: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) SurfaceCardVariant else SurfaceCardVariant.copy(alpha = 0.5f))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) DenonGreen else CardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = if (isSelected) DenonGreen else TextMuted,
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Aktiv",
                    tint = DenonGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
