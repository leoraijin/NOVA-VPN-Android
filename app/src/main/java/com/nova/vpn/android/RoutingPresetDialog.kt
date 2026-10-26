package com.nova.vpn.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nova.vpn.android.data.*
import kotlinx.coroutines.*

@Composable
internal fun RoutingPresetPicker(document: RoutingPresets, busy: Boolean, error: String?, editingId: String?,
    onEndEdit: () -> Unit, onManage: () -> Unit) {
    SoftCard {
        Text("Профиль маршрутизации: ${document.active.name}", style = MaterialTheme.typography.titleSmall)
        document.presets.firstOrNull { it.id == editingId && it.id != document.activeId }?.let {
            Text("Редактируется: ${it.name}. Изменения сохраняются без переключения VPN.")
            TextButton(onClick = onEndEdit, enabled = !busy) { Text("К активному профилю") }
        }
        document.pending?.let { change ->
            Text("Применяется: ${document.presets.first { it.id == change.presetId }.name}")
        }
        if (busy && document.pending == null) Text("Подготавливаем правила…")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onManage) { Text("Профили маршрутизации") }
    }
}

@Composable
internal fun RoutingPresetDialog(document: RoutingPresets, busy: Boolean,
    onActivate: (String) -> Unit, onEdit: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember { AppRoutingStore(context) }
    var editing by remember { mutableStateOf<RoutingPreset?>(null) }
    var adding by remember { mutableStateOf<RoutingPreset?>(null) }
    var name by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    val locked = busy || writing || document.pending != null
    fun change(operation: (RoutingPresets) -> RoutingPresets) {
        if (locked) return
        writing = true; failure = null
        scope.launch {
            try { withContext(Dispatchers.IO) { store.updatePresets {
                check(it.pending == null) { "Дождитесь переключения" }; operation(it)
            } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = "Не удалось сохранить профили маршрутизации." }
            finally { writing = false }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth(.96f).fillMaxHeight(.9f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Профили маршрутизации", style = MaterialTheme.typography.titleLarge)
                Text("Наборы правил работают с любым сервером. Исключённое приложение всегда использует обычную сеть Android.")
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                document.pending?.let { Text("Переключаем правила. Активным остаётся «${document.active.name}» до подтверждения службы.") }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(document.presets, key = { it.id }) { preset ->
                        SoftCard {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            Text(if (preset.id == document.activeId) "Активен" else "${preset.settings.packages.size} приложений · ${preset.settings.sites.size} сайтов")
                            TextButton(enabled = !locked, onClick = { onEdit(preset.id) }) { Text("Правила") }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(enabled = !locked && preset.id != document.activeId, onClick = { onActivate(preset.id) }) { Text("Применить") }
                                TextButton(enabled = !locked, onClick = { editing = preset; name = preset.name }) { Text("Имя") }
                                TextButton(enabled = !locked, onClick = {
                                    adding = preset.copy(id = java.util.UUID.randomUUID().toString()); name = (preset.name.take(36) + " — копия")
                                }) { Text("Копия") }
                            }
                            TextButton(enabled = !locked && preset.id != document.activeId, onClick = {
                                change { current -> check(preset.id != current.activeId); current.copy(presets = current.presets.filterNot { it.id == preset.id }) }
                            }) { Text("Удалить") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !locked && document.presets.size < 100, onClick = {
                        adding = RoutingPreset(java.util.UUID.randomUUID().toString(), "Все через VPN", AppRoutingSettings()); name = "Все через VPN"
                    }) { Text("Все через VPN") }
                    TextButton(enabled = !locked && document.presets.size < 100, onClick = {
                        adding = RoutingPreset(java.util.UUID.randomUUID().toString(), "Выбранные приложения", AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED)); name = "Выбранные приложения"
                    }) { Text("Только выбранные") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
        }
    }
    if (editing != null || adding != null) AlertDialog(
        onDismissRequest = { editing = null; adding = null }, title = { Text(if (adding != null) "Новый профиль" else "Название профиля") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, label = { Text("Название") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.trim().isNotEmpty() && !locked, onClick = {
            val creating = adding
            val selected = editing
            val label = name.trim()
            change { current -> if (creating != null) current.copy(presets = current.presets + creating.copy(name = label))
                else current.copy(presets = current.presets.map { if (it.id == selected?.id) it.copy(name = label) else it }) }
            editing = null; adding = null
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { editing = null; adding = null }) { Text("Отмена") } }
    )
}
