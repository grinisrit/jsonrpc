package me.y9san9.jsonrpc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class JsonRpcPendingResponse(
    val id: JsonRpcResponseId,
    val deferred: CompletableDeferred<JsonRpcResponse>,
)

// This optimizes handling of responses by not doing
// `filter` for every response
internal class JsonRpcResponseEngine(
    private val backgroundScope: CoroutineScope,
    private val incomingEngine: JsonRpcIncomingEngine,
) {
    private val failureSignal =
        CompletableDeferred<JsonRpcTransportException>()
    private var terminalFailure: JsonRpcTransportException? = null
    private val pending =
        mutableMapOf<
            JsonRpcResponseId,
            CompletableDeferred<JsonRpcResponse>,
            >()
    private val mutex = Mutex()

    fun start() {
        backgroundScope.launch(start = UNDISPATCHED) {
            incomingEngine.responses.collect { response ->
                val deferred =
                    mutex.withLock { pending.remove(response.id) }
                if (deferred != null) {
                    deferred.complete(response)
                }
            }
        }
    }

    suspend fun register(
        ids: List<JsonRpcRequestId>,
    ): List<JsonRpcPendingResponse> {
        val responseIds = ids.map { id -> id.toResponseId() }
        require(responseIds.distinct().size == responseIds.size) {
            "Request IDs must be unique"
        }
        val registrations = responseIds.map { id ->
            JsonRpcPendingResponse(
                id = id,
                deferred = CompletableDeferred(),
            )
        }

        mutex.withLock {
            terminalFailure?.let { failure -> throw failure }
            val alreadyPending = registrations.firstOrNull { registration ->
                registration.id in pending
            }
            check(alreadyPending == null) {
                "Request ID ${alreadyPending?.id} is already pending"
            }
            for (registration in registrations) {
                pending[registration.id] = registration.deferred
            }
        }

        return registrations
    }

    suspend fun fail(failure: JsonRpcTransportException) {
        val deferreds = mutex.withLock {
            if (terminalFailure != null) return
            terminalFailure = failure
            pending.values.toList().also { pending.clear() }
        }
        for (deferred in deferreds) {
            deferred.completeExceptionally(failure)
        }
        failureSignal.complete(failure)
    }

    suspend fun awaitFailure(): JsonRpcTransportException =
        failureSignal.await()

    suspend fun failureOrNull(): JsonRpcTransportException? =
        mutex.withLock { terminalFailure }

    suspend fun unregister(registrations: List<JsonRpcPendingResponse>) {
        val removed = mutex.withLock {
            registrations.mapNotNull { registration ->
                if (pending[registration.id] === registration.deferred) {
                    pending.remove(registration.id)
                } else {
                    null
                }
            }
        }
        for (deferred in removed) {
            deferred.cancel()
        }
    }
}
