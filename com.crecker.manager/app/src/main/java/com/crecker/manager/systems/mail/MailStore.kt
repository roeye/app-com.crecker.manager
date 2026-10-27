package com.crecker.manager.systems.mail

import android.content.Context
import com.crecker.manager.R
import com.crecker.manager.core.model.LocalizedException
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.settings.ScopedSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * מצב תיקייה כפי שנשמר במכשיר.
 *
 * "חדש" נקבע לפי UID (מספר עולה שהשרת נותן לכל הודעה), ולא לפי "לא נקרא" בשרת:
 * - [viewedUid]: ה-UID הגבוה ביותר שהיה ברשימה כשיצאת ממסך תיבת הדואר. מעליו = "חדש" (נקודה במסך ובמסך הבית).
 * - [notifiedUid]: ה-UID הגבוה ביותר שכבר דווח בהתראה. מעליו = יופיע בהתראה הבאה.
 */
data class FolderState(
    val uidValidity: Long,
    val total: Int,
    val unread: Int,
    val messages: List<MailHeader>,
    val viewedUid: Long,
    val notifiedUid: Long,
) {
    val maxUid: Long get() = messages.maxOfOrNull { it.uid } ?: 0L
    val newMessages: List<MailHeader> get() = messages.filter { it.uid > viewedUid }
    val unnotified: List<MailHeader> get() = messages.filter { it.uid > notifiedUid }

    fun markNotified() = copy(notifiedUid = maxOf(notifiedUid, maxUid))
    fun markViewed() = copy(viewedUid = maxOf(viewedUid, maxUid), notifiedUid = maxOf(notifiedUid, maxUid))
}

/**
 * ממזג מצב חדש מהשרת לתוך המצב השמור.
 * בפעם הראשונה (או אם UIDVALIDITY השתנה – התיבה נבנתה מחדש בשרת) כל מה שכבר יש נחשב "נראה",
 * כדי לא לקבל התראה על כל המיילים הישנים.
 */
fun FolderState?.merge(snapshot: FolderSnapshot): FolderState {
    val baseline = snapshot.messages.maxOfOrNull { it.uid } ?: 0L
    val previous = this?.takeIf { it.uidValidity == snapshot.uidValidity }
    return FolderState(
        uidValidity = snapshot.uidValidity,
        total = snapshot.total,
        unread = snapshot.unread,
        messages = snapshot.messages,
        viewedUid = previous?.viewedUid ?: baseline,
        notifiedUid = previous?.notifiedUid ?: baseline,
    )
}

/** מצב חשבון: התיקיות, מתי נבדק לאחרונה, ושגיאה מהבדיקה האחרונה (טקסט, בשפה שהייתה פעילה). */
data class AccountState(val folders: Map<String, FolderState>, val checkedAt: Long?, val error: String?) {
    fun visibleFolders(account: MailAccount) = account.folders.mapNotNull { name -> folders[name]?.let { name to it } }
    fun newCount(account: MailAccount) = visibleFolders(account).sumOf { it.second.newMessages.size }
    fun unreadCount(account: MailAccount) = visibleFolders(account).sumOf { it.second.unread }
}

/** תוצאה של רענון חשבון אחד. [newMessages] = הודעות שעוד לא דווחו בהתראה, לפי תיקייה. */
class RefreshResult(val account: MailAccount, val state: AccountState, val newMessages: Map<String, List<MailHeader>>, val error: String?)

/**
 * המטמון של המיילים (כותרות בלבד – לא תוכן) והמצב של "חדש".
 * נשמר ב-`files/mail-cache/<accountId>.json`, מוחרג מגיבוי.
 * מופע יחיד ([get]) – גם המסך וגם הבדיקה המתוזמנת עובדים מולו, עם נעילה כדי לא לדרוס זה את זה.
 */
class MailStore private constructor(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "mail-cache")
    private val mutex = Mutex()
    private val _state = MutableStateFlow<Map<String, AccountState>?>(null)

    /** null = עוד לא נטען מהדיסק. */
    val state: StateFlow<Map<String, AccountState>?> = _state.asStateFlow()

    suspend fun load() {
        if (_state.value != null) return
        mutex.withLock {
            if (_state.value != null) return
            _state.value = withContext(Dispatchers.IO) {
                dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { file ->
                    runCatching { file.nameWithoutExtension to file.readText().toAccountState() }.getOrNull()
                }.toMap()
            }
        }
    }

    /**
     * מתחבר לחשבון, מעדכן את כל התיקיות שלו ושומר.
     * @param markNotified לסמן את מה שנמצא כ"דווח" (בדיקה שאחריה נשלחת התראה).
     * @param context Context בשפת האפליקציה – לתרגום הודעות שגיאה.
     */
    suspend fun refresh(context: Context, settings: ScopedSettings, account: MailAccount, markNotified: Boolean): RefreshResult {
        load()
        val password = settings.getSecret(MailAccounts.passwordKey(account.id))
        val missing = account.missingFields() + listOfNotNull(R.string.field_password.takeIf { password.isNullOrEmpty() })

        val snapshots = mutableMapOf<String, FolderSnapshot>()
        var error: String? = null
        if (missing.isNotEmpty()) {
            error = context.getString(R.string.mail_err_missing, missing.joinToString { context.getString(it) })
        } else {
            try {
                ImapClient(account, password.orEmpty()).use { client ->
                    runInterruptible(Dispatchers.IO) { client.connect() }
                    for (folder in account.folders) {
                        currentCoroutineContext().ensureActive()
                        try {
                            snapshots[folder] = runInterruptible(Dispatchers.IO) { client.snapshot(folder, MESSAGES_PER_FOLDER) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = error ?: e.describe(context)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.describe(context)
            }
        }

        return mutex.withLock {
            val previous = _state.value.orEmpty()[account.id]
            val newMessages = mutableMapOf<String, List<MailHeader>>()
            val folders = previous?.folders.orEmpty().toMutableMap()
            for ((name, snapshot) in snapshots) {
                var merged = folders[name].merge(snapshot)
                newMessages[name] = merged.unnotified
                if (markNotified) merged = merged.markNotified()
                folders[name] = merged
            }
            // תיקיות שהוסרו מהחשבון לא נשמרות
            folders.keys.retainAll(account.folders.toSet())
            val updated = AccountState(folders, if (snapshots.isNotEmpty()) System.currentTimeMillis() else previous?.checkedAt, error)
            persist(account.id, updated)
            RefreshResult(account, updated, newMessages, error)
        }
    }

    /** ביציאה ממסך תיבת הדואר: כל מה שהוצג כבר לא "חדש" (וגם לא יופיע בהתראה). */
    suspend fun markViewed(accountIds: Collection<String>) = mutex.withLock {
        for (id in accountIds) {
            val current = _state.value.orEmpty()[id] ?: continue
            persist(id, current.copy(folders = current.folders.mapValues { it.value.markViewed() }))
        }
    }

    /** מחיקת חשבון: המטמון שלו נמחק מהמכשיר (בשרת לא נוגעים). */
    suspend fun forget(accountId: String) = mutex.withLock {
        withContext(Dispatchers.IO) { File(dir, "$accountId.json").delete() }
        _state.update { it.orEmpty() - accountId }
    }

    private suspend fun persist(accountId: String, state: AccountState) {
        _state.update { it.orEmpty() + (accountId to state) }
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, "$accountId.json.tmp")
            tmp.writeText(state.toJson().toString())
            if (!tmp.renameTo(File(dir, "$accountId.json"))) {
                File(dir, "$accountId.json").delete()
                if (!tmp.renameTo(File(dir, "$accountId.json"))) throw LocalizedException(R.string.mail_err_cache)
            }
        }
    }

    private fun AccountState.toJson() = JSONObject()
        .put("checkedAt", checkedAt ?: JSONObject.NULL)
        .put("error", error ?: JSONObject.NULL)
        .put(
            "folders",
            JSONObject().apply {
                folders.forEach { (name, f) ->
                    put(
                        name,
                        JSONObject()
                            .put("uidValidity", f.uidValidity)
                            .put("total", f.total)
                            .put("unread", f.unread)
                            .put("viewedUid", f.viewedUid)
                            .put("notifiedUid", f.notifiedUid)
                            .put("messages", JSONArray(f.messages.map { it.toJson() })),
                    )
                }
            },
        )

    private fun MailHeader.toJson() = JSONObject()
        .put("uid", uid)
        .put("from", from)
        .put("fromAddress", fromAddress)
        .put("subject", subject)
        .put("date", date)
        .put("seen", seen)
        .put("hasAttachments", hasAttachments)

    private fun String.toAccountState(): AccountState {
        val o = JSONObject(this)
        val folders = o.getJSONObject("folders")
        return AccountState(
            folders = folders.keys().asSequence().associateWith { name ->
                val f = folders.getJSONObject(name)
                val messages = f.getJSONArray("messages")
                FolderState(
                    uidValidity = f.getLong("uidValidity"),
                    total = f.getInt("total"),
                    unread = f.getInt("unread"),
                    messages = List(messages.length()) { messages.getJSONObject(it).toHeader() },
                    viewedUid = f.getLong("viewedUid"),
                    notifiedUid = f.getLong("notifiedUid"),
                )
            },
            checkedAt = if (o.isNull("checkedAt")) null else o.getLong("checkedAt"),
            error = if (o.isNull("error")) null else o.getString("error"),
        )
    }

    private fun JSONObject.toHeader() = MailHeader(
        uid = getLong("uid"),
        from = getString("from"),
        fromAddress = getString("fromAddress"),
        subject = getString("subject"),
        date = getLong("date"),
        seen = getBoolean("seen"),
        hasAttachments = getBoolean("hasAttachments"),
    )

    companion object {
        /** כמה הודעות אחרונות נשמרות ומוצגות מכל תיקייה. */
        const val MESSAGES_PER_FOLDER = 50

        @Volatile
        private var instance: MailStore? = null

        fun get(context: Context): MailStore =
            instance ?: synchronized(this) { instance ?: MailStore(context).also { instance = it } }
    }
}
