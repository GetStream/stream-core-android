package io.getstream.android.core.internal.watcher

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamCid
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.watcher.StreamCidWatcher
import io.getstream.android.core.api.watcher.StreamCidRewatchListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Implementation of [StreamCidWatcher] that uses [StreamSubscriptionManager] to monitor
 * connection state changes and trigger rewatch callbacks.
 *
 * This implementation subscribes to connection state changes via [StreamClientListener] and
 * maintains an internal [CoroutineScope] for invoking rewatch callbacks asynchronously.
 *
 * Exceptions thrown by the rewatch callback are caught, logged, and surfaced via the error callback
 * to prevent crashes while still allowing error handling by the product SDK.
 *
 * @param watched Concurrent map storing watched CIDs (defaults to empty [ConcurrentHashMap])
 * @param subscriptionManager Manager for subscribing to connection state change notifications
 * @param logger Logger for diagnostic output and error reporting
 */
@StreamInternalApi
internal class StreamCidWatcherImpl(
    private val watched: ConcurrentMap<StreamCid, Unit> = ConcurrentHashMap(),
    private val rewatchSubscriptions: StreamSubscriptionManager<StreamCidRewatchListener>,
    private val clientSubscriptions: StreamSubscriptionManager<StreamClientListener>,
    private val logger: StreamLogger
) : StreamCidWatcher {

    private var subscription: StreamSubscription? = null

    // Internal scope with SupervisorJob to prevent callback failures from cancelling the scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val listener =
        object : StreamClientListener {
            override fun onState(state: StreamConnectionState) {
                // Invoke rewatch callback on every connection state change
                if (state is StreamConnectionState.Connected && watched.isNotEmpty()) {
                    scope.launch {
                        val cids = watched.keys.toList()
                        logger.v {
                            "[onState] Triggering rewatch for ${cids.size} CIDs: ${cids.map { it.formatted() }}"
                        }

                        if (cids.isNotEmpty()) {
                            rewatchSubscriptions.forEach {
                                it.onRewatch(cids)
                            }.onFailure { error ->
                                logger.e(error) {
                                    "[onState] Rewatch callback failed for ${cids.size} CIDs. Error: ${error.message}"
                                }
                                clientSubscriptions.forEach {
                                    it.onError(error)
                                }
                            }
                        }
                    }
                } else {
                    logger.v {
                        "[onState] State: $state, CIDS count: ${watched.size}"
                    }
                }
            }
        }

    override fun start(): Result<Unit> = runCatching {
        if (subscription != null) {
            return@runCatching // Already started
        }

        subscription =
            clientSubscriptions
                .subscribe(
                    listener,
                    StreamSubscriptionManager.Options(
                        retention =
                            StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                    ),
                ).getOrThrow()
    }

    override fun stop(): Result<Unit> = runCatching {
        subscription?.cancel()
        subscription = null
        scope.cancel()
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
        options: StreamSubscriptionManager.Options
    ): Result<StreamSubscription> = rewatchSubscriptions.subscribe(listener, options)
}

