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
 * Represents a geographic coordinate with latitude and longitude values.
 *
 * This class is used to represent location data in geo-spatial filtering operations.
 *
 * @param latitude The latitude coordinate in degrees. Must be between -90 and 90.
 * @param longitude The longitude coordinate in degrees. Must be between -180 and 180.
 */
@StreamPublishedApi
public data class LocationCoordinate(val latitude: Double, val longitude: Double)
