package com.crecker.manager.core.remote

import com.crecker.manager.core.settings.ActionSettings

/** מפתחות ההגדרות של חיבור מרוחק – כל פעולה ששומרת אותם מקבלת את הטופס הגנרי ב-core/ui. */
object RemoteSettings {
    const val PROTOCOL = "remote_protocol"
    const val HOST = "remote_host"
    const val PORT = "remote_port"
    const val USER = "remote_user"
    const val PASSWORD = "remote_password" // secret
    const val DIR = "remote_dir"

    suspend fun load(settings: ActionSettings, defaultDir: String) = RemoteConfig(
        protocol = settings.getString(PROTOCOL)
            ?.let { runCatching { RemoteProtocol.valueOf(it) }.getOrNull() } ?: RemoteProtocol.FTP,
        host = settings.getString(HOST).orEmpty().trim(),
        port = settings.getString(PORT)?.toIntOrNull(),
        user = settings.getString(USER).orEmpty().trim(),
        password = settings.getSecret(PASSWORD).orEmpty(),
        remoteDir = settings.getString(DIR)?.takeIf { it.isNotBlank() } ?: defaultDir,
    )
}
