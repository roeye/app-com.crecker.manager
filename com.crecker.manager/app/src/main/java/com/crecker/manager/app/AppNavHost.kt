package com.crecker.manager.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crecker.manager.R
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.ui.LoggedActionScreen
import com.crecker.manager.core.ui.RunDetailScreen
import com.crecker.manager.core.ui.RunHistoryScreen
import com.crecker.manager.graph

/**
 * ניווט:
 * home → settings (הגדרות האפליקציה)
 * home → system/{systemId} → system/{systemId}/settings (הגדרות המערכת)
 * home → system/{systemId} → system/{systemId}/action/{actionId}
 *   ├─ …/settings
 *   └─ …/history → …/history/{runId}
 */
private object Routes {
    const val HOME = "home"
    const val APP_SETTINGS = "settings"
    const val SYSTEM = "system/{systemId}"
    const val SYSTEM_SETTINGS = "$SYSTEM/settings"
    const val ACTION = "$SYSTEM/action/{actionId}"
    const val SETTINGS = "$ACTION/settings"
    const val HISTORY = "$ACTION/history"
    const val RUN = "$HISTORY/{runId}"

    fun system(systemId: String) = "system/$systemId"
    fun action(systemId: String, actionId: String) = "${system(systemId)}/action/$actionId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSystem = { navController.navigate(Routes.system(it.id)) },
                onOpenSettings = { navController.navigate(Routes.APP_SETTINGS) },
            )
        }
        composable(Routes.APP_SETTINGS) {
            AppSettingsScreen(onBack = back)
        }
        composable(Routes.SYSTEM) { entry ->
            WithSystem(entry) { system ->
                SystemScreen(
                    system = system,
                    onBack = back,
                    onOpenSettings = { navController.navigate("${Routes.system(system.id)}/settings") },
                    onOpenAction = { navController.navigate(Routes.action(system.id, it.id)) },
                )
            }
        }
        composable(Routes.SYSTEM_SETTINGS) { entry ->
            WithSystem(entry) { system ->
                val store = LocalContext.current.graph.settings
                val settings = remember(system.id) { store.forSystem(system.id) }
                system.settingsScreen?.invoke(settings, back) ?: NotFound()
            }
        }
        composable(Routes.ACTION) { entry ->
            WithAction(entry) { system, action ->
                val base = Routes.action(system.id, action.id)
                val settings = rememberActionSettings(system, action)
                when (action) {
                    is SystemAction.Logged -> LoggedActionScreen(
                        systemId = system.id,
                        action = action,
                        onBack = back,
                        onOpenSettings = { navController.navigate("$base/settings") },
                        onOpenHistory = { navController.navigate("$base/history") },
                    )
                    is SystemAction.Custom -> action.screen(settings, back)
                }
            }
        }
        composable(Routes.SETTINGS) { entry ->
            WithAction(entry) { system, action ->
                val settings = rememberActionSettings(system, action)
                (action as? SystemAction.Logged)?.settingsScreen?.invoke(settings, back) ?: NotFound()
            }
        }
        composable(Routes.HISTORY) { entry ->
            WithAction(entry) { system, action ->
                RunHistoryScreen(system.id, action, back) { runId ->
                    navController.navigate("${Routes.action(system.id, action.id)}/history/$runId")
                }
            }
        }
        composable(Routes.RUN) { entry ->
            WithAction(entry) { system, action ->
                RunDetailScreen(system.id, action, entry.arguments?.getString("runId").orEmpty(), back)
            }
        }
    }
}

@Composable
private fun rememberActionSettings(system: ManagedSystem, action: SystemAction) =
    LocalContext.current.graph.settings.let { store -> remember(system.id, action.id) { store.forAction(system.id, action.id) } }

@Composable
private fun WithSystem(entry: NavBackStackEntry, content: @Composable (ManagedSystem) -> Unit) {
    SystemRegistry.find(entry.arguments?.getString("systemId"))?.let { content(it) } ?: NotFound()
}

@Composable
private fun WithAction(entry: NavBackStackEntry, content: @Composable (ManagedSystem, SystemAction) -> Unit) {
    WithSystem(entry) { system ->
        system.action(entry.arguments?.getString("actionId").orEmpty())?.let { content(system, it) } ?: NotFound()
    }
}

@Composable
private fun NotFound() {
    Text(stringResource(R.string.not_found), Modifier.fillMaxSize().padding(24.dp))
}
