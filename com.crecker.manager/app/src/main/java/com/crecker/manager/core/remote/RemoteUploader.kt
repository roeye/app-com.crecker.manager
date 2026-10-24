package com.crecker.manager.core.remote

import com.crecker.manager.R
import java.io.Closeable
import java.io.File
import java.time.Instant

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

    /** שדות חובה חסרים (R.string.field_*). */
    fun missingFields(): List<Int> = buildList {
        if (host.isBlank()) add(R.string.field_host)
        if (user.isBlank()) add(R.string.field_user)
        if (password.isEmpty()) add(R.string.field_password)
        if (remoteDir.isBlank()) add(R.string.field_remote_dir)
    }

    /** נתיב מלא בשרת לקובץ בתוך [remoteDir]. */
    fun remotePath(name: String) = "${remoteDir.trimEnd('/')}/$name"

    override fun toString() = "${protocol.label} $user@$host:$effectivePort$remoteDir"
}

/** מידע על קובץ בשרת. [size] הוא null אם השרת לא החזיר גודל. */
data class RemoteFileInfo(val modified: Instant, val size: Long?)

/**
 * העלאת קבצים לשרת מרוחק וקריאת מידע עליהם. כל המתודות חוסמות (blocking) – יש לקרוא להן מ-Dispatchers.IO.
 * שגיאות נזרקות כ-[com.crecker.manager.core.model.LocalizedException].
 */
interface RemoteUploader : Closeable {
    fun connect()

    /** מוודא שהתיקייה קיימת (יוצר אותה ואת ההורים שלה במידת הצורך) ונכנס אליה. מחזיר true אם נוצרה. */
    fun ensureDir(path: String): Boolean

    /**
     * מעלה בשם זמני (`name.tmp`) ואז משנה לשם הסופי – כך שהאתר אף פעם לא קורא קובץ חצי-מועלה.
     */
    fun uploadAtomic(local: File, remoteName: String)

    /** זמן השינוי האחרון (וגודל) של קובץ לפי נתיב מלא, או null אם הקובץ לא קיים. */
    fun fileInfo(path: String): RemoteFileInfo?

    companion object {
        fun create(config: RemoteConfig): RemoteUploader = when (config.protocol) {
            RemoteProtocol.FTP, RemoteProtocol.FTPS -> FtpUploader(config)
            RemoteProtocol.SFTP -> SftpUploader(config)
        }
    }
}
