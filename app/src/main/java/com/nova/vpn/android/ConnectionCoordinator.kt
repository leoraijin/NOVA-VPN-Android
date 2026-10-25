package com.nova.vpn.android

/** Synchronized intent/network epochs. No credentials, Android objects or timers in this reducer. */
internal class ConnectionCoordinator {
    enum class Phase { STOPPED, CONNECTING, ACTIVE, WAITING_NETWORK, RECOVERING, ERROR }
    data class Ticket(val intent: Long, val network: Long, val attempt: Long)
    data class Retry(val ticket: Ticket, val delayMillis: Long, val number: Int)
    data class View(val wanted: Boolean, val ready: Boolean, val phase: Phase, val ticket: Ticket, val failures: Int)

    private var intent = 0L
    private var network = 0L
    private var attempt = 0L
    private var wanted = false
    private var ready = false
    private var signature: String? = null
    private var failures = 0
    private var phase = Phase.STOPPED
    private fun ticket() = Ticket(intent, network, attempt)

    @Synchronized fun connect(): Ticket {
        intent++; attempt++; wanted = true; failures = 0
        phase = if (ready) Phase.CONNECTING else Phase.WAITING_NETWORK
        return ticket()
    }

    @Synchronized fun stop(): Ticket {
        intent++; attempt++; wanted = false; failures = 0; phase = Phase.STOPPED
        return ticket()
    }

    @Synchronized fun acceptStopped(value: Ticket, action: () -> Unit): Boolean {
        if (wanted || phase != Phase.STOPPED || value.intent != intent) return false
        action()
        return true
    }

    /** Null means that a duplicate callback must not restart or invalidate anything. */
    @Synchronized fun networkChanged(value: String?, isReady: Boolean): Ticket? {
        if (signature == value && ready == isReady) return null
        signature = value; ready = isReady; network++; attempt++; failures = 0
        if (wanted) phase = if (ready) Phase.RECOVERING else Phase.WAITING_NETWORK
        return ticket()
    }

    @Synchronized fun current(value: Ticket): Boolean = wanted && ready && value == ticket()
    @Synchronized fun accept(value: Ticket, requireNetwork: Boolean = true, action: () -> Unit): Boolean {
        if (!wanted || (requireNetwork && !ready) || value != ticket()) return false
        action()
        return true
    }

    @Synchronized fun succeeded(value: Ticket): Boolean {
        if (!current(value)) return false
        failures = 0; phase = Phase.ACTIVE
        return true
    }

    @Synchronized fun failed(value: Ticket, randomUnit: Double = kotlin.random.Random.nextDouble()): Retry? {
        if (!current(value)) return null
        failures = (failures + 1).coerceAtMost(30)
        attempt++; phase = Phase.RECOVERING
        val base = when (failures) { 1 -> 1000L; 2 -> 2000L; 3 -> 4000L; 4 -> 8000L; 5 -> 16000L; else -> 30000L }
        val jitter = (base * (randomUnit.coerceIn(0.0, 1.0) - .5) * .2).toLong()
        return Retry(ticket(), (base + jitter).coerceIn(900, 30000), failures)
    }

    @Synchronized fun terminal(value: Ticket): Ticket? {
        if (!current(value)) return null
        wanted = false; attempt++; phase = Phase.ERROR
        return ticket()
    }

    @Synchronized fun acceptTerminal(value: Ticket, action: () -> Unit): Boolean {
        if (wanted || phase != Phase.ERROR || value.intent != intent) return false
        action()
        return true
    }

    @Synchronized fun view() = View(wanted, ready, phase, ticket(), failures)
}
