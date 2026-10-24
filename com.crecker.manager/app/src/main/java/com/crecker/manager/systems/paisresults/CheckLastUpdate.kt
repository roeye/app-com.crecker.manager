package com.crecker.manager.systems.paisresults

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
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.model.formatDateTime
import com.crecker.manager.core.model.formatElapsed
import com.crecker.manager.core.remote.RemoteConfig
import com.crecker.manager.core.remote.RemoteFileInfo
import com.crecker.manager.core.remote.RemoteUploader
import com.crecker.manager.core.settings.ActionSettings
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
 * בודק בשרת מתי כל אחד מ-10 הקבצים עודכן לאחרונה (שעון ישראל + כמה זמן עבר).
 * קובץ שלא עודכן יותר מהסף מסומן כישן – כך הרצה מתוזמנת מתריעה כשה-cron הפסיק לעדכן.
 * ראו docs/systems/paisresults.md.
 */
object CheckLastUpdate {
    const val ID = "check_last_update"
    const val KEY_STALE_HOURS = "stale_hours"
    const val DEFAULT_STALE_HOURS = 24
    const val MAX_STALE_HOURS = 24 * 365

    val action = SystemAction.Logged(
        id = ID,
        title = R.string.pais_check_title,
        icon = Icons.Default.Update,
        description = R.string.pais_check_description,
        schedulable = true,
        settingsScreen = { settings, onBack -> CheckSettingsScreen(settings, onBack) },
        run = { execute() },
    )

    private class FileCheck(val file: PaisFile, val info: RemoteFileInfo?, val freshness: Freshness, val error: String?)

    private suspend fun ActionContext.execute(): RunStatus {
        val remote = loadPaisRemote() ?: return RunStatus.FAILED
        val staleHours = settings.getInt(KEY_STALE_HOURS) ?: DEFAULT_STALE_HOURS
        val staleAfter = Duration.ofHours(staleHours.toLong())
        log.info(getString(R.string.pais_check_log_start, PAIS_FILES.size, remote.toString(), staleHours))

        // נקודת ייחוס אחת לכל הקבצים – כדי ש"זמן שחלף" יהיה עקבי בטבלה
        val now = Instant.now()
        val checks = try {
            RemoteUploader.create(remote).use { uploader ->
                runInterruptible { uploader.connect() }
                log.success(getString(R.string.pais_log_connected))
                PAIS_FILES.map { check(uploader, remote, it, now, staleAfter) }
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
        file: PaisFile,
        now: Instant,
        staleAfter: Duration,
    ): FileCheck {
        currentCoroutineContext().ensureActive()
        return try {
            val info = runInterruptible { uploader.fileInfo(remote.remotePath(file.name)) }
            val freshness = freshnessOf(info, now, staleAfter)
            when {
                info == null -> log.error(getString(R.string.pais_check_log_missing, file.name))
                freshness == Freshness.STALE -> log.warn(getString(R.string.pais_check_log_stale, file.name, timeText(info, now)))
                else -> log.success("✓ ${file.name} – ${timeText(info, now)}")
            }
            FileCheck(file, info, freshness, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.describe(appContext)
            log.error("✗ ${file.name}: $message")
            FileCheck(file, null, Freshness.ERROR, message)
        }
    }

    /** "24/10/2026 14:05:32 (לפני 2 שע׳ 15 דק׳)" */
    private fun ActionContext.timeText(info: RemoteFileInfo, now: Instant) =
        "${formatDateTime(info.modified, PaisResultsSystem.ISRAEL_ZONE)} (${formatElapsed(appContext, info.modified, now)})"

    private fun ActionContext.summarize(checks: List<FileCheck>, now: Instant) {
        val counts = checks.groupingBy { it.freshness }.eachCount()
        log.info(
            getString(
                R.string.pais_check_log_counts,
                counts[Freshness.FRESH] ?: 0,
                counts[Freshness.STALE] ?: 0,
                (counts[Freshness.MISSING] ?: 0) + (counts[Freshness.ERROR] ?: 0),
            ),
        )
        checks.filter { it.info != null }.minByOrNull { it.info!!.modified }?.let { oldest ->
            val message = getString(R.string.pais_check_log_oldest, oldest.file.name, timeText(oldest.info!!, now))
            if (oldest.freshness == Freshness.STALE) log.warn(message) else log.info(message)
        }

        log.table(
            title = getString(R.string.pais_summary),
            headers = listOf(
                getString(R.string.pais_col_file),
                getString(R.string.pais_col_last_update),
                getString(R.string.pais_col_elapsed),
                getString(R.string.pais_col_size),
                getString(R.string.pais_col_status),
            ),
            rows = checks.map { c ->
                listOf(
                    c.file.name,
                    c.info?.let { formatDateTime(it.modified, PaisResultsSystem.ISRAEL_ZONE) } ?: "—",
                    c.info?.let { formatElapsed(appContext, it.modified, now) } ?: "—",
                    c.info?.size?.let { formatSize(it) } ?: "—",
                    when (c.freshness) {
                        Freshness.FRESH -> "✓"
                        Freshness.STALE -> "⚠ " + getString(R.string.pais_check_stale)
                        Freshness.MISSING -> "✗ " + getString(R.string.pais_check_not_found)
                        Freshness.ERROR -> "✗ ${c.error}"
                    },
                )
            },
        )
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

@Composable
private fun CheckSettingsScreen(settings: ActionSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val schedule = rememberScheduleFormState(settings)
    // null = עוד נטען
    var staleHours by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings) {
        staleHours = (settings.getInt(CheckLastUpdate.KEY_STALE_HOURS) ?: CheckLastUpdate.DEFAULT_STALE_HOURS).toString()
    }
    SettingsScaffold(
        title = stringResource(R.string.pais_check_settings_title),
        loaded = schedule.loaded && staleHours != null,
        onBack = onBack,
        onSave = save@{
            val hours = staleHours?.toIntOrNull()?.takeIf { it in 1..CheckLastUpdate.MAX_STALE_HOURS }
                ?: return@save R.string.pais_check_error_hours
            settings.putInt(CheckLastUpdate.KEY_STALE_HOURS, hours)
            schedule.saveAndApply(context)
            null
        },
    ) {
        RemoteInSystemSettingsNote()
        SectionTitle(stringResource(R.string.pais_check_section))
        OutlinedTextField(
            value = staleHours.orEmpty(),
            onValueChange = { staleHours = it.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.pais_check_stale_hours)) },
            supportingText = {
                Text(stringResource(R.string.pais_check_stale_hours_hint, CheckLastUpdate.DEFAULT_STALE_HOURS, CheckLastUpdate.MAX_STALE_HOURS))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ScheduleSection(schedule)
    }
}
