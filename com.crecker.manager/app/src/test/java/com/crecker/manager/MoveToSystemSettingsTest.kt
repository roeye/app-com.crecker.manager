package com.crecker.manager

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crecker.manager.core.settings.MoveToSystemSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveToSystemSettingsTest {
    private val migration = MoveToSystemSettings("sys", "act", listOf("host", "password"))
    private fun key(name: String) = stringPreferencesKey(name)

    @Test
    fun movesValuesAndSecrets() = runBlocking {
        val before = mutablePreferencesOf(
            key("sys/act/host") to "example.com",
            key("sys/act/secret:password") to "iv:cipher",
            key("sys/act/other") to "stays",
            booleanPreferencesKey("sys/act/flag") to true,
        )
        assertTrue(migration.shouldMigrate(before))

        val after = migration.migrate(before)
        assertEquals("example.com", after[key("sys/host")])
        assertEquals("iv:cipher", after[key("sys/secret:password")])
        assertNull(after[key("sys/act/host")])
        assertNull(after[key("sys/act/secret:password")])
        assertEquals("stays", after[key("sys/act/other")])
        assertEquals(true, after[booleanPreferencesKey("sys/act/flag")])
        assertFalse(migration.shouldMigrate(after))
    }

    @Test
    fun doesNotOverwriteExistingSystemValue() = runBlocking {
        val before = mutablePreferencesOf(
            key("sys/act/host") to "old.example.com",
            key("sys/host") to "new.example.com",
        )
        val after = migration.migrate(before)
        assertEquals("new.example.com", after[key("sys/host")])
        assertNull(after[key("sys/act/host")])
    }

    @Test
    fun nothingToMigrate() = runBlocking {
        assertFalse(migration.shouldMigrate(mutablePreferencesOf(key("sys/host") to "x")))
    }
}
