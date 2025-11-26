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

package io.getstream.android.core.internal.observers

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.observers.StreamStartableComponent
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.subscribe.StreamObservable
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Coordinates lifecycle and network signals to make connection recovery decisions.
 *
 * ### Responsibilities
 * - Bridges callbacks from [StreamNetworkMonitor] and [StreamLifecycleMonitor] into hot state flows
 *   exposed as [networkState] and [lifecycleState].
 * - Notifies registered [StreamConnectionRecoveryListener]s when reconnect/teardown actions should
 *   occur.
 * - Implements [StreamStartableComponent] so callers can hook into their own lifecycle.
 *
 * ### Usage
 *
 * ```kotlin
 * val subscription = connectionRecoveryManager.subscribe(listener).getOrThrow()
 * connectionRecoveryManager.start()
 * // … later …
 * subscription.cancel()
 * connectionRecoveryManager.stop()
 * ```
 */
internal interface StreamNetworkAndLifeCycleMonitor :
    StreamStartableComponent, StreamObservable<StreamNetworkAndLifecycleMonitorListener> {}

/**
 * Creates a [StreamNetworkAndLifeCycleMonitor] instance.
 *
 * @param logger The logger to use for logging.
 * @param lifecycleMonitor The lifecycle monitor to use for accessing lifecycle information.
 * @param networkMonitor The network monitor to use for accessing network information.
 * @param mutableNetworkState The mutable network state to use for accessing network information.
 * @param mutableLifecycleState The mutable lifecycle state to use for accessing lifecycle
 *   information.
 * @param subscriptionManager The subscription manager to use for managing listeners.
 * @return A new [StreamNetworkAndLifeCycleMonitor] instance.
 */
internal fun StreamNetworkAndLifeCycleMonitor(
    logger: StreamLogger,
    lifecycleMonitor: StreamLifecycleMonitor,
    networkMonitor: StreamNetworkMonitor,
    mutableNetworkState: MutableStateFlow<StreamNetworkState>,
    mutableLifecycleState: MutableStateFlow<StreamLifecycleState>,
    subscriptionManager: StreamSubscriptionManager<StreamNetworkAndLifecycleMonitorListener>,
): StreamNetworkAndLifeCycleMonitor =
    StreamNetworkAndLifecycleMonitorImpl(
        logger = logger,
        networkMonitor = networkMonitor,
        lifecycleMonitor = lifecycleMonitor,
        mutableNetworkState = mutableNetworkState,
        mutableLifecycleState = mutableLifecycleState,
        subscriptionManager = subscriptionManager,
    )
