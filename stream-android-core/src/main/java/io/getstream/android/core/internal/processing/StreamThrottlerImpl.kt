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
import io.getstream.android.core.api.processing.StreamThrottlePolicy
import io.getstream.android.core.api.processing.StreamThrottler
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Throttler implementation that supports [Leading][StreamThrottlePolicy.Leading],
 * [Trailing][StreamThrottlePolicy.Trailing], and
 * [LeadingAndTrailing][StreamThrottlePolicy.LeadingAndTrailing] strategies.
 *
 * A shared window-expiry job tracks the active coroutine across all modes. Resetting cancels it to
 * prevent stale coroutines from prematurely closing a new window.
 */
internal class StreamThrottlerImpl<T>(
    private val scope: CoroutineScope,
    private val logger: StreamLogger,
    private val policy: StreamThrottlePolicy,
) : StreamThrottler<T> {

    private val windowActive = AtomicBoolean(false)
    private val callbackRef = AtomicReference<suspend (T) -> Unit> {}
    private val trailingValue = AtomicReference<T?>(null)
    private val windowJob = AtomicReference<Job?>(null)

    override fun onValue(callback: suspend (T) -> Unit) {
        callbackRef.set(callback)
    }

    override fun submit(value: T): Boolean =
        when (policy) {
            is StreamThrottlePolicy.Leading -> submitLeading(value)
            is StreamThrottlePolicy.Trailing -> submitTrailing(value)
            is StreamThrottlePolicy.LeadingAndTrailing -> submitLeadingAndTrailing(value)
        }

    override fun reset() {
        windowJob.getAndSet(null)?.cancel()
        trailingValue.set(null)
        windowActive.set(false)
    }

    private fun submitLeading(value: T): Boolean {
        val accepted = windowActive.compareAndSet(false, true)
        if (!accepted) {
            logger.v { "[throttle:leading] Dropped: $value" }
        } else {
            logger.v { "[throttle:leading] Accepted: $value" }
            scope.launch { callbackRef.get().invoke(value) }
            scheduleWindowExpiry(deliverTrailing = false)
        }
        return accepted
    }

    private fun submitTrailing(value: T): Boolean {
        trailingValue.set(value)
        if (windowActive.compareAndSet(false, true)) {
            logger.v { "[throttle:trailing] Window started, pending: $value" }
            scheduleWindowExpiry(deliverTrailing = true)
        } else {
            logger.v { "[throttle:trailing] Updated pending: $value" }
        }
        return true
    }

    private fun submitLeadingAndTrailing(value: T): Boolean {
        val isLeading = windowActive.compareAndSet(false, true)
        if (isLeading) {
            logger.v { "[throttle:leading+trailing] Leading: $value" }
            trailingValue.set(null)
            scope.launch { callbackRef.get().invoke(value) }
            scheduleWindowExpiry(deliverTrailing = true)
        } else {
            logger.v { "[throttle:leading+trailing] Pending trailing: $value" }
            trailingValue.set(value)
        }
        return true
    }

    private fun scheduleWindowExpiry(deliverTrailing: Boolean) {
        val job =
            scope.launch {
                delay(policy.windowMs)
                windowActive.set(false)
                if (deliverTrailing) {
                    val pending = trailingValue.getAndSet(null)
                    if (pending != null) {
                        logger.v { "[throttle] Trailing delivery: $pending" }
                        callbackRef.get().invoke(pending)
                    }
                }
            }
        windowJob.getAndSet(job)?.cancel()
    }
}
