package com.crecker.manager.app

import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.systems.paisresults.PaisResultsSystem

/** כל המערכות באפליקציה. מערכת חדשה = שורה חדשה כאן. הסדר כאן = הסדר במסך הבית. */
object SystemRegistry {
    val systems: List<ManagedSystem> = listOf(
        PaisResultsSystem,
    )

    fun find(id: String?): ManagedSystem? = systems.firstOrNull { it.id == id }
}
