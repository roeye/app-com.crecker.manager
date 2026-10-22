package com.crecker.manager.core.model

import android.content.Context
import com.crecker.manager.core.settings.ActionSettings
import java.io.File

enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

data class LogTable(val headers: List<String>, val rows: List<List<String>>)

data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val message: String,
    val table: LogTable? = null,
)

enum class RunStatus(val label: String) {
    RUNNING("רץ…"),
    SUCCESS("הצלחה"),
    PARTIAL("הצלחה חלקית"),
    FAILED("נכשל"),
    CANCELLED("בוטל"),
}

enum class RunTrigger(val label: String) { MANUAL("ידני"), SCHEDULED("מתוזמן") }

data class RunRecord(
    val id: String,
    val systemId: String,
    val actionId: String,
    val trigger: RunTrigger,
    val startedAt: Long,
    val endedAt: Long?,
    val status: RunStatus,
    val entries: List<LogEntry>,
)

interface ActionLog {
    fun info(message: String)
    fun success(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun table(title: String, headers: List<String>, rows: List<List<String>>)
}

/** מה שפעולה מקבלת בזמן ריצה. */
class ActionContext(
    val appContext: Context,
    val settings: ActionSettings,
    /** תיקייה זמנית ייחודית להרצה – נמחקת אוטומטית בסוף (גם בכשל/ביטול). */
    val workDir: File,
    val log: ActionLog,
    val trigger: RunTrigger,
)

fun actionKey(systemId: String, actionId: String) = "$systemId/$actionId"
