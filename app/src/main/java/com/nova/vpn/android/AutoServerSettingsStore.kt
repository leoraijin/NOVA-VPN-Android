package com.nova.vpn.android

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class AutoServerOptions(val enabled: Boolean = false, val candidateIds: Set<String> = emptySet(), val revision: String = "initial") {
    fun encode(): String = JSONObject().put("schema", 1).put("enabled", enabled)
        .put("candidates", JSONArray(candidateIds.sorted())).put("revision", revision).toString()
    companion object {
        fun decode(value: String?): AutoServerOptions = runCatching {
            if (value == null) return AutoServerOptions()
            val json = JSONObject(value)
            require(json.getInt("schema") == 1)
            val ids = json.getJSONArray("candidates")
            require(ids.length() <= 2000)
            val candidates = (0 until ids.length()).map { ids.getString(it).also { id -> require(id.length in 1..128) } }.toSet()
            val enabled = json.getBoolean("enabled")
            require(!enabled || candidates.isNotEmpty())
            AutoServerOptions(enabled, candidates, json.getString("revision").also { require(it.length in 1..128) })
        }.getOrDefault(AutoServerOptions())
    }
}

/** Single preference value keeps enabled/scope/revision atomic; contains identifiers only. */
internal class AutoServerSettingsStore(context: Context) : AutoCloseable {
    private val recoveryContext = context.applicationContext
    private val preferences = context.applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var closed = false
    private val refresh = Runnable {
        val latest = runCatching { read() }.getOrElse { return@Runnable }
        if (!closed) mutable.value = latest
    }
    // Never resume the controller while a preference commit owns the store
    // monitor: its observer may need the service coordinator's monitor.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!closed && (key == null || key == KEY)) handler.post(refresh)
    }
    init { preferences.registerOnSharedPreferenceChangeListener(listener) }
    private fun read() = synchronized(com.nova.vpn.android.data.ProfileVault.lock) {
        com.nova.vpn.android.data.StoreRecoveryGuard.beforeAccess(recoveryContext)
        AutoServerOptions.decode(preferences.getString(KEY, null))
    }
    fun snapshot(): AutoServerOptions = read()
    fun save(enabled: Boolean, candidateIds: Set<String>? = null) = synchronized(com.nova.vpn.android.data.ProfileVault.lock) {
        com.nova.vpn.android.data.StoreRecoveryGuard.beforeAccess(recoveryContext)
        val candidates = candidateIds ?: read().candidateIds
        require(!enabled || candidates.isNotEmpty())
        require(candidates.size <= 2000 && candidates.all { it.length in 1..128 })
        val next = AutoServerOptions(enabled, candidates, java.util.UUID.randomUUID().toString())
        check(preferences.edit().putString(KEY, next.encode()).commit()) { "Не удалось сохранить авторежим" }
    }
    override fun close() {
        closed = true
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        handler.removeCallbacks(refresh)
    }
    companion object {
        private const val KEY = "automatic_servers_v1"
        fun pin(context: Context) = AutoServerSettingsStore(context).use { it.save(false) }
    }
}
