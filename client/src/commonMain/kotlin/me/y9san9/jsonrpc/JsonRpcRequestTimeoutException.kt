package me.y9san9.jsonrpc

import kotlin.time.Duration

/**
 * Indicates that sending a JSON-RPC request and receiving all of its responses
 * did not complete within [timeout]. A timeout permanently fails the connection
 * because a late response cannot be safely correlated with a later request.
 */
public class JsonRpcRequestTimeoutException(public val timeout: Duration) :
    JsonRpcTransportException(
        "JSON-RPC request did not complete within $timeout",
    )
