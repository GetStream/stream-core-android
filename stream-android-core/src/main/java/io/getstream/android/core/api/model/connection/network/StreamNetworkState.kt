package io.getstream.android.core.api.model.connection.network

import io.getstream.android.core.annotations.StreamInternalApi

@StreamInternalApi
public sealed class StreamNetworkState {

    /**
     * Signals that the platform reported a permanent loss of network connectivity.
     *
     * This state mirrors the `ConnectivityManager.NetworkCallback.onUnavailable` callback, which
     * indicates no viable network path exists. Applications should back off from network work and
     * surface an offline UI until a different state is received.
     *
     * ### Example
     * ```kotlin
     * when (state) {
     *     StreamNetworkState.Unavailable -> showOfflineBanner("No connection available")
     *     else -> hideOfflineBanner()
     * }
     * ```
     */
    public data object Unavailable : StreamNetworkState()

    /**
     * Represents the initial, indeterminate state before any network callbacks have fired.
     *
     * Use this as a cue to defer UI decisions until more definitive information arrives. The state
     * will transition to one of the other variants once the monitor observes connectivity events.
     */
    public data object Unknown : StreamNetworkState()

    /**
     * Indicates that a network was previously tracked but has been lost.
     *
     * This corresponds to `ConnectivityManager.NetworkCallback.onLost`. Stream monitors emit this
     * when the active network disconnects but the system may still attempt reconnection, so you can
     * show transient offline messaging or pause network-heavy tasks.
     */
    public data object Disconnected : StreamNetworkState()

    /**
     * A network path is currently active and considered connected.
     *
     * This state maps to `ConnectivityManager.NetworkCallback.onAvailable` and carries the most
     * recent [StreamNetworkInfo.Snapshot], allowing callers to inspect transports, metering, or
     * other network characteristics before resuming work.
     *
     * ### Example
     * ```kotlin
     * when (state) {
     *     is StreamNetworkState.Available ->
     *         logger.i { "Connected via ${state.snapshot?.transports}" }
     *     StreamNetworkState.Disconnected -> logger.w { "Network dropped" }
     *     StreamNetworkState.Unavailable -> logger.e { "No connection" }
     *     StreamNetworkState.Unknown -> logger.d { "Awaiting first update" }
     * }
     * ```
     *
     * @property snapshot Latest network snapshot, or `null` if collection failed.
     */
    public data class Available(val snapshot: StreamNetworkInfo.Snapshot?) : StreamNetworkState()
}
