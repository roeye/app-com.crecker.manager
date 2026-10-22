package com.crecker.manager.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crecker.manager.core.model.LogEntry
import com.crecker.manager.core.model.LogLevel
import com.crecker.manager.core.model.RunRecord
import com.crecker.manager.core.model.RunStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogColors {
    @Composable
    @ReadOnlyComposable
    fun success() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

    @Composable
    @ReadOnlyComposable
    fun warn() = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFB26A00)

    @Composable
    @ReadOnlyComposable
    fun of(level: LogLevel) = when (level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.SUCCESS -> success()
        LogLevel.WARN -> warn()
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    @Composable
    @ReadOnlyComposable
    fun of(status: RunStatus) = when (status) {
        RunStatus.RUNNING -> MaterialTheme.colorScheme.primary
        RunStatus.SUCCESS -> success()
        RunStatus.PARTIAL, RunStatus.CANCELLED -> warn()
        RunStatus.FAILED -> MaterialTheme.colorScheme.error
    }
}

private val timeFormat get() = SimpleDateFormat("HH:mm:ss", Locale.US)
private val dateTimeFormat get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)

fun formatTime(millis: Long): String = timeFormat.format(Date(millis))
fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

/** הלוג כטקסט – להעתקה ושיתוף. */
fun RunRecord.toShareText(title: String): String = buildString {
    appendLine(title)
    appendLine("${formatDateTime(startedAt)} · ${trigger.label} · ${status.label}")
    appendLine()
    entries.forEach { entry ->
        appendLine("[${formatTime(entry.time)}] ${entry.message}")
        entry.table?.let { table ->
            appendLine(table.headers.joinToString(" | "))
            table.rows.forEach { appendLine(it.joinToString(" | ")) }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "הלוג הועתק", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "שיתוף לוג"))
}

/** כותרת הלוג עם סטטוס + העתקה/שיתוף. */
@Composable
fun LogHeader(record: RunRecord?, title: String) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("לוג", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (record != null) {
            StatusBadge(record.status)
            if (record.status != RunStatus.RUNNING) {
                TextButton(onClick = { copyToClipboard(context, title, record.toShareText(title)) }) { Text("העתק") }
                IconButton(onClick = { shareText(context, title, record.toShareText(title)) }) {
                    Icon(Icons.Default.Share, "שיתוף")
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: RunStatus) {
    val color = LogColors.of(status)
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            status.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** הלוג עצמו – נגלל אוטומטית לסוף כשנוספות שורות. הטקסט ניתן לבחירה. */
@Composable
fun LogView(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (entries.isEmpty()) {
            Text(
                "עדיין אין הרצות. לחץ על \"הרץ\" כדי להתחיל.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Surface
        }
        SelectionContainer {
            LazyColumn(state = listState, modifier = Modifier.padding(8.dp)) {
                itemsIndexed(entries) { _, entry -> LogLine(entry) }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row {
            Text(
                formatTime(entry.time),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = LogColors.of(entry.level),
                fontWeight = if (entry.table != null) FontWeight.Bold else null,
            )
        }
        entry.table?.let { table ->
            Column(
                Modifier
                    .padding(top = 4.dp, bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                val columns = table.headers.indices
                val widths = columns.map { c ->
                    (table.rows.map { it.getOrNull(c).orEmpty() } + table.headers[c]).maxOf { it.length }
                }
                @Composable
                fun TableRow(cells: List<String>, bold: Boolean) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        columns.forEach { c ->
                            val cell = cells.getOrNull(c).orEmpty()
                            Text(
                                cell.padEnd(widths[c]),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (bold) FontWeight.Bold else null,
                                color = when {
                                    cell.startsWith("✓") -> LogColors.success()
                                    cell.startsWith("✗") -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                TableRow(table.headers, bold = true)
                table.rows.forEach { TableRow(it, bold = false) }
            }
        }
    }
}
