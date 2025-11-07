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

import io.getstream.android.core.api.processing.StreamBatcher
import io.getstream.android.core.api.utils.runCatchingCancellable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class StreamBatcherImpl<T>(
    private val scope: CoroutineScope,
    private val batchSize: Int = 10,
    private val initialDelayMs: Long = 100L,
    private val maxDelayMs: Long = 1_000L,
    private val autoStart: Boolean = true,
    channelCapacity: Int = Channel.UNLIMITED,
) : StreamBatcher<T> {
    private val inbox = StreamRestartableChannel<T>(channelCapacity)
    private val started = AtomicBoolean(false)
    private var worker: Job? = null
    private val startMutex = Mutex()

    private var batchHandler:
        suspend (batch: List<T>, windowAppliedMs: Long, emittedCount: Int) -> Unit =
        { _, _, _ -> /* no-op until set */
        }

    override fun onBatch(handler: suspend (List<T>, Long, Int) -> Unit) {
        batchHandler = handler
    }

    override suspend fun start(): Result<Unit> = runCatchingCancellable {
        startMutex.withLock {

            // Restart the channel
            inbox.start()

            // Start the worker
            if (worker == null) {
                // Start immediately on the caller thread; safe because we first suspend on
                // receive().
                worker = scope.launch(start = CoroutineStart.UNDISPATCHED) { runWorker() }
            }
        }
    }

    override suspend fun enqueue(item: T): Result<Unit> = runCatchingCancellable {
        if (!scope.isActive) {
            return Result.failure(IllegalStateException("Processor scope is cancelled"))
        }
        if (autoStart) {
            start()
        }
        inbox.send(item)
    }

    override fun offer(item: T): Boolean {
        if (autoStart) {
            inbox.start()
            val locked = startMutex.tryLock(this)
            if (locked && worker == null) {
                // Start immediately on the caller thread; safe because we first suspend on
                // receive().
                worker = scope.launch(start = CoroutineStart.UNDISPATCHED) { runWorker() }
            }
            if (locked) {
                startMutex.unlock()
            }
        }
        return inbox.trySend(item).isSuccess
    }

    override fun stop(): Result<Unit> = runCatching {
        inbox.close()
        worker?.cancel()
        worker = null
        started.set(false)
    }

    private suspend fun runWorker() = runCatchingCancellable {
        val buffer = ArrayList<T>(batchSize)
        var windowMs = initialDelayMs

        try {
            while (scope.isActive) {
                // Wait for first item of a batch
                val first = inbox.receive()
                buffer += first
                val startNs = System.nanoTime()

                // Collect until batch full or time window elapsed
                collectUntilTimeout(buffer, startNs, windowMs)

                // Emit snapshot
                val wasFull = buffer.size >= batchSize
                val snapshot = buffer.toList()
                batchHandler(snapshot, windowMs, snapshot.size)
                buffer.clear()

                // Adjust window (backoff if full) and apply debounce gap
                windowMs =
                    if (wasFull) {
                        backoff(windowMs)
                    } else {
                        initialDelayMs
                    }
                delay(windowMs)
            }
        } catch (_: ClosedReceiveChannelException) {
            // graceful shutdown
        }
    }

    private fun backoff(currentMs: Long): Long = min(currentMs * 2, maxDelayMs)

    /** Pull until batch is full or the remaining window elapses (monotonic time). */
    private suspend fun collectUntilTimeout(buffer: MutableList<T>, startNs: Long, windowMs: Long) {
        while (buffer.size < batchSize) {
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            val remaining = windowMs - elapsedMs
            val next = withTimeoutOrNull(remaining) { inbox.receive() }
            if (next != null) {
                buffer += next
            } else {
                break
            }
        }
    }
}
