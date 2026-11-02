package com.nova.vpn.android

import android.content.Context
import android.net.ConnectivityManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real framework registration, synthetic lifecycle only; never creates a VPN. */
internal object PhysicalNetworkMonitorSmoke {
    fun run(context: Context) {
        check(isIsolatedUsbTestPackage(context.packageName))
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val publications = AtomicInteger()
        val closedFirst = PhysicalNetworkMonitor(connectivity) { publications.incrementAndGet() }
        closedFirst.close()
        try {
            closedFirst.start()
            check(publications.get() == 0) { "Closed monitor restarted" }
        } finally { closedFirst.close() }

        val monitor = PhysicalNetworkMonitor(connectivity) { publications.incrementAndGet() }
        try {
            monitor.start()
            monitor.start() // Framework must not receive a duplicate callback registration.
        } finally { monitor.close() }
        val afterClose = publications.get()
        monitor.start()
        android.os.SystemClock.sleep(100)
        check(publications.get() == afterClose) { "Closed monitor published" }

        repeat(40) {
            val ready = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val raced = PhysicalNetworkMonitor(connectivity) { }
            val starter = Thread {
                try { check(ready.await(5, TimeUnit.SECONDS)); raced.start() }
                catch (error: Throwable) { failure.compareAndSet(null, error) }
            }
            val closer = Thread {
                try { check(ready.await(5, TimeUnit.SECONDS)); raced.close() }
                catch (error: Throwable) { failure.compareAndSet(null, error) }
            }
            starter.start(); closer.start(); ready.countDown()
            starter.join(5000); closer.join(5000)
            try {
                check(!starter.isAlive && !closer.isAlive) { "Monitor lifecycle deadlocked" }
                failure.get()?.let { throw it }
            } finally { raced.close() }
        }
    }
}
