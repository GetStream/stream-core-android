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
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.observers.StreamStartableComponent
import io.getstream.android.core.api.subscribe.StreamObservable
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.watcher.StreamWatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages a registry of watched resources (channels, conversations, streams) and automatically
 * triggers re-watching when the connection state changes.
 *
 * This component is critical for maintaining active subscriptions across reconnection cycles. When
 * the WebSocket disconnects and reconnects, all active watches must be re-established on the
 * server. [StreamWatcher] tracks which items of type [T] are currently being watched and invokes a
 * callback on every connection state change, allowing the product SDK to re-subscribe.
 *
 * ## Typical Usage Flow
 * 1. Product SDK watches a channel: `watcher.watch("messaging:general")`
 * 2. Watcher adds the identifier to its internal registry
 * 3. Product SDK registers a rewatch listener: `watcher.subscribe(StreamRewatchListener { ids,
 *    connectionId -> resubscribe(ids, connectionId) })`
 * 4. Call `watcher.start()` to begin monitoring connection state changes
 * 5. Connection state changes (e.g., reconnection after network loss)
 * 6. Watcher invokes listener with all watched entries: `["messaging:general",
 *    "livestream:sports"]`
 * 7. Product SDK re-establishes server-side watches for each entry
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
 * - Call [clear] to remove all watched entries from the registry
 *
 * @see StreamStartableComponent Lifecycle contract for start/stop operations
 */
@StreamInternalApi
public interface StreamWatcher<T> :
    StreamObservable<StreamRewatchListener<T>>, StreamStartableComponent {

    /**
     * Registers a channel or resource as actively watched.
     *
     * Adds the given [item] to the internal registry of watched resources. When the connection
     * state changes (especially during reconnection), the rewatch callback will include this item
     * in the list of resources that need to be re-subscribed.
     *
     * Multiple calls with the same [item] are idempotent—only one entry is maintained per unique
     * identifier.
     *
     * ## Example
     *
     * ```kotlin
     * val cid = "messaging:general"
     * watcher.watch(cid)
     *     .onSuccess { logger.i { "Now watching: $it" } }
     *     .onFailure { error -> logger.e(error) { "Failed to watch" } }
     * ```
     *
     * @param item The identifier to watch (e.g., "type:id")
     * @return [Result.success] with the [item] if registration succeeded, or [Result.failure] if an
     *   error occurred (e.g., internal registry corruption)
     */
    public fun watch(item: T): Result<T>

    /**
     * Unregisters a channel or resource from the watch registry.
     *
     * Removes the given [item] from the internal registry. Subsequent connection state changes will
     * no longer include this entry in the rewatch callback list.
     *
     * Calling this method with an identifier that is not being watched is a no-op (not an error).
     *
     * ## Example
     *
     * ```kotlin
     * val cid = "messaging:general"
     * watcher.stopWatching(cid)
     *     .onSuccess { logger.i { "Stopped watching: $it" } }
     *     .onFailure { error -> logger.e(error) { "Failed to stop watching" } }
     * ```
     *
     * @param item The identifier to stop watching
     * @return [Result.success] with the [item] if removal succeeded, or [Result.failure] if an
     *   error occurred (e.g., internal registry corruption)
     */
    public fun stopWatching(item: T): Result<T>

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
 * Creates a new [StreamWatcher] instance that observes connection state changes.
 *
 * This factory method instantiates the default implementation ([StreamWatcherImpl]) and creates the
 * necessary internal subscription manager. The watcher observes the provided connection state flow
 * and triggers rewatch callbacks when the connection state changes to Connected.
 *
 * ## Parameters
 * - **scope**: The [CoroutineScope] used for launching async operations (collecting state flow and
 *   invoking rewatch callbacks). This scope should typically use `SupervisorJob + Dispatchers.
 *   Default` to ensure callback failures don't cancel the scope and to avoid blocking the main
 *   thread. The scope is NOT cancelled when [StreamWatcher.stop] is called, allowing the component
 *   to be restarted.
 * - **logger**: A [StreamLogger] instance for diagnostic output, tagged appropriately (e.g.,
 *   "Watcher"). Used for logging state changes, rewatch events, and errors.
 * - **connectionState**: A [StateFlow] providing connection state updates. Typically obtained from
 *   `streamClient.connectionState`. The watcher will collect from this flow when started.
 *
 * ## Lifecycle
 *
 * The returned watcher is created in a stopped state. You must call [StreamWatcher.start] to begin
 * monitoring connection state changes. The component can be started and stopped multiple times
 * throughout its lifetime.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
 * val logger = StreamLogger.getLogger("MyApp.Watcher")
 *
 * // Create watcher - only needs the connection state flow
 * val watcher = StreamWatcher<String>(
 *     scope = scope,
 *     logger = logger,
 *     connectionState = streamClient.connectionState
 * )
 *
 * // Register rewatch listener
 * watcher.subscribe(StreamRewatchListener { ids, connectionId ->
 *     println("Re-watching ${ids.size} channels on connection $connectionId")
 *     // Re-establish server-side watches...
 * })
 *
 * // Start monitoring
 * watcher.start()
 *
 * // Watch channels
 * watcher.watch("messaging:general")
 * watcher.watch("livestream:sports")
 * ```
 *
 * @param scope Coroutine scope for async operations (state collection and rewatch callback
 *   invocations)
 * @param logger Logger for diagnostic output and error reporting
 * @param connectionState StateFlow providing connection state updates
 * @return A new [StreamWatcher] instance (implementation: [StreamWatcherImpl])
 * @see StreamWatcher The interface contract
 * @see StreamWatcherImpl The concrete implementation
 * @see StreamRewatchListener Callback interface for rewatch notifications
 */
@StreamInternalApi
public fun <T> StreamWatcher(
    scope: CoroutineScope,
    logger: StreamLogger,
    connectionState: StateFlow<StreamConnectionState>,
): StreamWatcher<T> {
    val rewatchSubscriptions = StreamSubscriptionManager<StreamRewatchListener<T>>(logger)
    return StreamWatcherImpl(
        scope = scope,
        connectionState = connectionState,
        rewatchSubscriptions = rewatchSubscriptions,
        logger = logger,
    )
}
