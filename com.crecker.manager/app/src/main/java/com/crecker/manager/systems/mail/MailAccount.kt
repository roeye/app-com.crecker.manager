package com.crecker.manager.systems.mail

import androidx.annotation.StringRes
import com.crecker.manager.R
import com.crecker.manager.core.settings.ScopedSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** הצפנת החיבור לשרת ה-IMAP. אין אפשרות לחיבור לא מוצפן – הסיסמה והמיילים עוברים בו. */
enum class MailSecurity(val defaultPort: Int, @StringRes val label: Int) {
    SSL(993, R.string.mail_security_ssl),
    STARTTLS(143, R.string.mail_security_starttls),
}

/** שם התיקייה הראשית ב-IMAP (תמיד קיימת). */
const val INBOX = "INBOX"

/**
 * תיבת דואר (חשבון IMAP). הסיסמה לא כאן – היא נשמרת מוצפנת ב-[MailAccounts.passwordKey].
 *
 * @param id מזהה יציב – המטמון והסיסמה מקושרים אליו.
 * @param name שם לתצוגה. ריק = [user].
 * @param folders התיקיות שמוצגות ונבדקות (ברירת מחדל: [INBOX]).
 */
data class MailAccount(
    val id: String,
    val name: String,
    val host: String,
    val port: Int?,
    val security: MailSecurity,
    val user: String,
    val folders: List<String>,
) {
    val label: String get() = name.ifBlank { user }
    val effectivePort: Int get() = port ?: security.defaultPort

    @StringRes
    fun missingFields(): List<Int> = buildList {
        if (host.isBlank()) add(R.string.mail_field_host)
        if (user.isBlank()) add(R.string.mail_field_user)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(8)
    }
}

/** רשימת החשבונות נשמרת כ-JSON בהגדרות המערכת. */
object MailAccounts {
    private const val KEY_ACCOUNTS = "accounts"

    fun passwordKey(accountId: String) = "password_$accountId"

    fun flow(settings: ScopedSettings): Flow<List<MailAccount>> = settings.stringFlow(KEY_ACCOUNTS).map(::parse)

    suspend fun load(settings: ScopedSettings): List<MailAccount> = parse(settings.getString(KEY_ACCOUNTS))

    suspend fun save(settings: ScopedSettings, accounts: List<MailAccount>) =
        settings.putString(KEY_ACCOUNTS, toJson(accounts))

    /** חשבון לא תקין ברשימה מדולג – לא מפיל את כל הרשימה. */
    private fun parse(json: String?): List<MailAccount> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return List(array.length()) { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val folders = o.optJSONArray("folders")
                MailAccount(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    host = o.optString("host"),
                    port = if (o.has("port")) o.getInt("port") else null,
                    security = runCatching { MailSecurity.valueOf(o.getString("security")) }.getOrDefault(MailSecurity.SSL),
                    user = o.optString("user"),
                    folders = folders?.let { a -> List(a.length()) { a.getString(it) } }?.takeIf { it.isNotEmpty() } ?: listOf(INBOX),
                )
            }.getOrNull()
        }.filterNotNull()
    }

    private fun toJson(accounts: List<MailAccount>): String = JSONArray(
        accounts.map { a ->
            JSONObject()
                .put("id", a.id)
                .put("name", a.name)
                .put("host", a.host)
                .apply { a.port?.let { put("port", it) } }
                .put("security", a.security.name)
                .put("user", a.user)
                .put("folders", JSONArray(a.folders))
        },
    ).toString()
}
