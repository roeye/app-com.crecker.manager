package com.crecker.manager.systems.paisresults

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import com.crecker.manager.R
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.ui.LastRunLine

/** האתר paisresults.co.il – תוצאות הגרלות מפעל הפיס. ראו docs/systems/paisresults.md. */
object PaisResultsSystem : ManagedSystem {
    override val id = "paisresults"
    override val name = "paisresults.co.il"
    override val description = R.string.pais_description
    override val category = R.string.category_sites
    override val icon = Icons.Default.Star

    override val actions = listOf(
        CronDataRetrieval.action,
    )

    @Composable
    override fun StatusLine() = LastRunLine(id, CronDataRetrieval.ID)
}
