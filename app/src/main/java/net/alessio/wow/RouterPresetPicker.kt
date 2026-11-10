package net.alessio.wow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

@Composable
fun RouterPresetPicker(onDismiss: () -> Unit, onSelect: (RouterPreset) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { RouterPresetStore(context) }
    val initial = remember(store) { runCatching { store.read() } }
    var custom by remember { mutableStateOf(initial.getOrDefault(emptyList())) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RouterPreset?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }

    fun save(next: List<RouterPreset>): Boolean {
        val result = runCatching { store.save(next) }
        error = result.exceptionOrNull()?.toUiText(R.string.preset_save_failed)
        if (result.isSuccess) custom = next
        return result.isSuccess
    }

    if (!creating && deleting == null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label(R.string.router_presets)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { Text(label(R.string.built_in_presets), style = MaterialTheme.typography.labelLarge) }
                items(RouterPreset.builtIns) { preset ->
                    TextButton(onClick = { onSelect(preset) }, modifier = Modifier.fillMaxWidth()) {
                        PresetLabel(preset)
                    }
                }
                item { Text(label(R.string.custom_presets), style = MaterialTheme.typography.labelLarge) }
                items(custom) { preset ->
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onSelect(preset) }, modifier = Modifier.weight(1f)) {
                            PresetLabel(preset)
                        }
                        TextButton(onClick = { deleting = preset }) { Text(label(R.string.delete)) }
                    }
                }
                if (initial.isFailure) item { Text(label(R.string.presets_unreadable), color = MaterialTheme.colorScheme.error) }
                error?.let { message -> item { Text(label(message.id, *message.args), color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(onClick = { creating = true }, enabled = initial.isSuccess) { Text(label(R.string.new_preset)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(label(R.string.close)) } }
    )

    if (creating) NewRouterPreset(
        onDismiss = { creating = false },
        onSave = { preset ->
            requireText((RouterPreset.builtIns + custom).none { it.name.equals(preset.name, ignoreCase = true) }, R.string.duplicate_preset)
            if (!save(custom + preset)) throw UiFailure(R.string.preset_save_failed)
            creating = false
            onSelect(preset)
        }
    )

    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(label(R.string.runtime_delete, preset.name)) },
            text = { Text(label(R.string.delete_preset_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    save(custom - preset)
                    deleting = null
                }) { Text(label(R.string.delete_preset)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(label(R.string.cancel)) } }
        )
    }
}

@Composable
private fun PresetLabel(preset: RouterPreset) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(preset.name)
        Text(
            preset.template,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewRouterPreset(onDismiss: () -> Unit, onSave: (RouterPreset) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var command by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label(R.string.new_router_preset)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(label(R.string.router_model)) }, singleLine = true)
                OutlinedTextField(command, { command = it }, label = { Text(label(R.string.wol_command)) }, singleLine = true)
                Text(label(R.string.preset_tokens),
                    style = MaterialTheme.typography.bodySmall)
                error?.let { Text(label(it.id, *it.args), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(onClick = {
                runCatching {
                    val preset = RouterPreset(name.trim(), command.trim())
                    preset.validate()
                    keyboard?.hide()
                    focus.clearFocus()
                    onSave(preset)
                }.onFailure { error = it.toUiText(R.string.invalid_preset) }
            }) { Text(label(R.string.save_preset)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(label(R.string.cancel)) } }
    )
}
