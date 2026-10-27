package com.crecker.manager.systems.mail

import java.util.Base64
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage

/**
 * קובץ מצורף.
 * @param path המיקום בעץ ה-MIME (אינדקס בכל רמה של multipart) – כדי להוריד אותו בהמשך בלי לשמור את התוכן.
 * @param size גודל משוער בבתים (בקידוד ההעברה), או -1 אם לא ידוע.
 */
data class MailAttachment(val path: List<Int>, val name: String, val mimeType: String, val size: Int)

/** תוכן הודעה לתצוגה. */
data class MailContent(
    val subject: String,
    val from: String,
    val to: List<String>,
    val cc: List<String>,
    /** epoch millis, או null אם לא ידוע. */
    val date: Long?,
    val text: String?,
    /** HTML שבו תמונות `cid:` כבר הוחלפו ב-data URI. */
    val html: String?,
    val attachments: List<MailAttachment>,
)

object MailParser {
    /** תמונה מוטמעת (cid) גדולה מזה לא מוטמעת ב-HTML – נשארת כקובץ מצורף בלבד. */
    private const val MAX_INLINE_IMAGE_BYTES = 3 * 1024 * 1024

    private class Builder {
        var text: String? = null
        var html: String? = null
        val attachments = mutableListOf<MailAttachment>()
        val inlineImages = mutableMapOf<String, String>()
    }

    fun parse(message: MimeMessage): MailContent {
        val builder = Builder()
        walk(message, emptyList(), builder)
        return MailContent(
            subject = message.subject.orEmpty(),
            from = message.from?.joinToString { it.display() }.orEmpty(),
            to = message.getRecipients(Message.RecipientType.TO)?.map { it.display() }.orEmpty(),
            cc = message.getRecipients(Message.RecipientType.CC)?.map { it.display() }.orEmpty(),
            date = (message.sentDate ?: message.receivedDate)?.time,
            text = builder.text,
            html = builder.html?.let { resolveCid(it, builder.inlineImages) },
            attachments = builder.attachments,
        )
    }

    private fun javax.mail.Address.display(): String =
        (this as? InternetAddress)?.let { a -> a.personal?.takeIf { it.isNotBlank() }?.let { "$it <${a.address}>" } ?: a.address }
            ?: toString()

    private fun walk(part: Part, path: List<Int>, out: Builder) {
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as Multipart
            for (i in 0 until multipart.count) walk(multipart.getBodyPart(i), path + i, out)
            return
        }
        val disposition = runCatching { part.disposition }.getOrNull()
        val fileName = runCatching { part.fileName }.getOrNull()
        val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true)
        val contentId = (part as? MimeBodyPart)?.contentID?.trim()?.removeSurrounding("<", ">")

        when {
            !isAttachment && fileName == null && part.isMimeType("text/plain") && out.text == null -> out.text = readText(part)
            !isAttachment && fileName == null && part.isMimeType("text/html") && out.html == null -> out.html = readText(part)
            !isAttachment && contentId != null && part.isMimeType("image/*") -> {
                val bytes = part.inputStream.use { it.readBytes() }
                if (bytes.size <= MAX_INLINE_IMAGE_BYTES) {
                    out.inlineImages[contentId] = "data:${baseType(part.contentType)};base64," + Base64.getEncoder().encodeToString(bytes)
                } else {
                    out.attachments += MailAttachment(path, fileName ?: contentId, baseType(part.contentType), decodedSize(part))
                }
            }
            else -> out.attachments += MailAttachment(
                path = path,
                name = fileName ?: if (part.isMimeType("message/rfc822")) "message.eml" else "attachment-${out.attachments.size + 1}",
                mimeType = baseType(part.contentType),
                size = decodedSize(part),
            )
        }
    }

    /** הגודל שהשרת מדווח הוא בקידוד ההעברה; ב-base64 הקובץ עצמו קטן בערך ברבע. */
    private fun decodedSize(part: Part): Int {
        val size = part.size
        val base64 = (part as? MimeBodyPart)?.encoding.equals("base64", ignoreCase = true)
        return if (size > 0 && base64) size / 4 * 3 else size
    }

    /** קידוד לא מוכר (charset) – נופלים ל-UTF-8 במקום להיכשל. */
    private fun readText(part: Part): String =
        runCatching { part.content as String }.getOrElse { part.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } }

    /** "text/html; charset=utf-8" → "text/html". */
    fun baseType(contentType: String?): String =
        contentType?.substringBefore(';')?.trim()?.lowercase()?.ifBlank { null } ?: "application/octet-stream"

    private val cidRef = Regex("""cid:([^"'\s)>]+)""", RegexOption.IGNORE_CASE)

    /** מחליף הפניות `cid:...` לתמונות שבתוך ההודעה ב-data URI – כך הן מוצגות גם כשהרשת חסומה. */
    fun resolveCid(html: String, images: Map<String, String>): String =
        if (images.isEmpty()) html else cidRef.replace(html) { m -> images[m.groupValues[1]] ?: m.value }

    private val remoteRefs = listOf(
        // src / srcset / background / poster / data של תגיות – תמונות, iframe, וידאו, object
        Regex("""\b(?:src|srcset|background|poster|data)\s*=\s*["']?\s*(?:https?:)?//""", RegexOption.IGNORE_CASE),
        // CSS: url(...) ו-@import
        Regex("""url\(\s*["']?\s*(?:https?:)?//""", RegexOption.IGNORE_CASE),
        Regex("""@import\s+["']?\s*(?:https?:)?//""", RegexOption.IGNORE_CASE),
        // <link rel=stylesheet href=...> (קישורים רגילים, <a href>, לא נטענים לבד)
        Regex("""<link\b[^>]*\bhref\s*=\s*["']?\s*(?:https?:)?//""", RegexOption.IGNORE_CASE),
    )

    /** האם ה-HTML טוען משהו מהרשת (תמונות מעקב, CSS, גופנים…) – כלומר יש מה לחסום. */
    fun hasRemoteContent(html: String): Boolean = remoteRefs.any { it.containsMatchIn(html) }
}
