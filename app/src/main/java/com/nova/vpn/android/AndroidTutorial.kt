package com.nova.vpn.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class TutorialStep(val title: String, val detail: String, val target: String) {
    Import("Добавьте свой ключ", "Серверы → «Добавить конфигурацию». Вставьте ключ или ссылку подписки от вашего провайдера. Здесь же можно считать QR-код или выбрать файл. NOVA не выдаёт ключи — нужен ваш профиль.", "Добавить конфигурацию"),
    Apps("Настройте приложения", "Выберите режим списка, затем отметьте приложения ниже. «Выбранные через VPN» — только отмеченные идут через VPN. «Выбранные напрямую» — отмеченные обходят VPN, остальные используют его. Список можно прокручивать и искать по названию.", "Режим списка и список приложений"),
    Sites("Настройте сайты", "Введите домен без https:// и пути, например example.com, и нажмите «Добавить». В карточке выберите «Через VPN» или «Напрямую». Правило охватывает поддомены и применяется раньше правила браузера. Карандаш редактирует домен.", "Маршрутизация по сайтам"),
    Servers("Выберите сервер", "После импорта нажмите карточку нужного сервера. Выбранный профиль используется для подключения на главной. При работающем VPN смена профиля применяется автоматически. Если сервер не отвечает, попробуйте другой из своей подписки.", "Карточки серверов"),
    Updates("Обновления с GitHub", "NOVA проверяет новые Android-выпуски на GitHub при открытии, если включён тумблер. «Проверить обновления» запускает проверку вручную. Новая версия открывает APK; установка требует вашего подтверждения в Android. Профили сохраняются при обновлении поверх приложения.", "Проверять обновления"),
    Connect("Включите VPN", "На главной нажмите кнопку подключения и подтвердите системный запрос VPN. Сначала добавьте ключ и выберите сервер. Проверка HTTPS уточнит состояние трафика. Во время работы в шторке остаётся одна тихая карточка с сервером; той же кнопкой можно отключить VPN.", "Кнопка подключения в карточке статуса");

    companion object {
        fun at(index: Int): TutorialStep? = entries.getOrNull(index)
    }
}

internal val LocalTutorialStep = compositionLocalOf<TutorialStep?> { null }

@Composable
internal fun TutorialPanel(step: TutorialStep, closing: Boolean, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val detailsScroll = rememberScrollState()
    LaunchedEffect(step) { detailsScroll.scrollTo(0) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.heightIn(max = 280.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Знакомство с NOVA · ${step.ordinal + 1} из ${TutorialStep.entries.size}", fontSize = 12.sp)
            LinearProgressIndicator(progress = { (step.ordinal + 1).toFloat() / TutorialStep.entries.size }, modifier = Modifier.fillMaxWidth())
            Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Column(Modifier.weight(1f, fill = false).verticalScroll(detailsScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(step.detail, fontSize = 13.sp)
                Text("↓ ${step.target} — карточка выделена ниже", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSkip, enabled = !closing, modifier = Modifier.weight(1.2f)) { Text("Пропустить", fontSize = 13.sp) }
                TextButton(onClick = onBack, enabled = !closing && step.ordinal > 0, modifier = Modifier.weight(0.8f)) { Text("Назад", fontSize = 13.sp) }
                Button(onClick = onNext, enabled = !closing, modifier = Modifier.weight(1f)) { Text(if (closing) "…" else if (step == TutorialStep.Connect) "Готово" else "Далее", fontSize = 14.sp) }
            }
        }
    }
}
