package com.modernlghub.remote.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.modernlghub.remote.BuildConfig
import com.modernlghub.remote.R
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.TvAction
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.system.TvPresenceService
import com.modernlghub.remote.system.TvRemoteWidgetProvider
import com.modernlghub.remote.ui.components.InlineNotice
import com.modernlghub.remote.ui.components.PrimaryButton
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.SecondaryButton
import com.modernlghub.remote.ui.components.SectionLabel
import com.modernlghub.remote.ui.components.StatusDot
import com.modernlghub.remote.ui.components.card
import com.modernlghub.remote.ui.components.fieldColors
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.StatusOn
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/** A short result line under a settings group (green = done, red = problem). */
data class Feedback(val ok: Boolean, val text: String)

/** Wires [SettingsContent] to the preferences and the system. */
@Composable
fun SettingsScreen(repo: TvRepository, state: TvState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = repo.prefs
    val scope = rememberCoroutineScope()

    // Saveable: a half-typed address survives rotation.
    var address by rememberSaveable { mutableStateOf(prefs.tvIp) }
    var token by rememberSaveable { mutableStateOf(prefs.hubToken) }
    var mac by rememberSaveable { mutableStateOf(prefs.tvMac) }
    var connectionFeedback by remember { mutableStateOf<Feedback?>(null) }
    var pairingFeedback by remember { mutableStateOf<Feedback?>(null) }
    var searching by remember { mutableStateOf(false) }
    var haptics by remember { mutableStateOf(prefs.hapticFeedbackEnabled) }
    var hwVolume by remember { mutableStateOf(prefs.hardwareVolumeEnabled) }
    var live by remember { mutableStateOf(prefs.liveNotificationEnabled) }
    var slots by remember { mutableStateOf(prefs.widgetSlots()) }
    fun notificationsAllowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notificationsBlocked by remember { mutableStateOf(live && !notificationsAllowed()) }

    val dirty = (address.isNotBlank() && address.trim() != prefs.tvIp) || token.trim() != prefs.hubToken

    /** Saves address and token; reconnects only if something actually changed. */
    fun applyConnection(newAddress: String, newToken: String) {
        var changed = false
        if (newAddress.isNotBlank() && newAddress.trim() != prefs.tvIp) {
            prefs.tvIp = newAddress
            changed = true
        }
        if (newToken.trim() != prefs.hubToken) {
            prefs.hubToken = newToken
            changed = true
        }
        if (changed) repo.resetConnection()
    }

    fun setLive(enabled: Boolean) {
        live = enabled
        prefs.liveNotificationEnabled = enabled
        if (enabled) TvPresenceService.start(context) else TvPresenceService.stop(context)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setLive(granted)
        notificationsBlocked = !granted
    }

    // Leaving with edited but not applied values used to drop them silently.
    val leave = {
        if (dirty) applyConnection(address, token)
        onBack()
    }
    BackHandler(onBack = leave)

    SettingsContent(
        connection = state.connection,
        connectedTo = prefs.baseUrl.removePrefix("http://"),
        address = address,
        onAddress = { address = it; connectionFeedback = null },
        token = token,
        onToken = { token = it; connectionFeedback = null },
        dirty = dirty,
        onApply = { applyConnection(address, token); connectionFeedback = null },
        searching = searching,
        onSearch = {
            searching = true
            connectionFeedback = null
            scope.launch {
                val found = repo.discoverHub()
                searching = false
                if (found != null) {
                    address = found
                    applyConnection(found, token)
                    connectionFeedback = Feedback(true, "Hub gefunden: $found")
                } else {
                    connectionFeedback = Feedback(false, "Kein Hub im WLAN gefunden. Ist der Fernseher an?")
                }
            }
        },
        connectionFeedback = connectionFeedback,
        mac = mac,
        onMac = { mac = it; prefs.tvMac = it },
        haptics = haptics,
        onHaptics = { haptics = it; prefs.hapticFeedbackEnabled = it },
        hwVolume = hwVolume,
        onHwVolume = { hwVolume = it; prefs.hardwareVolumeEnabled = it },
        live = live,
        onLive = { enabled ->
            if (enabled && !notificationsAllowed()) {
                @SuppressLint("InlinedApi")
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationsBlocked = false
                setLive(enabled)
            }
        },
        notificationsBlocked = notificationsBlocked,
        onOpenNotificationSettings = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
        slots = slots,
        onSlot = { index, picked ->
            prefs.setWidgetSlot(index, picked)
            slots = prefs.widgetSlots()
            TvRemoteWidgetProvider.refreshAll(context)
        },
        onResetPairing = {
            prefs.ssapClientKey = ""
            pairingFeedback = Feedback(true, "Kopplung zurückgesetzt. Der Fernseher fragt beim nächsten Mal neu.")
        },
        pairingFeedback = pairingFeedback,
        version = "${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
        onBack = leave,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    connection: Connection,
    connectedTo: String,
    address: String,
    onAddress: (String) -> Unit,
    token: String,
    onToken: (String) -> Unit,
    dirty: Boolean,
    onApply: () -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    connectionFeedback: Feedback?,
    mac: String,
    onMac: (String) -> Unit,
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    hwVolume: Boolean,
    onHwVolume: (Boolean) -> Unit,
    live: Boolean,
    onLive: (Boolean) -> Unit,
    notificationsBlocked: Boolean,
    onOpenNotificationSettings: () -> Unit,
    slots: List<TvAction>,
    onSlot: (Int, TvAction) -> Unit,
    onResetPairing: () -> Unit,
    pairingFeedback: Feedback?,
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = Spacing.ContentMaxWidth)
                .padding(horizontal = Spacing.Gutter)
                .padding(bottom = Spacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.s)) {
                RoundButton(
                    Icons.AutoMirrored.Rounded.ArrowBack, "Zurück",
                    size = 44.dp, iconSize = 22.dp, container = Color.Transparent, onClick = onBack,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(Spacing.m))

            SectionLabel("Hub")
            Group {
                val (dot, label) = when (connection) {
                    Connection.ONLINE -> StatusOn to "Verbunden mit $connectedTo"
                    Connection.UNAUTHORIZED -> StatusWarn to "Hub erreichbar, Token fehlt oder ist falsch"
                    Connection.CONNECTING -> TextTertiary to "Verbinde …"
                    Connection.OFFLINE -> StatusOff to "Nicht verbunden"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    StatusDot(dot, pulsing = connection == Connection.CONNECTING)
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Spacer(Modifier.height(Spacing.l))
                Field("Hub-Adresse (IP oder IP:Port)", address, KeyboardType.Uri, onChange = onAddress)
                Spacer(Modifier.height(Spacing.s + 2.dp))
                Field("Token (nur falls auf dem Hub gesetzt)", token, KeyboardType.Password, secret = true, onChange = onToken)
                Spacer(Modifier.height(Spacing.m))
                ButtonRow {
                    PrimaryButton("Übernehmen", enabled = dirty, onClick = onApply)
                    SecondaryButton(
                        if (searching) "Suche …" else "Im WLAN suchen",
                        icon = if (searching) null else Icons.Rounded.Search,
                        enabled = !searching,
                        onClick = onSearch,
                    )
                }
                FeedbackLine(connectionFeedback)
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel("Fernseher")
            Group {
                Field("MAC-Adresse zum Einschalten (Wake-on-LAN)", mac, KeyboardType.Ascii, onChange = onMac)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wird automatisch vom Hub übernommen, sobald der Fernseher einmal erreichbar war.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel("Bedienung")
            Group {
                Toggle("Haptisches Feedback", "Fühlbare Rasterung beim Wischen und Tippen", haptics, onHaptics)
                Divider()
                Toggle("Lautstärketasten steuern den Fernseher", "Solange die App oder die Mini-Fernbedienung offen ist", hwVolume, onHwVolume)
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel("Sperrbildschirm")
            Group {
                Toggle(
                    "Live-Steuerung",
                    "Steuerung im Sperrbildschirm und in der Now Bar; die Medienlautstärke regelt den Fernseher. Prüft den Fernseher nur im WLAN.",
                    live && !notificationsBlocked,
                    onLive,
                )
                if (notificationsBlocked) {
                    Spacer(Modifier.height(Spacing.m))
                    InlineNotice(
                        "Ohne Benachrichtigungen geht die Live-Steuerung nicht. Erlaube sie in den Android-Einstellungen.",
                        icon = Icons.Rounded.NotificationsOff,
                        tint = StatusWarn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    SecondaryButton("Benachrichtigungen erlauben", onClick = onOpenNotificationSettings)
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel("Widget (2×2)")
            Group {
                slots.forEachIndexed { index, action ->
                    if (index > 0) Divider()
                    SlotPicker("Taste ${index + 1}", action) { onSlot(index, it) }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel("Mauszeiger")
            Group {
                Text("Beim ersten Verbinden fragt der Fernseher einmal nach Erlaubnis.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(Spacing.m))
                SecondaryButton("Kopplung zurücksetzen", onClick = onResetPairing)
                FeedbackLine(pairingFeedback)
            }

            Spacer(Modifier.height(Spacing.xl))
            Text(
                version,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { content() }
}

@Composable
private fun FeedbackLine(feedback: Feedback?) {
    if (feedback == null) return
    Spacer(Modifier.height(Spacing.s + 2.dp))
    Text(
        feedback.text,
        style = MaterialTheme.typography.bodySmall,
        color = if (feedback.ok) StatusOn else StatusOff,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(18.dp), content = content)
}

@Composable
private fun Divider() {
    HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = Spacing.m))
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = Shape.Control,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
        colors = fieldColors(filled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(role = Role.Switch, pressedScale = 0.99f) { onChange(!checked) },
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(Spacing.xxs))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.width(Spacing.m))
        // The row handles the click (and is the TalkBack switch); the Switch is the visual.
        Switch(checked = checked, onCheckedChange = null, colors = switchColors())
    }
}

@Composable
private fun SlotPicker(label: String, current: TvAction, onPick: (TvAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .pressable(pressedScale = 0.99f, onClickLabel = "Belegung ändern") { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(current.shortLabel, style = MaterialTheme.typography.bodyMedium, color = Accent)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Accent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Surface1, shape = Shape.Control) {
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
