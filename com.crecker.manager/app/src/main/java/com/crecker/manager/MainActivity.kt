package com.crecker.manager

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.crecker.manager.app.AppNavHost
import com.crecker.manager.app.LockScreen
import com.crecker.manager.core.run.Notifications
import com.crecker.manager.ui.theme.SpartappManagerTheme

/** AppCompatActivity – נדרש כדי ש-AppCompatDelegate יחיל שפה ומצב תצוגה (כולל כיוון RTL/LTR). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // התוכן (כולל פרטי חיבור לשרתים) לא יופיע בצילומי מסך ובתצוגת האפליקציות האחרונות
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        // עדכון שם ערוץ ההתראות אחרי החלפת שפה
        Notifications.createChannels(this)
        val appLock = graph.appLock
        setContent {
            SpartappManagerTheme {
                val locked by appLock.locked.collectAsState()
                Box {
                    // הניווט נשאר ב-composition מתחת לנעילה, כדי שאחרי ביטול הנעילה חוזרים לאותו מסך
                    Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) { AppNavHost() }
                    if (locked) LockScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        // יציאה מהאפליקציה (לא שינוי תצורה כמו החלפת שפה) – נועלים לפתיחה הבאה
        if (isFinishing && !isChangingConfigurations) graph.appLock.lock()
        super.onDestroy()
    }
}
