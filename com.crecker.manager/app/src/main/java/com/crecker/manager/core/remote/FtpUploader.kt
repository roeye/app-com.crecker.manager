package com.crecker.manager.core.remote

import com.crecker.manager.R
import com.crecker.manager.core.model.LocalizedException
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
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
            throw LocalizedException(R.string.remote_err_refused, reply)
        }
        client.soTimeout = TIMEOUT_MS
        client.setDataTimeout(Duration.ofMillis(TIMEOUT_MS.toLong()))
        if (!client.login(config.user, config.password)) {
            throw LocalizedException(R.string.remote_err_login, reply)
        }
        (client as? FTPSClient)?.apply {
            execPBSZ(0)
            execPROT("P")
        }
        // Passive – עובד מאחורי NAT של רשתות סלולר/WiFi
        client.enterLocalPassiveMode()
        if (!client.setFileType(FTP.BINARY_FILE_TYPE)) throw LocalizedException(R.string.remote_err_binary, reply)
    }

    override fun ensureDir(path: String): Boolean {
        if (client.changeWorkingDirectory(path)) return false
        if (path.startsWith("/") && !client.changeWorkingDirectory("/")) {
            throw LocalizedException(R.string.remote_err_root, reply)
        }
        var created = false
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            if (client.changeWorkingDirectory(segment)) continue
            if (!client.makeDirectory(segment)) throw LocalizedException(R.string.remote_err_mkdir, segment, reply)
            created = true
            if (!client.changeWorkingDirectory(segment)) throw LocalizedException(R.string.remote_err_cd, segment, reply)
        }
        return created
    }

    override fun uploadAtomic(local: File, remoteName: String) {
        val temp = "$remoteName.tmp"
        val stored = local.inputStream().use { client.storeFile(temp, it) }
        if (!stored) throw LocalizedException(R.string.remote_err_upload, reply)
        // חלק מהשרתים לא מאפשרים rename על קובץ קיים – במקרה כזה מוחקים את הישן ומנסים שוב.
        if (!client.rename(temp, remoteName)) {
            client.deleteFile(remoteName)
            if (!client.rename(temp, remoteName)) {
                val error = reply
                client.deleteFile(temp)
                throw LocalizedException(R.string.remote_err_rename, temp, error)
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
