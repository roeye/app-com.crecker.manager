package com.crecker.manager.core.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.crecker.manager.R
import com.crecker.manager.core.remote.RemoteConfig
import com.crecker.manager.core.remote.RemoteProtocol
import com.crecker.manager.core.remote.RemoteSettings
import com.crecker.manager.core.remote.RemoteUploader
import com.crecker.manager.core.run.ActionScheduler
import com.crecker.manager.core.run.Notifications
import com.crecker.manager.core.run.ScheduleKeys
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.settings.ActionSettings
import com.crecker.manager.core.settings.ScopedSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** שלד למסך הגדרות: כותרת, חזרה, תוכן נגלל וכפתור שמירה. [onSave] מחזיר שגיאה (string res) או null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    loaded: Boolean,
    onBack: () -> Unit,
    onSave: suspend () -> Int?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
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
                        if (error == null) onBack() else snackbar.showSnackbar(resources.getString(error))
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

// region Remote (FTP / FTPS / SFTP)

class RemoteFormState(private val settings: ScopedSettings, private val defaultDir: String) {
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

    @StringRes
    fun validationError(): Int? = when {
        port.isNotBlank() && port.toIntOrNull()?.takeIf { it in 1..65535 } == null -> R.string.error_invalid_port
        remoteDir.isNotBlank() && !remoteDir.startsWith("/") -> R.string.error_dir_slash
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
fun rememberRemoteFormState(settings: ScopedSettings, defaultDir: String): RemoteFormState {
    val state = remember(settings.prefix) { RemoteFormState(settings, defaultDir) }
    LaunchedEffect(state) { state.load() }
    return state
}

@Composable
fun RemoteSettingsSection(state: RemoteFormState) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    SectionTitle(stringResource(R.string.remote_section))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteProtocol.entries.forEach { p ->
            FilterChip(selected = state.protocol == p, onClick = { state.protocol = p }, label = { Text(p.label) })
        }
    }
    if (state.protocol == RemoteProtocol.FTP) {
        Text(
            stringResource(R.string.remote_ftp_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedTextField(
        value = state.host, onValueChange = { state.host = it },
        label = { Text(stringResource(R.string.field_host)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(
        value = state.port, onValueChange = { state.port = it.filter(Char::isDigit) },
        label = { Text(stringResource(R.string.field_port)) },
        placeholder = { Text(stringResource(R.string.port_default_hint, state.protocol.defaultPort)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        value = state.user, onValueChange = { state.user = it },
        label = { Text(stringResource(R.string.field_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password, onValueChange = { state.password = it },
        label = { Text(stringResource(R.string.field_password)) },
        placeholder = { if (state.hasSavedPassword) Text(stringResource(R.string.password_saved_hint)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { showPassword = !showPassword }) { Text(stringResource(if (showPassword) R.string.hide else R.string.show)) }
        },
        supportingText = { Text(stringResource(R.string.password_encrypted_note)) },
    )
    OutlinedTextField(
        value = state.remoteDir, onValueChange = { state.remoteDir = it },
        label = { Text(stringResource(R.string.field_remote_dir)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
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
                        false to resources.getString(R.string.test_missing, missing.joinToString { resources.getString(it) })
                    } else {
                        withContext(Dispatchers.IO) { testConnection(context, config) }
                    }
                    testing = false
                }
            },
        ) { Text(stringResource(R.string.test_connection)) }
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

private fun testConnection(context: Context, config: RemoteConfig): Pair<Boolean, String> = try {
    RemoteUploader.create(config).use { uploader ->
        uploader.connect()
        val created = uploader.ensureDir(config.remoteDir)
        true to context.getString(if (created) R.string.test_ok_created else R.string.test_ok_exists, config.remoteDir)
    }
} catch (e: Exception) {
    false to e.describe(context)
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

private val intervalOptions = listOf(
    15 to R.string.interval_15m,
    30 to R.string.interval_30m,
    60 to R.string.interval_1h,
    180 to R.string.interval_3h,
    360 to R.string.interval_6h,
    720 to R.string.interval_12h,
    1440 to R.string.interval_1d,
)

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
    SectionTitle(stringResource(R.string.schedule_section))
    SwitchRow(stringResource(R.string.schedule_enabled), state.enabled) {
        state.enabled = it
        if (it && state.notifyOnFailure) requestNotificationsIfNeeded()
    }
    if (state.enabled) {
        Text(stringResource(R.string.schedule_every), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            intervalOptions.forEach { (minutes, label) ->
                FilterChip(
                    selected = state.intervalMinutes == minutes,
                    onClick = { state.intervalMinutes = minutes },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        SwitchRow(stringResource(R.string.schedule_notify), state.notifyOnFailure) {
            state.notifyOnFailure = it
            if (it) requestNotificationsIfNeeded()
        }
        Text(
            stringResource(R.string.schedule_note),
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
