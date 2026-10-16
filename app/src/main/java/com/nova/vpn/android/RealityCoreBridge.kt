package com.nova.vpn.android

/** The native library is loaded only after the user explicitly selects Reality Core. */
internal object RealityCoreBridge {
    private val loaded by lazy { runCatching { System.loadLibrary("nova_reality_jni") }.isSuccess }
    fun requireAvailable() = check(loaded) { "Reality Core недоступно для этой сборки или архитектуры. Выберите sing-box." }

    // Called only on the VPN service's serial queue; native callbacks never take this monitor.
    external fun validate(config: String, directory: String): Boolean
    external fun start(config: String, directory: String, tunFd: Int, callbacks: Callbacks): Long
    external fun stop(handle: Long)
    external fun isAlive(handle: Long): Boolean
    external fun version(): String

    interface Callbacks {
        fun protectSocket(fd: Int): Boolean
        fun coreWarning(message: String)
    }
}
