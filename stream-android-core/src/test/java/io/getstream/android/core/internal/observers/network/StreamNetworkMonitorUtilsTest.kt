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
import android.os.Build
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
internal class StreamNetworkMonitorUtilsTest {

    @MockK(relaxed = true) lateinit var capabilities: NetworkCapabilities

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `safeHasCapability returns value or null on error`() {
        every { capabilities.hasCapability(1) } returns true
        assertTrue(capabilities.safeHasCapability(1) == true)

        every { capabilities.hasCapability(2) } throws SecurityException("boom")
        assertNull(capabilities.safeHasCapability(2))
    }

    @Test
    fun `safeHasTransport returns value or null on error`() {
        every { capabilities.hasTransport(1) } returns true
        assertTrue(capabilities.safeHasTransport(1) == true)

        every { capabilities.hasTransport(2) } throws SecurityException("boom")
        assertNull(capabilities.safeHasTransport(2))
    }
}
