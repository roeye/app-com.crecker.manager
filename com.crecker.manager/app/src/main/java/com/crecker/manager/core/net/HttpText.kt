package com.crecker.manager.core.net

import java.nio.charset.Charset

/** התוכן כטקסט – לפי ה-charset שב-Content-Type, אחרת UTF-8. */
fun HttpResult.text(): String {
    val charset = contentType
        ?.substringAfter("charset=", "")
        ?.substringBefore(';')
        ?.trim('"', ' ')
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        ?: Charsets.UTF_8
    return String(bytes, charset).removePrefix(BOM)
}

private val BOM = Char(0xFEFF).toString()

val HttpResult.isHtml get() = contentType?.contains("html", ignoreCase = true) == true

private val dropBlocks = Regex("(?is)<(script|style|head)\\b.*?</\\1\\s*>")
private val comments = Regex("(?s)<!--.*?-->")
// כולל ה-\n שאחרי התגית – PHP מדפיס בדרך כלל "…<br>\n", וזו שורה אחת ולא שתיים
private val lineBreaks = Regex("(?i)(<br\\s*/?>|</(p|div|tr|li|h[1-6]|pre|table|ul|ol)\\s*>)[ \\t]*\\n?")
private val cellBreaks = Regex("(?i)</t[dh]\\s*>")
private val tags = Regex("<[^>]*>")
private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")
private val trailingSpaces = Regex("[ \\t]+\\n")
private val manyBlankLines = Regex("\\n{3,}")

/**
 * פלט HTML (למשל של סקריפט PHP) כטקסט קריא: `<br>` וסוגרי בלוקים → שורה חדשה, תגיות נמחקות, entities מפוענחים.
 * רווחים ושורות חדשות שכבר בטקסט **נשמרים** – PHP שולח `text/html` גם כשהסקריפט מדפיס טקסט רגיל עם `\n`.
 */
fun htmlToText(html: String): String = html
    .replace("\r\n", "\n")
    .replace(dropBlocks, "")
    .replace(comments, "")
    .replace(lineBreaks, "\n")
    .replace(cellBreaks, "\t")
    .replace(tags, "")
    .decodeEntities()
    .replace(trailingSpaces, "\n")
    .replace(manyBlankLines, "\n\n")
    .trim()

private fun String.decodeEntities(): String = this
    .replace(numericEntity) { m ->
        val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
        code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
    }
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&amp;", "&") // אחרון – כדי ש-"&amp;lt;" יישאר "&lt;"

/** שגיאה/אזהרה של PHP בפלט (כפי ש-PHP מדפיס כש-display_errors פעיל). */
enum class PhpProblem { FATAL, WARNING }

private val phpFatal = Regex("(?m)\\b(Fatal error|Parse error)\\s*:")
private val phpWarning = Regex("(?m)\\b(Warning|Notice|Deprecated)\\s*:")

/** הבעיה החמורה ביותר בפלט (אחרי [htmlToText]), או null. */
fun phpProblemIn(text: String): PhpProblem? = when {
    phpFatal.containsMatchIn(text) -> PhpProblem.FATAL
    phpWarning.containsMatchIn(text) -> PhpProblem.WARNING
    else -> null
}
