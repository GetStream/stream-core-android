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

package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.components.StreamAndroidComponentsProvider
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.processing.StreamBatcher
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.recovery.StreamConnectionRecoveryEvaluator
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager

/**
 * Optional overrides for internal components used by
 * [StreamClient][io.getstream.android.core.api.StreamClient].
 *
 * All fields default to `null`, meaning the factory creates default instances. Provide a non-null
 * value to replace a specific component — useful for sharing instances across product layers or
 * injecting custom implementations.
 *
 * ### Usage
 *
 * ```kotlin
 * // Share a single-flight processor between core and product API layer
 * val singleFlight = StreamSingleFlightProcessor(scope)
 *
 * val client = StreamClient(
 *     ...,
 *     components = StreamComponentProvider(
 *         singleFlight = singleFlight,
 *     ),
 * )
 *
 * val productApi = MyProductApi(singleFlight) // same instance
 * ```
 *
 * @param logProvider Logger provider used to create tagged loggers for internal components.
 *   Defaults to Android logcat.
 * @param singleFlight Request deduplication processor.
 * @param serialQueue Serial processing queue for ordered execution.
 * @param tokenManager Token lifecycle manager.
 * @param connectionIdHolder Connection ID storage.
 * @param socketFactory WebSocket factory.
 * @param batcher WebSocket message batcher.
 * @param healthMonitor Connection health monitor.
 * @param networkMonitor Network connectivity monitor.
 * @param lifecycleMonitor App lifecycle monitor.
 * @param connectionRecoveryEvaluator Reconnection heuristics evaluator.
 * @param clientSubscriptionManager Socket-level listener registry.
 * @param androidComponentsProvider Android system service provider.
 */
@Suppress("LongParameterList")
@StreamInternalApi
public data class StreamComponentProvider(
    val logProvider: StreamLoggerProvider = StreamLoggerProvider.defaultAndroidLogger(),
    val singleFlight: StreamSingleFlightProcessor? = null,
    val serialQueue: StreamSerialProcessingQueue? = null,
    val tokenManager: StreamTokenManager? = null,
    val connectionIdHolder: StreamConnectionIdHolder? = null,
    val socketFactory: StreamWebSocketFactory? = null,
    val batcher: StreamBatcher<String>? = null,
    val healthMonitor: StreamHealthMonitor? = null,
    val networkMonitor: StreamNetworkMonitor? = null,
    val lifecycleMonitor: StreamLifecycleMonitor? = null,
    val connectionRecoveryEvaluator: StreamConnectionRecoveryEvaluator? = null,
    val clientSubscriptionManager: StreamSubscriptionManager<StreamClientListener>? = null,
    val androidComponentsProvider: StreamAndroidComponentsProvider? = null,
)
