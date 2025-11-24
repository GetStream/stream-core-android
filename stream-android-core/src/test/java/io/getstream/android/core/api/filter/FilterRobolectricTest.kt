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

package io.getstream.android.core.api.filter

import android.os.Build
import io.getstream.android.core.api.model.location.CircularRegion
import io.getstream.android.core.api.model.location.LocationCoordinate
import io.getstream.android.core.api.model.location.kilometers
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class FilterRobolectricTest {

    private val centerCoordinate = LocationCoordinate(latitude = 41.8900, longitude = 12.4900)
    private val nearbyCoordinate = LocationCoordinate(latitude = 41.8920, longitude = 12.4920)
    private val farCoordinate = LocationCoordinate(latitude = 42.8900, longitude = 12.4900)

    @Test
    fun `equal filter should match when LocationCoordinate is within CircularRegion`() {
        val region = CircularRegion(center = centerCoordinate, radius = 5.kilometers)
        val filter = testFilterField.equal(region)

        assertTrue(filter matches TestData(location = nearbyCoordinate))
        assertFalse(filter matches TestData(location = farCoordinate))
    }

    @Test
    fun `equal filter should work with circular region map format from API`() {
        val mapRegion = mapOf("lat" to 41.8920, "lng" to 12.4920, "distance" to 5.0)
        val filter = testFilterField.equal(mapRegion)

        assertTrue(filter matches TestData(location = centerCoordinate))
        assertFalse(filter matches TestData(location = farCoordinate))
    }

    private data class TestData(val location: LocationCoordinate)

    private val testFilterField =
        object : FilterField<TestData> {
            override val remote: String = "location"
            override val localValue: (TestData) -> Any? = TestData::location
        }
}
