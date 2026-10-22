package com.crecker.manager.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.net.HttpURLConnection
import java.net.URI

class HttpResult(val code: Int, val bytes: ByteArray, val contentType: String?)

object HttpFetcher {
    /** User-Agent של דפדפן – חלק מהאתרים חוסמים בקשות שלא נראות כמו דפדפן. */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /** מוריד את התוכן כבייטים גולמיים (בלי המרה לטקסט – כדי לא לשבור קידוד). */
    suspend fun get(url: String, timeoutMs: Int = 20_000): HttpResult = runInterruptible(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Language", "he-IL,he;q=0.9,en;q=0.8")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResult(code, stream?.use { it.readBytes() } ?: ByteArray(0), connection.contentType)
        } finally {
            connection.disconnect()
        }
    }
}
