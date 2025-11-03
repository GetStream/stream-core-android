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

import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
internal class StreamNetworkSignalProcessingLegacyApiTest {

    @MockK(relaxed = true) lateinit var wifiManager: WifiManager
    @MockK(relaxed = true) lateinit var telephonyManager: TelephonyManager

    private lateinit var processing: StreamNetworkSignalProcessing

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { wifiManager.connectionInfo } returns null
        processing = StreamNetworkSignalProcessing()
    }

    @Test
    fun `best effort signal returns null on legacy devices`() {
        val signal =
            processing.bestEffortSignal(
                wifiManager,
                telephonyManager,
                capabilities = null,
                transports = setOf(Transport.WIFI, Transport.CELLULAR),
            )

        assertNull(signal)
    }
}
