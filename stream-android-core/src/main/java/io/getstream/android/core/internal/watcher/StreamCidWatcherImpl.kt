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
import io.getstream.android.core.api.model.StreamCid
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.watcher.StreamCidRewatchListener
import io.getstream.android.core.api.watcher.StreamCidWatcher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Implementation of [StreamCidWatcher] that uses [StreamSubscriptionManager] to monitor connection
 * state changes and trigger rewatch callbacks.
 *
 * This implementation subscribes to connection state changes via [StreamClientListener] and uses
 * the provided [CoroutineScope] for invoking rewatch callbacks asynchronously.
 *
 * Exceptions thrown by the rewatch callback are caught, logged, and surfaced via the error callback
 * to prevent crashes while still allowing error handling by the product SDK.
 *
 * @param scope Coroutine scope for async operations (should use SupervisorJob to prevent callback
 *   failures from cancelling the scope)
 * @param watched Concurrent map storing watched CIDs (defaults to empty [ConcurrentHashMap])
 * @param rewatchSubscriptions Manager for rewatch listener subscriptions
 * @param clientSubscriptions Manager for subscribing to connection state change notifications
 * @param logger Logger for diagnostic output and error reporting
 */
@StreamInternalApi
internal class StreamCidWatcherImpl(
    private val scope: CoroutineScope,
    private val watched: ConcurrentMap<StreamCid, Unit> = ConcurrentHashMap(),
    private val rewatchSubscriptions: StreamSubscriptionManager<StreamCidRewatchListener>,
    private val clientSubscriptions: StreamSubscriptionManager<StreamClientListener>,
    private val logger: StreamLogger,
) : StreamCidWatcher {

    private var subscription: StreamSubscription? = null

    private val listener =
        object : StreamClientListener {
            override fun onState(state: StreamConnectionState) {
                // Invoke rewatch callback on every connection state change
                if (state is StreamConnectionState.Connected && watched.isNotEmpty()) {
                    scope.launch {
                        val cids = watched.keys.toList()
                        val connectionId = state.connectionId
                        logger.v {
                            "[onState] Triggering rewatch for ${cids.size} CIDs on connection $connectionId: ${cids.map { it.formatted() }}"
                        }

                        if (cids.isNotEmpty()) {
                            rewatchSubscriptions
                                .forEach { it.onRewatch(cids, connectionId) }
                                .onFailure { error ->
                                    logger.e(error) {
                                        "[onState] Rewatch callback failed for ${cids.size} CIDs. Error: ${error.message}"
                                    }
                                    clientSubscriptions.forEach { it.onError(error) }
                                }
                        }
                    }
                } else {
                    logger.v { "[onState] State: $state, CIDS count: ${watched.size}" }
                }
            }
        }

    override fun start(): Result<Unit> {
        if (subscription != null) {
            return Result.success(Unit) // Already started
        }

        return clientSubscriptions
            .subscribe(
                listener,
                StreamSubscriptionManager.Options(
                    retention = StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                ),
            )
            .map { subscription = it }
    }
    }

    override fun stop(): Result<Unit> = runCatching {
        subscription?.cancel()
        subscription = null
        // Don't cancel scope - allows restart like other StreamStartableComponent implementations
    }

    override fun watch(cid: StreamCid) = runCatching {
        watched[cid] = Unit
        cid
    }

    override fun stopWatching(cid: StreamCid): Result<StreamCid> = runCatching {
        watched.remove(cid)
        cid
    }

    override fun clear(): Result<Unit> = runCatching { watched.clear() }

    override fun subscribe(
        listener: StreamCidRewatchListener,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = rewatchSubscriptions.subscribe(listener, options)
}
