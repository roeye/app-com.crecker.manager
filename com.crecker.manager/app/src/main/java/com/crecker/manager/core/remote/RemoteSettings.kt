package com.crecker.manager.core.remote

import com.crecker.manager.R
import com.crecker.manager.core.model.ActionContext
import com.crecker.manager.core.settings.ScopedSettings

/** מפתחות ההגדרות של חיבור מרוחק (ברמת מערכת או פעולה) – מי ששומר אותם מקבל את הטופס הגנרי ב-core/ui. */
object RemoteSettings {
    const val PROTOCOL = "remote_protocol"
    const val HOST = "remote_host"
    const val PORT = "remote_port"
    const val USER = "remote_user"
    const val PASSWORD = "remote_password" // secret
    const val DIR = "remote_dir"

    /** כל המפתחות – למשל להעברה עם [com.crecker.manager.core.settings.MoveToSystemSettings]. */
    val ALL_KEYS = listOf(PROTOCOL, HOST, PORT, USER, PASSWORD, DIR)

    suspend fun load(settings: ScopedSettings, defaultDir: String) = RemoteConfig(
        protocol = settings.getString(PROTOCOL)
            ?.let { runCatching { RemoteProtocol.valueOf(it) }.getOrNull() } ?: RemoteProtocol.FTP,
        host = settings.getString(HOST).orEmpty().trim(),
        port = settings.getString(PORT)?.toIntOrNull(),
        user = settings.getString(USER).orEmpty().trim(),
        password = settings.getSecret(PASSWORD).orEmpty(),
        remoteDir = settings.getString(DIR)?.takeIf { it.isNotBlank() } ?: defaultDir,
    )
}

/**
 * פרטי החיבור לשרת מהגדרות **המערכת** ([ActionContext.systemSettings]).
 * אם חסרים פרטים – רושם שגיאה שמפנה להגדרות של [systemName] ומחזיר null.
 */
suspend fun ActionContext.loadSystemRemote(systemName: String, defaultDir: String): RemoteConfig? {
    val remote = RemoteSettings.load(systemSettings, defaultDir)
    val missing = remote.missingFields()
    if (missing.isEmpty()) return remote
    log.error(getString(R.string.remote_log_missing_settings, systemName, missing.joinToString { getString(it) }))
    return null
}
