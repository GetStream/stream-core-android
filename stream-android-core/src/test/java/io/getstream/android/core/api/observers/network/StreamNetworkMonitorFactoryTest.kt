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
package io.getstream.android.core.api.observers.network

import android.net.ConnectivityManager
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.observers.network.StreamNetworkMonitorImpl
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertTrue

internal class StreamNetworkMonitorFactoryTest {

    @Test
    fun `factory creates monitor instance`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val subscriptionManager = mockk<StreamSubscriptionManager<StreamNetworkMonitorListener>>(relaxed = true)
        val scope = TestScope(StandardTestDispatcher())
        val connectivityManager = mockk<ConnectivityManager>()

        val monitor =
            StreamNetworkMonitor(
                logger = logger,
                scope = scope,
                subscriptionManager = subscriptionManager,
                wifiManager = mockk(relaxed = true),
                telephonyManager = mockk(relaxed = true),
                connectivityManager = connectivityManager,
            )

        assertTrue(monitor is StreamNetworkMonitorImpl)
        assertNotNull(monitor)
    }
}
