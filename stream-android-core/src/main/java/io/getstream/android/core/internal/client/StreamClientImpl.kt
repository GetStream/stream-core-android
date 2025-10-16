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

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamTypedKey.Companion.randomExecutionKey
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.utils.flatMap
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.getstream.android.core.internal.socket.model.ConnectUserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class StreamClientImpl<T>(
    private val userId: StreamUserId,
    private val tokenManager: StreamTokenManager,
    private val singleFlight: StreamSingleFlightProcessor,
    private val serialQueue: StreamSerialProcessingQueue,
    private val connectionIdHolder: StreamConnectionIdHolder,
    private val socketSession: StreamSocketSession<T>,
    private val mutableConnectionState: MutableStateFlow<StreamConnectionState>,
    private val logger: StreamLogger,
    private val subscriptionManager: StreamSubscriptionManager<StreamClientListener>,
    private val networkMonitor: StreamNetworkMonitor,
    private val scope: CoroutineScope,
) : StreamClient {
    companion object {
        private val connectKey = randomExecutionKey<StreamConnectedUser>()
        private val disconnectKey = randomExecutionKey<Unit>()
    }

    private var handle: StreamSubscription? = null
    private var networkMonitorHandle: StreamSubscription? = null
    override val connectionState: StateFlow<StreamConnectionState>
        get() = mutableConnectionState.asStateFlow()

    private var internalNetworkInfo: MutableStateFlow<StreamNetworkInfo.Snapshot?> =
        MutableStateFlow(null)

    @StreamInternalApi
    override val networkInfo: StateFlow<StreamNetworkInfo.Snapshot?>
        get() = internalNetworkInfo.asStateFlow()

    override fun subscribe(listener: StreamClientListener): Result<StreamSubscription> =
        subscriptionManager.subscribe(listener)

    override suspend fun connect(): Result<StreamConnectedUser> =
        singleFlight.run(connectKey) {
            if (networkMonitorHandle == null) {
                logger.v { "[connect] Starting network monitor" }
                networkMonitorHandle =
                    networkMonitor
                        .subscribe(
                            object : StreamNetworkMonitorListener {
                                override suspend fun onNetworkConnected(
                                    snapshot: StreamNetworkInfo.Snapshot?
                                ) {
                                    logger.v { "[connect] Network connected: $snapshot" }
                                    internalNetworkInfo.update { snapshot }
                                }

                                override suspend fun onNetworkLost(permanent: Boolean) {
                                    logger.v { "[connect] Network lost" }
                                    internalNetworkInfo.update { null }
                                }

                                override suspend fun onNetworkPropertiesChanged(
                                    snapshot: StreamNetworkInfo.Snapshot
                                ) {
                                    logger.v { "[connect] Network changed: $snapshot" }
                                    internalNetworkInfo.update { snapshot }
                                }
                            },
                            StreamSubscriptionManager.Options(
                                retention =
                                    StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                            ),
                        )
                        .getOrThrow()
            }

            networkMonitor.start()

            val currentState = connectionState.value
            if (currentState is StreamConnectionState.Connected) {
                logger.w { "[connect] Already connected!" }
                return@run currentState.connectedUser
            }
            if (handle == null) {
                logger.v { "[connect] Subscribing to socket events]" }
                handle =
                    socketSession
                        .subscribe(
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
                            },
                            StreamSubscriptionManager.Options(
                                retention =
                                    StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                            ),
                        )
                        .getOrThrow()
            }
            tokenManager
                .loadIfAbsent()
                .flatMap { token ->
                    socketSession.connect(
                        ConnectUserData(
                            userId = userId.rawValue,
                            token = token.rawValue,
                            name = null,
                            image = null,
                            invisible = false,
                            language = null,
                            custom = null,
                        )
                    )
                }
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
                .getOrThrow()
        }

    override suspend fun disconnect(): Result<Unit> =
        singleFlight.run(disconnectKey) {
            logger.d { "Disconnecting from socket" }
            mutableConnectionState.update(StreamConnectionState.Disconnected())
            connectionIdHolder.clear()
            socketSession.disconnect()
            handle?.cancel()
            networkMonitorHandle?.cancel()
            networkMonitorHandle = null
            handle = null
            tokenManager.invalidate()
            serialQueue.stop()
            singleFlight.clear(true)
        }

    private fun MutableStateFlow<StreamConnectionState>.update(state: StreamConnectionState) {
        this.update { state }
    }
}
