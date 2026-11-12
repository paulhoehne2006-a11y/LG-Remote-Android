package com.modernlghub.remote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.theme.AccentBlue
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(
    apps: List<LauncherApp>,
    apiClient: TvApiClient,
    onAppLaunched: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    val filtered = apps.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "Apps & Eingänge",
            color = TextWhite,
            fontSize = 22.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            text = "Synchronisiert mit Modern LG Hub",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("App oder Eingang suchen...", color = TextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = CardBorder
            ),
            singleLine = true
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filtered) { app ->
                AppGridCard(
                    app = app,
                    iconUrl = apiClient.getAppIconUrl(app.id),
                    onClick = {
                        haptics.click()
                        scope.launch {
                            apiClient.launchApp(app.id)
                            onAppLaunched()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppGridCard(
    app: LauncherApp,
    iconUrl: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (app.id.contains("hdmi1")) {
                    Text(text = "🖥️", fontSize = 28.sp)
                } else if (app.id.contains("hdmi2")) {
                    Text(text = "🎮", fontSize = 28.sp)
                } else if (app.id.contains("custom.launcher")) {
                    Text(text = "✨", fontSize = 28.sp)
                } else {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = app.title,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = app.title,
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
