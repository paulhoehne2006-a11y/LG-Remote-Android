package com.modernlghub.remote.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.modernlghub.remote.BuildConfig
import com.modernlghub.remote.data.TvAction
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.system.TvPresenceService
import com.modernlghub.remote.system.TvRemoteWidgetProvider
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.SectionLabel
import com.modernlghub.remote.ui.components.card
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.StatusOn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: TvRepository, state: TvState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = repo.prefs
    val scope = rememberCoroutineScope()

    var address by remember { mutableStateOf(prefs.tvIp) }
    var mac by remember { mutableStateOf(prefs.tvMac) }
    var info by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var haptics by remember { mutableStateOf(prefs.hapticFeedbackEnabled) }
    var hwVolume by remember { mutableStateOf(prefs.hardwareVolumeEnabled) }
    var live by remember { mutableStateOf(prefs.liveNotificationEnabled) }
    var slots by remember { mutableStateOf(prefs.widgetSlots()) }

    fun applyAddress(value: String) {
        if (value.isBlank() || value == prefs.tvIp) return
        prefs.tvIp = value
        repo.resetConnection()
    }

    fun setLive(enabled: Boolean) {
        live = enabled
        prefs.liveNotificationEnabled = enabled
        if (enabled) TvPresenceService.start(context) else TvPresenceService.stop(context)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setLive(granted)
        if (!granted) info = false to "Ohne Benachrichtigungs-Berechtigung geht die Sperrbildschirm-Steuerung nicht."
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", size = 44.dp, iconSize = 22.dp, container = Color.Transparent, onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Text("Einstellungen", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        SectionLabel("Fernseher")
        Group {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (state.isOnline) StatusOn else StatusOff))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (state.isOnline) "Verbunden mit ${prefs.baseUrl.removePrefix("http://")}" else "Nicht verbunden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Field("Hub-Adresse (IP oder IP:Port)", address, KeyboardType.Uri) { address = it }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { applyAddress(address); info = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                ) { Text("Übernehmen") }
                OutlinedButton(
                    onClick = {
                        searching = true
                        info = null
                        scope.launch {
                            val found = repo.discoverHub()
                            searching = false
                            if (found != null) {
                                address = found
                                applyAddress(found)
                                info = true to "Hub gefunden: $found"
                            } else {
                                info = false to "Kein Hub im WLAN gefunden. Läuft der Fernseher?"
                            }
                        }
                    },
                    enabled = !searching,
                ) {
                    if (searching) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Im WLAN suchen", color = TextPrimary)
                }
            }
            info?.let { (ok, text) ->
                Spacer(Modifier.height(10.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) StatusOn else StatusOff)
            }
            Divider()
            Field("MAC-Adresse für Einschalten (Wake-on-LAN)", mac, KeyboardType.Ascii) {
                mac = it
                prefs.tvMac = it
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Wird automatisch vom Hub übernommen, sobald der Fernseher einmal erreichbar war.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Bedienung")
        Group {
            Toggle("Haptisches Feedback", "Fühlbare Rasterung beim Wischen und Tippen", haptics) {
                haptics = it
                prefs.hapticFeedbackEnabled = it
            }
            Divider()
            Toggle("Lautstärketasten steuern den TV", "Solange die App oder die Mini-Fernbedienung offen ist", hwVolume) {
                hwVolume = it
                prefs.hardwareVolumeEnabled = it
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Sperrbildschirm")
        Group {
            Toggle(
                "Live-Steuerung",
                "Steuerung im Sperrbildschirm und in der Now Bar, die Medienlautstärke regelt den TV. Prüft den TV nur im WLAN.",
                live,
            ) { enabled ->
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (enabled && needsPermission) {
                    @SuppressLint("InlinedApi")
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    setLive(enabled)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Widget (2×2)")
        Group {
            slots.forEachIndexed { index, action ->
                if (index > 0) Divider()
                SlotPicker("Taste ${index + 1}", action) { picked ->
                    prefs.setWidgetSlot(index, picked)
                    slots = prefs.widgetSlots()
                    TvRemoteWidgetProvider.refreshAll(context)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Mauszeiger")
        Group {
            Text("Beim ersten Verbinden fragt der Fernseher einmal nach Erlaubnis.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                prefs.ssapClientKey = ""
                info = true to "Kopplung zurückgesetzt"
            }) { Text("Kopplung zurücksetzen", color = TextPrimary) }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Modern LG Remote ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(18.dp), content = content)
}

@Composable
private fun Divider() {
    HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 14.dp))
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Hairline, focusedLabelColor = Accent),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().pressable { onChange(!checked) }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Hairline,
            ),
        )
    }
}

@Composable
private fun SlotPicker(label: String, current: TvAction, onPick: (TvAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().pressable { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(current.shortLabel, style = MaterialTheme.typography.bodyMedium, color = Accent)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Accent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Surface1) {
            TvAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label, color = if (action == current) Accent else TextPrimary) },
                    onClick = {
                        open = false
                        onPick(action)
                    },
                )
            }
        }
    }
}
