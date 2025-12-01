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

package io.getstream.android.core.internal.filter

import io.getstream.android.core.api.model.location.BoundingBox
import io.getstream.android.core.api.model.location.CircularRegion
import io.getstream.android.core.api.model.location.LocationCoordinate
import io.getstream.android.core.api.model.location.contains
import io.getstream.android.core.api.model.location.kilometers

internal object FilterOperations {
    infix fun Any.equal(that: Any) = this == that || isNear(that) || isWithinBoundsOf(that)

    infix fun Any.greater(that: Any) = anyCompare(this, that)?.let { it > 0 } == true

    infix fun Any.greaterOrEqual(that: Any) = anyCompare(this, that)?.let { it >= 0 } == true

    infix fun Any.less(that: Any) = anyCompare(this, that)?.let { it < 0 } == true

    infix fun Any.lessOrEqual(that: Any) = anyCompare(this, that)?.let { it <= 0 } == true

    private fun anyCompare(a: Any, b: Any): Int? {
        if (a !is Comparable<*>) {
            return null
        }

        return try {
            @Suppress("UNCHECKED_CAST") (a as Comparable<Any>).compareTo(b)
        } catch (_: ClassCastException) {
            // The types were not compatible for comparison
            null
        }
    }

    infix fun Any?.exists(that: Any): Boolean = (that is Boolean) && (this != null) == that

    infix fun Any.`in`(that: Any): Boolean =
        when (that) {
            is Array<*> -> this in that
            is Iterable<*> -> this in that
            else -> false
        }

    // Not called "contains" to avoid overloading `Any.contains`, which is too broad
    infix fun Any.doesContain(that: Any): Boolean =
        when {
            that `in` this -> true

            this is Map<*, *> && that is Map<*, *> -> {
                // Partial match: check if all entries in 'that' are present in 'this'
                that.all { (thatKey, thatValue) ->
                    val thisValue = this[thatKey]

                    thisValue == thatValue ||
                        thisValue != null && thatValue != null && thisValue doesContain thatValue
                }
            }

            else -> false
        }

    private val whitespaceAndPunctuation = Regex("[\\s\\p{Punct}]+")

    infix fun Any.autocompletes(that: Any): Boolean {
        if (this !is String || that !is String || that.isEmpty()) {
            return false
        }

        // Split the text into words using whitespace and punctuation as delimiters
        return this.split(whitespaceAndPunctuation).any { word -> word.startsWith(that, true) }
    }

    fun search(what: Any, where: Any): Boolean =
        what is String &&
            where is String &&
            what.isNotEmpty() &&
            where.contains(what, ignoreCase = true)

    infix fun Any.containsPath(that: Any): Boolean {
        if (this !is Map<*, *> || that !is String) return false

        val pathParts = that.split(".")
        var current: Any? = this

        for (part in pathParts) {
            when {
                current !is Map<*, *> -> return false
                part !in current -> return false
                else -> current = current[part]
            }
        }

        return true
    }

    private fun Any.isNear(that: Any): Boolean {
        if (this !is LocationCoordinate) return false

        return when (that) {
            is CircularRegion -> this in that
            is Map<*, *> -> {
                // Handle map format as expected by the API:
                // { "lat": 41.8904, "lng": 12.4922, "distance": 5.0 }
                val lat = (that["lat"] as? Number)?.toDouble() ?: return false
                val lng = (that["lng"] as? Number)?.toDouble() ?: return false
                val distanceKm = (that["distance"] as? Number)?.toDouble() ?: return false

                this in
                    CircularRegion(
                        center = LocationCoordinate(lat, lng),
                        radius = distanceKm.kilometers,
                    )
            }

            else -> false
        }
    }

    private fun Any.isWithinBoundsOf(that: Any): Boolean {
        if (this !is LocationCoordinate) return false

        return when (that) {
            is BoundingBox -> this in that
            is Map<*, *> -> {
                // Handle map format as expected by the API:
                // { "ne_lat": 41.9200, "ne_lng": 12.5200, "sw_lat": 41.8800, "sw_lng": 12.4700 }
                val neLat = (that["ne_lat"] as? Number)?.toDouble() ?: return false
                val neLng = (that["ne_lng"] as? Number)?.toDouble() ?: return false
                val swLat = (that["sw_lat"] as? Number)?.toDouble() ?: return false
                val swLng = (that["sw_lng"] as? Number)?.toDouble() ?: return false

                this in
                    BoundingBox(
                        northeast = LocationCoordinate(neLat, neLng),
                        southwest = LocationCoordinate(swLat, swLng),
                    )
            }

            else -> false
        }
    }
}
