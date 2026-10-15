package com.nova.vpn.android

import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import com.nova.vpn.android.data.ImportedProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class CheckOutcome { SUCCESS, FAILED, UNSUPPORTED }
internal data class ServerCheckResult(val outcome: CheckOutcome, val millis: Int? = null, val time: Long = System.currentTimeMillis())
internal data class ServerCheckState(val running: Boolean = false, val currentId: String? = null,
    val results: Map<String, ServerCheckResult> = emptyMap(), val completed: Int = 0, val total: Int = 0, val message: String? = null)

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

    fun start(profiles: List<ImportedProfile>) {
        if (job?.isCompleted == false || profiles.isEmpty()) return
        val batch = ++generation
        mutableState.value = ServerCheckState(running = true, total = profiles.size)
        job = scope.launch {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    if (generation == batch) cancel("Сеть изменилась. Проверку можно повторить.")
                }
            }
            var registered = false
            var client: ServerCheckClient? = null
            try {
                connectivity.registerNetworkCallback(NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
                registered = true
                for (profile in profiles) {
                    ensureActive()
                    mutableState.update { it.copy(currentId = profile.id) }
                    val config = withContext(Dispatchers.Default) { runCatching { ServerCheckConfig.build(profile) }.getOrNull() }
                    val result = if (config == null) ServerCheckResult(CheckOutcome.UNSUPPORTED)
                    else {
                        // Each profile has a disposable process so timed-out native
                        // handshakes cannot accumulate while the rest of the batch runs.
                        client = ServerCheckClient(context)
                        try {
                            client.connect()
                            val millis = client.check(config)
                            ServerCheckResult(if (millis > 0) CheckOutcome.SUCCESS else CheckOutcome.FAILED, millis.takeIf { it > 0 })
                        } finally { client.close(); client = null }
                    }
                    ensureActive()
                    mutableState.update { it.copy(results = it.results + (profile.id to result), completed = it.completed + 1) }
                }
                mutableState.update { it.copy(message = "Проверка завершена") }
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
                if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
                mutableState.update { it.copy(running = false, currentId = null) }
            }
        }
    }
}

private class ServerCheckClient(private val context: Context) {
    private val connected = CompletableDeferred<Messenger>()
    private val died = CompletableDeferred<Unit>()
    private var bound = false
    private var remote: Messenger? = null
    private var serial = 0
    private var pending: Pair<Int, CompletableDeferred<Int>>? = null
    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == ServerCheckService.RESULT && message.arg1 == pending?.first) pending?.second?.complete(message.arg2)
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

    suspend fun check(config: String): Int {
        val deferred = CompletableDeferred<Int>()
        val id = ++serial
        pending = id to deferred
        connected.await().send(Message.obtain(null, ServerCheckService.CHECK, id, 0).apply {
            data = Bundle().apply { putString("config", config) }
            replyTo = replies
        })
        return try { withTimeout(12_000) { deferred.await() } }
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
