package com.crecker.manager.core.remote

import com.crecker.manager.R
import com.crecker.manager.core.model.LocalizedException
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import java.io.File

/**
 * SFTP (מעל SSH) דרך JSch.
 * הערה: מפתח השרת לא נבדק (StrictHostKeyChecking=no) – ראו docs/ARCHITECTURE.md.
 */
internal class SftpUploader(private val config: RemoteConfig) : RemoteUploader {
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    private val sftp get() = channel ?: throw LocalizedException(R.string.remote_err_not_connected)

    override fun connect() {
        try {
            val s = JSch().getSession(config.user, config.host, config.effectivePort)
            s.setPassword(config.password.toByteArray(Charsets.UTF_8))
            s.setConfig("StrictHostKeyChecking", "no")
            s.connect(TIMEOUT_MS)
            session = s
            channel = (s.openChannel("sftp") as ChannelSftp).apply { connect(TIMEOUT_MS) }
        } catch (e: JSchException) {
            throw LocalizedException(R.string.remote_err_connect, e.message.orEmpty(), cause = e)
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
            throw LocalizedException(R.string.remote_err_dir, path, e.message.orEmpty(), cause = e)
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
            throw LocalizedException(R.string.remote_err_upload, e.message.orEmpty(), cause = e)
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
