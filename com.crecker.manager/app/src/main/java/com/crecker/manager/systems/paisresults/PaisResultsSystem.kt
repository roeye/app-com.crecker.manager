package com.crecker.manager.systems.paisresults

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.crecker.manager.R
import com.crecker.manager.core.model.ActionContext
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.remote.RemoteConfig
import com.crecker.manager.core.remote.RemoteSettings
import com.crecker.manager.core.settings.MoveToSystemSettings
import com.crecker.manager.core.settings.SystemSettings
import com.crecker.manager.core.ui.LastRunLine
import com.crecker.manager.core.ui.RemoteSettingsSection
import com.crecker.manager.core.ui.SettingsScaffold
import com.crecker.manager.core.ui.rememberRemoteFormState
import java.time.ZoneId

/** האתר paisresults.co.il – תוצאות הגרלות מפעל הפיס. ראו docs/systems/paisresults.md. */
object PaisResultsSystem : ManagedSystem {
    override val id = "paisresults"
    override val name = "paisresults.co.il"
    override val description = R.string.pais_description
    override val category = R.string.category_sites
    override val icon = Icons.Default.Star

    /** תיקיית הקבצים בשרת (ברירת מחדל – ניתן לשינוי בהגדרות המערכת). */
    const val DEFAULT_REMOTE_DIR = "/paisresults.co.il/data"

    /** הזמנים מוצגים לפי שעון ישראל (כולל שעון קיץ). */
    val ISRAEL_ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

    override val actions = listOf(
        CronDataRetrieval.action,
        CheckLastUpdate.action,
    )

    override val settingsScreen: @Composable (SystemSettings, () -> Unit) -> Unit =
        { settings, onBack -> PaisSystemSettingsScreen(settings, onBack) }

    /** פרטי החיבור היו פעם הגדרות של cron_data_retrieval – עכשיו הם משותפים לכל הפעולות. */
    override val settingsMigrations = listOf(
        MoveToSystemSettings(id, CronDataRetrieval.ID, RemoteSettings.ALL_KEYS),
    )

    @Composable
    override fun StatusLine() = Column {
        LastRunLine(id, CronDataRetrieval.ID, R.string.pais_cron_short)
        LastRunLine(id, CheckLastUpdate.ID, R.string.pais_check_short)
    }
}

/** פרטי החיבור לשרת (מהגדרות המערכת). אם חסרים פרטים – רושם שגיאה ומחזיר null. */
internal suspend fun ActionContext.loadPaisRemote(): RemoteConfig? {
    val remote = RemoteSettings.load(systemSettings, PaisResultsSystem.DEFAULT_REMOTE_DIR)
    val missing = remote.missingFields()
    if (missing.isEmpty()) return remote
    log.error(getString(R.string.pais_log_missing_settings, missing.joinToString { getString(it) }))
    return null
}

/** הערה במסכי ההגדרות של הפעולות: פרטי החיבור נמצאים בהגדרות המערכת. */
@Composable
internal fun RemoteInSystemSettingsNote() {
    Text(
        stringResource(R.string.pais_remote_in_system_settings),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PaisSystemSettingsScreen(settings: SystemSettings, onBack: () -> Unit) {
    val remote = rememberRemoteFormState(settings, PaisResultsSystem.DEFAULT_REMOTE_DIR)
    SettingsScaffold(
        title = stringResource(R.string.pais_system_settings_title),
        loaded = remote.loaded,
        onBack = onBack,
        onSave = { remote.validationError() ?: run { remote.save(); null } },
    ) {
        Text(
            stringResource(R.string.pais_system_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RemoteSettingsSection(remote)
    }
}
