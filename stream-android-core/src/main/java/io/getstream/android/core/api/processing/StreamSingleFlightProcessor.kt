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
package io.getstream.android.core.api.processing

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.model.StreamTypedKey
import io.getstream.android.core.internal.processing.StreamSingleFlightProcessorImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * A concurrency construction that implements the **single-flight** pattern (à la Go's
 * `singleflight`): concurrent calls with the same [key] **share one in-flight execution** and all
 * await the same result.
 *
 * ### Semantics
 * - **One in-flight per key:** While a call for [key] is running, additional [run] calls with the
 *   same [key] do not start a new execution; they join the existing one.
 * - **Result-based API:** [run] never throws for work failures; it returns a [Result]. (The only
 *   exception is if the *awaiting caller* is cancelled—then a [CancellationException] is thrown to
 *   that caller, while the shared work continues for others.)
 * - **Cancellation:** [cancel] cancels the shared in-flight execution for [key] (if any); all
 *   joiners then observe `Result.failure(CancellationException)`. [clear] only clears bookkeeping
 *   and **does not** cancel.
 *
 * ### Usage
 * - Deduplicate idempotent reads (HTTP GETs, token fetches).
 * - Prevent duplicate work due to UI recomposition.
 *
 * ```kotlin
 *
 * fun getUser(userId: String) = singleFlight.run("GET:/users/$userId") {
 *       // This block runs once per in-flight key; additional callers await the same result.
 *       // Cancellation of one awaiting caller does not cancel the shared execution.
 *       httpClient.get("/users/$userId").body()
 * }
 * ```
 *
 * Doing a multiple calls to `getUser(101)` will only make one network call for that user.
 *
 * Implementations must be safe for concurrent use.
 */
@StreamCoreApi
interface StreamSingleFlightProcessor {
    /**
     * Runs [block], ensuring that **at most one execution** for [key] is in flight. Concurrent
     * callers with the same [key] await the same shared execution and receive its [Result].
     *
     * @param T The success type.
     * @param key A stable, deterministic key that identifies the logical request (e.g.,
     *   "GET:/users/42").
     * @param block The suspending work to execute once per in-flight key.
     * @return `Result.success(value)` on success, or `Result.failure(error)` if [block] fails or is
     *   cancelled. If the *awaiting caller* is cancelled, a [CancellationException] is thrown to
     *   that caller instead.
     */
    suspend fun <T> run(key: StreamTypedKey<T>, block: suspend () -> T): Result<T>

    /**
     * Checks if there is an in-flight execution for [key].
     *
     * @param key The key to check.
     */
    fun <T> has(key: StreamTypedKey<T>): Boolean

    /**
     * Cancels the current in-flight execution for [key], if any. Joiners will receive
     * `Result.failure(CancellationException)`. No-op if there is no in-flight execution.
     */
    fun <T> cancel(key: StreamTypedKey<T>): Result<Unit>

    /**
     * Clears internal bookkeeping. Useful if another component owns lifecycle/cancellation and you
     * want to drop references.
     */
    fun clear(cancelRunning: Boolean = true): Result<Unit>

    /**
     * Stops all execution and shuts down the runner. No further jobs are accepted. Stop is a
     * destructive action.
     */
    fun stop(): Result<Unit>
}

/**
 * Creates a new [StreamSingleFlightProcessor] instance.
 *
 * @param scope The coroutine scope to use for running the in-flight executions.
 * @return A new [StreamSingleFlightProcessor] instance.
 */
@StreamCoreApi
fun StreamSingleFlightProcessor(scope: CoroutineScope): StreamSingleFlightProcessor =
    StreamSingleFlightProcessorImpl(scope)
