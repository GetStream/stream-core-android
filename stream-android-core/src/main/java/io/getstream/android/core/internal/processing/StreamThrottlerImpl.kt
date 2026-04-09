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

package io.getstream.android.core.internal.processing

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.processing.StreamThrottler
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Leading-edge throttler: delivers the first value immediately, drops all subsequent values until
 * [windowMs] has elapsed, then allows the next value through.
 */
internal class StreamThrottlerImpl<T>(
    private val scope: CoroutineScope,
    private val logger: StreamLogger,
    private val windowMs: Long,
) : StreamThrottler<T> {

    private val windowActive = AtomicBoolean(false)
    private val callbackRef = AtomicReference<suspend (T) -> Unit> {}

    override fun onValue(callback: suspend (T) -> Unit) {
        callbackRef.set(callback)
    }

    override fun submit(value: T): Boolean {
        val accepted = windowActive.compareAndSet(false, true)
        if (!accepted) {
            logger.v { "[throttle] Dropped value (window active): $value" }
        } else {
            logger.v { "[throttle] Accepted value: $value" }
            scope.launch { callbackRef.get().invoke(value) }
            scope.launch {
                delay(windowMs)
                windowActive.set(false)
            }
        }
        return accepted
    }

    override fun reset() {
        windowActive.set(false)
    }
}
