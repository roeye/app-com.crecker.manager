package com.crecker.manager.core.remote

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.crecker.manager.R
import com.crecker.manager.core.model.ActionContext
import com.crecker.manager.core.model.ISRAEL_ZONE
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.model.formatDateTime
import com.crecker.manager.core.model.formatElapsed
import com.crecker.manager.core.model.formatSize
import com.crecker.manager.core.settings.ActionSettings
import com.crecker.manager.core.ui.RemoteInSystemSettingsNote
import com.crecker.manager.core.ui.ScheduleSection
import com.crecker.manager.core.ui.SectionTitle
import com.crecker.manager.core.ui.SettingsScaffold
import com.crecker.manager.core.ui.rememberScheduleFormState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import java.time.Duration
import java.time.Instant

/**
 * פעולה גנרית: בודקת בשרת (לפי חיבור ה-FTP/SFTP שבהגדרות המערכת) מתי כל אחד מ-[files] עודכן לאחרונה –
 * שעון ישראל + כמה זמן עבר. קובץ שלא עודכן יותר מהסף מסומן כישן, כך שהרצה מתוזמנת מתריעה כשהאתר הפסיק להתעדכן.
 * קריאה בלבד: לא מורידה, לא מעלה ולא יוצרת תיקיות.
 *
 * @param systemName שם המערכת – מופיע בהודעות שמפנות להגדרות המערכת.
 * @param defaultRemoteDir תיקיית ברירת המחדל בשרת (כמו בטופס החיבור של המערכת).
 * @param files שמות הקבצים בתוך תיקיית היעד.
 * @param defaultStaleHours ברירת המחדל של הסף בשעות.
 */
class LastUpdateCheck(
    @StringRes description: Int,
    private val systemName: String,
    private val defaultRemoteDir: String,
    private val files: List<String>,
    private val defaultStaleHours: Int,
) {
    val action = SystemAction.Logged(
        id = ID,
        title = R.string.last_update_title,
        icon = Icons.Default.Update,
        description = description,
        schedulable = true,
        settingsScreen = { settings, onBack -> CheckSettingsScreen(settings, onBack) },
        run = { execute() },
    )

    private class FileCheck(val name: String, val info: RemoteFileInfo?, val freshness: Freshness, val error: String?)

    private suspend fun ActionContext.execute(): RunStatus {
        val remote = loadSystemRemote(systemName, defaultRemoteDir) ?: return RunStatus.FAILED
        val staleHours = settings.getInt(KEY_STALE_HOURS) ?: defaultStaleHours
        val staleAfter = Duration.ofHours(staleHours.toLong())
        val resources = appContext.resources
        val threshold = resources.getQuantityString(R.plurals.last_update_log_threshold, staleHours, staleHours)
        log.info(resources.getQuantityString(R.plurals.last_update_log_start, files.size, files.size, remote.toString(), threshold))

        // נקודת ייחוס אחת לכל הקבצים – כדי ש"זמן שחלף" יהיה עקבי בטבלה
        val now = Instant.now()
        val checks = try {
            RemoteUploader.create(remote).use { uploader ->
                runInterruptible { uploader.connect() }
                log.success(getString(R.string.remote_log_connected))
                files.map { check(uploader, remote, it, now, staleAfter) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("✗ ${e.describe(appContext)}")
            return RunStatus.FAILED
        }

        summarize(checks, now)
        return overallStatus(checks.map { it.freshness })
    }

    private suspend fun ActionContext.check(
        uploader: RemoteUploader,
        remote: RemoteConfig,
        name: String,
        now: Instant,
        staleAfter: Duration,
    ): FileCheck {
        currentCoroutineContext().ensureActive()
        return try {
            val info = runInterruptible { uploader.fileInfo(remote.remotePath(name)) }
            val freshness = freshnessOf(info, now, staleAfter)
            when {
                info == null -> log.error(getString(R.string.last_update_log_missing, name))
                freshness == Freshness.STALE -> log.warn(getString(R.string.last_update_log_stale, name, timeText(info, now)))
                else -> log.success("✓ $name – ${timeText(info, now)}")
            }
            FileCheck(name, info, freshness, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.describe(appContext)
            log.error("✗ $name: $message")
            FileCheck(name, null, Freshness.ERROR, message)
        }
    }

    /** "24/10/2026 14:05:32 (לפני 2 שע׳ 15 דק׳)" */
    private fun ActionContext.timeText(info: RemoteFileInfo, now: Instant) =
        "${formatDateTime(info.modified, ISRAEL_ZONE)} (${formatElapsed(appContext, info.modified, now)})"

    private fun ActionContext.summarize(checks: List<FileCheck>, now: Instant) {
        val counts = checks.groupingBy { it.freshness }.eachCount()
        log.info(
            getString(
                R.string.last_update_log_counts,
                counts[Freshness.FRESH] ?: 0,
                counts[Freshness.STALE] ?: 0,
                (counts[Freshness.MISSING] ?: 0) + (counts[Freshness.ERROR] ?: 0),
            ),
        )
        // עם קובץ אחד "הישן ביותר" הוא פשוט הקובץ עצמו – אין טעם בשורה נוספת
        if (checks.size > 1) {
            checks.filter { it.info != null }.minByOrNull { it.info!!.modified }?.let { oldest ->
                val message = getString(R.string.last_update_log_oldest, oldest.name, timeText(oldest.info!!, now))
                if (oldest.freshness == Freshness.STALE) log.warn(message) else log.info(message)
            }
        }

        log.table(
            title = getString(R.string.log_summary),
            headers = listOf(
                getString(R.string.log_col_file),
                getString(R.string.last_update_col_last_update),
                getString(R.string.last_update_col_elapsed),
                getString(R.string.log_col_size),
                getString(R.string.last_update_col_status),
            ),
            rows = checks.map { c ->
                listOf(
                    c.name,
                    c.info?.let { formatDateTime(it.modified, ISRAEL_ZONE) } ?: "—",
                    c.info?.let { formatElapsed(appContext, it.modified, now) } ?: "—",
                    c.info?.size?.let { formatSize(it) } ?: "—",
                    when (c.freshness) {
                        Freshness.FRESH -> "✓"
                        Freshness.STALE -> "⚠ " + getString(R.string.last_update_stale)
                        Freshness.MISSING -> "✗ " + getString(R.string.last_update_not_found)
                        Freshness.ERROR -> "✗ ${c.error}"
                    },
                )
            },
        )
    }

    @Composable
    private fun CheckSettingsScreen(settings: ActionSettings, onBack: () -> Unit) {
        val context = LocalContext.current
        val schedule = rememberScheduleFormState(settings)
        // null = עוד נטען
        var staleHours by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(settings) {
            staleHours = (settings.getInt(KEY_STALE_HOURS) ?: defaultStaleHours).toString()
        }
        SettingsScaffold(
            title = stringResource(R.string.last_update_settings_title),
            loaded = schedule.loaded && staleHours != null,
            onBack = onBack,
            onSave = save@{
                val hours = staleHours?.toIntOrNull()?.takeIf { it in 1..MAX_STALE_HOURS }
                    ?: return@save R.string.last_update_error_hours
                settings.putInt(KEY_STALE_HOURS, hours)
                schedule.saveAndApply(context)
                null
            },
        ) {
            RemoteInSystemSettingsNote(systemName)
            SectionTitle(stringResource(R.string.last_update_section))
            OutlinedTextField(
                value = staleHours.orEmpty(),
                onValueChange = { staleHours = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.last_update_stale_hours)) },
                supportingText = {
                    Text(stringResource(R.string.last_update_stale_hours_hint, defaultStaleHours, MAX_STALE_HOURS))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            ScheduleSection(schedule)
        }
    }

    companion object {
        /** אותו מזהה בכל מערכת – ההגדרות וההיסטוריה ממילא מקושרות גם ל-systemId. */
        const val ID = "check_last_update"
        const val KEY_STALE_HOURS = "stale_hours"
        const val MAX_STALE_HOURS = 24 * 365
    }
}

/** מצב קובץ בבדיקת העדכון. */
enum class Freshness { FRESH, STALE, MISSING, ERROR }

/** null = הקובץ לא קיים. ישן = עבר יותר מ-[staleAfter] מאז העדכון האחרון. */
fun freshnessOf(info: RemoteFileInfo?, now: Instant, staleAfter: Duration): Freshness = when {
    info == null -> Freshness.MISSING
    Duration.between(info.modified, now) > staleAfter -> Freshness.STALE
    else -> Freshness.FRESH
}

/** חסר / שגיאה → נכשל; ישן → הצלחה חלקית; הכל עדכני → הצלחה. */
fun overallStatus(results: Collection<Freshness>): RunStatus = when {
    results.any { it == Freshness.MISSING || it == Freshness.ERROR } -> RunStatus.FAILED
    results.any { it == Freshness.STALE } -> RunStatus.PARTIAL
    else -> RunStatus.SUCCESS
}
