package com.crecker.manager.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.crecker.manager.R
import com.crecker.manager.core.settings.AppLanguage
import com.crecker.manager.core.settings.ThemeMode
import com.crecker.manager.graph

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Default.LightMode
        ThemeMode.DARK -> Icons.Default.DarkMode
    }

private enum class SettingDialog { THEME, LANGUAGE }

/** הגדרות כלליות של האפליקציה. כל הגדרה פותחת דיאלוג בחירה. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val prefs = LocalContext.current.graph.appPreferences
    // שינוי מצב תצוגה/שפה יוצר מחדש את ה-Activity, אז הערכים נקראים מחדש בכל מקרה
    var theme by remember { mutableStateOf(prefs.themeMode) }
    val language = remember { AppLanguage.current() }
    var dialog by remember { mutableStateOf<SettingDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                SettingItem(Icons.Default.Palette, stringResource(R.string.setting_theme), stringResource(theme.label)) {
                    dialog = SettingDialog.THEME
                }
                HorizontalDivider()
                SettingItem(Icons.Default.Language, stringResource(R.string.setting_language), stringResource(language.label)) {
                    dialog = SettingDialog.LANGUAGE
                }
                HorizontalDivider()
            }
        }
    }

    when (dialog) {
        SettingDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = theme,
            label = { stringResource(it.label) },
            icon = { it.icon },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                theme = it
                prefs.themeMode = it
            },
        )
        SettingDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.setting_language),
            options = AppLanguage.entries,
            selected = language,
            label = { stringResource(it.label) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                if (it != language) it.apply()
            },
        )
        null -> Unit
    }
}

@Composable
private fun SettingItem(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
    )
}

/** דיאלוג בחירה יחידה. בחירה סוגרת את הדיאלוג ומחילה מיד. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    icon: ((T) -> ImageVector)? = null,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        icon?.let {
                            Icon(it(option), null)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
