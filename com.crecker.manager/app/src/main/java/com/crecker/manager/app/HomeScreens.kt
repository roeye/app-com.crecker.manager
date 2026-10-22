package com.crecker.manager.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crecker.manager.R
import com.crecker.manager.core.model.ManagedSystem
import com.crecker.manager.core.model.SystemAction

/** מסך הבית – כל המערכות, מקובצות לפי קטגוריה. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSystem: (ManagedSystem) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            SystemRegistry.systems.groupBy { it.category }.forEach { (category, systems) ->
                item(key = "category:$category") {
                    Text(
                        category,
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

/** מסך מערכת – רשימת הפעולות שלה. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(system: ManagedSystem, onBack: () -> Unit, onOpenAction: (SystemAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(system.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (system.description.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(system.description, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text(
                    "פעולות",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(system.actions, key = { it.id }) { action ->
                ListItem(
                    modifier = Modifier.clickable { onOpenAction(action) },
                    leadingContent = { Icon(action.icon, null) },
                    headlineContent = { Text(action.title) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                )
                HorizontalDivider()
            }
        }
    }
}
