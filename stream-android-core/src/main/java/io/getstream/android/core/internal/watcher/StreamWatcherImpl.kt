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

package io.getstream.android.core.internal.watcher

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.utils.runCatchingCancellable
import io.getstream.android.core.api.watcher.StreamRewatchListener
import io.getstream.android.core.api.watcher.StreamWatcher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Implementation of [StreamWatcher] that observes connection state changes via [StateFlow] and
 * triggers rewatch callbacks.
 *
 * This implementation collects from the connection state flow and uses the provided
 * [CoroutineScope] for invoking rewatch callbacks asynchronously.
 *
 * Exceptions thrown by rewatch callbacks are caught and logged to prevent crashes while still
 * allowing error handling by the product SDK.
 *
 * @param scope Coroutine scope for async operations (should use SupervisorJob to prevent callback
 *   failures from cancelling the scope)
 * @param connectionState StateFlow providing connection state updates
 * @param watched Concurrent map storing watched entries (defaults to empty [ConcurrentHashMap])
 * @param rewatchSubscriptions Manager for rewatch listener subscriptions
 * @param logger Logger for diagnostic output and error reporting
 */
@StreamInternalApi
internal class StreamWatcherImpl<T>(
    private val scope: CoroutineScope,
    private val connectionState: StateFlow<StreamConnectionState>,
    private val watched: ConcurrentMap<T, Unit> = ConcurrentHashMap(),
    private val rewatchSubscriptions: StreamSubscriptionManager<StreamRewatchListener<T>>,
    private val logger: StreamLogger,
) : StreamWatcher<T> {

    private var collectionJob: Job? = null

    override fun start(): Result<Unit> =
        synchronized(this) {
            if (collectionJob?.isActive == true) {
                return Result.success(Unit) // Already started
            }

            runCatching {
                collectionJob =
                    scope.launch {
                        connectionState.collect { state -> handleConnectionStateChange(state) }
                    }
            }
        }

    private suspend fun handleConnectionStateChange(state: StreamConnectionState) {
        // Invoke rewatch callback when connected and have watched items
        if (state is StreamConnectionState.Connected && watched.isNotEmpty()) {
            val items = watched.keys.toSet()
            val connectionId = state.connectionId
            logger.v {
                "[handleConnectionStateChange] Triggering rewatch for ${items.size} items on connection $connectionId: ${items.joinToString()}"
            }

            if (items.isNotEmpty()) {
                // Collect listeners into a list, then call each sequentially
                val listeners = mutableListOf<StreamRewatchListener<T>>()
                rewatchSubscriptions.forEach { listeners.add(it) }

                // Call each listener's suspend onRewatch sequentially
                listeners.forEach { listener ->
                    runCatchingCancellable { listener.onRewatch(items, connectionId) }
                        .onFailure { error ->
                            logger.e(error) {
                                "[handleConnectionStateChange] Rewatch callback failed for ${items.size} items. Error: ${error.message}"
                            }
                        }
                }
            }
        } else {
            logger.v { "[handleConnectionStateChange] State: $state, items count: ${watched.size}" }
        }
    }

    override fun stop(): Result<Unit> =
        synchronized(this) {
            runCatching {
                collectionJob?.cancel()
                collectionJob = null
                // Don't cancel scope - allows restart like other StreamStartableComponent
                // implementations
            }
        }

    override fun watch(item: T) = runCatching {
        watched[item] = Unit
        item
    }

    override fun stopWatching(item: T): Result<T> = runCatching {
        watched.remove(item)
        item
    }

    override fun clear(): Result<Unit> = runCatching { watched.clear() }

    override fun subscribe(
        listener: StreamRewatchListener<T>,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> {
        if (collectionJob == null) {
            logger.w { "Call start() on this instance to receive rewatch updates!" }
        }
        return rewatchSubscriptions.subscribe(listener, options)
    }
}
