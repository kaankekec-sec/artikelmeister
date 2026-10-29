package com.artikelmeister.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAndScopesTest {

    @Test fun cancellationIsNeverReportedAsAnError() {
        assertNull(SyncErrors.userMessage(CancellationException("The coroutine scope left the composition"), "x"))
        assertNull(SyncErrors.userMessage(CancellationException(), "x"))
    }

    @Test fun ordinaryErrorsAreReported() {
        assertEquals("boom", SyncErrors.userMessage(IllegalStateException("boom"), "fallback"))
        assertEquals("fallback", SyncErrors.userMessage(IllegalStateException(), "fallback"))
    }

    @Test fun runCatchingCancellableRethrowsCancellationButCapturesOthers() {
        val ok = runCatchingCancellable { error("fail") }
        assertTrue(ok.isFailure)
        var rethrown = false
        try {
            runCatchingCancellable { throw CancellationException("cancel") }
        } catch (e: CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)
    }

    /** Simulates the real bug: a job in a scope that is cancelled mid-sync must not report an error and must clean up. */
    @Test fun cancelledSyncJobDoesNotReportErrorAndResetsFlag() = runBlocking {
        var isSyncing = false
        var reported: String? = null
        val started = CompletableDeferred<Unit>()
        val job: Job = launch {
            isSyncing = true
            try {
                started.complete(Unit)
                CompletableDeferred<Unit>().await() // suspends "forever" like a network call
            } catch (e: Exception) {
                reported = SyncErrors.userMessage(e, "Bağlantı kurulamadı")
                if (reported == null) throw e
            } finally {
                isSyncing = false
            }
        }
        started.await()
        job.cancel(CancellationException("The coroutine scope left the composition"))
        job.join()
        assertNull(reported)
        assertFalse(isSyncing)
        assertTrue(job.isCancelled)
    }

    @Test fun insufficientScopeResponsesAreDetected() {
        val body = """{"error":{"code":403,"message":"Request had insufficient authentication scopes.","status":"PERMISSION_DENIED","details":[{"reason":"ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}"""
        assertTrue(GoogleScopes.isInsufficientScopes(403, body))
        assertFalse(GoogleScopes.isInsufficientScopes(401, body))
        assertFalse(GoogleScopes.isInsufficientScopes(403, """{"error":{"message":"quota"}}"""))
    }

    @Test fun nativeAndManualFlowsAskForTheSameGeminiScopes() {
        for (s in listOf(GoogleScopes.GEMINI_RETRIEVER, GoogleScopes.CLOUD_PLATFORM, GoogleScopes.DRIVE_FILE)) {
            assertTrue(GoogleScopes.FULL_TOKEN.contains(s))
        }
        // Drive-only token set must stay narrow so existing users' Drive sync is unaffected.
        assertFalse(GoogleScopes.DRIVE_TOKEN.contains(GoogleScopes.GEMINI_RETRIEVER))
        assertFalse(GoogleScopes.DRIVE_TOKEN.contains(GoogleScopes.CLOUD_PLATFORM))
        assertNotNull(GeminiScopeException().message)
        assertTrue(GeminiScopeException().message!!.contains("Google Hesabını Yeniden Bağla"))
    }
}
