package com.nova.vpn.android

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Temporary foreground diagnostic ownership, independent of saved automatic mode. */
internal class AutomaticOperationGate {
    private var owners = 0
    private val requests = mutableMapOf<String, Long>()
    private val mutablePending = MutableStateFlow<Set<String>>(emptySet())
    val pending = mutablePending.asStateFlow()
    val paused: Boolean @Synchronized get() = owners > 0
    @Synchronized fun acquire(): AutoCloseable {
        owners++
        val closed = AtomicBoolean()
        return AutoCloseable { synchronized(this) { if (closed.compareAndSet(false, true)) owners-- } }
    }
    /** Registration and submission cannot race foreground diagnostic ownership. */
    @Synchronized fun dispatch(requestId: String, service: Long, submit: () -> Unit): Boolean {
        if (owners > 0) return false
        check(requestId !in requests)
        requests[requestId] = service
        mutablePending.value = requests.keys.toSet()
        try { submit() } catch (error: Throwable) { settled(requestId); throw error }
        return true
    }
    @Synchronized fun belongsTo(requestId: String, service: Long) = requests[requestId] == service
    @Synchronized fun requestsFor(service: Long) = requests.filterValues { it == service }.keys.toSet()
    @Synchronized fun settled(requestId: String) {
        requests.remove(requestId)
        mutablePending.value = requests.keys.toSet()
    }
    /** Called only after this service's native close, never on controller timeout. */
    @Synchronized fun serviceClosed(service: Long) {
        requests.entries.removeAll { it.value == service }
        mutablePending.value = requests.keys.toSet()
    }
}

internal object AutomaticProbePause {
    private val gate = AutomaticOperationGate()
    val paused get() = gate.paused
    val pending get() = gate.pending
    fun acquire() = gate.acquire()
    fun dispatch(requestId: String, service: Long, submit: () -> Unit) = gate.dispatch(requestId, service, submit)
    fun belongsTo(requestId: String, service: Long) = gate.belongsTo(requestId, service)
    fun requestsFor(service: Long) = gate.requestsFor(service)
    fun settled(requestId: String) = gate.settled(requestId)
    fun serviceClosed(service: Long) = gate.serviceClosed(service)
}
