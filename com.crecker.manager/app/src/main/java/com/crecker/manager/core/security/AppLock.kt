package com.crecker.manager.core.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.crecker.manager.core.settings.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * מצב הנעילה של האפליקציה.
 *
 * - נעולה בהפעלה (אם הנעילה פעילה).
 * - ננעלת שוב אחרי שהייה ברקע לפי [AppPreferences.relockTimeout] (נרשם ל-ProcessLifecycleOwner).
 * - בזמן אימות ([authenticating]) מעבר לרקע לא נספר – ב-Android 10 ומטה מסך ה-PIN/pattern
 *   הוא Activity נפרד, ובלי זה האפליקציה הייתה ננעלת מחדש מיד אחרי האימות.
 */
class AppLock(private val prefs: AppPreferences) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(prefs.lockEnabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile
    var authenticating = false

    private var backgroundSince: Long? = null

    override fun onStop(owner: LifecycleOwner) {
        if (!authenticating) backgroundSince = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundSince ?: return
        backgroundSince = null
        val timeout = prefs.relockTimeout.millis ?: return
        if (prefs.lockEnabled && SystemClock.elapsedRealtime() - since >= timeout) _locked.value = true
    }

    /** נקרא כשהאפליקציה נסגרת (יציאה עם "חזרה"). */
    fun lock() {
        if (prefs.lockEnabled) _locked.value = true
    }

    fun unlock() {
        backgroundSince = null
        _locked.value = false
    }
}
