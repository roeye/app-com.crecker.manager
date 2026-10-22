package com.crecker.manager.core.remote

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import java.io.File
import java.io.IOException

/**
 * SFTP (מעל SSH) דרך JSch.
 * הערה: מפתח השרת לא נבדק (StrictHostKeyChecking=no) – ראו docs/ARCHITECTURE.md.
 */
internal class SftpUploader(private val config: RemoteConfig) : RemoteUploader {
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    private val sftp get() = channel ?: throw IOException("לא מחובר")

    override fun connect() {
        try {
            val s = JSch().getSession(config.user, config.host, config.effectivePort)
            s.setPassword(config.password)
            s.setConfig("StrictHostKeyChecking", "no")
            s.connect(TIMEOUT_MS)
            session = s
            channel = (s.openChannel("sftp") as ChannelSftp).apply { connect(TIMEOUT_MS) }
        } catch (e: JSchException) {
            throw IOException("ההתחברות נכשלה (${e.message})", e)
        }
    }

    override fun ensureDir(path: String): Boolean {
        try {
            sftp.cd(path)
            return false
        } catch (_: SftpException) {
        }
        try {
            if (path.startsWith("/")) sftp.cd("/")
            var created = false
            for (segment in path.split('/').filter { it.isNotBlank() }) {
                try {
                    sftp.cd(segment)
                } catch (_: SftpException) {
                    sftp.mkdir(segment)
                    sftp.cd(segment)
                    created = true
                }
            }
            return created
        } catch (e: SftpException) {
            throw IOException("לא ניתן ליצור/לגשת לתיקייה $path (${e.message})", e)
        }
    }

    override fun uploadAtomic(local: File, remoteName: String) {
        val temp = "$remoteName.tmp"
        try {
            sftp.put(local.absolutePath, temp)
            try {
                sftp.rename(temp, remoteName)
            } catch (_: SftpException) {
                // SFTP v3 לא דורס קבצים ב-rename
                runCatching { sftp.rm(remoteName) }
                sftp.rename(temp, remoteName)
            }
        } catch (e: SftpException) {
            runCatching { sftp.rm(temp) }
            throw IOException("ההעלאה נכשלה (${e.message})", e)
        }
    }

    override fun close() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
