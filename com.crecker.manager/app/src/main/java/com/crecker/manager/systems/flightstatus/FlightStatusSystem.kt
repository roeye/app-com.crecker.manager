package com.crecker.manager.systems.flightstatus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.runtime.Composable
import com.crecker.manager.R
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.remote.LastUpdateCheck
import com.crecker.manager.core.settings.SystemSettings
import com.crecker.manager.core.ui.LastRunLine
import com.crecker.manager.core.ui.RemoteSystemSettingsScreen

/** האתר flightstatus.co.il – מצב טיסות. ראו docs/systems/flightstatus.md. */
object FlightStatusSystem : ManagedSystem {
    /** const – כדי שהפעולות יוכלו להשתמש בו בזמן האתחול של ה-object. */
    const val NAME = "flightstatus.co.il"

    /** תיקיית הקבצים בשרת (ברירת מחדל – ניתן לשינוי בהגדרות המערכת). */
    const val DEFAULT_REMOTE_DIR = "/flightstatus.co.il/data"

    const val DATA_FILE = "flights_data.json"

    /** הנתונים מתעדכנים בתדירות גבוהה – קובץ שלא עודכן שעה נחשב ישן. */
    const val DEFAULT_STALE_HOURS = 1

    override val id = "flightstatus"
    override val name = NAME
    override val description = R.string.flight_description
    override val category = R.string.category_sites
    override val icon = Icons.Default.Flight

    private val checkLastUpdate = LastUpdateCheck(
        description = R.string.flight_check_description,
        systemName = NAME,
        defaultRemoteDir = DEFAULT_REMOTE_DIR,
        files = listOf(DATA_FILE),
        defaultStaleHours = DEFAULT_STALE_HOURS,
    )

    override val actions = listOf(checkLastUpdate.action)

    override val settingsScreen: @Composable (SystemSettings, () -> Unit) -> Unit =
        { settings, onBack -> RemoteSystemSettingsScreen(NAME, settings, DEFAULT_REMOTE_DIR, onBack) }

    @Composable
    override fun StatusLine() {
        LastRunLine(id, LastUpdateCheck.ID)
    }
}
