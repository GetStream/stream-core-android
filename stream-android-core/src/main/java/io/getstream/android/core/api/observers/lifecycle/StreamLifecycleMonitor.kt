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

package io.getstream.android.core.api.observers.lifecycle

import androidx.lifecycle.Lifecycle
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.observers.StreamStartableComponent
import io.getstream.android.core.api.subscribe.StreamObservable
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.observers.lifecycle.StreamLifecycleMonitorImpl

/**
 * Aggregates lifecycle events from the host environment and re-emits them to registered listeners.
 *
 * ### Example
 *
 * ```kotlin
 * val subscription = lifecycleMonitor.subscribe(MyLifecycleListener())
 * lifecycleMonitor.start()
 * // … later …
 * subscription.getOrThrow().cancel()
 * lifecycleMonitor.stop()
 * ```
 */
@StreamInternalApi
public interface StreamLifecycleMonitor :
    StreamStartableComponent, StreamObservable<StreamLifecycleListener> {

    /**
     * Returns the current lifecycle state.
     *
     * @return The current lifecycle state.
     */
    public fun getCurrentState(): StreamLifecycleState
}

/**
 * Creates a [StreamLifecycleMonitor] instance.
 *
 * @param logger The logger to use for logging.
 * @param subscriptionManager The subscription manager to use for managing listeners.
 * @param lifecycle The host lifecycle to observe.
 * @return A new [StreamLifecycleMonitor] instance.
 */
@StreamInternalApi
public fun StreamLifecycleMonitor(
    logger: StreamLogger,
    lifecycle: Lifecycle,
    subscriptionManager: StreamSubscriptionManager<StreamLifecycleListener>,
): StreamLifecycleMonitor = StreamLifecycleMonitorImpl(logger, subscriptionManager, lifecycle)
