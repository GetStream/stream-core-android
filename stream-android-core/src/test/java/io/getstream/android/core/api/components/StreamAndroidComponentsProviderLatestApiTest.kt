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
package io.getstream.android.core.api.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class StreamAndroidComponentsProviderLatestApiTest {

    private lateinit var context: Context
    private lateinit var provider: StreamAndroidComponentsProvider

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = StreamAndroidComponentsProvider(context)
    }

    @Test
    fun `connectivity manager is returned from application context`() {
        val expected = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val result = provider.connectivityManager()

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `wifi manager is returned from application context`() {
        val expected = context.applicationContext.getSystemService(WifiManager::class.java)
        val result = provider.wifiManager()

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `telephony manager is returned from application context`() {
        val expected = context.applicationContext.getSystemService(TelephonyManager::class.java)
        val result = provider.telephonyManager()

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }
}
