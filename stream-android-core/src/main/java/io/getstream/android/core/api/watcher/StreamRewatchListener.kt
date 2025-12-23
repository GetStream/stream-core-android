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

/**
 * Callback interface invoked when watched resources need to be re-subscribed after a connection
 * state change.
 *
 * This functional interface is used with [StreamWatcher] to receive notifications when the
 * WebSocket connection state changes (especially during reconnections), allowing the product SDK to
 * re-establish server-side watches for all actively monitored resources.
 *
 * ## When This Is Called
 *
 * The callback is triggered on every [StreamConnectionState.Connected] event when the watched CID
 * registry is non-empty. This ensures that all active subscriptions are restored after:
 * - Initial connection establishment
 * - Network reconnection after temporary disconnection
 * - WebSocket recovery after connection loss
 *
 * ## Threading
 * - The callback is invoked asynchronously on an internal coroutine scope
 * - Implementations should be thread-safe as concurrent invocations are possible
 * - Long-running operations should be dispatched to an appropriate dispatcher
 *
 * ## Error Handling
 * - Exceptions thrown by this callback are caught and logged by the watcher
 * - Errors are also surfaced via [StreamClientListener.onError] for user-level handling
 * - A failing callback will not prevent other listeners from being notified
 *
 * ## Example Usage
 *
 * ```kotlin
 * val watcher = StreamWatcher<String>(scope, logger, rewatchManager, clientManager)
 *
 * // Register a rewatch listener
 * watcher.subscribe(
 *     listener = StreamRewatchListener { ids, connectionId ->
 *         logger.i { "Re-watching ${ids.size} channels on connection $connectionId" }
 *         ids.forEach { id ->
 *             // Re-establish server-side watch for each identifier
 *             channelClient.watch(id, connectionId)
 *         }
 *     }
 * )
 *
 * watcher.start()
 * ```
 *
 * @see StreamWatcher Main component that manages the watch registry and triggers callbacks
 */
@StreamInternalApi
public fun interface StreamRewatchListener<T> {

    /**
     * Called when watched resources need to be re-subscribed.
     *
     * This method is invoked with the complete set of entries currently in the watch registry
     * whenever the connection state changes to [StreamConnectionState.Connected] and the registry
     * is non-empty.
     *
     * Implementations should iterate over the provided list and re-establish server-side watches
     * for each CID to maintain active subscriptions across reconnection cycles.
     *
     * ## Contract
     * - The list is never empty when this method is called
     * - The list represents a snapshot of the watch registry at the time of invocation
     * - Modifications to the list do not affect the internal registry
     * - Multiple calls may occur for the same set of CIDs during reconnection scenarios
     * - The connectionId reflects the current active connection at the moment of invocation
     *
     * ## Error Handling
     *
     * Exceptions thrown by this method are caught, logged, and surfaced via error callbacks. The
     * failure of one listener does not prevent other listeners from being notified.
     *
     * @param items A non-empty set of entries that require re-watching
     * @param connectionId The connection ID of the current active WebSocket connection
     */
    public fun onRewatch(items: Set<T>, connectionId: String)
}
