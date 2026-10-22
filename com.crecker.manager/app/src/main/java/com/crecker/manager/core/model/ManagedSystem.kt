package com.crecker.manager.core.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * מערכת מנוהלת – כל דבר שיש לו פעולות: אתר, שרת, מכשיר וכו'.
 *
 * ה-host (המסכים ב-`app/`) לא יודע כלום על מה שבפנים; כל מערכת מגדירה את עצמה ואת הפעולות שלה.
 * מערכת חדשה = תיקייה חדשה תחת `systems/` + שורה ב-[com.crecker.manager.app.SystemRegistry].
 */
interface ManagedSystem {
    /** מזהה יציב – משמש בניווט, בהגדרות ובהיסטוריה. אסור לשנות אחרי שיש נתונים שמורים. */
    val id: String
    val name: String
    val description: String get() = ""

    /** תווית לקיבוץ במסך הבית ("אתרים", "שרתים"...). */
    val category: String
    val icon: ImageVector
    val actions: List<SystemAction>

    /** שורת סטטוס קצרה שמוצגת ברשימת המערכות. */
    @Composable
    fun StatusLine() {
    }

    fun action(id: String): SystemAction? = actions.firstOrNull { it.id == id }
}
