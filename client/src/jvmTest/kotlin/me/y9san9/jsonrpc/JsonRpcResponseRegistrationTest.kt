package me.y9san9.jsonrpc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import java.io.Closeable
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class JsonRpcResponseRegistrationTest {
    @Test
    fun `registers response before sending request`() {
        LifoDispatcher().use { dispatcher ->
            val transport = TestTransport()

            val result = runBlocking(dispatcher) {
                withTimeout(1_000) {
                    connector(transport).connect {
                        execute(request())
                    }
                }
            }

            val response = assertIs<JsonRpc.Result.Success<JsonRpcResponse>>(
                result,
            ).value
            assertEquals(JsonRpcResponseId.Long(1), response.id)
            assertEquals(JsonPrimitive("ok"), response.result)
        }
    }

    @Test
    fun `registers every batch response before sending request`() {
        LifoDispatcher().use { dispatcher ->
            val transport = TestTransport()

            val result = runBlocking(dispatcher) {
                withTimeout(1_000) {
                    connector(transport).connect {
                        execute(listOf(request(1), request(2)))
                    }
                }
            }

            val responses =
                assertIs<JsonRpc.Result.Success<List<JsonRpcResponse>>>(
                    result,
                ).value
            assertEquals(
                listOf(
                    JsonRpcResponseId.Long(1),
                    JsonRpcResponseId.Long(2),
                ),
                responses.map { response -> response.id },
            )
        }
    }

    @Test
    fun `removes registration when send fails`(): Unit = runBlocking {
        val transport = TestTransport(mode = SendMode.Fail)

        val result = connector(transport).connect {
            val failure = try {
                execute(request())
                null
            } catch (exception: Exception) {
                exception
            }
            assertIs<TestSendException>(failure)

            transport.mode = SendMode.Respond
            withTimeout(1_000) {
                execute(request())
            }
        }

        assertIs<JsonRpc.Result.Success<JsonRpcResponse>>(result)
    }

    @Test
    fun `removes registration when caller is cancelled`(): Unit = runBlocking {
        val transport = TestTransport(mode = SendMode.Ignore)

        val result = connector(transport).connect {
            val job = launch {
                execute(request())
            }
            transport.awaitSend()
            job.cancelAndJoin()

            transport.mode = SendMode.Respond
            withTimeout(1_000) {
                execute(request())
            }
        }

        assertIs<JsonRpc.Result.Success<JsonRpcResponse>>(result)
    }

    @Test
    fun `timeout poisons connection even when caller catches it`(): Unit =
        runBlocking {
            val transport = TestTransport(mode = SendMode.Ignore)

            val result = connector(
                transport = transport,
                requestTimeout = 50.milliseconds,
            ).connect {
                val failure = assertFailsWith<
                    JsonRpcRequestTimeoutException,
                    > {
                    execute(request())
                }
                assertEquals(50.milliseconds, failure.timeout)

                withContext(NonCancellable) {
                    transport.enqueueResponse(id = 1, result = "stale")
                    val retryFailure = assertFailsWith<
                        JsonRpcRequestTimeoutException,
                        > {
                        execute(request())
                    }
                    assertEquals(failure, retryFailure)
                }
            }

            val connectionFailure =
                assertIs<JsonRpc.Result.TransportFailure>(result)
            assertIs<JsonRpcRequestTimeoutException>(connectionFailure.cause)
        }

    private fun request(id: Long = 1): JsonRpcMethod = JsonRpcMethod(
        id = JsonRpcRequestId.Long(id),
        method = JsonRpcMethodName("test"),
    )

    private fun connector(
        transport: TestTransport,
        requestTimeout: Duration = 1_000.milliseconds,
    ): JsonRpc.Connector = JsonRpc.Connector(
        transport = TestTransportConnector(transport),
        config = JsonRpcConfig(
            side = JsonRpcSide.Client,
            requestTimeout = requestTimeout,
        ),
    )
}

private enum class SendMode {
    Respond,
    Ignore,
    Fail,
}

private class TestTransportConnector(private val transport: TestTransport) :
    JsonRpcTransport.Connector {
    override suspend fun <T> connect(
        block: suspend JsonRpcTransport.() -> T,
    ): JsonRpcTransport.Result<T> = try {
        JsonRpcTransport.Result.Success(transport.block())
    } catch (exception: JsonRpcTransportException) {
        JsonRpcTransport.Result.TransportFailure(
            message = exception.message,
            cause = exception,
        )
    }
}

private class TestTransport(@Volatile var mode: SendMode = SendMode.Respond) :
    JsonRpcTransport {
    private val incoming = Channel<IncomingMessage>(Channel.UNLIMITED)
    private val sends = Channel<Unit>(Channel.UNLIMITED)
    override val isActive: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun send(data: String) {
        sends.send(Unit)
        when (mode) {
            SendMode.Respond -> {
                val ids = Regex("\\\"id\\\":(\\d+)")
                    .findAll(data)
                    .map { match -> match.groupValues[1] }
                    .toList()
                assertNotNull(ids.firstOrNull())
                val responses = ids.reversed().map { id ->
                    """{"jsonrpc":"2.0","id":$id,"result":"ok"}"""
                }
                val message = IncomingMessage(
                    data = if (responses.size == 1) {
                        responses.single()
                    } else {
                        responses.joinToString(prefix = "[", postfix = "]")
                    },
                )
                incoming.send(message)
                message.read.await()
            }
            SendMode.Ignore -> Unit
            SendMode.Fail -> throw TestSendException()
        }
    }

    override suspend fun receive(): String {
        val message = incoming.receive()
        message.read.complete(Unit)
        return message.data
    }

    suspend fun awaitSend() {
        sends.receive()
    }

    fun enqueueResponse(id: Long, result: String) {
        incoming.trySend(
            IncomingMessage(
                data = """{"jsonrpc":"2.0","id":$id,"result":"$result"}""",
            ),
        ).getOrThrow()
    }
}

private data class IncomingMessage(
    val data: String,
    val read: CompletableDeferred<Unit> = CompletableDeferred(),
)

private class TestSendException : Exception()

private class LifoDispatcher :
    CoroutineDispatcher(),
    Closeable {
    private val tasks = LinkedBlockingDeque<Runnable>()
    private val running = AtomicBoolean(true)
    private val thread = Thread {
        while (running.get()) {
            try {
                tasks.takeLast().run()
            } catch (exception: InterruptedException) {
                if (running.get()) throw exception
            }
        }
    }.apply {
        name = "jsonrpc-test-lifo-dispatcher"
        isDaemon = true
        start()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks.putLast(block)
    }

    override fun close() {
        running.set(false)
        thread.interrupt()
    }
}
