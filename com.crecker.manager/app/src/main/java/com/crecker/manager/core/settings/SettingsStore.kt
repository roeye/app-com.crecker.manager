package com.crecker.manager.core.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** מאגר הגדרות יחיד לכל האפליקציה; כל פעולה מקבלת "פרוסה" משלה דרך [forAction]. */
class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore
    private val cipher = SecretCipher()

    fun forAction(systemId: String, actionId: String) =
        ActionSettings(systemId, actionId, dataStore, cipher)
}

/**
 * הגדרות של פעולה אחת. כל המפתחות מקבלים prefix של "systemId/actionId/", כך שפעולות לא מתנגשות.
 * סודות נשמרים מוצפנים ([putSecret]/[getSecret]).
 */
class ActionSettings internal constructor(
    val systemId: String,
    val actionId: String,
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    private val prefix = "$systemId/$actionId/"
    private fun stringKey(key: String) = stringPreferencesKey(prefix + key)
    private fun boolKey(key: String) = booleanPreferencesKey(prefix + key)
    private fun intKey(key: String) = intPreferencesKey(prefix + key)
    private fun secretKey(key: String) = stringPreferencesKey("${prefix}secret:$key")

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
            .onFailure { Log.w("ActionSettings", "Failed to decrypt secret '$key'", it) }
            .getOrNull()
    }

    suspend fun putSecret(key: String, value: String?) {
        val encrypted = value?.let(cipher::encrypt)
        dataStore.edit { if (encrypted == null) it.remove(secretKey(key)) else it[secretKey(key)] = encrypted }
    }
}
