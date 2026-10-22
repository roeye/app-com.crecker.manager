package com.crecker.manager.core.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.crecker.manager.core.remote.RemoteConfig
import com.crecker.manager.core.remote.RemoteProtocol
import com.crecker.manager.core.remote.RemoteSettings
import com.crecker.manager.core.remote.RemoteUploader
import com.crecker.manager.core.run.ActionScheduler
import com.crecker.manager.core.run.Notifications
import com.crecker.manager.core.run.ScheduleKeys
import com.crecker.manager.core.run.describe
import com.crecker.manager.core.settings.ActionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** שלד למסך הגדרות: כותרת, חזרה, תוכן נגלל וכפתור שמירה. [onSave] מחזיר הודעת שגיאה או null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    loaded: Boolean,
    onBack: () -> Unit,
    onSave: suspend () -> String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Button(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        saving = true
                        val error = onSave()
                        saving = false
                        if (error == null) onBack() else snackbar.showSnackbar(error)
                    }
                },
            ) { Text("שמירה") }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

// region Remote (FTP / FTPS / SFTP)

class RemoteFormState(private val settings: ActionSettings, private val defaultDir: String) {
    var loaded by mutableStateOf(false)
    var protocol by mutableStateOf(RemoteProtocol.FTP)
    var host by mutableStateOf("")
    var port by mutableStateOf("")
    var user by mutableStateOf("")

    /** סיסמה חדשה. ריק = להשאיר את השמורה. */
    var password by mutableStateOf("")
    var hasSavedPassword by mutableStateOf(false)
    var remoteDir by mutableStateOf(defaultDir)

    suspend fun load() {
        val config = RemoteSettings.load(settings, defaultDir)
        protocol = config.protocol
        host = config.host
        port = config.port?.toString().orEmpty()
        user = config.user
        remoteDir = config.remoteDir
        hasSavedPassword = settings.hasSecret(RemoteSettings.PASSWORD)
        loaded = true
    }

    fun validationError(): String? = when {
        port.isNotBlank() && port.toIntOrNull()?.takeIf { it in 1..65535 } == null -> "פורט לא תקין"
        remoteDir.isNotBlank() && !remoteDir.startsWith("/") -> "תיקיית היעד צריכה להתחיל ב-/"
        else -> null
    }

    suspend fun save() {
        settings.putString(RemoteSettings.PROTOCOL, protocol.name)
        settings.putString(RemoteSettings.HOST, host.trim())
        settings.putString(RemoteSettings.PORT, port.trim().ifBlank { null })
        settings.putString(RemoteSettings.USER, user.trim())
        settings.putString(RemoteSettings.DIR, remoteDir.trim().ifBlank { null })
        if (password.isNotEmpty()) settings.putSecret(RemoteSettings.PASSWORD, password)
    }

    /** הקונפיגורציה לפי מה שבטופס (גם לפני שמירה) – לבדיקת חיבור. */
    suspend fun currentConfig() = RemoteConfig(
        protocol = protocol,
        host = host.trim(),
        port = port.trim().toIntOrNull(),
        user = user.trim(),
        password = password.ifEmpty { settings.getSecret(RemoteSettings.PASSWORD).orEmpty() },
        remoteDir = remoteDir.trim().ifBlank { defaultDir },
    )
}

@Composable
fun rememberRemoteFormState(settings: ActionSettings, defaultDir: String): RemoteFormState {
    val state = remember(settings.systemId, settings.actionId) { RemoteFormState(settings, defaultDir) }
    LaunchedEffect(state) { state.load() }
    return state
}

@Composable
fun RemoteSettingsSection(state: RemoteFormState) {
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    SectionTitle("חיבור לשרת")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteProtocol.entries.forEach { p ->
            FilterChip(selected = state.protocol == p, onClick = { state.protocol = p }, label = { Text(p.label) })
        }
    }
    if (state.protocol == RemoteProtocol.FTP) {
        Text(
            "⚠ FTP רגיל שולח את הסיסמה ללא הצפנה. אם השרת תומך – עדיף FTPS או SFTP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedTextField(
        value = state.host, onValueChange = { state.host = it },
        label = { Text("שרת (host)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(
        value = state.port, onValueChange = { state.port = it.filter(Char::isDigit) },
        label = { Text("פורט") }, placeholder = { Text("${state.protocol.defaultPort} (ברירת מחדל)") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        value = state.user, onValueChange = { state.user = it },
        label = { Text("שם משתמש") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password, onValueChange = { state.password = it },
        label = { Text("סיסמה") },
        placeholder = { if (state.hasSavedPassword) Text("•••••• (שמורה – השאר ריק כדי לא לשנות)") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "הסתר" else "הצג") }
        },
        supportingText = { Text("נשמרת מוצפנת (Android Keystore)") },
    )
    OutlinedTextField(
        value = state.remoteDir, onValueChange = { state.remoteDir = it },
        label = { Text("תיקיית יעד") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            enabled = !testing,
            onClick = {
                scope.launch {
                    testing = true
                    testResult = null
                    val config = state.currentConfig()
                    val missing = config.missingFields()
                    testResult = if (missing.isNotEmpty()) {
                        false to "חסר: ${missing.joinToString()}"
                    } else {
                        withContext(Dispatchers.IO) { testConnection(config) }
                    }
                    testing = false
                }
            },
        ) { Text("בדיקת חיבור") }
        if (testing) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
    testResult?.let { (ok, message) ->
        Text(
            (if (ok) "✓ " else "✗ ") + message,
            color = if (ok) LogColors.success() else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun testConnection(config: RemoteConfig): Pair<Boolean, String> = try {
    RemoteUploader.create(config).use { uploader ->
        uploader.connect()
        val created = uploader.ensureDir(config.remoteDir)
        true to "החיבור הצליח. התיקייה ${config.remoteDir} ${if (created) "נוצרה" else "קיימת"}."
    }
} catch (e: Exception) {
    false to (e.message ?: e.describe())
}

// endregion

// region Schedule

class ScheduleFormState(private val settings: ActionSettings) {
    var loaded by mutableStateOf(false)
    var enabled by mutableStateOf(false)
    var intervalMinutes by mutableStateOf(ScheduleKeys.DEFAULT_INTERVAL_MINUTES)
    var notifyOnFailure by mutableStateOf(true)

    suspend fun load() {
        enabled = settings.getBoolean(ScheduleKeys.ENABLED, false)
        intervalMinutes = settings.getInt(ScheduleKeys.INTERVAL_MINUTES) ?: ScheduleKeys.DEFAULT_INTERVAL_MINUTES
        notifyOnFailure = settings.getBoolean(ScheduleKeys.NOTIFY_ON_FAILURE, true)
        loaded = true
    }

    suspend fun saveAndApply(context: Context) {
        settings.putBoolean(ScheduleKeys.ENABLED, enabled)
        settings.putInt(ScheduleKeys.INTERVAL_MINUTES, intervalMinutes)
        settings.putBoolean(ScheduleKeys.NOTIFY_ON_FAILURE, notifyOnFailure)
        ActionScheduler.apply(context.applicationContext, settings, enabled, intervalMinutes)
    }
}

@Composable
fun rememberScheduleFormState(settings: ActionSettings): ScheduleFormState {
    val state = remember(settings.systemId, settings.actionId) { ScheduleFormState(settings) }
    LaunchedEffect(state) { state.load() }
    return state
}

private val intervalOptions = listOf(15 to "15 דק'", 30 to "30 דק'", 60 to "שעה", 180 to "3 שעות", 360 to "6 שעות", 720 to "12 שעות", 1440 to "יום")

@Composable
fun ScheduleSection(state: ScheduleFormState) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canNotify(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    HorizontalDivider()
    SectionTitle("הרצה אוטומטית")
    SwitchRow("הרץ אוטומטית ברקע", state.enabled) {
        state.enabled = it
        if (it && state.notifyOnFailure) requestNotificationsIfNeeded()
    }
    if (state.enabled) {
        Text("כל:", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            intervalOptions.forEach { (minutes, label) ->
                FilterChip(
                    selected = state.intervalMinutes == minutes,
                    onClick = { state.intervalMinutes = minutes },
                    label = { Text(label) },
                )
            }
        }
        SwitchRow("התראה כשהרצה נכשלת או מצליחה חלקית", state.notifyOnFailure) {
            state.notifyOnFailure = it
            if (it) requestNotificationsIfNeeded()
        }
        Text(
            "ההרצה מתבצעת רק כשיש חיבור לאינטרנט. אנדרואיד עשוי לדחות אותה מעט כדי לחסוך בסוללה – זה לא תזמון מדויק לדקה.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
