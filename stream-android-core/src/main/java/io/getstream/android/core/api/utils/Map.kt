/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-android-base/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.getstream.android.core.api.utils

import io.getstream.android.core.annotations.StreamCoreApi
import java.util.concurrent.ConcurrentMap

/**
 * Computes the value for the given key if it is not already present in the map.
 *
 * @param key The key to compute the value for.
 * @param defaultValue The function to compute the value.
 * @return The value for the given key.
 */
@StreamCoreApi
inline fun <K, V> ConcurrentMap<K, V>.streamComputeIfAbsent(key: K, defaultValue: () -> V): V {
    this[key]?.let {
        return it
    }
    val newValue = defaultValue()
    val prev = putIfAbsent(key, newValue)
    return prev ?: newValue
}
