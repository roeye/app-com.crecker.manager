package com.crecker.manager.core.model

import android.content.Context
import androidx.annotation.StringRes
import com.crecker.manager.R
import com.crecker.manager.core.settings.ActionSettings
import com.crecker.manager.core.settings.SystemSettings
import java.io.File

enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

data class LogTable(val headers: List<String>, val rows: List<List<String>>)

data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val message: String,
    val table: LogTable? = null,
)

enum class RunStatus(@StringRes val label: Int) {
    RUNNING(R.string.status_running),
    SUCCESS(R.string.status_success),
    PARTIAL(R.string.status_partial),
    FAILED(R.string.status_failed),
    CANCELLED(R.string.status_cancelled),
}

enum class RunTrigger(@StringRes val label: Int) {
    MANUAL(R.string.trigger_manual),
    SCHEDULED(R.string.trigger_scheduled),
}

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

/** הודעות הלוג נשמרות כטקסט – בשפה שהייתה פעילה בזמן ההרצה. */
interface ActionLog {
    fun info(message: String)
    fun success(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun table(title: String, headers: List<String>, rows: List<List<String>>)
}

/** מה שפעולה מקבלת בזמן ריצה. */
class ActionContext(
    /** Context בשפת האפליקציה – להודעות לוג השתמשו ב-[getString]. */
    val appContext: Context,
    /** ההגדרות של הפעולה הזו בלבד. */
    val settings: ActionSettings,
    /** ההגדרות של המערכת – משותפות לכל הפעולות שלה (למשל פרטי החיבור לשרת). */
    val systemSettings: SystemSettings,
    /** תיקייה זמנית ייחודית להרצה – נמחקת אוטומטית בסוף (גם בכשל/ביטול). */
    val workDir: File,
    val log: ActionLog,
    val trigger: RunTrigger,
) {
    fun getString(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)
}

fun actionKey(systemId: String, actionId: String) = "$systemId/$actionId"
