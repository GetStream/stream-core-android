/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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
import io.getstream.android.core.api.processing.StreamDebouncer
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class StreamDebouncerImpl<T>(
    private val scope: CoroutineScope,
    private val logger: StreamLogger,
    private val delayMs: Long,
) : StreamDebouncer<T> {
    private val pendingJob = AtomicReference<Job?>(null)
    private val pendingValue = AtomicReference<T?>(null)
    private var callback: suspend (T) -> Unit = {}

    override fun onValue(callback: suspend (T) -> Unit) {
        this.callback = callback
    }

    override fun submit(value: T) {
        pendingValue.set(value)
        val oldJob = pendingJob.get()
        val newJob =
            scope.launch {
                delay(delayMs)
                val settled = pendingValue.getAndSet(null) ?: return@launch
                logger.v { "[debounce] Delivering settled value: $settled" }
                callback(settled)
            }
        if (!pendingJob.compareAndSet(oldJob, newJob)) {
            newJob.cancel()
            pendingValue.get()?.let { submit(it) }
            return
        }
        oldJob?.cancel()
    }

    override fun cancel() {
        pendingJob.getAndSet(null)?.cancel()
        pendingValue.set(null)
    }
}
