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
package io.getstream.android.core.internal.processing

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.utils.runCatchingCancellable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
internal class StreamSerialProcessingQueueImpl(
    private val logger: StreamLogger,
    private val scope: CoroutineScope,
    private val autoStart: Boolean = true,
    private val startMode: CoroutineStart = CoroutineStart.DEFAULT,
    capacity: Int = Channel.BUFFERED,
) : StreamSerialProcessingQueue {

    private data class JobItem<R>(
        val block: suspend () -> R,
        val reply: CompletableDeferred<Result<R>>,
    )

    private val inbox = StreamRestartableChannel<JobItem<*>>(capacity)
    private val stopped = AtomicBoolean(false)
    private var worker: Job? = null
    private val startMutex = Mutex()
    private val stopMutex = Mutex()

    override suspend fun start(): Result<Unit> = runCatchingCancellable {
        if (worker == null) {
            startMutex.withLock {
                if (worker == null) {
                    worker = scope.launch(start = startMode) { runWorker() }
                }
            }
        }
    }

    override suspend fun <T : Any> submit(job: suspend () -> T): Result<T> =
        runCatchingCancellable {
            if (autoStart) {
                start().getOrThrow()
            }
            if (stopped.get() || !scope.isActive) {
                throw ClosedSendChannelException("[SerialProcessingQueue] stopped")
            }

            val reply = CompletableDeferred<Result<T>>()
            inbox.send(JobItem(block = job, reply = reply))
            return reply.await()
        }

    override suspend fun stop(timeout: Long?): Result<Unit> = runCatchingCancellable {
        if (!stopped.compareAndSet(false, true)) {
            return Result.success(Unit)
        }

        inbox.close(null)

        val closeCause = ClosedSendChannelException("[SerialProcessingQueue] stopped")
        drainAndFailPending(closeCause)

        if (timeout == null) {
            worker?.cancel()
            return@runCatchingCancellable
        }

        // cancel running job & wait (with timeout) for worker to finish
        stopMutex.withLock {
            if (worker != null) {
                withTimeoutOrNull(timeout) { worker!!.join() }
                worker!!.cancel()
                worker = null
            }
        }
    }

    private suspend fun runWorker() {
        try {
            for (msg in inbox) {
                val item = msg as JobItem<Any?>
                val res =
                    try {
                        Result.success(item.block())
                    } catch (ce: CancellationException) {
                        // Job-level cancel: propagate to caller as failure,
                        // but DO NOT cancel the whole queue.
                        Result.failure(ce)
                    } catch (t: Throwable) {
                        Result.failure(t)
                    }
                item.reply.complete(res)
            }
        } catch (t: Throwable) {
            // Catastrophic failure or canceled
            inbox.close(null)
            drainAndFailPending(t)
            throw t
        }
    }

    private fun drainAndFailPending(cause: Throwable) {
        while (true) {
            val v = inbox.tryReceive().getOrNull() ?: break
            @Suppress("UNCHECKED_CAST") (v as JobItem<Any?>).reply.complete(Result.failure(cause))
        }
    }
}
