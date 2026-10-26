package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class ConnectionCoordinatorTest {
    @Test fun staleQueuedNetworkEffectsCannotCancelLatestRetryOrReviveStop() {
        val coordinator = ConnectionCoordinator()
        coordinator.networkChanged("wifi", true)
        coordinator.connect()
        val older = coordinator.networkChanged(null, false)!!
        val newer = coordinator.networkChanged("cellular", true)!!
        val retry = coordinator.failed(newer, .5)!!
        val queue = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val pending = queue.schedule({}, 1, java.util.concurrent.TimeUnit.DAYS)
            var effects = 0
            assertFalse(coordinator.accept(older, requireNetwork = false) { pending.cancel(false); effects++ })
            assertFalse(coordinator.accept(newer) { pending.cancel(false); effects++ })
            assertFalse(pending.isCancelled)
            assertTrue(coordinator.current(retry.ticket))
            assertTrue(coordinator.accept(retry.ticket) { effects++ })
            coordinator.stop()
            assertFalse(coordinator.accept(retry.ticket, requireNetwork = false) { pending.cancel(false); effects++ })
            assertEquals(1, effects)
            assertFalse(pending.isCancelled)
        } finally { queue.shutdownNow() }
    }

    @Test fun noNetworkWaitsAndManualStopInvalidatesEveryPendingResult() {
        val coordinator = ConnectionCoordinator()
        val initial = coordinator.connect()
        assertEquals(ConnectionCoordinator.Phase.WAITING_NETWORK, coordinator.view().phase)
        assertFalse(coordinator.current(initial))
        val online = coordinator.networkChanged("wifi", true)!!
        assertTrue(coordinator.current(online))
        val retry = coordinator.failed(online, .5)!!
        coordinator.stop()
        assertFalse(coordinator.current(retry.ticket))
        assertFalse(coordinator.succeeded(online))
        assertNull(coordinator.failed(retry.ticket))
        assertFalse(coordinator.view().wanted)
    }

    @Test fun duplicateCallbacksDoNotInvalidateConnectionButDnsAndNetworkChangesDo() {
        val coordinator = ConnectionCoordinator()
        coordinator.networkChanged("wifi_dns_a", true)
        val first = coordinator.connect()
        assertNull(coordinator.networkChanged("wifi_dns_a", true))
        assertTrue(coordinator.succeeded(first))
        val second = coordinator.networkChanged("wifi_dns_b", true)!!
        assertFalse(coordinator.succeeded(first))
        assertTrue(coordinator.current(second))
        val lost = coordinator.networkChanged(null, false)!!
        assertFalse(coordinator.current(lost))
        assertEquals(ConnectionCoordinator.Phase.WAITING_NETWORK, coordinator.view().phase)
        val cellular = coordinator.networkChanged("cellular", true)!!
        assertTrue(coordinator.succeeded(cellular))
    }

    @Test fun retryBackoffIsBoundedAndManualSelectionSupersedesIt() {
        val coordinator = ConnectionCoordinator()
        coordinator.networkChanged("wifi", true)
        var ticket = coordinator.connect()
        val delays = List(8) { coordinator.failed(ticket, .5)!!.also { ticket = it.ticket }.delayMillis }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L, 30000L), delays)
        val manual = coordinator.connect()
        assertFalse(coordinator.succeeded(ticket))
        assertTrue(coordinator.succeeded(manual))
        assertEquals(0, coordinator.view().failures)
    }

    @Test fun thirtyNetworkChangesCannotReviveAManuallyStoppedTunnel() {
        val coordinator = ConnectionCoordinator()
        coordinator.networkChanged("network_0", true)
        coordinator.connect(); coordinator.stop()
        repeat(30) { index ->
            val ticket = coordinator.networkChanged("network_${index + 1}", true)!!
            assertFalse(coordinator.current(ticket))
            assertFalse(coordinator.succeeded(ticket))
        }
        assertEquals(ConnectionCoordinator.Phase.STOPPED, coordinator.view().phase)
    }

    @Test fun delayedTerminalAndRollbackPublicationsCannotOverwriteNewIntent() {
        val coordinator = ConnectionCoordinator()
        coordinator.networkChanged("wifi", true)
        val old = coordinator.connect()
        val terminal = coordinator.terminal(old)!!
        val replacement = coordinator.connect()
        var published = false
        assertFalse(coordinator.acceptTerminal(terminal) { published = true })
        assertFalse(coordinator.accept(old) { coordinator.connect(); published = true })
        assertFalse(published)
        assertTrue(coordinator.current(replacement))
        coordinator.stop()
        assertFalse(coordinator.accept(replacement) { coordinator.connect(); published = true })
        assertFalse(published)
        assertFalse(coordinator.view().wanted)
    }

    @Test fun delayedStopCannotClearANewerConnectionOrStop() {
        val coordinator = ConnectionCoordinator()
        coordinator.networkChanged("wifi", true)
        coordinator.connect()
        val stopped = coordinator.stop()
        coordinator.networkChanged("cellular", true)
        assertTrue(coordinator.acceptStopped(stopped) { })
        coordinator.connect()
        assertFalse(coordinator.acceptStopped(stopped) { error("stale stop") })
        val latest = coordinator.stop()
        assertFalse(coordinator.acceptStopped(stopped) { error("older stop") })
        assertTrue(coordinator.acceptStopped(latest) { })
    }

    @Test fun physicalNetworkChangesDoNotHideATerminalConfigurationError() {
        val coordinator = ConnectionCoordinator()
        coordinator.networkChanged("wifi", true)
        val initial = coordinator.connect()
        val terminal = coordinator.terminal(initial)!!
        coordinator.networkChanged(null, false)
        var published = false
        assertTrue(coordinator.acceptTerminal(terminal) { published = true })
        assertTrue(published)
        assertFalse(coordinator.view().wanted)
        assertEquals(ConnectionCoordinator.Phase.ERROR, coordinator.view().phase)
    }
}
