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

import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Signal
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
internal class StreamNetworkSignalProcessingTest {

    @MockK(relaxed = true) lateinit var wifiManager: WifiManager
    @MockK(relaxed = true) lateinit var telephonyManager: TelephonyManager

    private lateinit var processing: StreamNetworkSignalProcessing

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this)
        processing = StreamNetworkSignalProcessing()
    }

    @AfterTest
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `bestEffortSignal returns wifi signal when wifi transport available`() {
        val wifiInfo =
            mockk<WifiInfo> {
                every { rssi } returns -45
                every { ssid } returns "\"Stream\""
                every { bssid } returns "00:11:22:33:44:55"
                every { frequency } returns 5200
            }
        every { wifiManager.connectionInfo } returns wifiInfo

        val signal =
            processing.bestEffortSignal(
                wifiManager = wifiManager,
                telephonyManager = telephonyManager,
                capabilities = null,
                transports = setOf(Transport.WIFI),
            )

        val wifiSignal = assertIs<Signal.Wifi>(signal)
        assertEquals(-45, wifiSignal.rssiDbm)
        assertEquals("Stream", wifiSignal.ssid)
        assertEquals("00:11:22:33:44:55", wifiSignal.bssid)
        assertEquals(5200, wifiSignal.frequencyMhz)
    }

    @Test
    fun `bestEffortSignal returns null when no transports`() {
        val signal =
            processing.bestEffortSignal(
                wifiManager,
                telephonyManager,
                capabilities = null,
                transports = emptySet(),
            )

        assertNull(signal)
    }
}
