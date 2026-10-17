package com.nova.vpn.android

/** Construct only after transport/TLS setup. Stop at response headers, before body validation. */
internal class ResponseLatency(private val nanoTime: () -> Long = System::nanoTime) {
    private val started = nanoTime()
    fun millis(): Int = ((nanoTime() - started) / 1_000_000).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    companion object {
        fun median(samples: List<Int>): Int {
            require(samples.size == 3 && samples.all { it > 0 })
            return samples.sorted()[1]
        }
    }
}
