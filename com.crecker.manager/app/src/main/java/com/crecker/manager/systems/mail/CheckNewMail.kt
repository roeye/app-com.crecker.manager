package com.crecker.manager.systems.mail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.crecker.manager.R
import com.crecker.manager.core.model.ActionContext
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.RunTrigger
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.run.Notifications
import com.crecker.manager.core.settings.ActionSettings
import com.crecker.manager.core.ui.ScheduleSection
import com.crecker.manager.core.ui.SectionTitle
import com.crecker.manager.core.ui.SettingsScaffold
import com.crecker.manager.core.ui.SwitchRow
import com.crecker.manager.core.ui.rememberScheduleFormState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * בודק בכל תיבות הדואר אם הגיעו הודעות חדשות. בהרצה מתוזמנת – התראה אחת שמסכמת את כל התיבות.
 * קריאה בלבד: לא משנה כלום בשרת. ראו docs/systems/mail.md.
 */
object CheckNewMail {
    const val ID = "check_new_mail"
    const val KEY_NOTIFY_NEW = "notify_new_mail"

    /** כמה הודעות חדשות מפורטות בהתראה. */
    private const val NOTIFICATION_PREVIEW = 5

    val action = SystemAction.Logged(
        id = ID,
        title = R.string.mail_check_title,
        icon = Icons.Default.MarkEmailUnread,
        description = R.string.mail_check_description,
        schedulable = true,
        settingsScreen = { settings, onBack -> CheckSettingsScreen(settings, onBack) },
        run = { execute() },
    )

    private suspend fun ActionContext.execute(): RunStatus {
        val accounts = MailAccounts.load(systemSettings)
        if (accounts.isEmpty()) {
            log.error(getString(R.string.mail_log_no_accounts))
            return RunStatus.FAILED
        }
        log.info(getString(R.string.mail_log_start, accounts.size))

        val store = MailStore.get(appContext)
        val results = accounts.map { account ->
            currentCoroutineContext().ensureActive()
            store.refresh(appContext, systemSettings, account, markNotified = true).also { logResult(it) }
        }

        val newMessages = results.flatMap { r -> r.newMessages.values.flatten().map { r.account to it } }
        if (newMessages.isEmpty()) log.info(getString(R.string.mail_log_no_new))
        else log.success(getString(R.string.mail_log_total_new, newMessages.size))

        log.table(
            title = getString(R.string.mail_summary),
            headers = listOf(
                getString(R.string.mail_col_account),
                getString(R.string.mail_col_folder),
                getString(R.string.mail_col_total),
                getString(R.string.mail_col_unread),
                getString(R.string.mail_col_new),
            ),
            rows = results.flatMap { r ->
                if (r.error != null && r.state.visibleFolders(r.account).isEmpty()) {
                    listOf(listOf(r.account.label, "—", "—", "—", "✗ ${r.error}"))
                } else {
                    r.state.visibleFolders(r.account).map { (folder, f) ->
                        listOf(r.account.label, folderLabel(folder), f.total.toString(), f.unread.toString(), (r.newMessages[folder]?.size ?: 0).toString())
                    }
                }
            },
        )

        if (newMessages.isNotEmpty() && trigger == RunTrigger.SCHEDULED && settings.getBoolean(KEY_NOTIFY_NEW, true)) {
            notifyNew(results, newMessages)
        }

        val failed = results.count { it.error != null }
        return when (failed) {
            0 -> RunStatus.SUCCESS
            results.size -> RunStatus.FAILED
            else -> RunStatus.PARTIAL
        }
    }

    private fun ActionContext.logResult(r: RefreshResult) {
        for ((folder, f) in r.state.visibleFolders(r.account)) {
            val new = r.newMessages[folder] ?: continue
            log.success(getString(R.string.mail_log_folder_ok, r.account.label, folderLabel(folder), f.unread, new.size))
            new.forEach { log.info(getString(R.string.mail_log_new_message, it.from.ifBlank { "?" }, it.subject.ifBlank { getString(R.string.mail_no_subject) })) }
        }
        r.error?.let { log.error(getString(R.string.mail_log_account_failed, r.account.label, it)) }
    }

    private fun ActionContext.folderLabel(folder: String) = if (folder == INBOX) getString(R.string.mail_folder_inbox) else folder

    /** התראה אחת לכל התיבות: "5 מיילים חדשים" + שורה לכל תיבה + כמה נושאים אחרונים. */
    private fun ActionContext.notifyNew(results: List<RefreshResult>, newMessages: List<Pair<MailAccount, MailHeader>>) {
        val title = appContext.resources.getQuantityString(R.plurals.mail_notif_title, newMessages.size, newMessages.size)
        val perAccount = results.mapNotNull { r ->
            r.newMessages.values.sumOf { it.size }.takeIf { it > 0 }?.let { "${r.account.label} – $it" }
        }
        val preview = newMessages.sortedByDescending { it.second.date }.take(NOTIFICATION_PREVIEW).map { (_, m) ->
            "${m.from.ifBlank { "?" }}: ${m.subject.ifBlank { getString(R.string.mail_no_subject) }}"
        }
        Notifications.systemUpdate(
            appContext,
            tag = "${MailSystem.ID}/new",
            title = title,
            text = perAccount.joinToString(" · "),
            details = (perAccount + "" + preview).joinToString("\n"),
        )
    }
}

@Composable
private fun CheckSettingsScreen(settings: ActionSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val schedule = rememberScheduleFormState(settings)
    // null = עוד נטען
    var notifyNew by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(settings) { notifyNew = settings.getBoolean(CheckNewMail.KEY_NOTIFY_NEW, true) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    SettingsScaffold(
        title = stringResource(R.string.mail_check_settings_title),
        loaded = schedule.loaded && notifyNew != null,
        onBack = onBack,
        onSave = {
            settings.putBoolean(CheckNewMail.KEY_NOTIFY_NEW, notifyNew == true)
            schedule.saveAndApply(context)
            null
        },
    ) {
        Text(
            stringResource(R.string.mail_accounts_in_system_settings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle(stringResource(R.string.mail_notifications_section))
        SwitchRow(stringResource(R.string.mail_notify_new), notifyNew == true) {
            notifyNew = it
            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canNotify(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Text(
            stringResource(R.string.mail_notify_new_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScheduleSection(schedule)
    }
}
