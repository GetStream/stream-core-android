/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-core-android/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.getstream.android.core.internal.socket

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.exceptions.StreamClientException
import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.model.exceptions.StreamEndpointException
import io.getstream.android.core.api.processing.StreamBatcher
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.socket.StreamWebSocket
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.utils.toErrorData
import io.getstream.android.core.internal.model.StreamConnectUserDetailsRequest
import io.getstream.android.core.internal.model.authentication.StreamWSAuthMessageRequest
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectionErrorEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import io.getstream.android.core.internal.serialization.StreamCompositeEventSerializationImpl
import io.getstream.android.core.internal.serialization.StreamCompositeSerializationEvent
import io.getstream.android.core.internal.socket.model.ConnectUserData
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Response

@OptIn(ExperimentalAtomicApi::class)
internal class StreamSocketSession<T>(
    private val logger: StreamLogger,
    private var config: StreamSocketConfig,
    private val internalSocket: StreamWebSocket<StreamWebSocketListener>,
    private val jsonSerialization: StreamJsonSerialization,
    private val eventParser: StreamCompositeEventSerializationImpl<T>,
    private val healthMonitor: StreamHealthMonitor,
    private val batcher: StreamBatcher<String>,
    private val subscriptionManager: StreamSubscriptionManager<StreamClientListener>,
    private val products: List<String>,
) : StreamSubscriptionManager<StreamClientListener> by subscriptionManager {
    private var socketSubscription: StreamSubscription? = null
    private var streamClientConnectedEvent: StreamClientConnectedEvent? = null

    /** Prevent duplicate notifications/cleanup cascades when we closed the socket ourselves. */
    private val closingByUs = AtomicBoolean(false)

    /** Idempotent cleanup guard. */
    private val cleaned = AtomicBoolean(false)

    private fun notifyState(state: StreamConnectionState): Result<Unit> {
        logger.d { "[notifyState] Notifying state: $state" }
        return subscriptionManager
            .forEach { it.onState(state) }
            .onFailure { e ->
                logger.e(e) { "[notifyState] Failed to notify state=$state. ${e.message}" }
            }
    }

    private val eventListener =
        object : StreamWebSocketListener {
            override fun onMessage(text: String) {
                logger.v { "[onMessage] Socket message: $text" }
                healthMonitor.acknowledgeHeartbeat()
                val accepted = batcher.offer(text)
                if (!accepted) {
                    val error =
                        IllegalStateException(
                            "Failed to offer message to debounce processor. Message dropped: $text"
                        )
                    logger.e(error) { "[onMessage] Message dropped: $text" }
                    disconnect(error)
                } else {
                    logger.v { "[onMessage] Message accepted: $text" }
                }
            }

            override fun onFailure(t: Throwable, response: Response?) {
                logger.e(t) { "[onFailure] Socket failure. ${t.message}" }
                notifyState(StreamConnectionState.Disconnected(t))
            }

            override fun onClosed(code: Int, reason: String) {
                logger.e { "[onClosed] Socket closed. Code: $code, Reason: $reason" }
                if (!closingByUs.get()) {
                    if (code == 1000) {
                        notifyState(StreamConnectionState.Disconnected())
                    } else {
                        notifyState(
                            StreamConnectionState.Disconnected(
                                IOException("Socket closed. Code: $code, Reason: $reason")
                            )
                        )
                    }
                }
                cleanup()
            }
        }

    /**
     * Terminates the active socket session and performs a best-effort shutdown of all components.
     *
     * The method emits a `StreamConnectionState.Disconnected` state (embedding [error] when
     * provided), cancels the active socket subscription, closes the underlying [StreamWebSocket],
     * and stops the health monitor and batch processor. Subsequent invocations are idempotent:
     * listeners are only notified on the first call while the socket close is still attempted every
     * time.
     *
     * @param error Optional cause that is propagated to subscribers via
     *   `StreamConnectionState.Disconnected(error)`.
     * @param code Close code forwarded to [StreamWebSocket.close]. Defaults to the standard manual
     *   shutdown code used by the SDK.
     * @param reason Reason string forwarded to [StreamWebSocket.close]. Defaults to the standard
     *   human-readable explanation used by the SDK.
     * @return The result returned by [StreamWebSocket.close] after cleanup completes.
     */
    fun disconnect(
        error: Throwable? = null,
        code: Int = SocketConstants.CLOSE_SOCKET_CODE,
        reason: String = SocketConstants.CLOSE_SOCKET_REASON,
    ): Result<Unit> {
        logger.d {
            "[disconnect] Disconnecting socket, code: $code, reason: $reason, error: $error"
        }
        if (!closingByUs.getAndSet(true)) {
            val newState =
                if (error == null) {
                    StreamConnectionState.Disconnected()
                } else {
                    StreamConnectionState.Disconnected(error)
                }
            notifyState(newState)
        }
        // Cancel subscriptions before closing to avoid duplicate callbacks.
        socketSubscription?.cancel()
        val closeRes =
            internalSocket
                .close(code, reason)
                .onSuccess { logger.d { "[disconnect] Socket closed" } }
                .onFailure { throwable ->
                    logger.e { "[disconnect] Failed to close socket. ${throwable.message}" }
                }
        cleanup()
        return closeRes
    }

    /**
     * Opens the socket and completes the Stream authentication handshake for the provided user.
     *
     * The call subscribes to lifecycle events, opens the underlying [StreamWebSocket], performs the
     * auth handshake, starts the health monitor once connected, and emits all intermediate
     * [StreamConnectionState] updates to registered [StreamClientListener] instances. When the
     * coroutine is cancelled or any step fails, all temporary subscriptions are cleaned up and the
     * failure is propagated via the returned [Result].
     *
     * @param data Payload describing the user being connected and the products being authorised.
     * @return `Result.success` with the established [StreamConnectionState.Connected] when the
     *   handshake finishes, or `Result.failure` containing the encountered error.
     */
    suspend fun connect(data: ConnectUserData): Result<StreamConnectionState.Connected> =
        suspendCancellableCoroutine { continuation ->
            var handshakeSubscription: StreamSubscription? = null

            // Ensure we clean up if the caller cancels the connect coroutine
            continuation.invokeOnCancellation { cause ->
                logger.d { "[connect] Cancelled: ${cause?.message}" }
                socketSubscription?.cancel()
                handshakeSubscription?.cancel()
                internalSocket.close(
                    SocketConstants.CLOSE_SOCKET_CODE,
                    SocketConstants.CLOSE_SOCKET_REASON,
                )
                cleanup()
            }

            val completeFailure: (t: Throwable) -> Unit = { t ->
                logger.e(t) { "[connect] Failed to connect. ${t.message}" }
                notifyState(StreamConnectionState.Disconnected(t))
                if (continuation.isActive) {
                    continuation.resume(Result.failure(t))
                }
            }

            logger.d { "[connect] Connecting to socket: $data" }
            cleaned.set(false)
            closingByUs.set(false)

            // Health hooks
            healthMonitor.onHeartbeat {
                val healthCheckEvent = streamClientConnectedEvent?.copy()
                if (healthCheckEvent != null) {
                    logger.v {
                        "[onInterval] Socket health check sending: $streamClientConnectedEvent"
                    }
                    eventParser
                        .serialize(StreamCompositeSerializationEvent.internal(healthCheckEvent))
                        .onSuccess { text -> internalSocket.send(text) }
                        .onFailure {
                            logger.e(it) {
                                "[onInterval] Socket health check failed. ${it.message}"
                            }
                        }
                } else {
                    logger.e { "[onInterval] Socket health check not run. Connected event is null" }
                }
            }

            healthMonitor.onUnhealthy {
                disconnect(StreamClientException("Socket did not receive any events."))
            }
            // Batch processing of incoming messages
            batcher.onBatch { batch, delay, count ->
                logger.v {
                    "[onBatch] Socket batch (delay: $delay ms, buffer size: $count): $batch"
                }

                batch.forEach { message ->
                    eventParser
                        .deserialize(message)
                        .onSuccess { event ->
                            logger.v { "[onBatch] Deserialized event: $event" }
                            val coreEvent = event.core
                            val productEvent = event.product

                            if (
                                coreEvent != null && coreEvent is StreamClientConnectionErrorEvent
                            ) {
                                notifyState(
                                    StreamConnectionState.Disconnected(
                                        StreamEndpointException("Connection error", coreEvent.error)
                                    )
                                )
                            }
                            subscriptionManager.forEach { listener ->
                                coreEvent
                                    ?.takeUnless { it is StreamHealthCheckEvent }
                                    ?.let { listener.onEvent(it) }

                                productEvent?.let { listener.onEvent(it) }
                            }
                        }
                        .onFailure {
                            logger.e(it) {
                                "[onBatch] Failed to deserialize socket message. ${it.message}"
                            }
                            // Attempt to parse as API error
                            jsonSerialization
                                .fromJson(message, StreamEndpointErrorData::class.java)
                                .onSuccess { apiError ->
                                    logger.e { "[onBatch] Parsed error event: $apiError" }
                                    notifyState(
                                        StreamConnectionState.Disconnected(
                                            StreamEndpointException("Connection error", apiError)
                                        )
                                    )
                                }
                                .onFailure { logger.i { "[onBatch] Failed to parse $message" } }
                        }
                }
            }

            // Success/Failure continuations
            val success: (StreamConnectedUser, String) -> Unit = { user, connectionId ->
                handshakeSubscription?.cancel()
                if (continuation.isActive) {
                    healthMonitor.start()
                    val connected = StreamConnectionState.Connected(user, connectionId)
                    notifyState(connected) // emit state before completing
                    continuation.resume(Result.success(connected))
                }
                logger.v { "[success] Connection successful" }
            }

            val failure: (Throwable) -> Unit = { throwable ->
                logger.e(throwable) { "[failure] Connection failed. ${throwable.message}" }
                handshakeSubscription?.cancel()
                socketSubscription?.cancel()
                completeFailure(throwable)
            }

            val apiFailure: (StreamEndpointErrorData) -> Unit = { apiError ->
                val error = StreamEndpointException("Connection error", apiError)
                logger.e(error) { "[apiFailure] Connection error: $apiError" }
                handshakeSubscription?.cancel()
                completeFailure(error)
            }

            // Notify listeners: Opening (block until dispatched)
            notifyState(StreamConnectionState.Connecting.Opening(data.userId))

            // Subscribe for socket events
            val socketSubRes = internalSocket.subscribe(eventListener)
            if (socketSubRes.isFailure) {
                failure(socketSubRes.exceptionOrNull()!!)
                return@suspendCancellableCoroutine
            }
            socketSubscription = socketSubRes.getOrNull()

            // Temporary listener to handle the initial open/auth handshake
            val connectListener =
                object : StreamWebSocketListener {
                    override fun onOpen(response: Response) {
                        if (response.code == 101) {
                            logger.d { "[onOpen] Socket opened" }
                            // Move to authenticating and send auth
                            notifyState(
                                StreamConnectionState.Connecting.Authenticating(data.userId)
                            )
                            val authRequest =
                                StreamWSAuthMessageRequest(
                                    products = products,
                                    token = data.token,
                                    userDetails =
                                        StreamConnectUserDetailsRequest(
                                            id = data.userId,
                                            image = data.image,
                                            invisible = data.invisible,
                                            language = data.language,
                                            name = data.name,
                                            custom = data.custom,
                                        ),
                                )
                            eventParser
                                .serialize(StreamCompositeSerializationEvent.internal(authRequest))
                                .mapCatching {
                                    logger.v { "[onOpen] Sending auth request: $it" }
                                    internalSocket.send(it)
                                }
                                .onFailure {
                                    logger.e(it) {
                                        "[onOpen] Failed to serialize auth request. ${it.message}"
                                    }
                                    failure(it)
                                }
                        } else {
                            val err =
                                IllegalStateException(
                                    "Failed to open socket. Code: ${response.code}"
                                )
                            logger.e(err) {
                                "[onOpen] Socket failed to open. Code: ${response.code}"
                            }
                            failure(err)
                        }
                    }

                    override fun onMessage(text: String) {
                        logger.d { "[onMessage] Socket message (string): $text" }
                        eventParser
                            .deserialize(text)
                            .map { it.core }
                            .map { authResponse ->
                                when (authResponse) {
                                    is StreamClientConnectedEvent -> {
                                        logger.v {
                                            "[onMessage] Handling connected event: $authResponse"
                                        }
                                        val me = authResponse.me
                                        val connectedUser =
                                            StreamConnectedUser(
                                                me.createdAt,
                                                me.id,
                                                me.language,
                                                me.role,
                                                me.updatedAt,
                                                me.blockedUserIds,
                                                me.teams,
                                                me.custom,
                                                me.deactivatedAt,
                                                me.deletedAt,
                                                me.image,
                                                me.lastActive,
                                                me.name,
                                            )
                                        streamClientConnectedEvent = authResponse
                                        success(connectedUser, authResponse.connectionId)
                                    }

                                    is StreamClientConnectionErrorEvent -> {
                                        logger.e {
                                            "[onMessage] Socket connection recoverable error: $authResponse"
                                        }
                                        apiFailure(authResponse.error)
                                    }
                                }
                            }
                            .recover {
                                logger.e(it) {
                                    "[onMessage] Failed to deserialize socket message. ${it.message}"
                                }
                                failure(it)
                            }
                    }

                    override fun onFailure(t: Throwable, response: Response?) {
                        val apiError = response?.toErrorData(jsonSerialization)?.getOrNull()
                        val exception =
                            StreamEndpointException(
                                message = "Socket failed during connection",
                                cause = t,
                                apiError = apiError,
                            )
                        logger.e(exception) {
                            "[onFailure] Socket failure during connection: ${exception.message}"
                        }
                        failure(exception)
                    }

                    override fun onClosed(code: Int, reason: String) {
                        val exception =
                            IOException(
                                "Socket closed during connection. Code: $code, Reason: $reason"
                            )
                        logger.e(exception) {
                            "[onClosed] Socket closed during connection. Code: $code, Reason: $reason"
                        }
                        failure(exception)
                    }
                }

            val hsRes =
                internalSocket.subscribe(
                    connectListener,
                    StreamSubscriptionManager.Options(
                        retention = StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                    ),
                )
            if (hsRes.isFailure) {
                failure(hsRes.exceptionOrNull()!!)
                return@suspendCancellableCoroutine
            }
            handshakeSubscription = hsRes.getOrNull()

            // Open socket
            val openRes = internalSocket.open(config)
            if (openRes.isFailure) {
                failure(openRes.exceptionOrNull()!!)
                return@suspendCancellableCoroutine
            }
        }

    private fun cleanup() {
        if (!cleaned.compareAndSet(false, true)) {
            return
        }
        logger.d { "[cleanup] Cleaning up socket" }
        healthMonitor.stop()
        batcher.stop()
        socketSubscription?.cancel()
        socketSubscription = null
        streamClientConnectedEvent = null
    }
}
