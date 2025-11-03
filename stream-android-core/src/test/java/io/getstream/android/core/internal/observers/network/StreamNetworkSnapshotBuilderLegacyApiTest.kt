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

import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTime::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
internal class StreamNetworkSnapshotBuilderLegacyApiTest {

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
    fun `build returns null for legacy api`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        val snapshot = builder.build(network, capabilities, linkProperties = null).getOrThrow()

        assertNull(snapshot)
    }
}
