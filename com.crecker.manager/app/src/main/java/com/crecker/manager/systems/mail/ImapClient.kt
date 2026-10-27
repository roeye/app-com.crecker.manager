package com.crecker.manager.systems.mail

import com.crecker.manager.R
import com.crecker.manager.core.model.LocalizedException
import com.crecker.manager.core.model.describe
import com.sun.mail.imap.IMAPFolder
import java.io.Closeable
import java.io.OutputStream
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.net.ssl.SSLException

/** כותרת של הודעה ברשימה (בלי התוכן). */
data class MailHeader(
    val uid: Long,
    val from: String,
    val fromAddress: String,
    val subject: String,
    /** epoch millis (תאריך הקבלה בשרת, או תאריך השליחה אם אין). */
    val date: Long,
    /** האם ההודעה מסומנת כנקראה **בשרת** (\Seen) – האפליקציה רק מציגה, לא משנה. */
    val seen: Boolean,
    val hasAttachments: Boolean,
)

/** מצב תיקייה בשרת: מזהה התיבה, מונים, וההודעות האחרונות (מהחדשה לישנה). */
data class FolderSnapshot(val uidValidity: Long, val total: Int, val unread: Int, val messages: List<MailHeader>)

/**
 * חיבור IMAP לקריאה בלבד.
 *
 * - כל תיקייה נפתחת ב-[Folder.READ_ONLY] (פקודת EXAMINE): השרת לא משנה בה כלום, כולל סימון "נקרא".
 * - התוכן נקרא עם BODY.PEEK (`mail.imap.peek`), כהגנה נוספת.
 * - אין במחלקה שום פעולה שכותבת לשרת (סימון, מחיקה, העברה, העלאה) – וכך צריך להישאר.
 *
 * כל המתודות חוסמות (רשת) – לקרוא מ-Dispatchers.IO בתוך `runInterruptible`.
 */
class ImapClient(private val account: MailAccount, private val password: String) : Closeable {
    private val protocol = if (account.security == MailSecurity.SSL) "imaps" else "imap"
    private val session = Session.getInstance(
        Properties().apply {
            val p = "mail.$protocol"
            put("$p.connectiontimeout", TIMEOUT_MS)
            put("$p.timeout", TIMEOUT_MS)
            put("$p.peek", "true")
            put("$p.ssl.checkserveridentity", "true")
            put("$p.fetchsize", (256 * 1024).toString())
            if (account.security == MailSecurity.STARTTLS) {
                put("$p.starttls.enable", "true")
                put("$p.starttls.required", "true")
            }
        },
    )
    private var store: Store? = null

    fun connect() {
        try {
            store = session.getStore(protocol).apply { connect(account.host, account.effectivePort, account.user, password) }
        } catch (e: Exception) {
            throw mailError(e)
        }
    }

    /** כל התיקיות שאפשר להציג (שמכילות הודעות), לפי השם המלא. */
    fun folders(): List<String> = try {
        connected().defaultFolder.list("*")
            .filter { it.type and Folder.HOLDS_MESSAGES != 0 }
            .map { it.fullName }
            .sortedWith(compareBy({ !it.equals(INBOX, ignoreCase = true) }, { it.lowercase() }))
    } catch (e: Exception) {
        throw mailError(e)
    }

    /** [limit] ההודעות האחרונות בתיקייה, מהחדשה לישנה. */
    fun snapshot(folderName: String, limit: Int): FolderSnapshot = withFolder(folderName) { folder ->
        val total = folder.messageCount
        val messages = if (total == 0) emptyArray() else folder.getMessages(maxOf(1, total - limit + 1), total)
        folder.fetch(
            messages,
            FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(FetchProfile.Item.CONTENT_INFO)
                add(UIDFolder.FetchProfileItem.UID)
            },
        )
        FolderSnapshot(
            uidValidity = folder.uidValidity,
            total = total,
            unread = folder.unreadMessageCount,
            messages = messages.map { m ->
                val from = m.from?.firstOrNull() as? InternetAddress
                MailHeader(
                    uid = folder.getUID(m),
                    from = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address.orEmpty(),
                    fromAddress = from?.address.orEmpty(),
                    subject = m.subject.orEmpty(),
                    date = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
                    seen = m.isSet(Flags.Flag.SEEN),
                    hasAttachments = runCatching { hasAttachments(m) }.getOrDefault(false),
                )
            }.sortedByDescending { it.uid },
        )
    }

    /** מונים בלבד (לבדיקת חיבור). */
    fun counts(folderName: String): Pair<Int, Int> = withFolder(folderName) { it.messageCount to it.unreadMessageCount }

    /**
     * ההודעה [uid] – או null אם היא כבר לא קיימת (נמחקה, או שהתיבה נבנתה מחדש: UIDVALIDITY השתנה).
     * התוכן נטען בעצלות, ולכן [block] רץ כשהתיקייה עדיין פתוחה.
     */
    fun <T> message(folderName: String, uidValidity: Long, uid: Long, block: (MimeMessage) -> T): T? =
        withFolder(folderName) { folder ->
            if (folder.uidValidity != uidValidity) return@withFolder null
            (folder.getMessageByUID(uid) as? MimeMessage)?.let(block)
        }

    /** מוריד את הקובץ המצורף שבמיקום [path] (ראו [MailAttachment.path]) אל [out]. */
    fun downloadAttachment(folderName: String, uidValidity: Long, uid: Long, path: List<Int>, out: OutputStream) {
        val found = message(folderName, uidValidity, uid) { message ->
            var part: Part = message
            for (index in path) part = (part.content as Multipart).getBodyPart(index)
            part.inputStream.use { it.copyTo(out) }
            true
        }
        if (found != true) throw LocalizedException(R.string.mail_message_gone)
    }

    private fun <T> withFolder(name: String, block: (IMAPFolder) -> T): T {
        val folder = try {
            (connected().getFolder(name) as IMAPFolder).apply { open(Folder.READ_ONLY) }
        } catch (e: Exception) {
            if (e is LocalizedException) throw e
            throw LocalizedException(R.string.mail_err_folder, name, e.message?.trim()?.ifBlank { null } ?: e.describe(), cause = e)
        }
        try {
            return block(folder)
        } catch (e: Exception) {
            throw mailError(e)
        } finally {
            runCatching { folder.close(false) } // false = בלי expunge
        }
    }

    private fun connected(): Store = store ?: throw LocalizedException(R.string.remote_err_not_connected)

    override fun close() {
        runCatching { store?.close() }
        store = null
    }

    private fun mailError(e: Exception): Exception = when {
        e is LocalizedException -> e
        e is AuthenticationFailedException -> LocalizedException(R.string.mail_err_login, cause = e)
        e.causes().any { it is CertificateException } ->
            LocalizedException(R.string.mail_err_certificate, account.host, cause = e)
        e.causes().any { it is UnknownHostException } ->
            LocalizedException(R.string.mail_err_unknown_host, account.host, cause = e)
        e.causes().any { it is SSLException } ->
            LocalizedException(R.string.mail_err_connect, e.causes().first { it is SSLException }.describe(), cause = e)
        e is MessagingException -> LocalizedException(R.string.mail_err_connect, (e.cause ?: e).describe(), cause = e)
        else -> e
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { t ->
        (t as? MessagingException)?.nextException ?: t.cause
    }.take(10)

    private companion object {
        const val TIMEOUT_MS = "30000"

        /** לפי BODYSTRUCTURE (שכבר הגיע ב-fetch) – בלי להוריד את התוכן. */
        fun hasAttachments(part: Part): Boolean = when {
            part.isMimeType("multipart/*") -> (part.content as Multipart).let { mp -> (0 until mp.count).any { hasAttachments(mp.getBodyPart(it)) } }
            else -> Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) ||
                (part.fileName != null && !Part.INLINE.equals(part.disposition, ignoreCase = true))
        }
    }
}
