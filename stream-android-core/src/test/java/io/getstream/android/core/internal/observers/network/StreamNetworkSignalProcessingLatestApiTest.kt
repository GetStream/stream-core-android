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

import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Signal
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class StreamNetworkSignalProcessingLatestApiTest {

    @MockK(relaxed = true) lateinit var wifiManager: WifiManager
    @MockK(relaxed = true) lateinit var telephonyManager: TelephonyManager

    private lateinit var processing: StreamNetworkSignalProcessing

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        processing = StreamNetworkSignalProcessing()
    }

    @AfterTest
    fun tearDown() {
        clearMocks(wifiManager, telephonyManager)
    }

    @Test
    fun `best effort prefers generic signal strength when available`() {
        val capabilities = mockk<NetworkCapabilities>(relaxed = true)
        every { capabilities.signalStrength } returns 120

        val signal =
            processing.bestEffortSignal(wifiManager, telephonyManager, capabilities, emptySet())

        assertIs<Signal.Generic>(signal)
        assertEquals(120, signal.value)
    }

    @Test
    fun `wifi signal is mapped when wifi transport present`() {
        val wifiInfo = mockk<WifiInfo>(relaxed = true)
        every { wifiInfo.rssi } returns -50
        every { wifiInfo.ssid } returns "\"TestWifi\""
        every { wifiInfo.bssid } returns "01:23:45:67:89:ab"
        every { wifiInfo.frequency } returns 2412
        every { wifiManager.connectionInfo } returns wifiInfo

        val signal =
            processing.bestEffortSignal(
                wifiManager,
                telephonyManager,
                capabilities = null,
                transports = setOf(Transport.WIFI),
            )

        val wifi = assertIs<Signal.Wifi>(signal)
        assertEquals(-50, wifi.rssiDbm)
        assertEquals("TestWifi", wifi.ssid)
        assertEquals("01:23:45:67:89:ab", wifi.bssid)
        assertNotNull(wifi.level0to4)
        assertEquals(2412, wifi.frequencyMhz)
    }

    @Test
    fun `cellular signal prefers NR metrics and falls back to LTE`() {
        val nrStrength = mockk<CellSignalStrengthNr>(relaxed = true)
        every { nrStrength.ssRsrp } returns -95
        every { nrStrength.ssRsrq } returns -10
        every { nrStrength.ssSinr } returns 20

        val lteStrength = mockk<CellSignalStrengthLte>(relaxed = true)
        every { lteStrength.rsrp } returns -110
        every { lteStrength.rsrq } returns -12
        every { lteStrength.rssnr } returns 5

        val strength = mockk<SignalStrength>(relaxed = true)
        every { strength.level } returns 3
        every { strength.cellSignalStrengths } returns listOf(nrStrength, lteStrength)
        every { telephonyManager.signalStrength } returns strength

        val signal =
            processing.bestEffortSignal(
                wifiManager,
                telephonyManager,
                capabilities = null,
                transports = setOf(Transport.CELLULAR),
            )

        val cellular = assertIs<Signal.Cellular>(signal)
        assertEquals("NR", cellular.rat)
        assertEquals(3, cellular.level0to4)
        assertEquals(-95, cellular.rsrpDbm)
        assertEquals(-10, cellular.rsrqDb)
        assertEquals(20, cellular.sinrDb)
    }

    @Test
    fun `cellular signal falls back when only LTE metrics available`() {
        val lteStrength = mockk<CellSignalStrengthLte>(relaxed = true)
        every { lteStrength.rsrp } returns -105
        every { lteStrength.rsrq } returns -11
        every { lteStrength.rssnr } returns 6

        val strength = mockk<SignalStrength>(relaxed = true)
        every { strength.level } returns 2
        every { strength.cellSignalStrengths } returns listOf(lteStrength)
        every { telephonyManager.signalStrength } returns strength

        val signal = processing.cellularSignal(telephonyManager)

        val cellular = assertIs<Signal.Cellular>(signal)
        assertEquals("LTE", cellular.rat)
        assertEquals(2, cellular.level0to4)
        assertEquals(-105, cellular.rsrpDbm)
        assertEquals(-11, cellular.rsrqDb)
        assertEquals(6, cellular.sinrDb)
    }

    @Test
    fun `cellular signal returns generic level when no specific RAT present`() {
        val strength = mockk<SignalStrength>(relaxed = true)
        every { strength.level } returns 1
        every { strength.cellSignalStrengths } returns emptyList()
        every { telephonyManager.signalStrength } returns strength

        val signal = processing.cellularSignal(telephonyManager)

        val cellular = assertIs<Signal.Cellular>(signal)
        assertNull(cellular.rat)
        assertEquals(1, cellular.level0to4)
        assertEquals(null, cellular.rsrpDbm)
    }
}
