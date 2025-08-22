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
import io.getstream.android.core.api.serialization.StreamClientEventSerialization
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.socket.StreamWebSocket
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.model.StreamConnectUserDetailsRequest
import io.getstream.android.core.internal.model.authentication.StreamWSAuthMessageRequest
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectionErrorEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import io.getstream.android.core.internal.socket.model.ConnectUserData
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Response

@OptIn(ExperimentalAtomicApi::class)
internal class StreamSocketSession(
    private val logger: StreamLogger,
    private var config: StreamSocketConfig,
    private val internalSocket: StreamWebSocket<StreamWebSocketListener>,
    private val jsonSerialization: StreamJsonSerialization,
    private val eventParser: StreamClientEventSerialization,
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

    /** Disconnect the socket and synchronously notify listeners. */
    fun disconnect(error: Throwable? = null): Result<Unit> {
        logger.d { "[disconnect] Disconnecting socket, error: $error" }
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
            internalSocket.close().onFailure { throwable ->
                logger.e { "[disconnect] Failed to close socket. ${throwable.message}" }
            }
        cleanup()
        return closeRes
    }

    /** Connects the user to the socket. */
    suspend fun connect(data: ConnectUserData): Result<StreamConnectionState.Connected> =
        suspendCancellableCoroutine { continuation ->
            var handshakeSubscription: StreamSubscription? = null

            // Ensure we clean up if the caller cancels the connect coroutine
            continuation.invokeOnCancellation { cause ->
                logger.d { "[connect] Cancelled: ${cause?.message}" }
                socketSubscription?.cancel()
                handshakeSubscription?.cancel()
                internalSocket.close()
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
                        .serialize(healthCheckEvent)
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
                            if (event !is StreamHealthCheckEvent) {
                                subscriptionManager.forEach { it.onEvent(event) }
                            }
                            if (event is StreamClientConnectionErrorEvent) {
                                notifyState(
                                    StreamConnectionState.Disconnected(
                                        StreamEndpointException("Connection error", event.error)
                                    )
                                )
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
            }

            val failure: (Throwable) -> Unit = { throwable ->
                handshakeSubscription?.cancel()
                socketSubscription?.cancel()
                completeFailure(throwable)
            }

            val apiFailure: (StreamEndpointErrorData) -> Unit = { apiError ->
                handshakeSubscription?.cancel()
                val error = StreamEndpointException("Connection error", apiError)
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
                                .serialize(authRequest)
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
