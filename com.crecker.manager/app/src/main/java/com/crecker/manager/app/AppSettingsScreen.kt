package com.crecker.manager.app

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.crecker.manager.core.security.DeviceAuth
import com.crecker.manager.core.settings.AppLanguage
import com.crecker.manager.core.settings.RelockTimeout
import com.crecker.manager.core.settings.ThemeMode
import com.crecker.manager.graph

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Default.LightMode
        ThemeMode.DARK -> Icons.Default.DarkMode
    }

private enum class SettingDialog { THEME, LANGUAGE, LOCK, RELOCK }

/** הגדרות כלליות של האפליקציה. כל הגדרה פותחת דיאלוג בחירה. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.graph.appPreferences
    // שינוי מצב תצוגה/שפה יוצר מחדש את ה-Activity, אז הערכים נקראים מחדש בכל מקרה
    var theme by remember { mutableStateOf(prefs.themeMode) }
    val language = remember { AppLanguage.current() }
    var dialog by remember { mutableStateOf<SettingDialog?>(null) }
    var lockEnabled by remember { mutableStateOf(prefs.lockEnabled) }
    var relock by remember { mutableStateOf(prefs.relockTimeout) }
    val lockAvailable = remember { DeviceAuth.isAvailable(context) }
    val confirmTitle = stringResource(R.string.auth_confirm_change)
    val promptSubtitle = stringResource(R.string.auth_prompt_subtitle)
    val unavailableMessage = stringResource(R.string.lock_unavailable_short)

    /**
     * שינוי בהגדרות הנעילה מחייב אימות – כדי שמי שמחזיק את המכשיר פתוח לא יוכל לבטל אותה.
     * בלי נעילת מסך במכשיר אין מול מה לאמת: הפעלת הנעילה נחסמת, כל שינוי אחר מוחל ישירות.
     */
    fun confirmLockChange(enablesLock: Boolean, apply: () -> Unit) {
        if (!lockAvailable) {
            if (enablesLock) Toast.makeText(context, unavailableMessage, Toast.LENGTH_LONG).show() else apply()
            return
        }
        DeviceAuth.authenticate(context, confirmTitle, promptSubtitle) { ok -> if (ok) apply() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { LogoTitle(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                CategoryHeader(stringResource(R.string.settings_category_security))
                SettingItem(
                    Icons.Default.Fingerprint,
                    stringResource(R.string.setting_app_lock),
                    stringResource(if (lockEnabled) R.string.app_lock_on else R.string.app_lock_off) +
                        if (lockEnabled && !lockAvailable) " · " + stringResource(R.string.lock_unavailable_short) else "",
                ) {
                    dialog = SettingDialog.LOCK
                }
                HorizontalDivider()
                if (lockEnabled) {
                    SettingItem(Icons.Default.Timer, stringResource(R.string.setting_relock), stringResource(relock.label)) {
                        dialog = SettingDialog.RELOCK
                    }
                    HorizontalDivider()
                }

                CategoryHeader(stringResource(R.string.settings_category_display))
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
        SettingDialog.LOCK -> ChoiceDialog(
            title = stringResource(R.string.setting_app_lock),
            options = listOf(true, false),
            selected = lockEnabled,
            label = { stringResource(if (it) R.string.app_lock_on else R.string.app_lock_off) },
            icon = { if (it) Icons.Default.Lock else Icons.Default.LockOpen },
            onDismiss = { dialog = null },
            onSelect = { enabled ->
                dialog = null
                if (enabled != lockEnabled) {
                    confirmLockChange(enablesLock = enabled) {
                        lockEnabled = enabled
                        prefs.lockEnabled = enabled
                    }
                }
            },
        )
        SettingDialog.RELOCK -> ChoiceDialog(
            title = stringResource(R.string.setting_relock),
            options = RelockTimeout.entries,
            selected = relock,
            label = { stringResource(it.label) },
            onDismiss = { dialog = null },
            onSelect = { timeout ->
                dialog = null
                if (timeout != relock) {
                    confirmLockChange(enablesLock = false) {
                        relock = timeout
                        prefs.relockTimeout = timeout
                    }
                }
            },
        )
        null -> Unit
    }
}

/** כותרת קטגוריה – באותו סגנון כמו הקטגוריות במסך הבית. */
@Composable
private fun CategoryHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
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
