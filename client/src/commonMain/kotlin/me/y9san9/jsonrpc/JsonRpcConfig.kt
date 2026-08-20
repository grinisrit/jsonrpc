package me.y9san9.jsonrpc

import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [JsonRpcConfig] has a number of settings which mostly have sensible defaults,
 * but are up to you to decide their actual values.
 *
 * [requestTimeout] bounds transport send and receipt of every response for one
 * execute call. It must be finite and positive.
 */
// TODO: move it into a bag of tags
public class JsonRpcConfig(
    public val side: JsonRpcSide,
    public val json: Json = Json,
    public val batchRequests: Boolean = true,
    public val requestTimeout: Duration = 30.seconds,
) {
    init {
        require(requestTimeout.isFinite() && requestTimeout.isPositive()) {
            "Request timeout must be finite and positive"
        }
    }
}
