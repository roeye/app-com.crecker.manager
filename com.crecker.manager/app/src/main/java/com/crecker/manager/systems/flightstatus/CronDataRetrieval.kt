package com.crecker.manager.systems.flightstatus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.crecker.manager.R
import com.crecker.manager.core.model.ActionContext
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.model.formatDuration
import com.crecker.manager.core.model.formatSize
import com.crecker.manager.core.net.HttpFetcher
import com.crecker.manager.core.net.PhpProblem
import com.crecker.manager.core.net.htmlToText
import com.crecker.manager.core.net.isHtml
import com.crecker.manager.core.net.phpProblemIn
import com.crecker.manager.core.net.text
import kotlinx.coroutines.CancellationException
import java.time.Duration

/**
 * מריץ ידנית את סקריפט ה-cron של האתר (בקשת GET לכתובת שלו) – למקרה שהרצה של ה-cron job בשרת לא קרתה או נכשלה.
 * מציג את קוד ה-HTTP, זמן הריצה והפלט של הסקריפט. ראו docs/systems/flightstatus.md.
 */
object CronDataRetrieval {
    const val ID = "cron_data_retrieval"
    const val URL = "https://www.flightstatus.co.il/cronjobs/data_retrieval.php"

    /** הסקריפט עושה עבודה אמיתית (משיכת נתונים) – מחכים לו הרבה יותר מבקשה רגילה. */
    private const val TIMEOUT_MS = 5 * 60 * 1000

    /** הפלט נשמר בהיסטוריה – פלט ארוך מזה נחתך. */
    private const val MAX_OUTPUT_CHARS = 30_000

    val action = SystemAction.Logged(
        id = ID,
        title = R.string.flight_cron_title,
        icon = Icons.Default.PlayArrow,
        description = R.string.flight_cron_description,
        run = { execute() },
    )

    private suspend fun ActionContext.execute(): RunStatus {
        log.info(getString(R.string.flight_cron_log_start, URL, TIMEOUT_MS / 60_000))
        // בלי ניסיונות חוזרים: הרצה כפולה של הסקריפט בשרת לא בהכרח בטוחה
        val started = System.nanoTime()
        val result = try {
            HttpFetcher.get(URL, TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = formatDuration(appContext, Duration.ofNanos(System.nanoTime() - started))
            log.error(getString(R.string.flight_cron_log_request_failed, elapsed, e.describe(appContext)))
            return RunStatus.FAILED
        }
        val elapsed = formatDuration(appContext, Duration.ofNanos(System.nanoTime() - started))

        val ok = result.code in 200..299
        val summary = getString(R.string.flight_cron_log_response, result.code, elapsed, formatSize(result.bytes.size))
        if (ok) log.success("✓ $summary") else log.error("✗ $summary")

        val text = result.text().let { if (result.isHtml) htmlToText(it) else it.trim() }
        if (text.isEmpty()) {
            log.info(getString(R.string.flight_cron_log_no_output))
        } else {
            val shown = if (text.length > MAX_OUTPUT_CHARS) {
                text.take(MAX_OUTPUT_CHARS) + "\n…\n" + getString(R.string.flight_cron_output_truncated, MAX_OUTPUT_CHARS, text.length)
            } else {
                text
            }
            log.output(getString(R.string.flight_cron_output_title, result.contentType ?: "—"), shown)
        }

        if (!ok) return RunStatus.FAILED
        return when (phpProblemIn(text)) {
            PhpProblem.FATAL -> {
                log.error(getString(R.string.flight_cron_log_php_fatal))
                RunStatus.FAILED
            }
            PhpProblem.WARNING -> {
                log.warn(getString(R.string.flight_cron_log_php_warning))
                RunStatus.PARTIAL
            }
            null -> RunStatus.SUCCESS
        }
    }
}
