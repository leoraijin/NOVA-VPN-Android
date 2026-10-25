package com.nova.vpn.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.net.URI
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SubscriptionDialog(onDismiss: () -> Unit, onChanged: (List<ImportedProfile>) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val repository = remember { SubscriptionRepository(context) }
    var snapshot by remember { mutableStateOf(ProfileSnapshot()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Subscription?>(null) }
    var keepServers by remember { mutableStateOf(true) }
    var task by remember { mutableStateOf<Job?>(null) }
    val revision by ProfileVault.changes.collectAsState()
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { ProfileVault(context).loadSnapshot() } }
            .onSuccess { snapshot = it; loaded = true }.onFailure { failure = "Не удалось открыть защищённое хранилище." }
    }
    fun work(operation: suspend () -> ProfileSnapshot) {
        if (busy) return
        busy = true; failure = null
        task = scope.launch {
            try {
                snapshot = operation()
                SubscriptionScheduling.request(context, snapshot.subscriptions)
                onChanged(snapshot.profiles)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = "Не удалось выполнить действие. Сохранённые серверы доступны." }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { task?.cancel(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(.96f).fillMaxHeight(.92f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("Подписки", style = MaterialTheme.typography.headlineSmall)
                Text("Обновления сохраняют избранное и не переключают текущий VPN.", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled = loaded && !busy, onClick = { adding = true }) { Text("Добавить") }
                    TextButton(enabled = loaded && !busy && snapshot.subscriptions.isNotEmpty(), onClick = { work {
                        for (source in snapshot.subscriptions) { currentCoroutineContext().ensureActive(); repository.refresh(source.id) }
                        withContext(Dispatchers.IO) { ProfileVault(context).loadSnapshot() }
                    } }) { Text("Обновить все") }
                    TextButton(onClick = { task?.cancel(); onDismiss() }) { Text(if (busy) "Отмена" else "Закрыть") }
                }
                if (busy || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (loaded && snapshot.subscriptions.isEmpty()) item { Text("Добавьте HTTPS-подписку или импортируйте её на экране серверов.") }
                    items(snapshot.subscriptions, key = { it.id }) { source ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium)
                                Text(runCatching { URI(source.url).host }.getOrDefault("Подписка"), style = MaterialTheme.typography.bodySmall)
                                val count = snapshot.memberships.count { it.subscriptionId == source.id }
                                Text("Серверов: $count · +${source.added} / ~${source.updated} / −${source.removed}")
                                Text(subscriptionStatus(source.status), color = if (source.status in setOf("error", "auth", "rate_limited", "partial")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                Text("Последнее обновление: ${formatSubscriptionTime(source.lastSuccess)}", style = MaterialTheme.typography.bodySmall)
                                Text("Трафик: ${formatSubscriptionBytes(source.usedBytes)} / ${if (source.totalBytes == 0L) "без лимита" else formatSubscriptionBytes(source.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                                Text("Срок: ${source.expiresAtSeconds?.let { formatSubscriptionTime(it * 1000) } ?: "не указан"}", style = MaterialTheme.typography.bodySmall)
                                Text(if (source.intervalHours == 0) "Обновление вручную" else "Примерно каждые ${source.intervalHours} ч, при наличии сети и заряда", style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(enabled = !busy, onClick = { work { repository.refresh(source.id) } }) { Text("Обновить") }
                                    TextButton(enabled = !busy, onClick = { editing = source }) { Text("Изменить") }
                                    TextButton(enabled = !busy, onClick = { removing = source; keepServers = true }) { Text("Удалить") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) SubscriptionEditor(editing, onDismiss = { adding = false; editing = null }) { source ->
        work { withContext(Dispatchers.IO) { repository.save(source) } }
        adding = false; editing = null
    }
    removing?.let { source -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Удалить подписку?") },
        text = { Column { Text("Общие серверы других подписок и выбранный сервер сохранятся.")
            Row { Checkbox(checked = keepServers, onCheckedChange = { keepServers = it }); Text("Оставить все серверы как ручные") } } },
        confirmButton = { TextButton(onClick = {
            removing = null
            work { withContext(Dispatchers.IO) { repository.remove(source.id, keepServers) } }
        }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } }) }
}

@Composable
private fun SubscriptionEditor(source: Subscription?, onDismiss: () -> Unit, onSave: (Subscription) -> Unit) {
    var name by remember(source?.id) { mutableStateOf(source?.name.orEmpty()) }
    var url by remember(source?.id) { mutableStateOf(source?.url.orEmpty()) }
    var identity by remember(source?.id) { mutableStateOf(source?.sendDeviceId ?: false) }
    var interval by remember(source?.id) { mutableStateOf(source?.intervalHours ?: 0) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (source == null) "Новая подписка" else "Изменить подписку") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = url, onValueChange = { url = it.take(4096) }, label = { Text("HTTPS-ссылка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row { Checkbox(checked = identity, onCheckedChange = { identity = it }); Text("Передавать идентификатор устройства провайдеру") }
            Text("Привязанная в разделе идентичностей подписка использует её сохранённые заголовки.", style = MaterialTheme.typography.bodySmall)
            Text("Автообновление (Android может отложить запуск):", style = MaterialTheme.typography.bodySmall)
            Row { listOf(0, 6, 12, 24).forEach { hours -> TextButton(modifier = Modifier.weight(1f), onClick = { interval = hours }) { Text("${if (interval == hours) "✓ " else ""}${if (hours == 0) "Выкл" else "${hours}ч"}") } } }
            if (error) Text("Укажите название и корректную HTTPS-ссылку без логина и пароля в адресе.", color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(onClick = {
            val candidate = (source ?: Subscription(name = name.trim(), url = url.trim())).copy(name = name.trim(), url = url.trim(), sendDeviceId = identity, intervalHours = interval)
            if (runCatching { candidate.validate() }.isSuccess) onSave(candidate) else error = true
        }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

internal fun subscriptionStatus(status: String): String = when (status) {
    "ok" -> "Обновлено"; "not_modified" -> "Изменений нет"; "partial" -> "Неполное обновление: старые серверы сохранены"
    "auth" -> "Проверьте доступ к подписке и настройки идентичности"; "rate_limited" -> "Провайдер ограничил запросы. Повторите позже"
    "error" -> "Не удалось обновить. Сохранённые серверы не удалены"; else -> "Ещё не обновлялась"
}

private fun formatSubscriptionTime(value: Long): String = if (value <= 0) "ещё не было" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
private fun formatSubscriptionBytes(value: Long?): String = value?.let { String.format(java.util.Locale.getDefault(), "%.2f ГБ", it / 1073741824.0) } ?: "нет данных"
