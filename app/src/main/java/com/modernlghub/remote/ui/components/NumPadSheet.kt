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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumPadSheet(
    onSendKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ziffernblock & TV-Steuerung",
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = TextWhite
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val numRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("INFO", "0", "CC")
            )

            numRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCardVariant)
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    haptics.click()
                                    onSendKey(key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = TextWhite,
                                fontSize = if (key.length > 2) 13.sp else 20.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Extra TV shortcut bar: Guide, QMenu, Teletext, Channel Up/Down
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ExtraButton(label = "GUIDE") {
                    haptics.click()
                    onSendKey("GUIDE")
                }
                ExtraButton(label = "Q.MENU") {
                    haptics.click()
                    onSendKey("QMENU")
                }
                ExtraButton(label = "CH +") {
                    haptics.click()
                    onSendKey("CHANNELUP")
                }
                ExtraButton(label = "CH –") {
                    haptics.click()
                    onSendKey("CHANNELDOWN")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExtraButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCardVariant)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = TextWhite, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
