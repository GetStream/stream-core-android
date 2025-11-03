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
package io.getstream.android.core.api.model.connection.network

import io.getstream.android.core.annotations.StreamInternalApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Container for the strongly typed connectivity metadata that Stream surfaces to observers.
 *
 * The nested data structures mirror the information exposed by Android's connectivity stack while
 * normalising names and value ranges so they remain stable across API levels.
 */
@StreamInternalApi
public class StreamNetworkInfo {

    /**
     * Immutable capture of the device's currently selected network at a specific point in time.
     *
     * Every field maps back to values provided by [android.net.NetworkCapabilities] or
     * [android.net.LinkProperties]. Nullable fields either were not reported by the platform or are
     * hidden due to missing runtime permissions (for example, fine location for Wi-Fi SSID).
     *
     * @property timestamp When the snapshot was assembled. Defaults to [Clock.System.now].
     * @property transports Physical or virtual mediums (Wi-Fi, cellular, VPN, …) backing the
     *   connection.
     * @property internet Whether the OS believes the network offers internet reachability.
     * @property validated Whether the OS completed captive portal / validation checks.
     * @property captivePortal True when the network is gated behind a captive portal login flow.
     * @property vpn Indicates that the network represents, or is routed through, a VPN.
     * @property trusted True when the network is marked as trusted by the system.
     * @property localOnly True for local-only networks without internet routing (e.g., Wi-Fi
     *   Direct).
     * @property metered Billing hint describing if traffic may incur charges.
     * @property roaming True when the cellular connection is roaming, `null` when unknown.
     * @property congested False when the OS explicitly reports the network is *not* congested.
     * @property suspended False when the OS explicitly reports the network is *not* suspended.
     * @property bandwidthConstrained False when the OS explicitly reports the network is *not*
     *   bandwidth constrained.
     * @property bandwidthKbps Platform-estimated downstream and upstream throughput in kilobits per
     *   second.
     * @property priority Platform hint for whether latency or bandwidth should be prioritised.
     * @property signal Normalised radio-level signal information when exposed.
     * @property link Link-layer metadata such as interface name, IP addresses, DNS servers, and
     *   MTU.
     */
    @OptIn(ExperimentalTime::class)
    @StreamInternalApi
    public data class Snapshot(
        val timestamp: Instant = Clock.System.now(),
        val transports: Set<Transport> = emptySet(),
        val internet: Boolean? = null,
        val validated: Boolean? = null,
        val captivePortal: Boolean? = null,
        val vpn: Boolean? = null,
        val trusted: Boolean? = null,
        val localOnly: Boolean? = null,
        val metered: Metered = Metered.UNKNOWN_OR_METERED,
        val roaming: Boolean? = null,
        val congested: Boolean? = null,
        val suspended: Boolean? = null,
        val bandwidthConstrained: Boolean? = null,
        val bandwidthKbps: Bandwidth? = null,
        val priority: PriorityHint = PriorityHint.NONE,
        val signal: Signal? = null,
        val link: Link? = null,
    )

    /**
     * Enumerates the logical transport mediums that Android associates with a network.
     *
     * Values correspond to the `TRANSPORT_*` constants from [android.net.NetworkCapabilities]. When
     * the system reports no recognised transports, [UNKNOWN] is used as a defensive fallback.
     */
    public enum class Transport {
        WIFI,
        CELLULAR,
        ETHERNET,
        BLUETOOTH,
        WIFI_AWARE,
        LOW_PAN,
        USB,
        THREAD,
        SATELLITE,
        VPN,
        UNKNOWN,
    }

    /** Expresses the OS-level priority hint for the current network when multiplexing is needed. */
    public enum class PriorityHint {
        /** No explicit hint was published by the platform. */
        NONE,

        /** Low latency should be favoured over throughput (e.g., for real-time media). */
        LATENCY,

        /** Throughput should be prioritised over latency (e.g., large downloads). */
        BANDWIDTH,
    }

    /** Billing and quota hint for the connection. */
    public enum class Metered {
        /** The platform guarantees the connection is currently unmetered. */
        NOT_METERED,

        /** Temporarily unmetered (e.g., carrier promotion) but should not be relied upon. */
        TEMPORARILY_NOT_METERED,

        /** Unknown or treated as metered to stay on the safe side. */
        UNKNOWN_OR_METERED,
    }

    /**
     * Platform-supplied bandwidth estimate in kilobits per second. Values are positive when
     * available; `null` indicates the estimate is missing or deemed unreliable.
     */
    public data class Bandwidth(val downKbps: Int?, val upKbps: Int?)

    /**
     * Link-layer metadata for the network interface backing the connection.
     *
     * Includes identifiers and addressing information that can be used for diagnostics or to tailor
     * behaviour (for example, adapting timeouts when behind a known proxy).
     */
    public data class Link(
        val interfaceName: String?,
        val addresses: List<String>,
        val dnsServers: List<String>,
        val domains: List<String>,
        val mtu: Int?,
        val httpProxy: String?,
    )

    /**
     * Normalised radio signal readings for different transport technologies.
     *
     * Only the values surfaced by the platform are exposed; absent metrics stay `null`. Use
     * [strengthDbm] and [level0to4] as generic helpers when the underlying transport is unknown.
     */
    public sealed interface Signal {
        /**
         * Raw signal strength in dBm when the transport exposes it. Larger (less negative) values
         * generally indicate a stronger signal.
         */
        public val strengthDbm: Int?
            get() = null

        /** Canonical 0..4 "bars" representation when available from the platform APIs. */
        public val level0to4: Int?
            get() = null

        /**
         * Wi-Fi specific signal details reported by [android.net.wifi.WifiManager].
         *
         * @property rssiDbm Received signal strength indicator in dBm, `null` if withheld.
         * @property level0to4 Normalised signal level (0 weakest – 4 strongest).
         * @property ssid Network SSID when permissions allow disclosure.
         * @property bssid Access point BSSID when available.
         * @property frequencyMhz Operating frequency of the access point.
         */
        public data class Wifi(
            val rssiDbm: Int?,
            override val level0to4: Int?,
            val ssid: String?,
            val bssid: String?,
            val frequencyMhz: Int?,
            override val strengthDbm: Int? = rssiDbm,
        ) : Signal

        /**
         * Cellular radio measurements aggregated from [android.telephony.SignalStrength].
         *
         * @property rat Radio access technology label (NR, LTE, WCDMA, …) when determined.
         * @property level0to4 Normalised signal level (0 weakest – 4 strongest).
         * @property rsrpDbm Reference signal received power for LTE/NR, in dBm.
         * @property rsrqDb Reference signal received quality for LTE/NR, in dB.
         * @property sinrDb Signal-to-interference-plus-noise ratio for LTE/NR, in dB.
         */
        public data class Cellular(
            val rat: String?,
            override val level0to4: Int?,
            val rsrpDbm: Int?,
            val rsrqDb: Int?,
            val sinrDb: Int?,
            override val strengthDbm: Int? = rsrpDbm,
        ) : Signal

        /** Fallback signal bucket used when the platform only exposes a generic strength value. */
        public data class Generic(val value: Int, override val strengthDbm: Int? = value) : Signal
    }
}
