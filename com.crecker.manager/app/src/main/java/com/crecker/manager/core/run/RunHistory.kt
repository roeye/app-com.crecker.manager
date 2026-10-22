package com.crecker.manager.core.run

import android.content.Context
import com.crecker.manager.core.model.LogEntry
import com.crecker.manager.core.model.LogLevel
import com.crecker.manager.core.model.LogTable
import com.crecker.manager.core.model.RunRecord
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.RunTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** שמירת היסטוריית הרצות כקבצי JSON: `files/run-history/<system>__<action>/<startedAt>_<id>.json`. */
class RunHistory(context: Context, private val keepPerAction: Int = 20) {
    private val root = File(context.filesDir, "run-history")
    private val version = MutableStateFlow(0)

    private fun dir(systemId: String, actionId: String) = File(root, "${systemId}__$actionId")

    suspend fun save(record: RunRecord) = withContext(Dispatchers.IO) {
        val dir = dir(record.systemId, record.actionId).apply { mkdirs() }
        File(dir, "${record.startedAt}_${record.id}.json").writeText(record.toJson().toString())
        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(keepPerAction)
            ?.forEach { it.delete() }
        version.value++
    }

    /** ההרצות של פעולה, מהחדשה לישנה. מתעדכן אוטומטית אחרי כל [save]. */
    fun observe(systemId: String, actionId: String): Flow<List<RunRecord>> =
        version.map { list(systemId, actionId) }.flowOn(Dispatchers.IO)

    private fun list(systemId: String, actionId: String): List<RunRecord> =
        dir(systemId, actionId).listFiles()
            ?.sortedByDescending { it.name }
            ?.mapNotNull { file -> runCatching { file.readText().toRunRecord() }.getOrNull() }
            .orEmpty()

    private fun RunRecord.toJson() = JSONObject()
        .put("id", id)
        .put("systemId", systemId)
        .put("actionId", actionId)
        .put("trigger", trigger.name)
        .put("startedAt", startedAt)
        .put("endedAt", endedAt ?: JSONObject.NULL)
        .put("status", status.name)
        .put("entries", JSONArray(entries.map { it.toJson() }))

    private fun LogEntry.toJson() = JSONObject()
        .put("time", time)
        .put("level", level.name)
        .put("message", message)
        .apply {
            table?.let { t ->
                put("table", JSONObject()
                    .put("headers", JSONArray(t.headers))
                    .put("rows", JSONArray(t.rows.map { JSONArray(it) })))
            }
        }

    private fun String.toRunRecord(): RunRecord {
        val o = JSONObject(this)
        val entries = o.getJSONArray("entries")
        return RunRecord(
            id = o.getString("id"),
            systemId = o.getString("systemId"),
            actionId = o.getString("actionId"),
            trigger = RunTrigger.valueOf(o.getString("trigger")),
            startedAt = o.getLong("startedAt"),
            endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
            status = RunStatus.valueOf(o.getString("status")),
            entries = List(entries.length()) { entries.getJSONObject(it).toLogEntry() },
        )
    }

    private fun JSONObject.toLogEntry() = LogEntry(
        time = getLong("time"),
        level = LogLevel.valueOf(getString("level")),
        message = getString("message"),
        table = optJSONObject("table")?.let { t ->
            val rows = t.getJSONArray("rows")
            LogTable(
                headers = t.getJSONArray("headers").strings(),
                rows = List(rows.length()) { rows.getJSONArray(it).strings() },
            )
        },
    )

    private fun JSONArray.strings() = List(length()) { getString(it) }
}
