package com.nova.vpn.android

import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.AppPreferencesStore
import com.nova.vpn.android.data.VpnEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class CheckOutcome { SUCCESS, FAILED, UNSUPPORTED }
internal data class ServerCheckResult(val outcome: CheckOutcome, val millis: Int? = null, val time: Long = System.currentTimeMillis(),
    val successes: Int = 0, val attempts: Int = 0, val networkSignature: String? = null,
    val configurationSignature: String? = null, val measuredAtNanos: Long = 0, val events: List<DiagnosticEvent> = emptyList(),
    val engine: VpnEngine = VpnEngine.SING_BOX)
internal data class ServerCheckState(val running: Boolean = false, val currentId: String? = null,
    val results: Map<String, ServerCheckResult> = emptyMap(), val completed: Int = 0, val total: Int = 0, val message: String? = null,
    val progressEvents: List<DiagnosticEvent> = emptyList())

/** No timers or startup hooks: only the screen's explicit button calls start(). */
internal class ServerChecks(private val context: Context, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(ServerCheckState())
    val state = mutableState.asStateFlow()
    @Volatile private var job: Job? = null
    @Volatile private var generation = 0

    fun cancel(message: String? = null) {
        job?.cancel()
        mutableState.update { it.copy(running = false, currentId = null, message = message ?: if(it.running) "Проверка остановлена" else it.message) }
    }

    fun reset() { cancel(); mutableState.value = ServerCheckState() }

    fun start(profiles: List<ImportedProfile>, diagnostic: Boolean = false, onBest: ((ImportedProfile) -> Boolean)? = null) {
        if (job?.isCompleted == false || profiles.isEmpty()) return
        val batch = ++generation
        val preferences = AppPreferencesStore(context)
        val engine = preferences.vpnEngine()
        mutableState.value = ServerCheckState(running = true, total = profiles.size)
        job = scope.launch {
          CHECK_MUTEX.withLock {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkSignature = java.util.concurrent.atomic.AtomicReference<String?>()
            val monitor = PhysicalNetworkMonitor(connectivity) { snapshot ->
                val previous = networkSignature.getAndSet(snapshot.signature)
                if (previous != null && previous != snapshot.signature && generation == batch)
                    cancel("Сеть изменилась. Проверку можно повторить.")
            }
            var client: ServerCheckClient? = null
            try {
                monitor.start()
                val physicalSignature = networkSignature.get() ?: error("No ready physical network")
                for (profile in profiles) {
                    ensureActive()
                    mutableState.update { it.copy(currentId = profile.id, progressEvents = emptyList()) }
                    check(preferences.vpnEngine() == engine) { "Engine changed during check" }
                    val config = withContext(Dispatchers.Default) { runCatching { ServerCheckConfig.build(profile, engine, context.packageName) }.getOrNull() }
                    val result = if (config == null) ServerCheckResult(CheckOutcome.UNSUPPORTED, events = listOf(
                        DiagnosticEvent(0, DiagnosticStage.SERVICE, "engine_config_unsupported", engine.id, 0, "other", emptyMap())))
                    else {
                        // Each profile has a disposable process so timed-out native
                        // handshakes cannot accumulate while the rest of the batch runs.
                        client = ServerCheckClient(context) { events ->
                            if (generation == batch) mutableState.update {
                                if (it.running && it.currentId == profile.id) it.copy(progressEvents = events) else it
                            }
                        }
                        try {
                            client.connect()
                            val result = client.check(config, diagnostic, engine)
                            ServerCheckResult(if (result.unsupported) CheckOutcome.UNSUPPORTED else if (result.millis != null) CheckOutcome.SUCCESS else CheckOutcome.FAILED,
                                result.millis, successes = result.successes, attempts = result.attempts, events = result.events)
                        } catch (_: TimeoutCancellationException) {
                            ServerCheckResult(CheckOutcome.FAILED, events = client?.lastEvents.orEmpty())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            ServerCheckResult(CheckOutcome.FAILED, events = client?.lastEvents.orEmpty())
                        } finally { client.close(); client = null }
                    }
                    ensureActive()
                    val scoped = result.copy(networkSignature = physicalSignature,
                        configurationSignature = ServerSelectionPolicy.configurationSignature(profile), measuredAtNanos = android.os.SystemClock.elapsedRealtimeNanos(), engine = engine)
                    mutableState.update { it.copy(results = it.results + (profile.id to scoped), completed = it.completed + 1) }
                }
                ensureActive()
                check(preferences.vpnEngine() == engine) { "Engine changed during check" }
                if (onBest != null && networkSignature.get() == physicalSignature && generation == batch) {
                    val best = ServerSelectionPolicy.best(profiles, mutableState.value.results, physicalSignature, android.os.SystemClock.elapsedRealtimeNanos(), engine)
                    if (best != null) {
                        val accepted = onBest(best)
                        mutableState.update { it.copy(message = if (accepted) "Выбран сервер: ${best.displayName}"
                            else "Результат подбора устарел. Текущий выбор сохранён.") }
                    } else mutableState.update { it.copy(message = "Рабочий сервер не найден. Текущий выбор сохранён.") }
                } else mutableState.update { it.copy(message = "Проверка завершена") }
            } catch (_: TimeoutCancellationException) {
                mutableState.update { current ->
                    val id = current.currentId
                    current.copy(results = if(id == null) current.results else current.results + (id to ServerCheckResult(CheckOutcome.FAILED)),
                        message = "Проверка остановлена: превышено время ожидания. Можно повторить.")
                }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(message = "Не удалось завершить проверку. Попробуйте ещё раз.") } }
            finally {
                client?.close()
                monitor.close()
                mutableState.update { it.copy(running = false, currentId = null) }
            }
          }
        }
    }
    companion object { private val CHECK_MUTEX = Mutex() }
}

private class ServerCheckClient(private val context: Context, private val onProgress: (List<DiagnosticEvent>) -> Unit) {
    var lastEvents: List<DiagnosticEvent> = emptyList()
        private set
    private val connected = CompletableDeferred<Messenger>()
    private val died = CompletableDeferred<Unit>()
    private var bound = false
    private var remote: Messenger? = null
    private var serial = 0
    private var pending: Pair<Int, CompletableDeferred<ProbeAssessment>>? = null
    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == ServerCheckService.PROGRESS && message.arg1 == pending?.first) {
            val events = DiagnosticEventCodec.decode(message.data.getString("diagnostic_events"))
            if (events.isNotEmpty()) { lastEvents = events; onProgress(events) }
        }
        if (message.what == ServerCheckService.RESULT && message.arg1 == pending?.first) pending?.second?.complete(
            ProbeAssessment(message.arg2.takeIf { it > 0 }, message.data.getInt("successes"), message.data.getInt("attempts"),
                DiagnosticEventCodec.decode(message.data.getString("diagnostic_events")), message.data.getBoolean("unsupported")))
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binder.linkToDeath({ died.complete(Unit) }, 0)
            remote = Messenger(binder)
            connected.complete(remote!!)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            died.complete(Unit)
            pending?.second?.completeExceptionally(IllegalStateException("Check process disconnected"))
        }
        override fun onNullBinding(name: ComponentName) { connected.completeExceptionally(IllegalStateException("Check binding unavailable")) }
        override fun onBindingDied(name: ComponentName) { onServiceDisconnected(name) }
    }

    suspend fun connect() {
        bound = context.bindService(Intent(context, ServerCheckService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(bound)
        withTimeout(5_000) { connected.await() }
    }

    suspend fun check(config: String, diagnostic: Boolean, engine: VpnEngine): ProbeAssessment {
        val deferred = CompletableDeferred<ProbeAssessment>()
        val id = ++serial
        pending = id to deferred
        connected.await().send(Message.obtain(null, ServerCheckService.CHECK, id, 0).apply {
            data = Bundle().apply { putString("config", config); putBoolean("diagnostic", diagnostic); putString("engine", engine.id) }
            replyTo = replies
        })
        return try { withTimeout(if (diagnostic) 45_000 else 30_000) { deferred.await() } }
        finally { pending = null }
    }

    suspend fun close() {
        pending?.second?.cancel(); pending = null
        runCatching { remote?.send(Message.obtain(null, ServerCheckService.CANCEL)) }
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        withContext(NonCancellable) {
            if (remote != null) withTimeoutOrNull(3_000) { died.await() }
            if (died.isCompleted) withContext(Dispatchers.IO) {
                runCatching {
                    val cache = java.io.File(context.cacheDir, "manual-server-checks").canonicalFile
                    if (cache.parentFile == context.cacheDir.canonicalFile) cache.deleteRecursively()
                }
            }
        }
        remote = null
    }
}
