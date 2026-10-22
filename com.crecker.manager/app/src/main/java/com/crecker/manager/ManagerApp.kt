package com.crecker.manager

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.crecker.manager.core.run.ActionRunner
import com.crecker.manager.core.run.Notifications
import com.crecker.manager.core.run.RunHistory
import com.crecker.manager.core.security.AppLock
import com.crecker.manager.core.settings.AppPreferences
import com.crecker.manager.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ManagerApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.appPreferences.applyTheme()
        ProcessLifecycleOwner.get().lifecycle.addObserver(graph.appLock)
        Notifications.createChannels(this)
    }
}

/** התלויות המשותפות של האפליקציה (service locator פשוט). */
class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appPreferences = AppPreferences(context)
    val appLock = AppLock(appPreferences)
    val settings = SettingsStore(context)
    val history = RunHistory(context)
    val runner = ActionRunner(context, settings, history, appScope)
}

val Context.graph: AppGraph get() = (applicationContext as ManagerApp).graph
