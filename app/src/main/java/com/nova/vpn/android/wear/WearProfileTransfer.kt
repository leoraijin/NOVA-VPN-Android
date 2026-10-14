package com.nova.vpn.android.wear

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.util.UUID

/** Explicit, single-watch transfer. Success means the watch saved and read back the profile. */
object WearProfileTransfer {
    private val handler = Handler(Looper.getMainLooper())
    private var inProgress = false

    fun send(context: Context, config: String, onResult: (String?) -> Unit) {
        val app = context.applicationContext
        handler.post { sendOnMain(app, config, onResult) }
    }

    private fun sendOnMain(context: Context, config: String, onResult: (String?) -> Unit) {
        if (inProgress) { onResult("Передача уже выполняется. Дождитесь ответа часов."); return }
        val id = UUID.randomUUID().toString()
        val payload = runCatching { WearProfileProtocol.encode(id, config) }.getOrNull()
            ?: run { onResult("Профиль некорректен или слишком большой для передачи на часы."); return }
        val client = runCatching { Wearable.getMessageClient(context) }.getOrNull()
            ?: run { onResult("Сервис связи с часами недоступен."); return }
        inProgress = true
        var finished = false
        var target: String? = null
        var timeout: Runnable? = null
        lateinit var listener: MessageClient.OnMessageReceivedListener
        fun finish(message: String?) {
            if (finished) return
            finished = true
            timeout?.let(handler::removeCallbacks)
            runCatching { client.removeListener(listener) }
            inProgress = false
            onResult(message)
        }
        listener = MessageClient.OnMessageReceivedListener { event ->
            handler.post {
                if (!finished && event.path == WearProfileProtocol.RESULT_PATH && event.sourceNodeId == target) {
                    when (WearProfileProtocol.result(event.data, id)) {
                        "stored" -> finish(null)
                        "storage_error" -> finish("Часы получили профиль, но не смогли сохранить его. Откройте NOVA на часах и повторите передачу.")
                    }
                }
            }
        }
        val deadline = Runnable { finish("Часы не подтвердили сохранение профиля. Проверьте связь с телефоном и обновите NOVA на обоих устройствах.") }
        timeout = deadline
        handler.postDelayed(deadline, 25_000)
        runCatching { client.addListener(listener) }
            .onSuccess { task ->
                task.addOnFailureListener { finish("Не удалось включить приём ответа часов.") }
                task.addOnSuccessListener {
                    if (finished) { runCatching { client.removeListener(listener) }; return@addOnSuccessListener }
                    runCatching { Wearable.getCapabilityClient(context).getCapability(WearProfileProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE) }
                        .onSuccess { discovery ->
                            discovery.addOnFailureListener { finish("Не удалось найти NOVA на часах. Проверьте сопряжение.") }
                            discovery.addOnSuccessListener discovered@{ info ->
                                if (finished) return@discovered
                                val nearby = info.nodes.filter { it.isNearby }
                                val node = if (nearby.size == 1) nearby.single() else info.nodes.singleOrNull()
                                if (node == null) {
                                    finish(if (info.nodes.isEmpty()) "Часы с NOVA не найдены. Установите NOVA Wear OS 0.3.4 или новее, откройте её на часах и проверьте сопряжение."
                                        else "Найдено несколько часов с NOVA. Оставьте подключёнными только те часы, на которые нужно передать профиль.")
                                    return@discovered
                                }
                                target = node.id
                                runCatching { client.sendMessage(node.id, WearProfileProtocol.PROFILE_PATH, payload) }
                                    .onSuccess { sending -> sending.addOnFailureListener { finish("Не удалось доставить профиль на часы. Проверьте связь и повторите передачу.") } }
                                    .onFailure { finish("Сервис передачи профиля недоступен.") }
                            }
                        }.onFailure { finish("Сервис поиска часов недоступен.") }
                }
            }.onFailure { finish("Не удалось включить приём ответа часов.") }
    }
}
