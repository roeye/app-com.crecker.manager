package com.crecker.manager.core.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.crecker.manager.R

enum class ThemeMode(val nightMode: Int, @StringRes val label: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.theme_system),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO, R.string.theme_light),
    DARK(AppCompatDelegate.MODE_NIGHT_YES, R.string.theme_dark),
}

/**
 * שפת האפליקציה. נשמרת ע"י המערכת (per-app language): ב-Android 13+ ע"י LocaleManager,
 * ובגרסאות קודמות ע"י AppCompat (autoStoreLocales במניפסט).
 */
enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    AUTO("", R.string.language_auto),
    ENGLISH("en", R.string.language_en),
    HEBREW("he", R.string.language_he);

    fun apply() = AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
    )

    companion object {
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return AUTO
            return when (locales[0]?.language) {
                "en" -> ENGLISH
                "he", "iw" -> HEBREW
                else -> AUTO
            }
        }
    }
}

/**
 * הגדרות כלליות של האפליקציה. SharedPreferences (ולא DataStore) כי מצב התצוגה נקרא באופן
 * סינכרוני ב-Application.onCreate, לפני שהמסך הראשון מצויר.
 */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        set(value) {
            prefs.edit { putString(KEY_THEME, value.name) }
            applyTheme()
        }

    fun applyTheme() = AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)

    /** נעילת אפליקציה (טביעת אצבע / פנים / נעילת מסך). פעילה כברירת מחדל. */
    var lockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_LOCK_ENABLED, value) }

    var relockTimeout: RelockTimeout
        get() = prefs.getString(KEY_RELOCK, null)
            ?.let { runCatching { RelockTimeout.valueOf(it) }.getOrNull() } ?: RelockTimeout.ONE_MINUTE
        set(value) = prefs.edit { putString(KEY_RELOCK, value.name) }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LOCK_ENABLED = "lock_enabled"
        const val KEY_RELOCK = "relock_timeout"
    }
}

/** כמה זמן ברקע עד שהאפליקציה ננעלת שוב. null = רק כשהאפליקציה נסגרת. */
enum class RelockTimeout(val millis: Long?, @StringRes val label: Int) {
    IMMEDIATELY(0, R.string.relock_immediately),
    ONE_MINUTE(60_000, R.string.relock_1m),
    FIVE_MINUTES(300_000, R.string.relock_5m),
    ON_CLOSE(null, R.string.relock_on_close),
}

/**
 * Context בשפת האפליקציה – לטקסטים שנוצרים מחוץ ל-UI (לוג, התראות, Worker).
 * ב-Android 12 ומטה ה-Application context לא מתעדכן אוטומטית לשפה שנבחרה.
 */
fun Context.localized(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    return createConfigurationContext(config)
}
