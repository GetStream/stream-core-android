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
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.PriorityHint
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.intArrayOf
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
internal class StreamNetworkSnapshotBuilderTest {

    @MockK(relaxed = true) lateinit var signalProcessing: StreamNetworkSignalProcessing
    @MockK(relaxed = true) lateinit var wifiManager: WifiManager
    @MockK(relaxed = true) lateinit var telephonyManager: TelephonyManager

    private lateinit var builder: StreamNetworkSnapshotBuilder

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this)
        builder = StreamNetworkSnapshotBuilder(signalProcessing, wifiManager, telephonyManager)
    }

    @Test
    fun `build maps transports capabilities and link data`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>(relaxed = true)
        val linkProperties = mockk<LinkProperties>(relaxed = true)

        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) } returns true
        every { capabilities.linkDownstreamBandwidthKbps } returns 50_000
        every { capabilities.linkUpstreamBandwidthKbps } returns 10_000

        val signal = StreamNetworkInfo.Signal.Generic(40)
        every {
            signalProcessing.bestEffortSignal(wifiManager, telephonyManager, capabilities, any())
        } returns signal

        val snapshot = builder.build(network, capabilities, linkProperties).getOrThrow()

        assertNotNull(snapshot)
        assertEquals(setOf(Transport.WIFI), snapshot.transports)
        assertTrue(snapshot.internet ?: false)
        assertTrue(snapshot.validated ?: false)
        assertFalse(snapshot.vpn ?: true)
        assertEquals(StreamNetworkInfo.Metered.TEMPORARILY_NOT_METERED, snapshot.metered)
        assertEquals(PriorityHint.LATENCY, snapshot.priority)
        assertEquals(signal, snapshot.signal)
        assertEquals(50_000, snapshot.bandwidthKbps?.downKbps)
        assertEquals(10_000, snapshot.bandwidthKbps?.upKbps)
        assertEquals(false, snapshot.roaming)
        assertNotNull(snapshot.link)
    }
}
