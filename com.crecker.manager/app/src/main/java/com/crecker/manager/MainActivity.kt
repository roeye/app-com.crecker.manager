package com.crecker.manager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.crecker.manager.app.AppNavHost
import com.crecker.manager.core.run.Notifications
import com.crecker.manager.ui.theme.SpartappManagerTheme

/** AppCompatActivity – נדרש כדי ש-AppCompatDelegate יחיל שפה ומצב תצוגה (כולל כיוון RTL/LTR). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // עדכון שם ערוץ ההתראות אחרי החלפת שפה
        Notifications.createChannels(this)
        setContent {
            SpartappManagerTheme {
                AppNavHost()
            }
        }
    }
}
