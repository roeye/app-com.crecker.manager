package com.crecker.manager.systems.paisresults

import androidx.annotation.StringRes
import com.crecker.manager.R

/**
 * קובץ שמורד מ-pais.co.il ונשמר בשם [name].
 *
 * בדיקת תקינות: HTTP 200, לפחות [minBytes] בתים, ומכיל את [marker] (כדי לזהות דף שגיאה/חסימה).
 * הסף והסמן נקבעו לפי בדיקה של התשובות בפועל (22/10/2026) – ראו docs/systems/paisresults.md.
 */
data class PaisFile(val name: String, val url: String, val minBytes: Int, val marker: String)

/** קבצי nextraffle הם JSON קצר (~90-160 בתים), לכן הסף שלהם נמוך מ-1KB. */
private const val NEXT_RAFFLE_MIN_BYTES = 50
private const val NEXT_RAFFLE_MARKER = "nextLottoryDate" // sic – כך זה מאוית בתשובה של pais
private const val RSS_MIN_BYTES = 1024
private const val RSS_MARKER = "<item>"

val PAIS_FILES = listOf(
    PaisFile("nextraffle-lotto.xml", "https://www.pais.co.il/include/getNextLotteryDate.ashx?type=1", NEXT_RAFFLE_MIN_BYTES, NEXT_RAFFLE_MARKER),
    PaisFile("nextraffle-chance.xml", "https://www.pais.co.il/include/getNextLotteryDate.ashx?type=3", NEXT_RAFFLE_MIN_BYTES, NEXT_RAFFLE_MARKER),
    PaisFile("nextraffle-777.xml", "https://www.pais.co.il/include/getNextLotteryDate.ashx?type=5", NEXT_RAFFLE_MIN_BYTES, NEXT_RAFFLE_MARKER),
    PaisFile("nextraffle-123.xml", "https://www.pais.co.il/include/getNextLotteryDate.ashx?type=4", NEXT_RAFFLE_MIN_BYTES, NEXT_RAFFLE_MARKER),
    PaisFile("nextraffle-manui.xml", "https://www.pais.co.il/include/getNextLotteryDate.ashx?type=6", NEXT_RAFFLE_MIN_BYTES, NEXT_RAFFLE_MARKER),
    PaisFile("rssfeed-lotto.xml", "https://www.pais.co.il/rssfeed.ashx?lottery=lotto", RSS_MIN_BYTES, RSS_MARKER),
    PaisFile("rssfeed-chance.xml", "https://www.pais.co.il/rssfeed.ashx?lottery=Chance", RSS_MIN_BYTES, RSS_MARKER),
    PaisFile("rssfeed-777.xml", "https://www.pais.co.il/rssfeed.ashx?lottery=777", RSS_MIN_BYTES, RSS_MARKER),
    PaisFile("rssfeed-123.xml", "https://www.pais.co.il/rssfeed.ashx?lottery=123", RSS_MIN_BYTES, RSS_MARKER),
    // דף HTML (לא RSS) עם תוצאות מנוי
    PaisFile("rssfeed-manui.xml", "https://www.pais.co.il/Subscriber/showMoreResults.aspx", RSS_MIN_BYTES, "lot_results_item"),
)

/** בעיית תקינות – הודעה מתורגמת (string res + ארגומנטים). */
class ValidationProblem(@StringRes val messageRes: Int, vararg val args: Any)

/** מחזיר את הבעיה, או null אם התוכן תקין. */
fun PaisFile.validate(httpCode: Int, bytes: ByteArray): ValidationProblem? = when {
    httpCode != 200 -> ValidationProblem(R.string.pais_invalid_http, httpCode)
    bytes.size < minBytes -> ValidationProblem(R.string.pais_invalid_small, bytes.size, minBytes)
    !String(bytes, Charsets.UTF_8).contains(marker) -> ValidationProblem(R.string.pais_invalid_marker, marker)
    else -> null
}

fun formatSize(bytes: Int): String =
    if (bytes < 1024) "$bytes B" else "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)
