package com.crecker.manager.core.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.crecker.manager.core.settings.ActionSettings

sealed interface SystemAction {
    /** מזהה יציב בתוך המערכת – משמש בניווט, בהגדרות ובהיסטוריה. */
    val id: String
    val title: String
    val icon: ImageVector

    /**
     * פעולה עם מסך גנרי: הסבר, כפתור הרצה/ביטול, לוג חי והיסטוריית הרצות.
     * הפעולה מספקת רק את הלוגיקה ב-[run]; כל ה-UI, הקבצים הזמניים, השמירה וההתראות מגיעים מה-core.
     */
    class Logged(
        override val id: String,
        override val title: String,
        override val icon: ImageVector,
        val description: String,
        /** האם אפשר להריץ את הפעולה אוטומטית ברקע (WorkManager). */
        val schedulable: Boolean = false,
        /** אפשרויות שמוצגות מעל כפתור ההרצה (למשל צ'קבוקסים). */
        val options: (@Composable (settings: ActionSettings) -> Unit)? = null,
        /** מסך הגדרות של הפעולה (נפתח מכפתור ⚙). */
        val settingsScreen: (@Composable (settings: ActionSettings, onBack: () -> Unit) -> Unit)? = null,
        val run: suspend ActionContext.() -> RunStatus,
    ) : SystemAction

    /** פעולה עם מסך מותאם לחלוטין. */
    class Custom(
        override val id: String,
        override val title: String,
        override val icon: ImageVector,
        val screen: @Composable (settings: ActionSettings, onBack: () -> Unit) -> Unit,
    ) : SystemAction
}
