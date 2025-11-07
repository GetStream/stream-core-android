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
import android.os.ext.SdkExtensions
import android.telephony.TelephonyManager
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Bandwidth
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Link
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Metered
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.PriorityHint
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport
import kotlin.time.ExperimentalTime

@Suppress("NewApi")
internal class StreamNetworkSnapshotBuilder(
    private val signalProcessing: StreamNetworkSignalProcessing,
    private val wifiManager: WifiManager,
    private val telephonyManager: TelephonyManager,
    private val extensionVersionProvider: (Int) -> Int = DEFAULT_EXTENSION_PROVIDER,
) {
    @OptIn(ExperimentalTime::class)
    fun build(
        network: Network,
        networkCapabilities: NetworkCapabilities?,
        linkProperties: LinkProperties?,
    ): Result<StreamNetworkInfo.Snapshot?> = runCatching {
        if (!supportsSnapshots()) {
            return@runCatching null
        }

        val transports = transportsFor(networkCapabilities)
        val metered = networkCapabilities.resolveMetered()

        StreamNetworkInfo.Snapshot(
            transports = transports,
            internet = networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated =
                networkCapabilities.flagIfAtLeast(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                    Build.VERSION_CODES.M,
                ),
            captivePortal =
                networkCapabilities.flagIfAtLeast(
                    NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL,
                    Build.VERSION_CODES.M,
                ),
            vpn = isVpn(networkCapabilities),
            trusted = networkCapabilities.flag(NetworkCapabilities.NET_CAPABILITY_TRUSTED),
            localOnly =
                networkCapabilities.flagIfAtLeast(
                    NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK,
                    Build.VERSION_CODES.VANILLA_ICE_CREAM,
                ),
            metered = metered,
            roaming =
                networkCapabilities
                    .flagIfAtLeast(
                        NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING,
                        Build.VERSION_CODES.P,
                    )
                    ?.not(),
            congested =
                networkCapabilities.negatedCapabilityAsFalse(
                    NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED,
                    Build.VERSION_CODES.P,
                ),
            suspended =
                networkCapabilities.negatedCapabilityAsFalse(
                    NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED,
                    Build.VERSION_CODES.P,
                ),
            bandwidthConstrained = bandwidthConstraintHint(networkCapabilities),
            bandwidthKbps = bandwidthFor(networkCapabilities),
            priority = resolvePriority(networkCapabilities),
            signal =
                signalProcessing.bestEffortSignal(
                    wifiManager,
                    telephonyManager,
                    networkCapabilities,
                    transports,
                ),
            link = linkProperties?.toLink(),
        )
    }

    private fun transportsFor(capabilities: NetworkCapabilities?): Set<Transport> {
        if (capabilities == null) {
            return emptySet()
        }
        val transports =
            KNOWN_TRANSPORTS.mapNotNull { (id, transport) ->
                    transport.takeIf { capabilities.safeHasTransport(id) == true }
                }
                .toMutableSet()
        if (transports.isEmpty()) {
            transports += Transport.UNKNOWN
        }
        return transports
    }

    private fun LinkProperties.toLink(): Link? {
        val addresses = linkAddresses.mapNotNull { it.address?.hostAddress }
        val dnsServers = dnsServers.mapNotNull { it.hostAddress }
        val domains = domains?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        val mtuValue =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mtu.takeIf { it > 0 }
            } else {
                null
            }
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

    private fun supportsSnapshots(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    private fun NetworkCapabilities?.flagIfAtLeast(capability: Int, minSdk: Int): Boolean? =
        if (Build.VERSION.SDK_INT >= minSdk) flag(capability) else null

    private fun NetworkCapabilities?.negatedCapabilityAsFalse(
        capability: Int,
        minSdk: Int,
    ): Boolean? =
        when (flagIfAtLeast(capability, minSdk)) {
            true -> false
            else -> null
        }

    private fun NetworkCapabilities?.resolveMetered(): Metered =
        when {
            flag(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true -> Metered.NOT_METERED
            flag(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED) == true ->
                Metered.TEMPORARILY_NOT_METERED

            else -> Metered.UNKNOWN_OR_METERED
        }

    private fun isVpn(capabilities: NetworkCapabilities?): Boolean =
        capabilities.transport(NetworkCapabilities.TRANSPORT_VPN) ||
            capabilities.flag(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == false

    private fun bandwidthConstraintHint(capabilities: NetworkCapabilities?): Boolean? =
        if (isBandwidthConstraintSupported()) {
            capabilities.negatedCapabilityAsFalse(
                NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED,
                Build.VERSION_CODES.R,
            )
        } else {
            null
        }

    private fun isBandwidthConstraintSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            extensionVersionProvider(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 16

    private fun bandwidthFor(capabilities: NetworkCapabilities?): Bandwidth? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val down = capabilities?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }
        val up = capabilities?.linkUpstreamBandwidthKbps?.takeIf { it > 0 }
        return if (down != null || up != null) Bandwidth(downKbps = down, upKbps = up) else null
    }

    private fun resolvePriority(capabilities: NetworkCapabilities?): PriorityHint =
        when {
            capabilities.flag(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY) == true ->
                PriorityHint.LATENCY

            capabilities.flag(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH) == true ->
                PriorityHint.BANDWIDTH

            else -> PriorityHint.NONE
        }

    private companion object {
        private val DEFAULT_EXTENSION_PROVIDER: (Int) -> Int = { extension ->
            SdkExtensions.getExtensionVersion(extension)
        }

        private val KNOWN_TRANSPORTS =
            listOf(
                NetworkCapabilities.TRANSPORT_WIFI to Transport.WIFI,
                NetworkCapabilities.TRANSPORT_CELLULAR to Transport.CELLULAR,
                NetworkCapabilities.TRANSPORT_ETHERNET to Transport.ETHERNET,
                NetworkCapabilities.TRANSPORT_BLUETOOTH to Transport.BLUETOOTH,
                NetworkCapabilities.TRANSPORT_WIFI_AWARE to Transport.WIFI_AWARE,
                NetworkCapabilities.TRANSPORT_LOWPAN to Transport.LOW_PAN,
                NetworkCapabilities.TRANSPORT_USB to Transport.USB,
                NetworkCapabilities.TRANSPORT_THREAD to Transport.THREAD,
                NetworkCapabilities.TRANSPORT_SATELLITE to Transport.SATELLITE,
                NetworkCapabilities.TRANSPORT_VPN to Transport.VPN,
            )
    }
}
