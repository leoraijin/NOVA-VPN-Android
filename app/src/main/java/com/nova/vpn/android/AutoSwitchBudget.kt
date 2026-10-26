package com.nova.vpn.android

import org.json.JSONArray
import org.json.JSONObject

internal data class AutoSwitchEvent(val id: String, val atMillis: Long)
internal data class AutoSwitchBudget(val boot: Int, val observedMillis: Long,
    val events: List<AutoSwitchEvent> = emptyList(), val retryAtMillis: Long = 0) {
    fun reserve(id: String, now: Long): AutoSwitchBudget? {
        require(id.length in 1..128 && now >= observedMillis)
        val recent = events.filter { now - it.atMillis < WINDOW }
        if (now < retryAtMillis || recent.size >= 3 || recent.any { it.id == id }) return null
        return copy(observedMillis = now, events = recent + AutoSwitchEvent(id, now))
    }

    fun finish(id: String, applied: Boolean, now: Long): AutoSwitchBudget {
        require(now >= observedMillis)
        if (events.none { it.id == id }) return this
        return copy(observedMillis = now,
            events = if (applied) events.map { if (it.id == id) it.copy(atMillis = now) else it } else events.filter { it.id != id },
            retryAtMillis = if (applied) retryAtMillis else maxOf(retryAtMillis, now + 30_000))
    }

    fun encode(): String = JSONObject().put("schema", 1).put("boot", boot).put("observed", observedMillis)
        .put("retryAt", retryAtMillis).put("events", JSONArray().apply {
            events.forEach { put(JSONObject().put("id", it.id).put("at", it.atMillis)) }
        }).toString()

    companion object {
        const val WINDOW = 900_000L
        fun decode(value: String?, boot: Int, now: Long): AutoSwitchBudget {
            require(boot >= 0 && now >= 0 && now <= Long.MAX_VALUE - WINDOW)
            if (value == null) return AutoSwitchBudget(boot, now)
            return runCatching {
                val json = JSONObject(value)
                require(json.getInt("schema") == 1)
                if (json.getInt("boot") != boot) return AutoSwitchBudget(boot, now)
                val observed = json.getLong("observed")
                val retry = json.getLong("retryAt")
                require(observed in 0..now && retry in 0..now + WINDOW)
                val entries = json.getJSONArray("events")
                require(entries.length() <= 3)
                val events = List(entries.length()) { index ->
                    val event = entries.getJSONObject(index)
                    AutoSwitchEvent(event.getString("id").also { require(it.length in 1..128) },
                        event.getLong("at").also { require(it in 0..observed) })
                }
                require(events.map { it.id }.distinct().size == events.size)
                AutoSwitchBudget(boot, now, events.filter { now - it.atMillis < WINDOW }, retry)
            }.getOrElse { AutoSwitchBudget(boot, now, retryAtMillis = now + WINDOW) }
        }
    }
}
