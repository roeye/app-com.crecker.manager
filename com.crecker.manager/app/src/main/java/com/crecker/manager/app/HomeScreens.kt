package com.crecker.manager.app

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crecker.manager.R
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.model.SystemAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** מסך הבית – כל המערכות, מקובצות לפי קטגוריה. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSystem: (ManagedSystem) -> Unit, onOpenSettings: () -> Unit) {
    DoubleBackToExit()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { LogoTitle(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            SystemRegistry.systems.groupBy { it.category }.forEach { (category, systems) ->
                item(key = "category:$category") {
                    Text(
                        stringResource(category),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(systems, key = { it.id }) { system ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenSystem(system) },
                        leadingContent = { Icon(system.icon, null) },
                        headlineContent = { Text(system.name) },
                        supportingContent = { system.StatusLine() },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * "חזרה" ראשונה מציגה toast; "חזרה" נוספת בתוך [EXIT_WINDOW_MS] יוצאת מהאפליקציה.
 * ה-toast מבוטל ידנית אחרי שנייה – LENGTH_SHORT של אנדרואיד ארוך יותר.
 */
@Composable
private fun DoubleBackToExit() {
    val context = LocalContext.current
    val message = stringResource(R.string.press_back_again_to_exit)
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<Toast?>(null) }
    var lastBackAt by remember { mutableLongStateOf(0L) }

    BackHandler {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < EXIT_WINDOW_MS) {
            toast?.cancel()
            (context as? Activity)?.finish()
            return@BackHandler
        }
        lastBackAt = now
        toast?.cancel()
        val shown = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
        toast = shown
        scope.launch {
            delay(EXIT_WINDOW_MS)
            shown.cancel()
        }
    }
}

private const val EXIT_WINDOW_MS = 1_000L

/** מסך מערכת – רשימת הפעולות שלה. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(system: ManagedSystem, onBack: () -> Unit, onOpenAction: (SystemAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(system.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            system.description?.let { description ->
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(description), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.actions_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(system.actions, key = { it.id }) { action ->
                ListItem(
                    modifier = Modifier.clickable { onOpenAction(action) },
                    leadingContent = { Icon(action.icon, null) },
                    headlineContent = { Text(stringResource(action.title)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                )
                HorizontalDivider()
            }
        }
    }
}
