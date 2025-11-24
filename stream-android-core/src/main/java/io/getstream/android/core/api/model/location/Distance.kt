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
 * Represents a distance measurement with type safety and automatic unit conversion.
 *
 * @param inMeters The distance value stored internally in meters.
 */
@JvmInline
@StreamPublishedApi
public value class Distance private constructor(public val inMeters: Double) {

    /** Returns the distance value in kilometers. */
    public val inKilometers: Double
        get() = inMeters / 1000.0

    internal companion object {
        fun fromMeters(meters: Double): Distance = Distance(meters)
    }
}

/**
 * Extension property to create a [Distance] from a [Double] value in meters.
 *
 * ## Example
 *
 * ```kotlin
 * val distance = 1500.2.meters  // 1500.2 meters
 * ```
 */
@StreamPublishedApi
public val Double.meters: Distance
    get() = Distance.fromMeters(this)

/**
 * Extension property to create a [Distance] from a [Double] value in kilometers.
 *
 * ## Example
 *
 * ```kotlin
 * val distance = 1.5.kilometers  // 1.5 kilometers
 * ```
 */
@StreamPublishedApi
public val Double.kilometers: Distance
    get() = Distance.fromMeters(this * 1000.0)

/**
 * Extension property to create a [Distance] from an [Int] value in meters.
 *
 * ## Example
 *
 * ```kotlin
 * val distance = 1500.meters  // 1500 meters
 * ```
 */
@StreamPublishedApi
public val Int.meters: Distance
    get() = toDouble().meters

/**
 * Extension property to create a [Distance] from an [Int] value in kilometers.
 *
 * ## Example
 *
 * ```kotlin
 * val distance = 5.kilometers  // 5 kilometers
 * ```
 */
@StreamPublishedApi
public val Int.kilometers: Distance
    get() = toDouble().kilometers
