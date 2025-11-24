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

import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * A rectangular geographic region defined by northeast and southwest corner coordinates.
 *
 * @param northeast The northeast (top-right) corner coordinate of the bounding box.
 * @param southwest The southwest (bottom-left) corner coordinate of the bounding box.
 */
@StreamPublishedApi
public data class BoundingBox(val northeast: LocationCoordinate, val southwest: LocationCoordinate)

/**
 * Checks if the specified coordinate is within this bounding box.
 *
 * @param coordinate The coordinate to check.
 * @return True if the coordinate is within the bounding box, false otherwise.
 */
internal operator fun BoundingBox.contains(coordinate: LocationCoordinate): Boolean {
    return coordinate.latitude >= southwest.latitude &&
        coordinate.latitude <= northeast.latitude &&
        coordinate.longitude >= southwest.longitude &&
        coordinate.longitude <= northeast.longitude
}

/** Converts this bounding box to a map representation suitable for API requests. */
internal fun BoundingBox.toRequestMap(): Map<String, Any> =
    mapOf(
        "ne_lat" to northeast.latitude,
        "ne_lng" to northeast.longitude,
        "sw_lat" to southwest.latitude,
        "sw_lng" to southwest.longitude,
    )
