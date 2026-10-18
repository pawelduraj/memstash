package dev.pawelduraj.memstash.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pawelduraj.memstash.BuildConfig
import dev.pawelduraj.memstash.R
import dev.pawelduraj.memstash.data.CopyCleanup
import dev.pawelduraj.memstash.data.Library
import dev.pawelduraj.memstash.data.SendMode
import dev.pawelduraj.memstash.data.Settings
import dev.pawelduraj.memstash.data.SortOrder
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    library: Library,
    settings: Settings,
    onPickFolder: () -> Unit,
    onRequestTile: () -> Unit,
    onBack: () -> Unit,
) {
    val indexSize by vm.indexSize.collectAsStateWithLifecycle()
    val indexing by vm.indexing.collectAsStateWithLifecycle()
    val hasHistory by vm.hasHistory.collectAsStateWithLifecycle()
    val hiddenFromGallery by vm.hiddenFromGallery.collectAsStateWithLifecycle()
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var chooseCopyCleanup by remember { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val location = remember(library) { library.location(context) }

    if (showLicenses) {
        LicensesScreen(onBack = { showLicenses = false })
        return
    }

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(scroll)
    ) {
        ScreenBar(stringResource(R.string.settings), onBack)

        Section(stringResource(R.string.section_folder))
        Text(
            location,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            if (indexing) stringResource(R.string.loading_list) else stringResource(R.string.memes_in_folder, indexSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPickFolder) { Text(stringResource(R.string.change_folder)) }
            OutlinedButton(onClick = { vm.refreshIndex() }) { Text(stringResource(R.string.refresh)) }
            TextButton(onClick = { confirmDisconnect = true }) { Text(stringResource(R.string.disconnect_folder)) }
        }
        // Galleries see only folders on the phone.
        if (library.isOnDevice) {
            SwitchRow(
                checked = hiddenFromGallery == true,
                title = stringResource(R.string.hide_from_gallery),
                description = stringResource(R.string.hide_from_gallery_description),
                enabled = hiddenFromGallery != null,
            ) { vm.setHiddenFromGallery(it) }
        }

        Section(stringResource(R.string.section_send))
        Choice(
            selected = settings.sendMode == SendMode.GALLERY,
            title = stringResource(R.string.mode_gallery),
            description = stringResource(R.string.mode_gallery_description),
        ) { vm.updateSettings { it.copy(sendMode = SendMode.GALLERY) } }
        Choice(
            selected = settings.sendMode == SendMode.CLIPBOARD,
            title = stringResource(R.string.mode_clipboard),
            description = stringResource(R.string.mode_clipboard_description),
        ) { vm.updateSettings { it.copy(sendMode = SendMode.CLIPBOARD) } }
        if (settings.sendMode == SendMode.GALLERY) {
            ValueRow(stringResource(R.string.delete_copies), copyCleanupLabel(settings.copyCleanup)) { chooseCopyCleanup = true }
            OutlinedButton(onClick = { vm.deleteCopiesNow() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.delete_copies_now))
            }
        }

        Section(stringResource(R.string.section_look))
        Text(stringResource(R.string.columns, settings.columns), modifier = Modifier.padding(horizontal = 16.dp))
        Slider(
            value = settings.columns.toFloat(),
            onValueChange = { value ->
                val columns = value.roundToInt()
                if (columns != settings.columns) vm.updateSettings { it.copy(columns = columns) }
            },
            valueRange = Settings.COLUMN_RANGE.first.toFloat()..Settings.COLUMN_RANGE.last.toFloat(),
            steps = Settings.COLUMN_RANGE.last - Settings.COLUMN_RANGE.first - 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(stringResource(R.string.sort_label), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        Choice(settings.sortOrder == SortOrder.NAME, stringResource(R.string.sort_name)) {
            vm.updateSettings { it.copy(sortOrder = SortOrder.NAME) }
        }
        Choice(settings.sortOrder == SortOrder.NEWEST, stringResource(R.string.sort_newest)) {
            vm.updateSettings { it.copy(sortOrder = SortOrder.NEWEST) }
        }

        Section(stringResource(R.string.section_behavior))
        SwitchRow(
            checked = settings.focusSearchOnOpen,
            title = stringResource(R.string.keyboard_on_open),
            description = stringResource(R.string.keyboard_on_open_description),
        ) { on -> vm.updateSettings { it.copy(focusSearchOnOpen = on) } }
        SwitchRow(
            checked = settings.closeCompletely,
            title = stringResource(R.string.close_completely),
            description = stringResource(R.string.close_completely_description),
        ) { on -> vm.updateSettings { it.copy(closeCompletely = on) } }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.tile_title))
                Text(
                    stringResource(R.string.tile_how, stringResource(R.string.tile_label)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                FilledTonalButton(onClick = onRequestTile) { Text(stringResource(R.string.tile_add)) }
            }
            OutlinedButton(onClick = { confirmClearHistory = true }, enabled = hasHistory) { Text(stringResource(R.string.clear_history)) }
        }

        Section(stringResource(R.string.section_about))
        Text("MemStash ${BuildConfig.VERSION_NAME}", modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            "$COPYRIGHT\n\n${stringResource(R.string.license_notice)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openLink(context, PRIVACY_POLICY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
            OutlinedButton(onClick = { openLink(context, SOURCE_CODE_URL) }) { Text(stringResource(R.string.source_code)) }
            OutlinedButton(onClick = { showLicenses = true }) { Text(stringResource(R.string.licenses)) }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (chooseCopyCleanup) {
        AlertDialog(
            onDismissRequest = { chooseCopyCleanup = false },
            title = { Text(stringResource(R.string.delete_copies)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CopyCleanup.entries.forEach { option ->
                        Choice(option == settings.copyCleanup, copyCleanupLabel(option)) {
                            chooseCopyCleanup = false
                            vm.setCopyCleanup(option)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseCopyCleanup = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.disconnect_title),
            text = stringResource(R.string.disconnect_text, location),
            confirmLabel = stringResource(R.string.disconnect_folder),
            onConfirm = {
                confirmDisconnect = false
                vm.disconnectFolder()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_title),
            text = stringResource(R.string.clear_history_text),
            confirmLabel = stringResource(R.string.clear_history_confirm),
            onConfirm = {
                confirmClearHistory = false
                vm.clearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

internal const val COPYRIGHT = "Copyright © 2026 Paweł Duraj"
private const val PRIVACY_POLICY_URL = "https://memstash.pawelduraj.dev/privacy/"
private const val SOURCE_CODE_URL = "https://github.com/pawelduraj/memstash"

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun copyCleanupLabel(cleanup: CopyCleanup): String = when (cleanup) {
    CopyCleanup.ON_OPEN -> stringResource(R.string.copy_cleanup_on_open)
    else -> pluralStringResource(R.plurals.copy_cleanup_after, cleanup.minutes, cleanup.minutes)
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun ScreenBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    checked: Boolean,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(title)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, description: String? = null, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
