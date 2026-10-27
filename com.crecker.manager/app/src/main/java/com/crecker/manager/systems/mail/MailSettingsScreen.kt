package com.crecker.manager.systems.mail

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.settings.SystemSettings
import com.crecker.manager.core.ui.LogColors
import com.crecker.manager.core.ui.SectionTitle
import com.crecker.manager.core.ui.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NEW_ACCOUNT = "+new"

/** הגדרות המערכת: רשימת תיבות הדואר, ומסך עריכה לכל תיבה. */
@Composable
fun MailSettingsScreen(settings: SystemSettings, onBack: () -> Unit) {
    // null = הרשימה; אחרת מזהה החשבון שבעריכה (או NEW_ACCOUNT)
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    when (val id = editing) {
        null -> AccountsList(settings, onBack, onEdit = { editing = it })
        else -> {
            BackHandler { editing = null }
            AccountForm(settings, existingId = id.takeIf { it != NEW_ACCOUNT }, onDone = { editing = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsList(settings: SystemSettings, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val accountsFlow = remember(settings.prefix) { MailAccounts.flow(settings) }
    val accounts by accountsFlow.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mail_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(NEW_ACCOUNT) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.mail_add_account)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.mail_settings_hint), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val list = accounts ?: return@LazyColumn
            if (list.isEmpty()) {
                item { Text(stringResource(R.string.mail_no_accounts_add), Modifier.padding(16.dp)) }
            }
            items(list, key = { it.id }) { account ->
                ListItem(
                    modifier = Modifier.clickable { onEdit(account.id) },
                    leadingContent = { Icon(Icons.Default.Email, null) },
                    headlineContent = { Text(account.label) },
                    supportingContent = {
                        val inbox = stringResource(R.string.mail_folder_inbox)
                        val folders = account.folders.joinToString { if (it == INBOX) inbox else it }
                        Text("${account.user} · ${account.host}:${account.effectivePort} · $folders")
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

private class AccountFormState(private val settings: SystemSettings, private val existingId: String?) {
    var loaded by mutableStateOf(false)
    var name by mutableStateOf("")
    var user by mutableStateOf("")

    /** סיסמה חדשה. ריק = להשאיר את השמורה. */
    var password by mutableStateOf("")
    var hasSavedPassword by mutableStateOf(false)
    var host by mutableStateOf("")
    var port by mutableStateOf("")
    var security by mutableStateOf(MailSecurity.SSL)

    /** התיקיות שנבחרו. */
    var folders by mutableStateOf(listOf(INBOX))

    /** תיקיות שנטענו מהשרת (לבחירה). */
    var serverFolders by mutableStateOf<List<String>>(emptyList())

    val isNew get() = existingId == null

    suspend fun load() {
        MailAccounts.load(settings).firstOrNull { it.id == existingId }?.let { a ->
            name = a.name
            user = a.user
            host = a.host
            port = a.port?.toString().orEmpty()
            security = a.security
            folders = a.folders
            hasSavedPassword = settings.hasSecret(MailAccounts.passwordKey(a.id))
        }
        loaded = true
    }

    @StringRes
    fun validationError(): Int? = when {
        user.isBlank() -> R.string.mail_error_user
        host.isBlank() -> R.string.mail_error_host
        port.isNotBlank() && port.toIntOrNull()?.takeIf { it in 1..65535 } == null -> R.string.error_invalid_port
        password.isEmpty() && !hasSavedPassword -> R.string.mail_error_password
        folders.isEmpty() -> R.string.mail_error_no_folder
        else -> null
    }

    fun account(id: String = existingId ?: "") = MailAccount(
        id = id,
        name = name.trim(),
        host = host.trim(),
        port = port.trim().toIntOrNull(),
        security = security,
        user = user.trim(),
        folders = folders,
    )

    suspend fun save() {
        val account = account(existingId ?: MailAccount.newId())
        val accounts = MailAccounts.load(settings)
        val updated = if (accounts.any { it.id == account.id }) accounts.map { if (it.id == account.id) account else it } else accounts + account
        if (password.isNotEmpty()) settings.putSecret(MailAccounts.passwordKey(account.id), password)
        MailAccounts.save(settings, updated)
    }

    suspend fun delete(store: MailStore) {
        val id = existingId ?: return
        MailAccounts.save(settings, MailAccounts.load(settings).filterNot { it.id == id })
        settings.putSecret(MailAccounts.passwordKey(id), null)
        store.forget(id)
    }

    /** הסיסמה לבדיקה: מה שבטופס, או השמורה. */
    suspend fun currentPassword(): String =
        password.ifEmpty { existingId?.let { settings.getSecret(MailAccounts.passwordKey(it)) }.orEmpty() }
}

@Composable
private fun AccountForm(settings: SystemSettings, existingId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state = remember(existingId) { AccountFormState(settings, existingId) }
    LaunchedEffect(state) { state.load() }

    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    /** מתחבר עם הערכים שבטופס (גם לפני שמירה) ומריץ את [block]. */
    fun withClient(block: (ImapClient) -> String) {
        scope.launch {
            busy = true
            result = null
            val account = state.account()
            val password = state.currentPassword()
            val missing = account.missingFields() + listOfNotNull(R.string.field_password.takeIf { password.isEmpty() })
            result = if (missing.isNotEmpty()) {
                false to resources.getString(R.string.test_missing, missing.joinToString { resources.getString(it) })
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        ImapClient(account, password).use { client ->
                            client.connect()
                            true to block(client)
                        }
                    } catch (e: Exception) {
                        false to e.describe(context)
                    }
                }
            }
            busy = false
        }
    }

    SettingsScaffold(
        title = stringResource(if (state.isNew) R.string.mail_new_account else R.string.mail_edit_account),
        loaded = state.loaded,
        onBack = onDone,
        onSave = { state.validationError() ?: run { state.save(); null } },
    ) {
        Text(
            stringResource(R.string.mail_read_only_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.user, onValueChange = { state.user = it },
            label = { Text(stringResource(R.string.mail_field_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
            value = state.name, onValueChange = { state.name = it },
            label = { Text(stringResource(R.string.mail_field_name)) },
            placeholder = { Text(state.user) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(stringResource(R.string.mail_server_section))
        OutlinedTextField(
            value = state.host, onValueChange = { state.host = it },
            label = { Text(stringResource(R.string.mail_field_host)) },
            placeholder = { Text("mail.example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MailSecurity.entries.forEach { s ->
                FilterChip(selected = state.security == s, onClick = { state.security = s }, label = { Text(stringResource(s.label)) })
            }
        }
        OutlinedTextField(
            value = state.port, onValueChange = { state.port = it.filter(Char::isDigit).take(5) },
            label = { Text(stringResource(R.string.field_port)) },
            placeholder = { Text(stringResource(R.string.port_default_hint, state.security.defaultPort)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        SectionTitle(stringResource(R.string.mail_folders))
        Text(
            stringResource(R.string.mail_folders_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (state.folders + state.serverFolders).distinct().forEach { folder ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = folder in state.folders,
                    onCheckedChange = { checked -> state.folders = if (checked) state.folders + folder else state.folders - folder },
                )
                Text(folderLabel(folder))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                withClient { client ->
                    val (total, unread) = client.counts(INBOX)
                    resources.getString(R.string.mail_test_ok, resources.getString(R.string.mail_folder_inbox), total, unread)
                }
            }) { Text(stringResource(R.string.test_connection)) }
            OutlinedButton(enabled = !busy, onClick = {
                withClient { client ->
                    val list = client.folders()
                    state.serverFolders = list
                    resources.getString(R.string.mail_folders_loaded, list.size)
                }
            }) { Text(stringResource(R.string.mail_load_folders)) }
            if (busy) {
                Spacer(Modifier.width(4.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        result?.let { (ok, message) ->
            Text(
                (if (ok) "✓ " else "✗ ") + message,
                color = if (ok) LogColors.success() else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (!state.isNew) {
            HorizontalDivider()
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.mail_delete_account)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.mail_delete_account)) },
            text = { Text(stringResource(R.string.mail_delete_confirm, state.account().label)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        state.delete(MailStore.get(context))
                        onDone()
                    }
                }) { Text(stringResource(R.string.mail_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "INBOX" מוצג כ"דואר נכנס"; שאר התיקיות בשם שלהן בשרת. */
@Composable
fun folderLabel(folder: String): String = if (folder == INBOX) stringResource(R.string.mail_folder_inbox) else folder
