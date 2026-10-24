package com.crecker.manager.core.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** קטע בשם המפתח שמסמן סוד מוצפן: "<prefix>secret:<key>". */
internal const val SECRET_MARKER = "secret:"

/**
 * מאגר הגדרות יחיד לכל האפליקציה (חייב להיות מופע אחד בלבד – ב-AppGraph).
 * כל מערכת מקבלת "פרוסה" משלה דרך [forSystem], וכל פעולה דרך [forAction].
 *
 * @param migrations העברות שרצות פעם אחת, לפני הקריאה הראשונה (למשל [MoveToSystemSettings]).
 */
class SettingsStore(context: Context, migrations: List<DataMigration<Preferences>> = emptyList()) {
    private val dataStore = PreferenceDataStoreFactory.create(migrations = migrations) {
        context.applicationContext.preferencesDataStoreFile("settings")
    }
    private val cipher = SecretCipher()

    fun forSystem(systemId: String) = SystemSettings(systemId, dataStore, cipher)

    fun forAction(systemId: String, actionId: String) =
        ActionSettings(systemId, actionId, dataStore, cipher)
}

/**
 * פרוסה של ההגדרות: כל המפתחות מקבלים [prefix], כך שמערכות ופעולות לא מתנגשות.
 * סודות נשמרים מוצפנים ([putSecret]/[getSecret]).
 */
sealed class ScopedSettings(
    /** "systemId/" למערכת, "systemId/actionId/" לפעולה. */
    val prefix: String,
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    private fun stringKey(key: String) = stringPreferencesKey(prefix + key)
    private fun boolKey(key: String) = booleanPreferencesKey(prefix + key)
    private fun intKey(key: String) = intPreferencesKey(prefix + key)
    private fun secretKey(key: String) = stringPreferencesKey(prefix + SECRET_MARKER + key)

    fun stringFlow(key: String): Flow<String?> = dataStore.data.map { it[stringKey(key)] }
    fun booleanFlow(key: String, default: Boolean): Flow<Boolean> =
        dataStore.data.map { it[boolKey(key)] ?: default }

    suspend fun getString(key: String): String? = stringFlow(key).first()
    suspend fun getBoolean(key: String, default: Boolean): Boolean = booleanFlow(key, default).first()
    suspend fun getInt(key: String): Int? = dataStore.data.first()[intKey(key)]

    suspend fun putString(key: String, value: String?) = dataStore.edit {
        if (value == null) it.remove(stringKey(key)) else it[stringKey(key)] = value
    }

    suspend fun putBoolean(key: String, value: Boolean) = dataStore.edit { it[boolKey(key)] = value }
    suspend fun putInt(key: String, value: Int) = dataStore.edit { it[intKey(key)] = value }

    suspend fun hasSecret(key: String): Boolean = dataStore.data.first()[secretKey(key)] != null

    /** מחזיר null אם אין ערך או אם הפענוח נכשל (למשל אחרי איפוס מפתח ה-Keystore). */
    suspend fun getSecret(key: String): String? {
        val stored = dataStore.data.first()[secretKey(key)] ?: return null
        return runCatching { cipher.decrypt(stored) }
            .onFailure { Log.w("ScopedSettings", "Failed to decrypt secret '$prefix$key'", it) }
            .getOrNull()
    }

    suspend fun putSecret(key: String, value: String?) {
        val encrypted = value?.let(cipher::encrypt)
        dataStore.edit { if (encrypted == null) it.remove(secretKey(key)) else it[secretKey(key)] = encrypted }
    }
}

/** הגדרות של מערכת – משותפות לכל הפעולות שלה (למשל פרטי החיבור לשרת). prefix: "systemId/". */
class SystemSettings internal constructor(
    val systemId: String,
    dataStore: DataStore<Preferences>,
    cipher: SecretCipher,
) : ScopedSettings("$systemId/", dataStore, cipher)

/** הגדרות של פעולה אחת. prefix: "systemId/actionId/". */
class ActionSettings internal constructor(
    val systemId: String,
    val actionId: String,
    dataStore: DataStore<Preferences>,
    cipher: SecretCipher,
) : ScopedSettings("$systemId/$actionId/", dataStore, cipher)

/**
 * מעבירה מפתחות מחרוזת (כולל סודות) מההגדרות של פעולה להגדרות של המערכת –
 * למשל כשהגדרה של פעולה אחת הופכת למשותפת לכל הפעולות.
 *
 * - רצה לפני הקריאה הראשונה מה-DataStore, ולכן אין מצב שבו פעולה קוראת לפני שההעברה הסתיימה.
 * - ערך שכבר קיים ברמת המערכת לא נדרס. המפתח הישן נמחק בכל מקרה.
 * - סודות מוצפנים באותו מפתח Keystore, ולכן הטקסט המוצפן מועתק כמו שהוא.
 */
class MoveToSystemSettings(systemId: String, actionId: String, keys: List<String>) : DataMigration<Preferences> {
    private val from = "$systemId/$actionId/"
    private val to = "$systemId/"
    private val names = keys.flatMap { listOf(it, SECRET_MARKER + it) }

    override suspend fun shouldMigrate(currentData: Preferences) =
        names.any { currentData.contains(stringPreferencesKey(from + it)) }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        for (name in names) {
            val old = stringPreferencesKey(from + name)
            val value = prefs[old] ?: continue
            val new = stringPreferencesKey(to + name)
            if (prefs[new] == null) prefs[new] = value
            prefs.remove(old)
        }
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}
