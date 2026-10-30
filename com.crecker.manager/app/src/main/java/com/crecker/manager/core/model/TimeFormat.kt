package com.crecker.manager.core.model

import android.content.Context
import com.crecker.manager.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** שעון ישראל (כולל מעבר אוטומטי לשעון קיץ) – האתרים מציגים זמנים לפיו. */
val ISRAEL_ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US)

/** תאריך ושעה באזור הזמן [zone] (למשל `Asia/Jerusalem` – כולל שעון קיץ). */
fun formatDateTime(instant: Instant, zone: ZoneId): String = dateTimeFormatter.withZone(zone).format(instant)

/**
 * פירוק משך זמן לתצוגה: עד שתי היחידות הגדולות ביותר שאינן אפס (ימים+שעות, שעות+דקות או דקות).
 * כל זוג הוא (string res של היחידה, ערך). רשימה ריקה = פחות מדקה. הסימן מתעלם (ראו [formatElapsed]).
 */
fun elapsedParts(elapsed: Duration): List<Pair<Int, Long>> {
    val total = elapsed.abs()
    val parts = listOf(
        R.string.time_days to total.toDays(),
        R.string.time_hours to total.toHours() % 24,
        R.string.time_minutes to total.toMinutes() % 60,
    )
    val first = parts.indexOfFirst { it.second > 0 }
    if (first < 0) return emptyList()
    return parts.drop(first).take(2).filter { it.second > 0 }
}

/**
 * כמה זמן עבר מ-[from] עד [now], למשל "לפני 2 ימ׳ 3 שע׳".
 * זמן עתידי (שעון לא מסונכרן בין השרת למכשיר) מוצג כ"בעתיד".
 */
fun formatElapsed(context: Context, from: Instant, now: Instant): String {
    val elapsed = Duration.between(from, now)
    val parts = elapsedParts(elapsed)
    if (parts.isEmpty()) return context.getString(R.string.time_less_than_minute_ago)
    val text = parts.joinToString(" ") { (res, value) -> context.getString(res, value) }
    return context.getString(if (elapsed.isNegative) R.string.time_in_future else R.string.time_ago, text)
}
