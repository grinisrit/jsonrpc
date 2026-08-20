package me.y9san9.jsonrpc

/**
 * This is the exception that is thrown when transport disconnects due to
 * external circumstances or when JSON-RPC can no longer safely continue using
 * it, for example after a request timeout. It may be useful if you want to
 * setup client reconnection.
 *
 * Custom transport adapters should throw this exception for transport
 * failures so connectors can classify them consistently.
 */
public open class JsonRpcTransportException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
