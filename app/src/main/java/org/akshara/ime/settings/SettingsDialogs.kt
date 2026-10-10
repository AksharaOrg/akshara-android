package org.akshara.ime.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.akshara.ime.R
import org.akshara.ime.ime.KeyboardTheme

/** The one dialog Settings shows at a time; the Activity sets it and [SettingsDialogHost] draws it. */
internal sealed interface SettingsDialog {
    /** Pick one of [labels]; picking closes the dialog. */
    data class Choice(val title: String, val labels: List<String>, val selected: Int, val onPick: (Int) -> Unit) : SettingsDialog

    data class Confirm(val title: String, val message: String, val action: String, val run: () -> Unit) : SettingsDialog

    /** Gboard's apply sheet: a preview that follows the Key borders switch, then Apply. */
    data class ThemeSheet(
        val title: String,
        val keyBorders: Boolean,
        val preview: (keyBorders: Boolean) -> KeyboardTheme,
        val onApply: (keyBorders: Boolean) -> Unit
    ) : SettingsDialog
}

@Composable
internal fun SettingsDialogHost(dialog: SettingsDialog?, onDismiss: () -> Unit) {
    when (dialog) {
        null -> Unit
        is SettingsDialog.Choice -> ChoiceDialog(dialog, onDismiss)
        is SettingsDialog.Confirm -> ConfirmDialog(dialog, onDismiss)
        is SettingsDialog.ThemeSheet -> ThemeSheet(dialog, onDismiss)
    }
}

@Composable
private fun ChoiceDialog(dialog: SettingsDialog.Choice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            Column(Modifier.selectableGroup()) {
                dialog.labels.forEachIndexed { index, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = index == dialog.selected, role = Role.RadioButton) {
                                onDismiss()
                                dialog.onPick(index)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = index == dialog.selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
private fun ConfirmDialog(dialog: SettingsDialog.Confirm, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = { Text(dialog.message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); dialog.run() }) {
                Text(dialog.action, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSheet(dialog: SettingsDialog.ThemeSheet, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var borders by remember(dialog) { mutableStateOf(dialog.keyBorders) }
    fun close(then: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); then() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(dialog.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(top = 16.dp))
            AndroidView(
                factory = { ThemePreviewView(it) },
                update = { it.theme = dialog.preview(borders) },
                modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 400.dp).fillMaxWidth().aspectRatio(1f / .62f)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 56.dp)
                    .toggleable(value = borders, role = Role.Switch) { borders = it },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.key_borders), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                SettingsSwitch(borders)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { close() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(onClick = { val chosen = borders; close { dialog.onApply(chosen) } }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.theme_apply))
                }
            }
        }
    }
}
