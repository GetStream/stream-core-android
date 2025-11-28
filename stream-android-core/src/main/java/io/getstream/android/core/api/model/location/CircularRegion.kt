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

package io.getstream.android.core.api.model.location

import android.location.Location
import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * A circular geographic region defined by a center point and a radius.
 *
 * @param center The center coordinate of the circular region.
 * @param radius The radius of the circular region.
 */
@StreamPublishedApi
public data class CircularRegion(val center: LocationCoordinate, val radius: Distance)

/**
 * Checks if the specified coordinate is within this circular region.
 *
 * @param coordinate The coordinate to check.
 * @return True if the coordinate is within the region, false otherwise.
 */
internal operator fun CircularRegion.contains(coordinate: LocationCoordinate): Boolean {
    val centerLocation =
        Location("").apply {
            latitude = this@contains.center.latitude
            longitude = this@contains.center.longitude
        }
    val coordinateLocation =
        Location("").apply {
            latitude = coordinate.latitude
            longitude = coordinate.longitude
        }
    val distance = centerLocation.distanceTo(coordinateLocation).toDouble()
    return distance <= this@contains.radius.inMeters
}

/** Converts this circular region to a map representation suitable for API requests. */
internal fun CircularRegion.toRequestMap(): Map<String, Any> =
    mapOf("lat" to center.latitude, "lng" to center.longitude, "distance" to radius.inKilometers)
