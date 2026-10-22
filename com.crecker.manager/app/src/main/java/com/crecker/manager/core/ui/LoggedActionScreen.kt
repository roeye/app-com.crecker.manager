package com.crecker.manager.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crecker.manager.core.model.RunRecord
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.model.actionKey
import com.crecker.manager.graph
import java.util.Locale

/** ההרצה הנוכחית (חיה) של פעולה, או האחרונה מההיסטוריה אם לא רצה מאז שהאפליקציה עלתה. */
@Composable
fun rememberLatestRun(systemId: String, actionId: String): RunRecord? {
    val graph = LocalContext.current.graph
    val live by graph.runner.runs.collectAsState()
    val historyFlow = remember(systemId, actionId) { graph.history.observe(systemId, actionId) }
    val history by historyFlow.collectAsState(initial = emptyList())
    return live[actionKey(systemId, actionId)] ?: history.firstOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggedActionScreen(
    systemId: String,
    action: SystemAction.Logged,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val graph = LocalContext.current.graph
    val settings = remember(systemId, action.id) { graph.settings.forAction(systemId, action.id) }
    val run = rememberLatestRun(systemId, action.id)
    val running = run?.status == RunStatus.RUNNING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(action.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.AutoMirrored.Filled.List, "היסטוריית הרצות") }
                    if (action.settingsScreen != null) {
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "הגדרות") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Text(action.description, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            action.options?.invoke(settings)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !running,
                    onClick = { graph.runner.start(systemId, action) },
                    modifier = Modifier.weight(1f),
                ) {
                    if (running) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("רץ…")
                    } else {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("הרץ")
                    }
                }
                if (running) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { graph.runner.cancel(systemId, action.id) }) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(4.dp))
                        Text("ביטול")
                    }
                }
            }

            LogHeader(run, action.title)
            run?.let {
                Text(
                    "${formatDateTime(it.startedAt)} · ${it.trigger.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LogView(run?.entries.orEmpty(), Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunHistoryScreen(
    systemId: String,
    action: SystemAction,
    onBack: () -> Unit,
    onOpenRun: (runId: String) -> Unit,
) {
    val graph = LocalContext.current.graph
    val historyFlow = remember(systemId, action.id) { graph.history.observe(systemId, action.id) }
    val history by historyFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("היסטוריית הרצות") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Text("אין עדיין הרצות שמורות.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(history, key = { it.id }) { record ->
                ListItem(
                    modifier = Modifier.clickable { onOpenRun(record.id) },
                    headlineContent = { Text(formatDateTime(record.startedAt)) },
                    supportingContent = {
                        val duration = record.endedAt
                            ?.let { " · %.1f ש'".format(Locale.US, (it - record.startedAt) / 1000.0) }
                            .orEmpty()
                        Text("${record.trigger.label}$duration")
                    },
                    trailingContent = { StatusBadge(record.status) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(systemId: String, action: SystemAction, runId: String, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val historyFlow = remember(systemId, action.id) { graph.history.observe(systemId, action.id) }
    val history by historyFlow.collectAsState(initial = null)
    val record = history?.firstOrNull { it.id == runId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record?.let { formatDateTime(it.startedAt) } ?: "הרצה") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                history == null -> CircularProgressIndicator()
                record == null -> Text("ההרצה לא נמצאה (ייתכן שנמחקה – נשמרות רק 20 האחרונות).")
                else -> {
                    LogHeader(record, action.title)
                    Text(record.trigger.label, style = MaterialTheme.typography.bodySmall)
                    LogView(record.entries, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

/** שורת "ריצה אחרונה" – לשימוש ב-[com.crecker.manager.core.model.ManagedSystem.StatusLine]. */
@Composable
fun LastRunLine(systemId: String, actionId: String) {
    val run = rememberLatestRun(systemId, actionId)
    if (run == null) {
        Text("לא הורץ עדיין", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("● ", color = LogColors.of(run.status), style = MaterialTheme.typography.bodySmall)
        Text(
            "ריצה אחרונה: ${run.status.label} · ${formatDateTime(run.startedAt)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
