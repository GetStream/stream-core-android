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

package io.getstream.android.core.internal.subscribe.utils

import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import kotlinx.coroutines.runBlocking

/**
 * Iterates over all listeners, invoking [block] for each one. This is a convenience wrapper over
 * [StreamSubscriptionManager#forEach] that allows suspending [block]s.
 *
 * @see StreamSubscriptionManager#forEach
 */
internal fun <T> StreamSubscriptionManager<T>.forEachSuspend(block: suspend (T) -> Unit) {
    this.forEach { runBlocking { block(it) } }
}
