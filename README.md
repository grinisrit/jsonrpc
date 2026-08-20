# Json RPC

Idiomatic Json RPC Implementation in Kotlin and kotlinx.serialization.
It has ktor implementation, but more engines may be added if needed. It is
really simple to implement, but annoying to do every time for every project,
so I made this into library.

## Example

```kotlin
import me.y9san9.jsonrpc.JsonRpc
import me.y9san9.jsonrpc.ktor.ktor

suspend fun main() {
    val rpc = JsonRpc.websocket("wss://example.org")

    rpc.connect {
        val response = execute(...)

        incoming.onRequest { request ->
            println("Incoming Request: $request")
            respond(response)
        }

        incoming.onRequest(JsonRpcMethodName("test")) { /* ... */ }
    }
}
```

## Install

Library is available on Maven Central and may be installed in the following
ways:

```kotlin
dependencies {
    implementation("io.github.mzd00.jsonrpc:ktor-client:$version")
}
```

```toml
[versions]
jsonrpc = "$version"

[libraries]
jsonrpc = { module = "io.github.mzd00.jsonrpc:ktor-client", version.ref = "jsonrpc" }
```

`$version` should be the same as the last version in releases section.

## Request timeout

Requests have a finite 30-second deadline covering transport send and response
receipt. A timeout throws `JsonRpcRequestTimeoutException`, permanently fails
the connection, and is classified by the Ktor adapter as a transport failure
even if the first timeout is caught inside the connection block. Use an
explicit config to choose another positive finite duration:

```kotlin
val config = JsonRpcConfig(
    side = JsonRpcSide.Client,
    requestTimeout = 2.minutes,
)
val rpc = JsonRpc.websocket("wss://example.org", config)
```

## Compatibility policy

This fork is an internal library. Binary compatibility is not guaranteed
between releases; consumers must be recompiled when upgrading.

## Publishing

Maintainer instructions for signing and publishing releases are in
[docs/Publishing.md](docs/Publishing.md).

## Server-Client

TODO: at the moment only client behaviour is supported. Since I myself
don't have any jsonrpc server to build with this. What I want to add
for server is different errors that Server will tell to Client if Client
did mistaked.

And, of course, an intergration with ktor-server is still missing, but it's
not hard to master. I also would want to introduce dsl for ktor-server that
looks like this:

```kotlin
embeddedServer {
    jsonrpc { this: JsonRpcServer
        // launch a coroutine to respond to methods
        method("test") { call: JsonRpcCall ->
            call.respond(/* ... */)
            call.notification(/* ... */)
        }
        // this.jsonrpc works fine
    }
}
```
