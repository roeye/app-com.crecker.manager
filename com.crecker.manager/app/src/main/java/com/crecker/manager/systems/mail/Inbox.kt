package com.crecker.manager.systems.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crecker.manager.R
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.graph
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** תיבת דואר מאוחדת: ההודעות מכל התיבות ברשימה אחת, וסינון לפי תיבה. קריאה בלבד. */
object Inbox {
    const val ID = "inbox"

    val action = SystemAction.Custom(
        id = ID,
        title = R.string.mail_inbox_title,
        icon = Icons.Default.Inbox,
        screen = { _, onBack -> InboxScreen(onBack) },
    )
}

/** הפניה להודעה. נשמרת כמחרוזת כדי לשרוד סיבוב מסך ([encode]/[decode]). */
data class MessageRef(val accountId: String, val folder: String, val uidValidity: Long, val uid: Long) {
    fun encode() = "$accountId|$uidValidity|$uid|$folder"

    companion object {
        /** התיקייה אחרונה – היא היחידה שיכולה להכיל "|". */
        fun decode(value: String): MessageRef? = value.split("|", limit = 4).takeIf { it.size == 4 }?.let {
            MessageRef(it[0], it[3], it[1].toLongOrNull() ?: return null, it[2].toLongOrNull() ?: return null)
        }
    }
}

private class InboxItem(val account: MailAccount, val folder: String, val uidValidity: Long, val header: MailHeader, val isNew: Boolean) {
    val ref get() = MessageRef(account.id, folder, uidValidity, header.uid)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val settings = remember { graph.settings.forSystem(MailSystem.ID) }
    val store = remember { MailStore.get(context) }
    val accountsFlow = remember { MailAccounts.flow(settings) }
    val accounts by accountsFlow.collectAsState(initial = null)
    val states by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    var openMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // null = כל התיבות
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // התיבות שהוצגו בביקור הזה – ביציאה מהמסך ההודעות שלהן כבר לא "חדשות"
    val viewed = remember { mutableSetOf<String>() }
    DisposableEffect(Unit) {
        onDispose {
            val ids = viewed.toList()
            graph.appScope.launch { store.markViewed(ids) }
        }
    }

    fun refresh(list: List<MailAccount>) {
        if (refreshing) return
        scope.launch {
            refreshing = true
            try {
                coroutineScope { list.map { async { store.refresh(context, settings, it, markNotified = false) } }.awaitAll() }
            } finally {
                refreshing = false
            }
        }
    }

    val loadedAccounts = accounts
    LaunchedEffect(loadedAccounts != null) {
        store.load()
        loadedAccounts?.let { refresh(it) }
    }

    val open = openMessage?.let(MessageRef::decode)
    val openAccount = open?.let { ref -> loadedAccounts?.firstOrNull { it.id == ref.accountId } }
    if (open != null && openAccount != null) {
        val header = states?.get(open.accountId)?.folders?.get(open.folder)?.messages?.firstOrNull { it.uid == open.uid }
        MessageScreen(openAccount, open, header, onBack = { openMessage = null })
        return
    }

    val shown = loadedAccounts.orEmpty().filter { filter == null || it.id == filter }
    SideEffect { shown.forEach { viewed += it.id } }
    val items = remember(shown, states) {
        shown.flatMap { account ->
            states?.get(account.id)?.visibleFolders(account).orEmpty().flatMap { (folder, f) ->
                f.messages.map { InboxItem(account, folder, f.uidValidity, it, it.uid > f.viewedUid) }
            }
        }.sortedByDescending { it.header.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mail_inbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(enabled = !refreshing && loadedAccounts != null, onClick = { loadedAccounts?.let(::refresh) }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.mail_refresh))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadedAccounts?.let(::refresh) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                val list = loadedAccounts ?: return@LazyColumn
                if (list.isEmpty()) {
                    item { Text(stringResource(R.string.mail_no_accounts_hint), Modifier.padding(16.dp)) }
                    return@LazyColumn
                }
                if (list.size > 1) {
                    item(key = "filters") { AccountFilters(list, states, filter) { filter = it } }
                }
                shown.mapNotNull { a -> states?.get(a.id)?.error?.let { a to it } }.forEach { (account, error) ->
                    item(key = "error:${account.id}") {
                        Text(
                            "✗ ${account.label}: $error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                if (items.isEmpty() && !refreshing) {
                    item { Text(stringResource(R.string.mail_empty), Modifier.padding(16.dp)) }
                }
                items(items, key = { "${it.account.id}|${it.folder}|${it.header.uid}" }) { item ->
                    MessageRow(item, showAccount = list.size > 1 || item.folder != INBOX) { openMessage = item.ref.encode() }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AccountFilters(accounts: List<MailAccount>, states: Map<String, AccountState>?, selected: String?, onSelect: (String?) -> Unit) {
    fun newCount(account: MailAccount) = states?.get(account.id)?.newCount(account) ?: 0
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(withCount(stringResource(R.string.mail_filter_all), accounts.sumOf(::newCount))) },
        )
        accounts.forEach { account ->
            FilterChip(
                selected = selected == account.id,
                onClick = { onSelect(account.id) },
                label = { Text(withCount(account.label, newCount(account))) },
            )
        }
    }
}

private fun withCount(label: String, count: Int) = if (count > 0) "$label · $count" else label

@Composable
private fun MessageRow(item: InboxItem, showAccount: Boolean, onClick: () -> Unit) {
    val header = item.header
    val unread = !header.seen
    val weight = if (unread) FontWeight.Bold else FontWeight.Normal
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // נקודה = חדש מאז הביקור הקודם במסך
        Box(Modifier.padding(top = 6.dp).size(8.dp)) {
            if (item.isNew) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    header.from.ifBlank { stringResource(R.string.mail_unknown_sender) },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatShortDate(header.date, System.currentTimeMillis(), ZoneId.systemDefault()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                header.subject.ifBlank { stringResource(R.string.mail_no_subject) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAccount || header.hasAttachments) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showAccount) listOf(item.account.label, folderLabel(item.folder)).joinToString(" · ") else "",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (header.hasAttachments) {
                        Icon(
                            Icons.Default.Attachment,
                            stringResource(R.string.mail_has_attachments),
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val dayMonthFormat = DateTimeFormatter.ofPattern("dd/MM", Locale.US)
private val fullDateFormat = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US)

/** תאריך קצר לרשימה: היום → שעה; השנה → יום/חודש; אחרת → תאריך מלא. */
fun formatShortDate(millis: Long, nowMillis: Long, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    return when {
        date.toLocalDate() == now.toLocalDate() -> timeFormat.format(date)
        date.year == now.year -> dayMonthFormat.format(date)
        else -> fullDateFormat.format(date)
    }
}
