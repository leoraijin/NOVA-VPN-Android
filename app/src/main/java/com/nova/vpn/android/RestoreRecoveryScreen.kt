package com.nova.vpn.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.FullTransfer
import com.nova.vpn.android.data.RestoreProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deliberately independent of all app stores, appearance settings and VPN configuration. */
@Composable
internal fun RestoreRecoveryScreen(activity: Activity, problem: RestoreProblem) {
    var busy by remember { mutableStateOf(false) }
    var retryFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Нужно восстановить данные", style = MaterialTheme.typography.headlineMedium)
                Text("Не удалось завершить восстановление. Доступ к профилям и настройкам приостановлен, чтобы не использовать несогласованные данные.")
                Text("Автоматический сброс данных не выполняется. Повторите попытку, если ошибка была временной.")
                Text(problem.code, style = MaterialTheme.typography.labelLarge)
                if (retryFailed) Text("Повторная попытка не помогла. Данные сохранены.")
                Button(enabled = !busy, onClick = {
                    busy = true; retryFailed = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { FullTransfer.recoverPending(activity.applicationContext) } }
                        retryFailed = result.isFailure
                        busy = false
                    }
                }) { Text(if (busy) "Восстанавливаем…" else "Повторить восстановление") }
                OutlinedButton(enabled = !busy, onClick = {
                    (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("NOVA recovery", problem.code))
                }) { Text("Скопировать код ошибки") }
                TextButton(enabled = !busy, onClick = { activity.finishAndRemoveTask() }) { Text("Закрыть приложение") }
            }
        }
    }
}
