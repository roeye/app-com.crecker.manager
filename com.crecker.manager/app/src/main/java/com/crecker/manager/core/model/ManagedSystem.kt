package com.crecker.manager.core.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.crecker.manager.core.settings.SystemSettings

/**
 * מערכת מנוהלת – כל דבר שיש לו פעולות: אתר, שרת, מכשיר וכו'.
 *
 * ה-host (המסכים ב-`app/`) לא יודע כלום על מה שבפנים; כל מערכת מגדירה את עצמה ואת הפעולות שלה.
 * מערכת חדשה = תיקייה חדשה תחת `systems/` + שורה ב-[com.crecker.manager.app.SystemRegistry].
 */
interface ManagedSystem {
    /** מזהה יציב – משמש בניווט, בהגדרות ובהיסטוריה. אסור לשנות אחרי שיש נתונים שמורים. */
    val id: String

    /** שם המערכת (שם פרטי, למשל דומיין – לא מתורגם). */
    val name: String

    @get:StringRes
    val description: Int? get() = null

    /** תווית לקיבוץ במסך הבית (R.string.category_*). */
    @get:StringRes
    val category: Int
    val icon: ImageVector
    val actions: List<SystemAction>

    /** מסך הגדרות של המערכת – הגדרות שמשותפות לכל הפעולות (נפתח מכפתור ⚙ במסך המערכת). */
    val settingsScreen: (@Composable (settings: SystemSettings, onBack: () -> Unit) -> Unit)? get() = null

    /** העברות של הגדרות שמורות (למשל [com.crecker.manager.core.settings.MoveToSystemSettings]). רצות פעם אחת. */
    val settingsMigrations: List<DataMigration<Preferences>> get() = emptyList()

    /** שורת סטטוס קצרה שמוצגת ברשימת המערכות. */
    @Composable
    fun StatusLine() {
    }

    fun action(id: String): SystemAction? = actions.firstOrNull { it.id == id }
}
