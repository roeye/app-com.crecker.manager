package com.crecker.manager.core.remote

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.IOException
import java.time.Duration

/** FTP רגיל או FTPS מפורש (AUTH TLS), דרך Apache Commons Net. */
internal class FtpUploader(private val config: RemoteConfig) : RemoteUploader {
    private val client: FTPClient =
        if (config.protocol == RemoteProtocol.FTPS) FTPSClient(false) else FTPClient()

    private val reply get() = client.replyString?.trim().orEmpty()

    override fun connect() {
        client.controlEncoding = "UTF-8"
        client.connectTimeout = TIMEOUT_MS
        client.defaultTimeout = TIMEOUT_MS
        client.connect(config.host, config.effectivePort)
        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            throw IOException("השרת דחה את החיבור ($reply)")
        }
        client.soTimeout = TIMEOUT_MS
        client.setDataTimeout(Duration.ofMillis(TIMEOUT_MS.toLong()))
        if (!client.login(config.user, config.password)) {
            throw IOException("ההתחברות נכשלה – שם משתמש או סיסמה שגויים ($reply)")
        }
        (client as? FTPSClient)?.apply {
            execPBSZ(0)
            execPROT("P")
        }
        // Passive – עובד מאחורי NAT של רשתות סלולר/WiFi
        client.enterLocalPassiveMode()
        if (!client.setFileType(FTP.BINARY_FILE_TYPE)) throw IOException("לא ניתן לעבור למצב בינארי ($reply)")
    }

    override fun ensureDir(path: String): Boolean {
        if (client.changeWorkingDirectory(path)) return false
        if (path.startsWith("/") && !client.changeWorkingDirectory("/")) {
            throw IOException("לא ניתן לגשת לתיקיית השורש ($reply)")
        }
        var created = false
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            if (client.changeWorkingDirectory(segment)) continue
            if (!client.makeDirectory(segment)) throw IOException("לא ניתן ליצור את התיקייה '$segment' ($reply)")
            created = true
            if (!client.changeWorkingDirectory(segment)) throw IOException("לא ניתן להיכנס לתיקייה '$segment' ($reply)")
        }
        return created
    }

    override fun uploadAtomic(local: File, remoteName: String) {
        val temp = "$remoteName.tmp"
        val stored = local.inputStream().use { client.storeFile(temp, it) }
        if (!stored) throw IOException("ההעלאה נכשלה ($reply)")
        // חלק מהשרתים לא מאפשרים rename על קובץ קיים – במקרה כזה מוחקים את הישן ומנסים שוב.
        if (!client.rename(temp, remoteName)) {
            client.deleteFile(remoteName)
            if (!client.rename(temp, remoteName)) {
                val error = reply
                client.deleteFile(temp)
                throw IOException("שינוי השם מ-$temp נכשל ($error)")
            }
        }
    }

    override fun close() {
        runCatching { if (client.isConnected) client.logout() }
        runCatching { client.disconnect() }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
