package io.getstream.android.core.api.watcher

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.StreamCid

/**
 * Callback interface invoked when watched resources need to be re-subscribed after a connection
 * state change.
 *
 * This functional interface is used with [StreamCidWatcher] to receive notifications when the
 * WebSocket connection state changes (especially during reconnections), allowing the product SDK
 * to re-establish server-side watches for all actively monitored resources.
 *
 * ## When This Is Called
 *
 * The callback is triggered on every [StreamConnectionState.Connected] event when the watched
 * CID registry is non-empty. This ensures that all active subscriptions are restored after:
 * - Initial connection establishment
 * - Network reconnection after temporary disconnection
 * - WebSocket recovery after connection loss
 *
 * ## Threading
 *
 * - The callback is invoked asynchronously on an internal coroutine scope
 * - Implementations should be thread-safe as concurrent invocations are possible
 * - Long-running operations should be dispatched to an appropriate dispatcher
 *
 * ## Error Handling
 *
 * - Exceptions thrown by this callback are caught and logged by the watcher
 * - Errors are also surfaced via [StreamClientListener.onError] for user-level handling
 * - A failing callback will not prevent other listeners from being notified
 *
 * ## Example Usage
 *
 * ```kotlin
 * val watcher = StreamCidWatcher(logger, rewatchManager, clientManager)
 *
 * // Register a rewatch listener
 * watcher.subscribe(
 *     listener = StreamCidRewatchListener { cids ->
 *         logger.i { "Re-watching ${cids.size} channels" }
 *         cids.forEach { cid ->
 *             // Re-establish server-side watch for each CID
 *             channelClient.watch(cid)
 *         }
 *     }
 * )
 *
 * watcher.start()
 * ```
 *
 * @see StreamCidWatcher Main component that manages the watch registry and triggers callbacks
 * @see StreamCid Channel identifier in format "type:id" (e.g., "messaging:general")
 */
@StreamInternalApi
public fun interface StreamCidRewatchListener {

    /**
     * Called when watched resources need to be re-subscribed.
     *
     * This method is invoked with the complete list of [StreamCid]s currently in the watch
     * registry whenever the connection state changes to [StreamConnectionState.Connected] and
     * the registry is non-empty.
     *
     * Implementations should iterate over the provided list and re-establish server-side watches
     * for each CID to maintain active subscriptions across reconnection cycles.
     *
     * ## Contract
     *
     * - The list is never empty when this method is called
     * - The list represents a snapshot of the watch registry at the time of invocation
     * - Modifications to the list do not affect the internal registry
     * - Multiple calls may occur for the same set of CIDs during reconnection scenarios
     *
     * ## Error Handling
     *
     * Exceptions thrown by this method are caught, logged, and surfaced via error callbacks.
     * The failure of one listener does not prevent other listeners from being notified.
     *
     * @param list A non-empty list of [StreamCid]s that require re-watching
     */
    public fun onRewatch(list: List<StreamCid>)
}
