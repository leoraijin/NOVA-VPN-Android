package com.nova.vpn.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

internal data class DiagnosticSnapshot(val text: String, val json: String, val advice: DiagnosticAdvice) {
    fun withProbe(result: ServerCheckResult): DiagnosticSnapshot {
        val events = DiagnosticEventCodec.decode(DiagnosticEventCodec.encode(result.events))
        val detail = org.json.JSONObject().put("outcome", result.outcome.name.lowercase())
            .put("engine", result.engine.id)
            .put("successes", result.successes.coerceIn(0, 6)).put("attempts", result.attempts.coerceIn(0, 6))
            .put("response_ms", result.millis?.takeIf { it > 0 } ?: org.json.JSONObject.NULL)
            .put("events", org.json.JSONArray(DiagnosticEventCodec.encode(events)))
        val nextAdvice = if (result.outcome == CheckOutcome.SUCCESS) DiagnosticAdvice("HTTPS через профиль подтверждён",
            "Успешных прикладных проверок: ${result.successes} из ${result.attempts}.", DiagnosticAction.CHECK_SERVERS, "Открыть серверы")
        else DiagnosticCatalog.advice(events.lastOrNull { it.code.contains("failed") || it.code.startsWith("core_") ||
            it.code in setOf("engine_config_unsupported", "engine_unavailable") })
        return copy(text = text.substringBefore("\nIsolated profile check:") + "\nIsolated profile check:\n" + detail.toString(2),
            json = org.json.JSONObject(json).put("isolated_check", detail).toString(2), advice = nextAdvice)
    }
}

@Composable
internal fun DiagnosticsDialog(snapshot: DiagnosticSnapshot, onDismiss: () -> Unit,
    onAction: (DiagnosticAction) -> Unit, onCopy: (String) -> Unit, onSave: (String, Boolean) -> Unit,
    checking: Boolean = false, checkAvailable: Boolean = false, checkMessage: String? = null, checkStage: DiagnosticStage? = null,
    onCheck: () -> Unit = {}, onCancelCheck: () -> Unit = {}) {
    var json by remember { mutableStateOf(false) }
    val content = if (json) snapshot.json else snapshot.text
    val availableHeight = LocalConfiguration.current.screenHeightDp.dp * .65f
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Диагностика") }, text = {
        Column(modifier = Modifier.heightIn(max = availableHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(snapshot.advice.title, style = MaterialTheme.typography.titleMedium)
            Text(snapshot.advice.detail, style = MaterialTheme.typography.bodySmall)
            if (checking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(when (checkStage) {
                    DiagnosticStage.TLS -> "Этап: TLS контрольного адреса"
                    DiagnosticStage.INTERNET -> "Этап: HTTPS-ответ"
                    DiagnosticStage.DNS -> "Этап: DNS"
                    DiagnosticStage.SERVER -> "Этап: соединение через прокси"
                    DiagnosticStage.NETWORK -> "Этап: физическая сеть"
                    else -> "Этап: подготовка проверочного процесса"
                }, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCancelCheck) { Text("Отменить проверку") }
            } else OutlinedButton(onClick = onCheck, enabled = checkAvailable) { Text("Проверить профиль") }
            checkMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Проверка не меняет выбранный сервер или маршруты. Системный DNS проверяется для имени сервера; DNS ядра может отличаться. TLS контрольного HTTPS отмечается отдельно от TLS/Reality VPN-сервера.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onAction(snapshot.advice.action) }) { Text(snapshot.advice.actionLabel) }
            Row {
                FilterChip(selected = !json, onClick = { json = false }, label = { Text("Текст") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = json, onClick = { json = true }, label = { Text("JSON") })
            }
            Text("Отчёт не включает адреса серверов, ключи, URL подписок и названия приложений.", style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(content, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCopy(content) }, modifier = Modifier.weight(1f)) { Text("Копировать") }
                OutlinedButton(onClick = { onSave(content, json) }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
}
