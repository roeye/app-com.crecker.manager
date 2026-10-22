package com.crecker.manager.systems.paisresults

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.crecker.manager.R
import com.crecker.manager.core.model.ActionContext
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.net.HttpFetcher
import com.crecker.manager.core.remote.RemoteConfig
import com.crecker.manager.core.remote.RemoteSettings
import com.crecker.manager.core.remote.RemoteUploader
import com.crecker.manager.core.settings.ActionSettings
import com.crecker.manager.core.ui.RemoteSettingsSection
import com.crecker.manager.core.ui.ScheduleSection
import com.crecker.manager.core.ui.SettingsScaffold
import com.crecker.manager.core.ui.rememberRemoteFormState
import com.crecker.manager.core.ui.rememberScheduleFormState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File

/**
 * מוריד את 10 קבצי הפיס, בודק שכל אחד תקין, ומעלה אותם לשרת של האתר.
 * ראו docs/systems/paisresults.md.
 */
object CronDataRetrieval {
    const val ID = "cron_data_retrieval"
    const val DEFAULT_REMOTE_DIR = "/paisresults.co.il/data"
    const val KEY_ONLY_IF_ALL_VALID = "only_if_all_valid"

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2_000L

    val action = SystemAction.Logged(
        id = ID,
        title = R.string.pais_cron_title,
        icon = Icons.Default.Refresh,
        description = R.string.pais_cron_description,
        schedulable = true,
        options = { settings -> OnlyIfAllValidOption(settings) },
        settingsScreen = { settings, onBack -> CronSettingsScreen(settings, onBack) },
        run = { execute() },
    )

    private class Download(val file: PaisFile, val size: Int?, val error: String?) {
        val ok get() = error == null
    }

    private suspend fun ActionContext.execute(): RunStatus {
        val remote = RemoteSettings.load(settings, DEFAULT_REMOTE_DIR)
        val missing = remote.missingFields()
        if (missing.isNotEmpty()) {
            log.error(getString(R.string.pais_log_missing_settings, missing.joinToString { getString(it) }))
            return RunStatus.FAILED
        }
        val onlyIfAllValid = settings.getBoolean(KEY_ONLY_IF_ALL_VALID, false)

        // שלב 1 – הורדה
        log.info(getString(R.string.pais_log_step_download, PAIS_FILES.size))
        val downloads = PAIS_FILES.map { download(it) }
        val valid = downloads.filter { it.ok }
        val invalidCount = downloads.size - valid.size
        if (invalidCount == 0) log.success(getString(R.string.pais_log_all_valid, downloads.size))
        else log.warn(getString(R.string.pais_log_some_invalid, valid.size, downloads.size, invalidCount))

        val uploadErrors = mutableMapOf<String, String>()
        val uploaded = mutableSetOf<String>()
        val status: RunStatus = when {
            valid.isEmpty() -> {
                log.error(getString(R.string.pais_log_none_valid))
                RunStatus.FAILED
            }
            invalidCount > 0 && onlyIfAllValid -> {
                log.error(getString(R.string.pais_log_strict_abort))
                RunStatus.FAILED
            }
            else -> {
                // שלב 2 – העלאה
                if (invalidCount > 0) log.warn(getString(R.string.pais_log_skip_invalid))
                log.info(getString(R.string.pais_log_step_upload, remote.toString()))
                upload(remote, valid, uploaded, uploadErrors)
                when {
                    uploaded.size == PAIS_FILES.size -> RunStatus.SUCCESS
                    uploaded.isNotEmpty() -> RunStatus.PARTIAL
                    else -> RunStatus.FAILED
                }
            }
        }

        log.table(
            title = getString(R.string.pais_summary),
            headers = listOf(
                getString(R.string.pais_col_file),
                getString(R.string.pais_col_size),
                getString(R.string.pais_col_download),
                getString(R.string.pais_col_upload),
            ),
            rows = downloads.map { d ->
                listOf(
                    d.file.name,
                    d.size?.let(::formatSize) ?: "—",
                    if (d.ok) "✓" else "✗ ${d.error}",
                    when (d.file.name) {
                        in uploaded -> "✓"
                        in uploadErrors -> "✗ ${uploadErrors[d.file.name]}"
                        else -> "—"
                    },
                )
            },
        )
        return status
    }

    /** הורדה עם ניסיונות חוזרים. קובץ תקין נשמר בתיקייה הזמנית בשם היעד שלו. */
    private suspend fun ActionContext.download(file: PaisFile): Download {
        var lastError = ""
        for (attempt in 1..MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            var size: Int? = null
            try {
                val result = HttpFetcher.get(file.url)
                size = result.bytes.size
                val problem = file.validate(result.code, result.bytes)
                if (problem == null) {
                    File(workDir, file.name).writeBytes(result.bytes)
                    log.success("↓ ${file.name} – ${formatSize(result.bytes.size)}")
                    return Download(file, result.bytes.size, null)
                }
                lastError = getString(problem.messageRes, *problem.args)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.describe(appContext)
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn(getString(R.string.pais_log_retry, file.name, lastError, attempt + 1, MAX_ATTEMPTS))
                delay(RETRY_DELAY_MS)
            } else {
                log.error("✗ ${file.name}: $lastError")
                return Download(file, size, lastError)
            }
        }
        error("unreachable")
    }

    private suspend fun ActionContext.upload(
        remote: RemoteConfig,
        files: List<Download>,
        uploaded: MutableSet<String>,
        errors: MutableMap<String, String>,
    ) {
        try {
            RemoteUploader.create(remote).use { uploader ->
                runInterruptible { uploader.connect() }
                log.success(getString(R.string.pais_log_connected))
                val created = runInterruptible { uploader.ensureDir(remote.remoteDir) }
                if (created) log.info(getString(R.string.pais_log_dir_created, remote.remoteDir))
                for (d in files) {
                    currentCoroutineContext().ensureActive()
                    try {
                        runInterruptible { uploader.uploadAtomic(File(workDir, d.file.name), d.file.name) }
                        uploaded += d.file.name
                        log.success("↑ ${d.file.name}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = e.describe(appContext)
                        errors[d.file.name] = message
                        log.error(getString(R.string.pais_log_upload_failed, d.file.name, message))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // כשל בחיבור / בתיקייה – אף קובץ שנותר לא הועלה
            log.error("✗ ${e.describe(appContext)}")
            val noConnection = getString(R.string.pais_no_connection)
            files.filter { it.file.name !in uploaded }.forEach { errors[it.file.name] = noConnection }
        }
    }
}

@Composable
private fun OnlyIfAllValidOption(settings: ActionSettings) {
    val scope = rememberCoroutineScope()
    val flow = remember(settings) { settings.booleanFlow(CronDataRetrieval.KEY_ONLY_IF_ALL_VALID, false) }
    val checked by flow.collectAsState(initial = false)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = { scope.launch { settings.putBoolean(CronDataRetrieval.KEY_ONLY_IF_ALL_VALID, it) } },
        )
        Column {
            Text(stringResource(R.string.pais_only_if_all_valid))
            Text(
                stringResource(R.string.pais_only_if_all_valid_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CronSettingsScreen(settings: ActionSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val remote = rememberRemoteFormState(settings, CronDataRetrieval.DEFAULT_REMOTE_DIR)
    val schedule = rememberScheduleFormState(settings)
    SettingsScaffold(
        title = stringResource(R.string.pais_settings_title),
        loaded = remote.loaded && schedule.loaded,
        onBack = onBack,
        onSave = {
            remote.validationError() ?: run {
                remote.save()
                schedule.saveAndApply(context)
                null
            }
        },
    ) {
        RemoteSettingsSection(remote)
        ScheduleSection(schedule)
    }
}
