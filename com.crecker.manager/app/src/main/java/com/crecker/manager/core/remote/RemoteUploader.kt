package com.crecker.manager.core.remote

import java.io.Closeable
import java.io.File

enum class RemoteProtocol(val label: String, val defaultPort: Int) {
    FTP("FTP", 21),
    FTPS("FTPS (TLS)", 21),
    SFTP("SFTP", 22),
}

data class RemoteConfig(
    val protocol: RemoteProtocol,
    val host: String,
    /** null = פורט ברירת המחדל של הפרוטוקול. */
    val port: Int?,
    val user: String,
    val password: String,
    val remoteDir: String,
) {
    val effectivePort get() = port ?: protocol.defaultPort

    fun missingFields(): List<String> = buildList {
        if (host.isBlank()) add("שרת (host)")
        if (user.isBlank()) add("שם משתמש")
        if (password.isEmpty()) add("סיסמה")
        if (remoteDir.isBlank()) add("תיקיית יעד")
    }

    override fun toString() = "${protocol.label} $user@$host:$effectivePort$remoteDir"
}

/**
 * העלאת קבצים לשרת מרוחק. כל המתודות חוסמות (blocking) – יש לקרוא להן מ-Dispatchers.IO.
 * שגיאות נזרקות כ-IOException עם הודעה קריאה.
 */
interface RemoteUploader : Closeable {
    fun connect()

    /** מוודא שהתיקייה קיימת (יוצר אותה ואת ההורים שלה במידת הצורך) ונכנס אליה. מחזיר true אם נוצרה. */
    fun ensureDir(path: String): Boolean

    /**
     * מעלה בשם זמני (`name.tmp`) ואז משנה לשם הסופי – כך שהאתר אף פעם לא קורא קובץ חצי-מועלה.
     */
    fun uploadAtomic(local: File, remoteName: String)

    companion object {
        fun create(config: RemoteConfig): RemoteUploader = when (config.protocol) {
            RemoteProtocol.FTP, RemoteProtocol.FTPS -> FtpUploader(config)
            RemoteProtocol.SFTP -> SftpUploader(config)
        }
    }
}
