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

package io.getstream.android.core.api.recovery

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.model.connection.recovery.Recovery
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.internal.recovery.StreamConnectionRecoveryEvaluatorImpl

/**
 * Evaluates the current connection state and network availability to determine whether a reconnect
 * or disconnect action should be taken.
 *
 * Implementations are responsible for defining the heuristics that determine when a reconnect or
 * disconnect should be triggered based on the current connection state and network availability.
 */
@StreamInternalApi
public interface StreamConnectionRecoveryEvaluator {
    /**
     * Evaluates the current connection state and network availability to determine whether a
     * reconnect or disconnect action should be taken.
     *
     * @param lifecycleState The current lifecycle state.
     * @param networkState The current network state.
     * @return A [io.getstream.android.core.api.model.connection.recovery.Recovery] indicating the
     *   action to take, or `null` if no action is needed.
     */
    public suspend fun evaluate(
        connectionState: StreamConnectionState,
        lifecycleState: StreamLifecycleState,
        networkState: StreamNetworkState,
    ): Result<Recovery?>
}

/**
 * Creates a new [StreamConnectionRecoveryEvaluator] instance.
 *
 * @param logger The logger to use for logging.
 * @param singleFlightProcessor The single-flight processor to use for managing concurrent requests.
 * @return A new [StreamConnectionRecoveryEvaluator] instance.
 */
@StreamInternalApi
public fun StreamConnectionRecoveryEvaluator(
    logger: StreamLogger,
    singleFlightProcessor: StreamSingleFlightProcessor,
): StreamConnectionRecoveryEvaluator =
    StreamConnectionRecoveryEvaluatorImpl(logger, singleFlightProcessor)
