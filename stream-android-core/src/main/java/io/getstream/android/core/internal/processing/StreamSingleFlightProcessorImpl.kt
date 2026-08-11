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

import io.getstream.android.core.api.model.StreamTypedKey
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException

/**
 * SingleFlight implementation that coalesces concurrent calls by key.
 *
 * The shared work runs in [scope] (recommend a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`),
 * so cancelling one awaiting caller does not cancel the shared execution.
 */
internal class StreamSingleFlightProcessorImpl(
    private val scope: CoroutineScope,
    private val flights: ConcurrentMap<Any, Deferred<Result<*>>> = ConcurrentHashMap(),
) : StreamSingleFlightProcessor {

    private val closed = AtomicBoolean(false)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> run(key: StreamTypedKey<T>, block: suspend () -> T): Result<T> {
        if (closed.get()) {
            return Result.failure(ClosedSendChannelException("SingleFlight is closed"))
        }

        // Fast path: already in flight
        flights[key]?.let { existing ->
            return try {
                existing.await() as Result<T>
            } catch (t: Throwable) {
                // If the deferred itself was cancelled, surface as failure Result
                Result.failure(t)
            }
        }

        // Create the shared execution (lazy): only the winner starts it.
        val newExecution =
            scope.async(start = CoroutineStart.LAZY) {
                // Always produce a Result (success/failure). Awaiters never throw for block
                // failures.
                try {
                    Result.success(block())
                } catch (ce: CancellationException) {
                    Result.failure(ce)
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }

        // Install ours or join the winner
        val existing = flights.putIfAbsent(key, newExecution)
        val job =
            if (existing != null) {
                // We lost the race. `scope.async` attached this LAZY deferred to `scope`'s Job
                // at construction, so leaving it unstarted keeps an inert child (holding `block`
                // and its captures) attached for the scope's lifetime. Cancel to detach it.
                newExecution.cancel()
                existing
            } else {
                // Evict on completion of the shared work itself, never from an awaiting
                // caller's frame. The work is detached in [scope], so a cancelled installer
                // whose finally removed the entry would leave the job still running while the
                // map went empty — the next caller for this key would miss and start a second
                // execution. Conditional remove leaves any newer flight that replaced us intact.
                newExecution.invokeOnCompletion { flights.remove(key, newExecution) }
                newExecution.also { it.start() }
            }

        return try {
            job.await() as Result<T>
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override fun <T> has(key: StreamTypedKey<T>): Boolean {
        return flights.containsKey(key)
    }

    override fun <T> cancel(key: StreamTypedKey<T>): Result<Unit> = runCatching {
        flights[key]?.cancel()
    }

    override fun clear(cancelRunning: Boolean): Result<Unit> = runCatching {
        if (cancelRunning) {
            // Cancel current refs; we don't await. Callers will see failure on await.
            flights.values.forEach { it.cancel() }
        }
        flights.clear()
    }

    override fun stop(): Result<Unit> = runCatching {
        // Idempotent: first caller flips to closed and cancels all; others no-op
        if (closed.compareAndSet(false, true)) {
            clear(cancelRunning = true).getOrThrow()
        }
    }
}
