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

package io.getstream.android.core.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.sample.ui.theme.StreamandroidcoreTheme
import kotlin.time.ExperimentalTime

@Composable
@OptIn(ExperimentalTime::class)
public fun NetworkInfoCard(snapshot: StreamNetworkInfo.Snapshot?) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (snapshot == null) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Network",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "No active network detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@OutlinedCard
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Network",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Last update: ${snapshot.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(snapshot.transports.toList()) { transport ->
                    TransportChip(label = transport.label())
                }
            }

            Divider()

            val signalData = snapshot.signalSummary()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Signal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = signalData.description, style = MaterialTheme.typography.bodyMedium)
                signalData.progress?.let { progress ->
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                }
            }

            Divider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                NetworkFactRow(
                    label = "Internet",
                    value = snapshot.internet.toStatusValue("Available", "Unavailable"),
                    state = snapshot.internet,
                )
                NetworkFactRow(
                    label = "Validated",
                    value = snapshot.validated.toStatusValue("Validated", "Pending"),
                    state = snapshot.validated,
                )
                NetworkFactRow(
                    label = "VPN",
                    value = snapshot.vpn.toStatusValue("Enabled", "Disabled"),
                    state = snapshot.vpn,
                )
                NetworkFactRow(
                    label = "Metered",
                    value = snapshot.metered.label,
                    state =
                        when (snapshot.metered) {
                            StreamNetworkInfo.Metered.NOT_METERED,
                            StreamNetworkInfo.Metered.TEMPORARILY_NOT_METERED -> true
                            StreamNetworkInfo.Metered.UNKNOWN_OR_METERED -> false
                        },
                    alert = snapshot.metered == StreamNetworkInfo.Metered.UNKNOWN_OR_METERED,
                )
                NetworkFactRow(label = "Priority", value = snapshot.priority.label, state = null)
            }

            Divider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Throughput",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                val bandwidth = snapshot.bandwidthKbps
                val downText = bandwidth?.downKbps?.let { "$it kbps" } ?: "Unknown"
                val upText = bandwidth?.upKbps?.let { "$it kbps" } ?: "Unknown"
                NetworkFactRow(label = "Downlink", value = downText, state = null)
                NetworkFactRow(label = "Uplink", value = upText, state = null)
            }

            snapshot.link?.let { link ->
                Divider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Link",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    link.interfaceName?.let {
                        NetworkFactRow(label = "Interface", value = it, state = null)
                    }
                    if (link.addresses.isNotEmpty()) {
                        NetworkFactRow(
                            label = "Address",
                            value = link.addresses.first(),
                            state = null,
                        )
                    }
                    if (link.dnsServers.isNotEmpty()) {
                        NetworkFactRow(
                            label = "DNS",
                            value = link.dnsServers.joinToString(),
                            state = null,
                        )
                    }
                    link.httpProxy?.let {
                        NetworkFactRow(label = "Proxy", value = it, state = null)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkInfoCardPreview() {
    StreamandroidcoreTheme { NetworkInfoCard(sampleSnapshot()) }
}

@OptIn(ExperimentalTime::class)
private fun sampleSnapshot(): StreamNetworkInfo.Snapshot =
    StreamNetworkInfo.Snapshot(
        transports = setOf(StreamNetworkInfo.Transport.WIFI, StreamNetworkInfo.Transport.VPN),
        internet = true,
        validated = true,
        captivePortal = false,
        vpn = true,
        trusted = true,
        localOnly = false,
        metered = StreamNetworkInfo.Metered.UNKNOWN_OR_METERED,
        roaming = false,
        bandwidthKbps = StreamNetworkInfo.Bandwidth(downKbps = 12_000, upKbps = 2_500),
        priority = StreamNetworkInfo.PriorityHint.LATENCY,
        signal =
            StreamNetworkInfo.Signal.Wifi(
                rssiDbm = -55,
                level0to4 = 4,
                ssid = "Stream Guest",
                bssid = "AA:BB:CC:00:11:22",
                frequencyMhz = 5220,
            ),
        link =
            StreamNetworkInfo.Link(
                interfaceName = "wlan0",
                addresses = listOf("192.168.0.12"),
                dnsServers = listOf("1.1.1.1", "8.8.8.8"),
                domains = listOf("getstream.io"),
                mtu = 1500,
                httpProxy = "proxy.local:8080",
            ),
    )
