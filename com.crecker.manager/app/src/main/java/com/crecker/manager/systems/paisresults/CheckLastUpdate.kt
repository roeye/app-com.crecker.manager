package com.crecker.manager.systems.paisresults

import com.crecker.manager.R
import com.crecker.manager.core.remote.LastUpdateCheck

/**
 * בודק בשרת מתי כל אחד מ-10 הקבצים עודכן לאחרונה – הפעולה הגנרית [LastUpdateCheck].
 * ראו docs/systems/paisresults.md.
 */
object CheckLastUpdate {
    const val ID = LastUpdateCheck.ID
    const val DEFAULT_STALE_HOURS = 24

    val action = LastUpdateCheck(
        description = R.string.pais_check_description,
        systemName = PaisResultsSystem.NAME,
        defaultRemoteDir = PaisResultsSystem.DEFAULT_REMOTE_DIR,
        files = PAIS_FILES.map { it.name },
        defaultStaleHours = DEFAULT_STALE_HOURS,
    ).action
}
