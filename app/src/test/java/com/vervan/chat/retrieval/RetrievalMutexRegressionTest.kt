package com.vervan.chat.retrieval

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the RAG hang fixed in ChatViewModel.retrieveSourcesInner and
 * LocalApiServer.performRetrieval: both used to wrap `RetrievalEngine.retrieve()` in
 * `AppContainer.withEmbedding {}`, which locks `embeddingMutex` — the same Mutex
 * `RetrievalEngine.retrieve()` (via `embedWith`) locks again internally for its actual embed
 * call. `kotlinx.coroutines.sync.Mutex` is not reentrant, so the inner lock attempt suspended
 * forever waiting for the outer one (held by the same coroutine) to release — every grounded
 * chat query with a local embedding model active hung with no error, no timeout, no response.
 *
 * RetrievalEngine can't be constructed in a plain JVM test without a live Room database and a
 * loaded native embedding model, so this isolates the actual hazard instead: a suspend function
 * that locks a shared [Mutex] internally, called two ways — directly (how the fixed call sites
 * do it now) and from inside an outer lock on that same Mutex (how the buggy code did it).
 */
class RetrievalMutexRegressionTest {
    private val mutex = Mutex()

    /** Stands in for RetrievalEngine.retrieve(): locks the shared mutex itself, only around the
     * one call that needs it — same shape as embedWith. */
    private suspend fun retrieveStandIn(): String = mutex.withLock { "ok" }

    @Test
    fun `calling retrieve directly completes`() = runBlocking {
        assertEquals("ok", withTimeout(2_000) { retrieveStandIn() })
    }

    // Explicit `: Unit` return type — without it Kotlin infers String (retrieveStandIn's type)
    // for this expression body, and JUnit4 rejects a non-void @Test method before running
    // anything in the class at all ("should be void" InvalidTestClassError).
    @Test(expected = TimeoutCancellationException::class)
    fun `wrapping retrieve in an outer lock on the same mutex hangs`(): Unit = runBlocking {
        // This is the exact bug shape: app.container.withEmbedding { retrievalEngine.retrieve(...) }
        withTimeout(2_000) {
            mutex.withLock { retrieveStandIn() }
        }
    }
}
