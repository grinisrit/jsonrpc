package me.y9san9.jsonrpc.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.serialization.json.Json
import me.y9san9.jsonrpc.JsonRpc
import me.y9san9.jsonrpc.JsonRpcConfig
import me.y9san9.jsonrpc.JsonRpcSide

/** Creates a jsonrpc connector using ktor-client. */
public fun JsonRpc.Companion.websocket(
    url: String,
    httpClient: HttpClient = HttpClient(CIO),
    pingIntervalMillis: Long? = DEFAULT_PING_INTERVAL_MILLIS,
    json: Json = Json,
    request: HttpRequestBuilder.() -> Unit = {},
): JsonRpc.Connector {
    val config = JsonRpcConfig(json = json, side = JsonRpcSide.Client)
    return websocket(
        url = url,
        config = config,
        httpClient = httpClient,
        pingIntervalMillis = pingIntervalMillis,
        request = request,
    )
}

/** Creates a jsonrpc connector using ktor-client and an explicit [config]. */
public fun JsonRpc.Companion.websocket(
    url: String,
    config: JsonRpcConfig,
    httpClient: HttpClient = HttpClient(CIO),
    pingIntervalMillis: Long? = DEFAULT_PING_INTERVAL_MILLIS,
    request: HttpRequestBuilder.() -> Unit = {},
): JsonRpc.Connector {
    require(config.side == JsonRpcSide.Client) {
        "Ktor WebSocket connector requires client-side JSON-RPC config"
    }
    val transport =
        KtorJsonRpcTransport.Connector(
            url = url,
            httpClient = httpClient,
            pingIntervalMillis = pingIntervalMillis,
            request = request,
        )
    return JsonRpc.Connector(transport, config)
}
