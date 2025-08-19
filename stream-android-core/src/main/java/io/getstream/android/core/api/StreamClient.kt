/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-android-base/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.getstream.android.core.api

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.model.config.StreamClientConfig
import io.getstream.android.core.api.model.config.StreamEndpointConfig
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.state.StreamClientState
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.internal.client.createClient
import kotlinx.coroutines.CoroutineScope

/**
 * Entry point for establishing and managing a connection to Stream services.
 *
 * ### Semantics
 * - Exposes connection-related state via [state] (hot, read-only).
 * - Implementations may perform network I/O and authentication during [connect].
 *
 * ### Threading
 * - All APIs are safe to call from any thread.
 * - [connect] and [disconnect] are `suspend` and must not block the caller thread.
 *
 * ### Lifecycle
 * - Create → [connect] → use → [disconnect].
 * - After [disconnect], resources are released; [state] continues to reflect the latest status.
 *
 * ### Errors & cancellation
 * - Methods return `Result<…>`: `success` on completion, `failure` on error.
 * - If the *caller* is cancelled while awaiting, a [kotlinx.coroutines.CancellationException] is
 *   thrown.
 *
 * ### Usage
 *
 * ```kotlin
 * // Use
 * val result = client.connect()
 * if (result.isSuccess) {
 *     // Observe connection state
 *     // Connected...
 * } else {
 *     val cause = result.exceptionOrNull()
 *     // handle error
 * }
 *
 * // Observe exposed client state
 * client.state.collect {
 *   // Get state updates...
 * }
 *
 * // …
 * client.disconnect()
 * ```
 */
@StreamCoreApi
interface StreamClient<S : StreamClientState> {

    /**
     * Read-only, hot state holder for this client.
     *
     * **Semantics**
     * - Emits connection status changes (e.g., connecting/connected/disconnected).
     * - Hot & conflated: new collectors receive the latest value immediately.
     */
    val state: S

    /**
     * Establishes a connection for the current user.
     *
     * **Result contract**
     * - `Result.success(StreamConnectedUser)` on success.
     * - `Result.failure(cause)` if the connection fails.
     *
     * **Cancellation**
     * - Throws [kotlinx.coroutines.CancellationException] if the awaiting coroutine is cancelled.
     */
    suspend fun connect(): Result<StreamConnectedUser>

    /**
     * Terminates the active connection and releases related resources.
     *
     * **Result contract**
     * - `Result.success(Unit)` on a clean shutdown.
     * - `Result.failure(cause)` if teardown fails.
     *
     * **Cancellation**
     * - Throws [kotlinx.coroutines.CancellationException] if the awaiting coroutine is cancelled.
     */
    suspend fun disconnect(): Result<Unit>

    /**
     * Subscribes to client events and state
     *
     * @param listener The listener to subscribe.
     */
    fun subscribe(listener: StreamClientListener): Result<StreamSubscription>
}

@StreamCoreApi
/**
 * ### Overview
 *
 * Creates a [StreamClient] with the given [apiKey], [userId], [tokenProvider] and [scope]. The
 * client is created in a disconnected state. You must call `connect()` to establish a connection.
 * The client is automatically disconnected when the [scope] is cancelled or the app is put into
 * background or the network is lost. When the app is back in foreground a re-connect attempt is
 * going to be made depending on previous state.
 *
 * **Important**: The client instance **must be kept alive for the duration of the connection**. Do
 * not create a new client for every operation.
 *
 * **Token provider:**
 * - The [tokenProvider] is used to fetch tokens on demand. The first token is cached internally.
 *   When the first request needs to be made, the token is fetched from the provider. If you already
 *   have a token, you can cache it in your provider and return it as a valid token in `loadToken`.
 *   See [StreamTokenProvider] for more details.
 *
 * **Scope:**
 * - The [scope] is used to launch the client's internal coroutines. It is recommended to use a
 *   `CoroutineScope(SupervisorJob() + Dispatchers.Default)` for this purpose.
 *
 * ### Security
 * - The [tokenProvider] is used to fetch tokens on demand. The first token is cached internally.
 *   When the token expires, the provider is called again to fetch a new one.
 * - The expiration is determined by a `401` response from the server at which point the request is
 *   retried with the new token.
 *
 * ### Performance
 * - The client uses a single-flight pattern to deduplicate concurrent requests.
 * - The client uses a serial processing queue to ensure that requests are executed in order.
 * - The client uses a debounce message processor to coalesce high-frequency events.
 *
 * ### Usage
 *
 * ```kotlin
 * val client = StreamClient(
 *     apiKey = "my-api-key",
 *     userId = "my-user-id",
 *     tokenProvider = MyTokenProvider(),
 *     scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
 * )
 * ```
 *
 * @param apiKey The API key.
 * @param userId The user ID.
 * @param tokenProvider The token provider.
 * @param scope The coroutine scope.
 */
fun StreamClient(
    apiKey: StreamApiKey,
    userId: StreamUserId,
    endpoint: StreamEndpointConfig,
    scope: CoroutineScope,
    tokenProvider: StreamTokenProvider,
    configure: StreamClientConfig.() -> Unit = {},
): StreamClient<StreamClientState> {
    val cfg = StreamClientConfig(apiKey, userId, endpoint, tokenProvider).apply(configure)
    return createClient(
        apiKey = cfg.apiKey,
        userId = userId,
        endpoint = endpoint,
        tokenProvider = cfg.tokenProvider,
        tokenManager = cfg.tokenManager,
        singleFlightProcessor = cfg.processing.singleFlight,
        serialProcessingQueue = cfg.processing.serialQueue,
        retryProcessor = cfg.processing.retry,
        httpInterceptors = cfg.http.interceptors,
        connectionIdHolder = cfg.http.connectionIdHolder,
        socketFactory = cfg.socket.socketFactory,
        healthMonitor = cfg.socket.healthMonitor,
        socketSubscriptionManager = cfg.socket.socketSubscriptions,
        socketEventDebounceProcessor = cfg.socket.eventDebounce,
        json = cfg.serialization.json,
        clientSubscriptionManager = cfg.processing.clientSubscriptions,
        logProvider = cfg.logging.loggerProvider,
        scope = scope,
    )
}
