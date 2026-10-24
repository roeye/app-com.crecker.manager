package com.crecker.manager.app

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.crecker.manager.R
import com.crecker.manager.core.security.DeviceAuth
import com.crecker.manager.graph

/** מסך נעילה – מכסה את כל האפליקציה עד לאימות. מציג את דיאלוג האימות אוטומטית. */
@Composable
fun LockScreen() {
    val context = LocalContext.current
    val appLock = context.graph.appLock
    val promptTitle = stringResource(R.string.auth_prompt_title, stringResource(R.string.app_name))
    val promptSubtitle = stringResource(R.string.auth_prompt_subtitle)
    // נבדק מחדש כשחוזרים מהגדרות המכשיר
    var available by remember { mutableStateOf(DeviceAuth.isAvailable(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { available = DeviceAuth.isAvailable(context) }

    // כניסה לאפליקציה – בלי "אישור" אחרי זיהוי פנים
    fun prompt() = DeviceAuth.authenticate(context, promptTitle, promptSubtitle, requireConfirmation = false) { ok ->
        if (ok) appLock.unlock()
    }

    LaunchedEffect(available) { if (available) prompt() }
    // "חזרה" במסך הנעול יוצאת מהאפליקציה ולא מנווטת מאחורי הנעילה
    BackHandler { (context as? Activity)?.finish() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppLogo(size = 96.dp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lock_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            if (available) {
                Button(onClick = ::prompt) {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.unlock))
                }
            } else {
                Text(
                    stringResource(R.string.lock_unavailable),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
                    Text(stringResource(R.string.open_security_settings))
                }
                TextButton(onClick = appLock::unlock) { Text(stringResource(R.string.continue_without_lock)) }
            }
        }
    }
}
