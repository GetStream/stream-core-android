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

package io.getstream.android.core.internal.recovery

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamTypedKey.Companion.asStreamTypedKey
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.model.connection.recovery.Recovery
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.recovery.StreamConnectionRecoveryEvaluator

internal class StreamConnectionRecoveryEvaluatorImpl(
    private val logger: StreamLogger,
    private val singleFlightProcessor: StreamSingleFlightProcessor,
) : StreamConnectionRecoveryEvaluator {

    companion object {
        private val evaluateKey = "ev".asStreamTypedKey<Recovery?>()
    }

    private var hasConnectedBefore: Boolean = false
    private var lastLifecycleState: StreamLifecycleState = StreamLifecycleState.Unknown
    private var lastNetworkState: StreamNetworkState = StreamNetworkState.Unknown
    private var lastNetworkSnapshot: StreamNetworkInfo.Snapshot? = null
    private var lastConnectionState: StreamConnectionState = StreamConnectionState.Idle

    override suspend fun evaluate(
        connectionState: StreamConnectionState,
        lifecycleState: StreamLifecycleState,
        networkState: StreamNetworkState,
    ): Result<Recovery?> =
        singleFlightProcessor.run(evaluateKey) {
            logger.v {
                "[evaluate] connectionState=$connectionState, lifecycleState=$lifecycleState, networkState=$networkState"
            }

            val isConnected = connectionState is StreamConnectionState.Connected
            val isConnecting = connectionState is StreamConnectionState.Connecting
            val isDisconnected =
                connectionState is StreamConnectionState.Disconnected ||
                    connectionState is StreamConnectionState.Idle
            val previousLifecycle = lastLifecycleState
            val previousNetwork = lastNetworkState
            val networkAvailable = networkState is StreamNetworkState.Available
            val networkBecameAvailable =
                networkAvailable && previousNetwork !is StreamNetworkState.Available
            val lifecycleForeground = lifecycleState == StreamLifecycleState.Foreground
            val returningToForeground =
                previousLifecycle == StreamLifecycleState.Background && lifecycleForeground

            val shouldDisconnect =
                (isConnected || isConnecting) &&
                    (!networkAvailable || lifecycleState == StreamLifecycleState.Background)

            val shouldConnect =
                hasConnectedBefore &&
                    isDisconnected &&
                    lifecycleForeground &&
                    networkAvailable &&
                    (networkWasUnavailable || lifecycleWasBackground)

            val connectSnapshot =
                when (networkState) {
                    is StreamNetworkState.Available -> networkState.snapshot
                    else -> lastNetworkSnapshot
                }

            val result =
                when {
                    shouldConnect -> Recovery.Connect(connectSnapshot)
                    shouldDisconnect -> Recovery.Disconnect(networkState)
                    else -> null
                }

            if (isConnected) {
                hasConnectedBefore = true
            }
            lastConnectionState = connectionState
            lastLifecycleState = lifecycleState
            lastNetworkState = networkState
            lastNetworkSnapshot = connectSnapshot
            result
        }
}
