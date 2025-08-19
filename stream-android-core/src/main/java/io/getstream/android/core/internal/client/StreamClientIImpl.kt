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
package io.getstream.android.core.internal.client

import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamTypedKey.Companion.randomExecutionKey
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.state.StreamClientState
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.utils.flatMap
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.getstream.android.core.internal.socket.model.ConnectUserData
import io.getstream.android.core.internal.state.MutableStreamClientState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class StreamClientIImpl(
    private val userId: StreamUserId,
    private val tokenManager: StreamTokenManager,
    private val singleFlight: StreamSingleFlightProcessor,
    private val serialQueue: StreamSerialProcessingQueue,
    private val connectionIdHolder: StreamConnectionIdHolder,
    private val socketSession: StreamSocketSession,
    private val mutableClientState: MutableStreamClientState,
    private val logger: StreamLogger,
    private val subscriptionManager: StreamSubscriptionManager<StreamClientListener>,
) : StreamClient<StreamClientState> {
    companion object {
        private val connectKey = randomExecutionKey<StreamConnectedUser>()
        private val disconnectKey = randomExecutionKey<Unit>()
    }

    private var handle: StreamSubscription? = null
    private val internalEvents =
        MutableSharedFlow<StreamClientWsEvent>(
            replay = 1,
            extraBufferCapacity = 50,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val state: StreamClientState
        get() = mutableClientState

    override fun subscribe(listener: StreamClientListener): Result<StreamSubscription> =
        subscriptionManager.subscribe(listener)

    override suspend fun connect(): Result<StreamConnectedUser> =
        singleFlight.run(connectKey) {
            val currentState = state.connectionState.value
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
                                    mutableClientState.update(state)
                                    subscriptionManager.forEach { it.onState(state) }
                                }

                                override fun onEvent(event: StreamClientWsEvent) {
                                    logger.v { "[client#onEvent]: $event" }
                                    subscriptionManager.forEach { it.onEvent(event) }
                                    event.let(internalEvents::tryEmit)
                                }
                            },
                            StreamSubscriptionManager.SubscribeOptions(
                                retention =
                                    StreamSubscriptionManager.SubscribeOptions.SubscriptionRetention
                                        .KEEP_UNTIL_CANCELLED
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
                        mutableClientState.update(connected)
                        connectionIdHolder.setConnectionId(connected.connectionId).map {
                            connected.connectedUser
                        }
                    },
                    onFailure = { error ->
                        logger.e(error) { "Failed to connect to socket: $error" }
                        mutableClientState.update(StreamConnectionState.Disconnected.Error(error))
                        Result.failure(error)
                    },
                )
                .getOrThrow()
        }

    override suspend fun disconnect(): Result<Unit> =
        singleFlight.run(disconnectKey) {
            logger.d { "Disconnecting from socket" }
            mutableClientState.update(StreamConnectionState.Disconnected.Manual)
            connectionIdHolder.clear()
            socketSession.disconnect()
            handle?.cancel()
            handle = null
            tokenManager.invalidate()
            serialQueue.stop()
            singleFlight.clear(true)
        }
}
