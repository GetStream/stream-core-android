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

package io.getstream.android.core.api.watcher

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamCid
import io.getstream.android.core.api.observers.StreamStartableComponent
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamObservable
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.watcher.StreamCidWatcherImpl
import kotlinx.coroutines.CoroutineScope

/**
 * Manages a registry of watched resources (channels, conversations, streams) and automatically
 * triggers re-watching when the connection state changes.
 *
 * This component is critical for maintaining active subscriptions across reconnection cycles. When
 * the WebSocket disconnects and reconnects, all active watches must be re-established on the
 * server. [StreamCidWatcher] tracks which [StreamCid]s (Channel IDs) are currently being watched
 * and invokes a callback on every connection state change, allowing the product SDK to
 * re-subscribe.
 *
 * ## Typical Usage Flow
 * 1. Product SDK watches a channel: `watcher.watch(StreamCid.parse("messaging:general"))`
 * 2. Watcher adds the CID to its internal registry
 * 3. Product SDK registers a rewatch listener: `watcher.subscribe(StreamCidRewatchListener { cids
 *    -> resubscribe(cids) })`
 * 4. Call `watcher.start()` to begin monitoring connection state changes
 * 5. Connection state changes (e.g., reconnection after network loss)
 * 6. Watcher invokes listener with all watched CIDs: `["messaging:general", "livestream:sports"]`
 * 7. Product SDK re-establishes server-side watches for each CID
 *
 * ## Threading
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
public interface StreamCidWatcher :
    StreamObservable<StreamCidRewatchListener>, StreamStartableComponent {

    /**
     * Registers a channel or resource as actively watched.
     *
     * Adds the given [cid] to the internal registry of watched resources. When the connection state
     * changes (especially during reconnection), the rewatch callback will include this CID in the
     * list of resources that need to be re-subscribed.
     *
     * Multiple calls with the same [cid] are idempotent—only one entry is maintained per unique
     * CID.
     *
     * ## Example
     *
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
    public fun watch(cid: StreamCid): Result<StreamCid>

    /**
     * Unregisters a channel or resource from the watch registry.
     *
     * Removes the given [cid] from the internal registry. Subsequent connection state changes will
     * no longer include this CID in the rewatch callback list.
     *
     * Calling this method with a CID that is not being watched is a no-op (not an error).
     *
     * ## Example
     *
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
    public fun stopWatching(cid: StreamCid): Result<StreamCid>

    /**
     * Removes all entries from the watch registry.
     *
     * After calling this method, the rewatch callback will NOT be invoked on subsequent connection
     * state changes (since the registry is empty) until new resources are watched.
     *
     * This method is typically called during cleanup or logout to ensure no stale watches remain.
     *
     * ## Example
     *
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

/**
 * Creates a new [StreamCidWatcher] instance with the provided dependencies.
 *
 * This factory method instantiates the default implementation ([StreamCidWatcherImpl]) and wires
 * all required dependencies for monitoring connection state changes and triggering rewatch
 * callbacks.
 *
 * ## Parameters
 * - **scope**: The [CoroutineScope] used for launching async operations (rewatch callbacks). This
 *   scope should typically use `SupervisorJob + Dispatchers.Default` to ensure callback failures
 *   don't cancel the scope and to avoid blocking the main thread. The scope is NOT cancelled when
 *   [StreamCidWatcher.stop] is called, allowing the component to be restarted.
 * - **logger**: A [StreamLogger] instance for diagnostic output, tagged appropriately (e.g.,
 *   "SCCidWatcher"). Used for logging state changes, rewatch events, and errors.
 * - **streamRewatchSubscriptionManager**: Manages subscriptions to [StreamCidRewatchListener]. This
 *   manager handles the list of listeners that will be invoked when connection state changes
 *   require re-watching CIDs.
 * - **streamClientSubscriptionManager**: Manages subscriptions to [StreamClientListener]. This is
 *   used to subscribe to connection state changes (via [StreamClientListener.onState]) and to
 *   surface errors (via [StreamClientListener.onError]) when rewatch callbacks fail.
 *
 * ## Lifecycle
 *
 * The returned watcher is created in a stopped state. You must call [StreamCidWatcher.start] to
 * begin monitoring connection state changes. The component can be started and stopped multiple
 * times throughout its lifetime.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
 * val logger = StreamLogger.getLogger("MyApp.CidWatcher")
 *
 * val rewatchManager = StreamSubscriptionManager<StreamCidRewatchListener>(
 *     logger = logger.withTag("RewatchSubscriptions")
 * )
 * val clientManager = StreamSubscriptionManager<StreamClientListener>(
 *     logger = logger.withTag("ClientSubscriptions")
 * )
 *
 * val watcher = StreamCidWatcher(
 *     scope = scope,
 *     logger = logger,
 *     streamRewatchSubscriptionManager = rewatchManager,
 *     streamClientSubscriptionManager = clientManager
 * )
 *
 * // Register rewatch listener
 * watcher.subscribe(StreamCidRewatchListener { cids, connectionId ->
 *     println("Re-watching ${cids.size} channels on connection $connectionId")
 *     // Re-establish server-side watches...
 * })
 *
 * // Start monitoring
 * watcher.start()
 *
 * // Watch channels
 * watcher.watch(StreamCid.parse("messaging:general"))
 * watcher.watch(StreamCid.parse("livestream:sports"))
 * ```
 *
 * @param scope Coroutine scope for async operations (rewatch callback invocations)
 * @param logger Logger for diagnostic output and error reporting
 * @param streamRewatchSubscriptionManager Subscription manager for rewatch listeners
 * @param streamClientSubscriptionManager Subscription manager for client state listeners
 * @return A new [StreamCidWatcher] instance (implementation: [StreamCidWatcherImpl])
 * @see StreamCidWatcher The interface contract
 * @see StreamCidWatcherImpl The concrete implementation
 * @see StreamCidRewatchListener Callback interface for rewatch notifications
 */
@StreamInternalApi
public fun StreamCidWatcher(
    scope: CoroutineScope,
    logger: StreamLogger,
    streamRewatchSubscriptionManager: StreamSubscriptionManager<StreamCidRewatchListener>,
    streamClientSubscriptionManager: StreamSubscriptionManager<StreamClientListener>,
): StreamCidWatcher =
    StreamCidWatcherImpl(
        scope = scope,
        logger = logger,
        rewatchSubscriptions = streamRewatchSubscriptionManager,
        clientSubscriptions = streamClientSubscriptionManager,
    )
