package io.github.ard9.linguaplayer.ui

import io.github.ard9.linguaplayer.reminder.ReviewReminder
import io.github.ard9.linguaplayer.data.ReminderPrefs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.R
import io.github.ard9.linguaplayer.data.AppLanguage

/** App settings: the language, and a way to the guide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    reminder: ReminderPrefs,
    onReminderChange: (ReminderPrefs) -> Unit,
    onOpenGuide: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Surface(color = Cinema.Seat, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp).selectableGroup()) {
                    Text(
                        stringResource(R.string.language),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    LanguageRow(stringResource(R.string.lang_system), language == AppLanguage.System) {
                        onLanguageChange(AppLanguage.System)
                    }
                    LanguageRow(stringResource(R.string.lang_english), language == AppLanguage.English) {
                        onLanguageChange(AppLanguage.English)
                    }
                    LanguageRow(stringResource(R.string.lang_persian), language == AppLanguage.Persian) {
                        onLanguageChange(AppLanguage.Persian)
                    }
                }
            }

            ReminderSection(reminder, onReminderChange)

            Surface(
                color = Cinema.Seat,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onOpenGuide)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Icon(PlayerIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.guide), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_guide_text), style = MaterialTheme.typography.bodySmall, color = Cinema.Dim)
                    }
                }
            }

            Text(
                stringResource(R.string.settings_other),
                style = MaterialTheme.typography.bodySmall,
                color = Cinema.Dim,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

/** The daily review reminder: a switch and the time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(reminder: ReminderPrefs, onChange: (ReminderPrefs) -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) onChange(reminder.copy(enabled = true))
    }
    fun turnOn() {
        if (ReviewReminder.canNotify(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onChange(reminder.copy(enabled = true))
        } else {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Surface(color = Cinema.Seat, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reminder), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.reminder_text), style = MaterialTheme.typography.bodySmall, color = Cinema.Dim)
                }
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = { on -> if (on) turnOn() else onChange(reminder.copy(enabled = false)) },
                )
            }
            if (reminder.enabled) {
                TextButton(onClick = { pickTime = true }) {
                    Text(
                        stringResource(
                            R.string.reminder_at,
                            stringResource(R.string.time_of_day, reminder.minutesOfDay / 60, reminder.minutesOfDay % 60),
                        )
                    )
                }
            }
            if (denied) {
                Text(stringResource(R.string.reminder_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(
            initialHour = reminder.minutesOfDay / 60,
            initialMinute = reminder.minutesOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    onChange(reminder.copy(minutesOfDay = state.hour * 60 + state.minute))
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
