package io.getstream.android.core.api.watcher

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamCid
import io.getstream.android.core.api.observers.StreamStartableComponent
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamObservable
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.watcher.StreamCidWatcherImpl

/**
 * Manages a registry of watched resources (channels, conversations, streams) and automatically
 * triggers re-watching when the connection state changes.
 *
 * This component is critical for maintaining active subscriptions across reconnection cycles. When
 * the WebSocket disconnects and reconnects, all active watches must be re-established on the server.
 * [StreamCidWatcher] tracks which [StreamCid]s (Channel IDs) are currently being watched and
 * invokes a callback on every connection state change, allowing the product SDK to re-subscribe.
 *
 * ## Typical Usage Flow
 *
 * 1. Product SDK watches a channel: `watcher.watch(StreamCid.parse("messaging:general"))`
 * 2. Watcher adds the CID to its internal registry
 * 3. Product SDK registers a rewatch listener: `watcher.subscribe(StreamCidRewatchListener { cids -> resubscribe(cids) })`
 * 4. Call `watcher.start()` to begin monitoring connection state changes
 * 5. Connection state changes (e.g., reconnection after network loss)
 * 6. Watcher invokes listener with all watched CIDs: `["messaging:general", "livestream:sports"]`
 * 7. Product SDK re-establishes server-side watches for each CID
 *
 * ## Threading
 *
 * - All methods are thread-safe and can be called from any thread
 * - The rewatch callback is invoked asynchronously on an internal coroutine scope
 * - Callback invocation happens asynchronously on connection state changes
 *
 * ## Lifecycle
 *
 * This component implements [StreamStartableComponent] and requires explicit lifecycle management:
 * - Call [start] to begin monitoring connection state changes
 * - Call [stopWatching] when a resource is no longer needed
 * - Call [stop] when shutting down to cancel monitoring and clean up resources
 * - Call [clear] to remove all watched CIDs from the registry
 *
 * @see StreamCid Channel identifier in format "type:id" (e.g., "messaging:general")
 * @see StreamStartableComponent Lifecycle contract for start/stop operations
 */
@StreamInternalApi
public interface StreamCidWatcher : StreamObservable<StreamCidRewatchListener>, StreamStartableComponent {

    /**
     * Registers a channel or resource as actively watched.
     *
     * Adds the given [cid] to the internal registry of watched resources. When the connection state
     * changes (especially during reconnection), the rewatch callback will include this CID in the
     * list of resources that need to be re-subscribed.
     *
     * Multiple calls with the same [cid] are idempotent—only one entry is maintained per unique CID.
     *
     * ## Example
     * ```kotlin
     * val cid = StreamCid.parse("messaging:general")
     * watcher.watch(cid)
     *     .onSuccess { logger.i { "Now watching: ${it.formatted()}" } }
     *     .onFailure { error -> logger.e(error) { "Failed to watch" } }
     * ```
     *
     * @param cid The channel identifier to watch (format: "type:id")
     * @return [Result.success] with the [cid] if registration succeeded, or [Result.failure] if an
     *   error occurred (e.g., internal registry corruption)
     */
    public fun watch(cid: StreamCid) : Result<StreamCid>

    /**
     * Unregisters a channel or resource from the watch registry.
     *
     * Removes the given [cid] from the internal registry. Subsequent connection state changes will
     * no longer include this CID in the rewatch callback list.
     *
     * Calling this method with a CID that is not being watched is a no-op (not an error).
     *
     * ## Example
     * ```kotlin
     * val cid = StreamCid.parse("messaging:general")
     * watcher.stopWatching(cid)
     *     .onSuccess { logger.i { "Stopped watching: ${it.formatted()}" } }
     *     .onFailure { error -> logger.e(error) { "Failed to stop watching" } }
     * ```
     *
     * @param cid The channel identifier to stop watching
     * @return [Result.success] with the [cid] if removal succeeded, or [Result.failure] if an error
     *   occurred (e.g., internal registry corruption)
     */
    public fun stopWatching(cid: StreamCid) : Result<StreamCid>

    /**
     * Removes all entries from the watch registry.
     *
     * After calling this method, the rewatch callback will NOT be invoked on subsequent
     * connection state changes (since the registry is empty) until new resources are watched.
     *
     * This method is typically called during cleanup or logout to ensure no stale watches remain.
     *
     * ## Example
     * ```kotlin
     * // During logout
     * watcher.clear()
     *     .onSuccess { logger.i { "All watches cleared" } }
     *     .onFailure { error -> logger.e(error) { "Failed to clear watches" } }
     * ```
     *
     * @return [Result.success] if the registry was cleared successfully, or [Result.failure] if an
     *   error occurred (e.g., concurrent modification exception)
     */
    public fun clear(): Result<Unit>
}

@StreamInternalApi
public fun StreamCidWatcher(
    logger: StreamLogger,
    streamRewatchSubscriptionManager: StreamSubscriptionManager<StreamCidRewatchListener>,
    streamClientSubscriptionManager: StreamSubscriptionManager<StreamClientListener>
) : StreamCidWatcher = StreamCidWatcherImpl(
    logger = logger,
    rewatchSubscriptions = streamRewatchSubscriptionManager,
    clientSubscriptions = streamClientSubscriptionManager
)

