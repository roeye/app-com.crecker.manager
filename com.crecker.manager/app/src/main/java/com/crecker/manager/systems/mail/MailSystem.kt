package com.crecker.manager.systems.mail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.crecker.manager.R
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.settings.SystemSettings
import com.crecker.manager.core.ui.LastRunLine
import com.crecker.manager.graph

/**
 * כל תיבות הדואר (IMAP) במקום אחד – **קריאה בלבד**: לא מסמן כנקרא, לא מוחק ולא משנה כלום בשרת.
 * ראו docs/systems/mail.md.
 */
object MailSystem : ManagedSystem {
    const val ID = "mail"

    override val id = ID
    override val name = "Mail"
    override val description = R.string.mail_description
    override val category = R.string.category_communication
    override val icon = Icons.Default.Email

    override val actions = listOf(
        Inbox.action,
        CheckNewMail.action,
    )

    override val settingsScreen: @Composable (SystemSettings, () -> Unit) -> Unit =
        { settings, onBack -> MailSettingsScreen(settings, onBack) }

    /** שורה לכל תיבה ("support@… · 3 חדשים · 12 לא נקראו") + הבדיקה האחרונה. */
    @Composable
    override fun StatusLine() {
        val context = LocalContext.current
        val accountsFlow = remember { MailAccounts.flow(context.graph.settings.forSystem(ID)) }
        val accounts by accountsFlow.collectAsState(initial = null)
        val store = remember { MailStore.get(context) }
        val states by store.state.collectAsState()
        LaunchedEffect(store) { store.load() }

        Column {
            val list = accounts ?: return@Column
            if (list.isEmpty()) {
                Text(stringResource(R.string.mail_no_accounts_short), style = MaterialTheme.typography.bodySmall)
            }
            list.forEach { account -> AccountStatusLine(account, states?.get(account.id)) }
            LastRunLine(ID, CheckNewMail.ID, R.string.mail_check_short)
        }
    }
}

@Composable
private fun AccountStatusLine(account: MailAccount, state: AccountState?) {
    val newCount = state?.newCount(account) ?: 0
    val parts = buildList {
        add(account.label)
        when {
            state == null || state.visibleFolders(account).isEmpty() ->
                add(stringResource(if (state?.error != null) R.string.mail_status_error else R.string.mail_status_not_checked))
            else -> {
                if (newCount > 0) add(stringResource(R.string.mail_new_count, newCount))
                val unread = state.unreadCount(account)
                add(if (unread > 0) stringResource(R.string.mail_unread_count, unread) else stringResource(R.string.mail_no_unread))
                if (state.error != null) add(stringResource(R.string.mail_status_error))
            }
        }
    }
    val color = when {
        state?.error != null -> MaterialTheme.colorScheme.error
        newCount > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // יישור לראש השורה – כשהטקסט נשבר, הנקודה בגובה השורה הראשונה (כמו LastRunLine)
    Row(verticalAlignment = Alignment.Top) {
        Text("● ", color = color, style = MaterialTheme.typography.bodySmall)
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
}
