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

package io.getstream.android.core.internal.client

import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamTypedKey.Companion.randomExecutionKey
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.model.connection.recovery.Recovery
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.recovery.StreamConnectionRecoveryEvaluator
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.utils.flatMap
import io.getstream.android.core.api.utils.onTokenError
import io.getstream.android.core.api.utils.update
import io.getstream.android.core.internal.observers.StreamNetworkAndLifeCycleMonitor
import io.getstream.android.core.internal.observers.StreamNetworkAndLifecycleMonitorListener
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.getstream.android.core.internal.socket.model.ConnectUserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class StreamClientImpl<T>(
    private val userId: StreamUserId,
    private val tokenManager: StreamTokenManager,
    private val singleFlight: StreamSingleFlightProcessor,
    private val serialQueue: StreamSerialProcessingQueue,
    private val connectionIdHolder: StreamConnectionIdHolder,
    private val socketSession: StreamSocketSession<T>,
    private val networkAndLifeCycleMonitor: StreamNetworkAndLifeCycleMonitor,
    private val connectionRecoveryEvaluator: StreamConnectionRecoveryEvaluator,
    private val mutableConnectionState: MutableStateFlow<StreamConnectionState>,
    private val logger: StreamLogger,
    private val subscriptionManager: StreamSubscriptionManager<StreamClientListener>,
    private val scope: CoroutineScope,
) : StreamClient {
    companion object {
        private val connectKey = randomExecutionKey<StreamConnectedUser>()
        private val disconnectKey = randomExecutionKey<Unit>()
    }

    private var socketSessionHandle: StreamSubscription? = null
    private var networkAndLifecycleMonitorHandle: StreamSubscription? = null
    override val connectionState: StateFlow<StreamConnectionState>
        get() = mutableConnectionState.asStateFlow()

    override suspend fun connect(): Result<StreamConnectedUser> =
        singleFlight.run(connectKey) {
            val currentState = connectionState.value
            if (currentState is StreamConnectionState.Connected) {
                logger.w { "[connect] Already connected!" }
                return@run currentState.connectedUser
            }

            val retentionOptions =
                StreamSubscriptionManager.Options(
                    retention = StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                )

            if (socketSessionHandle == null) {
                logger.v { "[connect] Subscribing to socket events]" }
                val clientListener =
                    object : StreamClientListener {
                        override fun onState(state: StreamConnectionState) {
                            logger.v { "[client#onState]: $state" }
                            mutableConnectionState.update(state)
                            subscriptionManager.forEach { it.onState(state) }
                        }

                        override fun onEvent(event: Any) {
                            logger.v { "[client#onEvent]: $event" }
                            subscriptionManager.forEach { it.onEvent(event) }
                        }

                        override fun onError(err: Throwable) {
                            logger.e(err) { "[client#onError]: $err" }
                            subscriptionManager.forEach { it.onError(err) }
                        }
                    }
                socketSessionHandle =
                    socketSession.subscribe(clientListener, retentionOptions).getOrThrow()
            }

            if (networkAndLifecycleMonitorHandle == null) {
                logger.v { "[connect] Setup network and lifecycle monitor callback" }
                val networkAndLifecycleMonitorListener =
                    object : StreamNetworkAndLifecycleMonitorListener {
                        override fun onNetworkAndLifecycleState(
                            networkState: StreamNetworkState,
                            lifecycleState: StreamLifecycleState,
                        ) {
                            scope.launch {
                                val connectionState = mutableConnectionState.value
                                val recovery =
                                    connectionRecoveryEvaluator.evaluate(
                                        connectionState,
                                        lifecycleState,
                                        networkState,
                                    )
                                recoveryEffect(recovery)
                            }
                        }
                    }
                networkAndLifecycleMonitorHandle =
                    networkAndLifeCycleMonitor
                        .subscribe(networkAndLifecycleMonitorListener, retentionOptions)
                        .getOrThrow()
            }
            tokenManager
                .loadIfAbsent()
                .flatMap { token -> connectSocketSession(token) }
                .fold(
                    onSuccess = { connected ->
                        logger.d { "Connected to socket: $connected" }
                        mutableConnectionState.update(connected)
                        connectionIdHolder.setConnectionId(connected.connectionId).map {
                            connected.connectedUser
                        }
                    },
                    onFailure = { error ->
                        logger.e(error) { "Failed to connect to socket: $error" }
                        mutableConnectionState.update(StreamConnectionState.Disconnected(error))
                        Result.failure(error)
                    },
                )
                .flatMap { connectedUser ->
                    networkAndLifeCycleMonitor.start().map { connectedUser }
                }
                .getOrThrow()
        }

    override suspend fun disconnect(): Result<Unit> =
        singleFlight.run(disconnectKey) {
            logger.d { "Disconnecting from socket" }
            mutableConnectionState.update(StreamConnectionState.Disconnected())
            connectionIdHolder.clear()
            socketSession.disconnect()
            socketSessionHandle?.cancel()
            networkAndLifeCycleMonitor.stop()
            networkAndLifecycleMonitorHandle?.cancel()
            networkAndLifecycleMonitorHandle = null
            socketSessionHandle = null
            tokenManager.invalidate()
            serialQueue.stop()
            singleFlight.clear(true)
        }

    override fun subscribe(
        listener: StreamClientListener,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = subscriptionManager.subscribe(listener, options)

    private suspend fun connectSocketSession(
        token: StreamToken
    ): Result<StreamConnectionState.Connected> {
        val data =
            ConnectUserData(
                userId = userId.rawValue,
                token = token.rawValue,
                name = null,
                image = null,
                invisible = false,
                language = null,
                custom = null,
            )
        return socketSession.connect(data).onTokenError { error, code ->
            logger.e(error) { "Token error: $code" }
            tokenManager.invalidate()
            tokenManager.refresh().flatMap { newToken ->
                // One retry once with new token
                socketSession.connect(data.copy(token = newToken.rawValue))
            }
        }
    }

    private suspend fun recoveryEffect(recovery: Result<Recovery?>) {
        recovery.fold(
            onSuccess = { recovery ->

                when (recovery) {
                    is Recovery.Connect<*> -> {
                        logger.v { "[recovery] Connecting: $recovery" }
                        connect().notifyFailure(subscriptionManager)
                    }

                    is Recovery.Disconnect<*> -> {
                        logger.v { "[recovery] Disconnecting: $recovery" }
                        socketSession.disconnect().notifyFailure(subscriptionManager)
                    }

                    is Recovery.Error -> {
                        logger.e(recovery.error) { "[recovery] Error: ${recovery.error.message}" }
                        subscriptionManager.forEach { it.onError(recovery.error) }
                    }

                    null -> {
                        logger.v { "[recovery] No action" }
                    }
                }
                if (recovery != null) {
                    subscriptionManager.forEach { it.onRecovery(recovery) }
                }
            },
            onFailure = { error ->
                logger.e(error) { "[recovery] Error: ${error.message}" }
                subscriptionManager.forEach { it.onError(error) }
            },
        )
    }

    private fun <V> Result<V>.notifyFailure(
        subscriptionManager: StreamSubscriptionManager<StreamClientListener>
    ) = onFailure { error -> subscriptionManager.forEach { it.onError(error) } }
}
