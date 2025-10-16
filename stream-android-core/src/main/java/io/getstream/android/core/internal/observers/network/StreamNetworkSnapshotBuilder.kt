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
package io.getstream.android.core.internal.observers.network

import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Bandwidth
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Link
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Metered
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.PriorityHint
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport
import kotlin.time.ExperimentalTime

internal class StreamNetworkSnapshotBuilder(
    private val signalProcessing: StreamNetworkSignalProcessing,
    private val wifiManager: WifiManager,
    private val telephonyManager: TelephonyManager,
) {
    @OptIn(ExperimentalTime::class)
    fun build(
        network: Network,
        networkCapabilities: NetworkCapabilities?,
        linkProperties: LinkProperties?,
    ): Result<StreamNetworkInfo.Snapshot?> = runCatching {
        val transports = transportsFor(networkCapabilities)
        val internet = networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                null
            }
        val captivePortal =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            } else {
                null
            }
        val notVpn = networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        val vpn =
            networkCapabilities.transport(NetworkCapabilities.TRANSPORT_VPN) || (notVpn == false)
        val trusted = networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        val localOnly =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK)
            } else {
                null
            }

        val metered =
            when {
                networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true ->
                    Metered.NOT_METERED
                networkCapabilities.flag(
                    NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
                ) == true -> Metered.TEMPORARILY_NOT_METERED
                else -> Metered.UNKNOWN_OR_METERED
            }

        val congested =
            when (networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)) {
                true -> false
                else -> null
            }
        val suspended =
            when (networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) {
                true -> false
                else -> null
            }
        val bandwidthConstrained =
            when (
                networkCapabilities.flag(
                    NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED
                )
            ) {
                true -> false
                else -> null
            }

        val priority =
            when {
                networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY) ==
                    true -> PriorityHint.LATENCY
                networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH) ==
                    true -> PriorityHint.BANDWIDTH
                else -> PriorityHint.NONE
            }

        val bandwidth =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Bandwidth(
                    downKbps = networkCapabilities?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
                    upKbps = networkCapabilities?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
                )
            } else {
                null
            }

        val signal =
            signalProcessing.bestEffortSignal(
                wifiManager,
                telephonyManager,
                networkCapabilities,
                transports,
            )

        val link = linkProperties?.toLink()

        val notRoaming =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            } else {
                null
            }

        StreamNetworkInfo.Snapshot(
            transports = transports,
            internet = internet,
            validated = validated,
            captivePortal = captivePortal,
            vpn = vpn,
            trusted = trusted,
            localOnly = localOnly,
            metered = metered,
            roaming = notRoaming?.not(),
            congested = congested,
            suspended = suspended,
            bandwidthConstrained = bandwidthConstrained,
            bandwidthKbps = bandwidth,
            priority = priority,
            signal = signal,
            link = link,
        )
    }

    private fun transportsFor(capabilities: NetworkCapabilities?): Set<Transport> {
        if (capabilities == null) return emptySet()
        val out = mutableSetOf<Transport>()
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
            out += Transport.WIFI
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
            out += Transport.CELLULAR
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true)
            out += Transport.ETHERNET
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true)
            out += Transport.BLUETOOTH
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) == true)
            out += Transport.WIFI_AWARE
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) == true)
            out += Transport.LOW_PAN
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_USB) == true)
            out += Transport.USB
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_THREAD) == true)
            out += Transport.THREAD
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) == true)
            out += Transport.SATELLITE
        if (capabilities.safeHasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
            out += Transport.VPN
        if (out.isEmpty()) out += Transport.UNKNOWN
        return out
    }

    private fun LinkProperties.toLink(): Link? {
        val addresses = linkAddresses.mapNotNull { it.address?.hostAddress }
        val dnsServers = dnsServers.mapNotNull { it.hostAddress }
        val domains = domains?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        val mtuValue =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mtu.takeIf { it > 0 } else null
        val httpProxyValue = httpProxy?.let { "${it.host}:${it.port}" }
        return Link(
            interfaceName = interfaceName,
            addresses = addresses,
            dnsServers = dnsServers,
            domains = domains,
            mtu = mtuValue,
            httpProxy = httpProxyValue,
        )
    }

    private fun NetworkCapabilities?.flag(capability: Int): Boolean? =
        this?.safeHasCapability(capability)

    private fun NetworkCapabilities?.transport(transport: Int): Boolean =
        this?.safeHasTransport(transport) == true
}
