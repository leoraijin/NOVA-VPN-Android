package com.nova.vpn.android.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable

/** Sends a profile only after an explicit user tap in the server list. */
object WearProfileTransfer {
    private const val PROFILE_PATH = "/nova/profile"
    private const val MAX_TRANSFER_BYTES = 90 * 1024

    fun send(context: Context, config: String, onResult: (String?) -> Unit) {
        val payload = config.toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_TRANSFER_BYTES) {
            onResult("Конфигурация слишком большая для передачи на часы.")
            return
        }
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    onResult("Часы не найдены. Проверьте сопряжение и повторите попытку.")
                    return@addOnSuccessListener
                }
                var remaining = nodes.size
                var sent = false
                nodes.forEach { node ->
                    Wearable.getMessageClient(context).sendMessage(node.id, PROFILE_PATH, payload)
                        .addOnSuccessListener {
                            sent = true
                            remaining--
                            if (remaining == 0) onResult(null)
                        }
                        .addOnFailureListener {
                            remaining--
                            if (remaining == 0) onResult(if (sent) null else "Не удалось передать конфигурацию на часы.")
                        }
                }
            }
            .addOnFailureListener { onResult("Не удалось найти сопряжённые часы.") }
    }
}
