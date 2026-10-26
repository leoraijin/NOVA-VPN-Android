package com.nova.vpn.android

internal data class ProbeAssessment(val millis: Int?, val successes: Int, val attempts: Int, val events: List<DiagnosticEvent> = emptyList(),
    val unsupported: Boolean = false) {
    companion object {
        fun from(samples: List<Int?>): ProbeAssessment {
            val successful = samples.filterNotNull().filter { it > 0 }.sorted()
            val median = if (successful.size < 2) null else {
                val middle = successful.size / 2
                if (successful.size % 2 == 1) successful[middle]
                else ((successful[middle - 1].toLong() + successful[middle]) / 2).toInt()
            }
            return ProbeAssessment(median, successful.size, samples.size)
        }
    }
}
