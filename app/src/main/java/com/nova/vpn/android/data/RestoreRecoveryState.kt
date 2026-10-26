package com.nova.vpn.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RestoreProblem(val code: String) {
    AUTHENTICATION("RESTORE_AUTH_FAILED"), KEY("RESTORE_KEY_UNAVAILABLE"),
    STORAGE("RESTORE_STORAGE_ERROR"), FORMAT("RESTORE_INVALID_RECORD"), OTHER("RESTORE_FAILED")
}

/** Fixed public codes only: exception text, paths and profile contents never enter UI state. */
internal object RestoreRecoveryState {
    private val mutable = MutableStateFlow<RestoreProblem?>(null)
    val problem = mutable.asStateFlow()
    fun failed(error: Throwable) {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val names = causes.map { it.javaClass.simpleName }.toSet()
        mutable.value = when {
            names.any { it in setOf("AEADBadTagException", "BadPaddingException") } -> RestoreProblem.AUTHENTICATION
            names.any { it in setOf("RestoreKeyUnavailableException", "KeyPermanentlyInvalidatedException", "UnrecoverableKeyException", "UserNotAuthenticatedException") } -> RestoreProblem.KEY
            causes.any { it is java.io.IOException } || "ErrnoException" in names -> RestoreProblem.STORAGE
            names.any { it in setOf("JSONException", "IllegalArgumentException") } -> RestoreProblem.FORMAT
            else -> RestoreProblem.OTHER
        }
    }
    fun completed() { mutable.value = null }
}
