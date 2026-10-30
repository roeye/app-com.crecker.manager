package com.crecker.manager.systems.paisresults

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import com.crecker.manager.R
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.remote.RemoteSettings
import com.crecker.manager.core.settings.MoveToSystemSettings
import com.crecker.manager.core.settings.SystemSettings
import com.crecker.manager.core.ui.LastRunLine
import com.crecker.manager.core.ui.RemoteSystemSettingsScreen

/** האתר paisresults.co.il – תוצאות הגרלות מפעל הפיס. ראו docs/systems/paisresults.md. */
object PaisResultsSystem : ManagedSystem {
    /** const – כדי שהפעולות יוכלו להשתמש בו בזמן האתחול של ה-object. */
    const val NAME = "paisresults.co.il"

    /** תיקיית הקבצים בשרת (ברירת מחדל – ניתן לשינוי בהגדרות המערכת). */
    const val DEFAULT_REMOTE_DIR = "/paisresults.co.il/data"

    override val id = "paisresults"
    override val name = NAME
    override val description = R.string.pais_description
    override val category = R.string.category_sites
    override val icon = Icons.Default.Star

    override val actions = listOf(
        CronDataRetrieval.action,
        CheckLastUpdate.action,
    )

    override val settingsScreen: @Composable (SystemSettings, () -> Unit) -> Unit =
        { settings, onBack -> RemoteSystemSettingsScreen(NAME, settings, DEFAULT_REMOTE_DIR, onBack) }

    /** פרטי החיבור היו פעם הגדרות של cron_data_retrieval – עכשיו הם משותפים לכל הפעולות. */
    override val settingsMigrations = listOf(
        MoveToSystemSettings(id, CronDataRetrieval.ID, RemoteSettings.ALL_KEYS),
    )

    @Composable
    override fun StatusLine() = Column {
        LastRunLine(id, CronDataRetrieval.ID, R.string.pais_cron_short)
        LastRunLine(id, CheckLastUpdate.ID, R.string.last_update_short)
    }
}
