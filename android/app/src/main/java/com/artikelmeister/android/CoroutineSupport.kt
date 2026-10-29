package com.artikelmeister.android

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but never swallows [CancellationException]. Cancellation must always
 * propagate so structured concurrency keeps working and so it is never shown as an error.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

/** Decides what (if anything) the user should be told about a failed background operation. */
object SyncErrors {
    /** Returns null for cancellation (never a user-visible error), else a display message. */
    fun userMessage(error: Throwable, fallback: String): String? {
        if (error is CancellationException) return null
        return error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}
