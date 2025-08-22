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
package io.getstream.android.core.api.processing

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.internal.processing.StreamSerialProcessingQueueImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel

/**
 * A simple **serial execution** queue for suspending tasks.
 *
 * Implementations guarantee that submitted jobs are executed **one-by-one** in the order they were
 * accepted (FIFO), never concurrently. This is useful for protecting critical sections, enforcing
 * request ordering, or simplifying stateful operations that are not thread-safe when run in
 * parallel.
 *
 * ### Semantics
 * - **Serialization:** Only one job runs at a time.
 * - **Ordering:** Jobs are executed in the order they are successfully submitted.
 * - **Result delivery:** The return value is wrapped in [Result]; failures are surfaced as
 *   [Result.failure] rather than throwing.
 * - **Backpressure:** If the queue is full (implementation-defined capacity), `submit` will suspend
 *   until space is available or the queue is stopped.
 * - **Stop behavior:** Calling [stop] makes the queue **unavailable** for further submissions and
 *   **cancels** the currently running job (if any). Any jobs that are queued but not yet started
 *   are failed.
 *
 * ### Cancellation
 * - Cancelling the *caller* of [submit] only cancels the suspension while waiting for the result;
 *   the job may still run if it was already accepted by the queue (implementation-specific). Call
 *   [stop] to cancel/flush work at the queue level.
 */
@StreamCoreApi
interface StreamSerialProcessingQueue {
    /**
     * Submits a suspending [job] for **serialized** execution.
     *
     * If the processor is not running, it will be started automatically by the implementation. The
     * job will run exclusively (no other jobs run in parallel) and in FIFO order relative to other
     * accepted jobs.
     *
     * @param T The job's success result type.
     * @param job The suspending block to execute on the queue's worker.
     * @return A [Result] containing the job's return value on success, or the failure cause.
     *
     * ### Behavior
     * - **Backpressure:** May suspend if the internal queue is at capacity.
     * - **Cancellation:** Cancelling the awaiting caller does not necessarily cancel the enqueued
     *   job once accepted (implementation-specific). Use [stop] to cancel at the queue level.
     * - **Exceptions:** Exceptions thrown by [job] are captured and returned via [Result.failure].
     */
    suspend fun <T : Any> submit(job: suspend () -> T): Result<T>

    /**
     * Starts the processor if it's not already running.
     *
     * @return `Result.success(Unit)` if the processor was started successfully; otherwise a
     *   `Result.failure(cause)` describing why the start failed.
     */
    suspend fun start(): Result<Unit>

    /**
     * Stops the processor and **fails** outstanding work.
     *
     * After calling this:
     * - **No new jobs** are accepted (subsequent [submit] calls should fail fast).
     * - The **currently running job** (if any) is **cancelled** and completed with failure.
     * - All **queued but not yet started jobs** are completed with failure.
     * - The queue is **not reusable** unless the implementation documents otherwise.
     *
     * @param timeout The maximum time to wait for the running job to complete before
     *   hard-cancelling the processor.
     * @return [Result.success] if the processor was stopped successfully; otherwise a
     *   [Result.failure] describing why the stop failed.
     */
    suspend fun stop(timeout: Long? = null): Result<Unit>
}

/**
 * Creates a new [StreamSerialProcessingQueue] instance.
 *
 * @param logger The logger to use for logging.
 * @param scope The coroutine scope to use for running the processor.
 * @param autoStart Whether to automatically start the processor when a job is submitted.
 * @param startMode The coroutine start mode to use when starting the processor.
 * @param capacity The capacity of the internal queue.
 * @return A new [StreamSerialProcessingQueue] instance.
 */
@StreamCoreApi
fun StreamSerialProcessingQueue(
    logger: StreamLogger,
    scope: CoroutineScope,
    autoStart: Boolean = true,
    startMode: CoroutineStart = CoroutineStart.DEFAULT,
    capacity: Int = Channel.BUFFERED,
): StreamSerialProcessingQueue =
    StreamSerialProcessingQueueImpl(
        logger = logger,
        scope = scope,
        autoStart = autoStart,
        startMode = startMode,
        capacity = capacity,
    )
