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
package io.getstream.android.core.api.model

import io.getstream.android.core.annotations.StreamInternalApi

/**
 * **Retry-policy value object** used by Stream’s internal implementations.
 *
 * A policy answers two questions for each failed attempt:
 * 1. **Should we try again?** — via [giveUpFunction].
 * 2. **How long should we wait?** — via [nextBackOffDelayFunction].
 * > The primary constructor is **private**. Create instances through the [exponential] / [linear]
 * > factory methods or by calling `copy( … )` on an existing instance. This guarantees that the
 * > invariants below always hold.
 *
 * ### Invariants
 * * `minRetries ≥ 0`
 * * `maxRetries ≥ minRetries`
 * * `0 ≤ minBackoffMills ≤ maxBackoffMills`
 *
 * @param minRetries Minimum number of retry attempts (not counting the original call). Set to `0`
 *   to disable retries.
 * @param maxRetries Upper bound for attempts. When `retryIndex > maxRetries`, [giveUpFunction] is
 *   forced to return `true`.
 * @param minBackoffMills Smallest delay the policy may return (ms).
 * @param maxBackoffMills Largest delay the policy may return (ms).
 * @param initialDelayMillis Delay before the **first** retry (after the initial failure).
 * @param giveUpFunction Receives the **1-based** retry index and the last `Throwable`; return
 *   `true` to stop retrying.
 * @param nextBackOffDelayFunction Computes the delay for the upcoming retry. Receives the retry
 *   index and the previous delay (or `initialDelayMillis` on the first retry).
 */
@StreamInternalApi
@ConsistentCopyVisibility
public data class StreamRetryPolicy
private constructor(
    val minRetries: Int,
    val maxRetries: Int,
    val minBackoffMills: Long,
    val maxBackoffMills: Long,
    val initialDelayMillis: Long,
    val giveUpFunction: (retry: Int, cause: Throwable) -> Boolean,
    val nextBackOffDelayFunction: (retry: Int, previousDelay: Long) -> Long,
) {
    public companion object {
        /**
         * Creates an **exponential back-off** policy.
         *
         * Delay grows according to:
         * ```text
         * nextDelay = prevDelay + retryIndex × backoffStepMillis
         * ```
         *
         * then clamped to `[backoffStepMillis, maxBackoffMillis]`.
         *
         * Example with defaults:
         * ```
         * attempt 1 → 0 ms
         * retry  1 → 250 ms
         * retry  2 → 500 ms
         * retry  3 → 1 000 ms
         * …
         * ```
         *
         * @param minRetries Minimum retry attempts (inclusive).
         * @param maxRetries Maximum retry attempts (inclusive).
         * @param backoffStepMillis Base step that controls growth steepness.
         * @param maxBackoffMillis Upper bound for any delay.
         * @param initialDelayMillis Delay before the *first* retry.
         * @param giveUp Custom predicate to override the default “> maxRetries”.
         */
        public fun exponential(
            minRetries: Int = 1,
            maxRetries: Int = 5,
            backoffStepMillis: Long = 250,
            maxBackoffMillis: Long = 15_000,
            initialDelayMillis: Long = 0,
            giveUp: (Int, Throwable) -> Boolean = { retry, _ -> retry > maxRetries },
        ): StreamRetryPolicy =
            StreamRetryPolicy(
                    minRetries = minRetries,
                    maxRetries = maxRetries,
                    minBackoffMills = backoffStepMillis,
                    maxBackoffMills = maxBackoffMillis,
                    initialDelayMillis = initialDelayMillis,
                    giveUpFunction = giveUp,
                    nextBackOffDelayFunction = { retry, prev ->
                        (prev + retry * backoffStepMillis)
                            .coerceAtMost(maxBackoffMillis)
                            .coerceIn(backoffStepMillis, maxBackoffMillis)
                    },
                )
                .validate()

        /**
         * Creates a **linear back-off** policy.
         *
         * Delay increases by a constant [backoffStepMillis] each retry, capped at
         * [maxBackoffMillis].
         *
         * Example with defaults:
         * ```
         * attempt 1 → 0 ms
         * retry  1 → 250 ms
         * retry  2 → 500 ms
         * retry  3 → 750 ms
         * …
         * ```
         *
         * Parameter semantics match [exponential].
         */
        public fun linear(
            minRetries: Int = 1,
            maxRetries: Int = 5,
            backoffStepMillis: Long = 250,
            maxBackoffMillis: Long = 15_000,
            initialDelayMillis: Long = 0,
            giveUp: (Int, Throwable) -> Boolean = { retry, _ -> retry > maxRetries },
        ): StreamRetryPolicy =
            StreamRetryPolicy(
                    minRetries = minRetries,
                    maxRetries = maxRetries,
                    minBackoffMills = backoffStepMillis,
                    maxBackoffMills = maxBackoffMillis,
                    initialDelayMillis = initialDelayMillis,
                    giveUpFunction = giveUp,
                    nextBackOffDelayFunction = { _, prev ->
                        (prev + backoffStepMillis).coerceAtMost(maxBackoffMillis)
                    },
                )
                .validate()

        /**
         * **Fixed-delay back-off**: every retry waits exactly [delayMillis], clamped to
         * [maxBackoffMillis] for safety.
         *
         * ```
         * attempt 1 → 0 ms
         * retry  1 → delayMillis
         * retry  2 → delayMillis
         * …
         * ```
         */
        public fun fixed(
            minRetries: Int = 1,
            maxRetries: Int = 5,
            delayMillis: Long = 500,
            maxBackoffMillis: Long = 15_000,
            initialDelayMillis: Long = 0,
            giveUp: (Int, Throwable) -> Boolean = { retry, _ -> retry > maxRetries },
        ): StreamRetryPolicy =
            StreamRetryPolicy(
                    minRetries = minRetries,
                    maxRetries = maxRetries,
                    minBackoffMills = delayMillis,
                    maxBackoffMills = maxBackoffMillis,
                    initialDelayMillis = initialDelayMillis,
                    giveUpFunction = giveUp,
                    nextBackOffDelayFunction = { _, _ ->
                        delayMillis.coerceAtMost(maxBackoffMillis)
                    },
                )
                .validate()

        /**
         * **Custom builder** – explicitly specify every parameter while still benefiting from the
         * invariant checks in the private constructor.
         *
         * Example:
         * ```
         * val jitterPolicy = StreamRetryPolicy.custom(
         *     minRetries = 2,
         *     maxRetries = 6,
         *     minBackoffMills = 200,
         *     maxBackoffMills = 10_000,
         *     initialDelayMillis = 100,
         *     giveUp = { _, cause -> cause is AuthenticationException },
         * ) { attempt, prev ->
         *     val base = prev * 2
         *     // add ±20 % jitter
         *     val jitter = (base * Random.nextDouble(0.8, 1.2)).toLong()
         *     jitter
         * }
         * ```
         *
         * @param nextDelay Lambda receives **1-based retry index** and previous delay (or
         *   `initialDelayMillis` for the first retry) and must return the next delay in **ms**.
         */
        public fun custom(
            minRetries: Int,
            maxRetries: Int,
            minBackoffMills: Long,
            maxBackoffMills: Long,
            initialDelayMillis: Long,
            giveUp: (Int, Throwable) -> Boolean = { retry, _ -> retry > maxRetries },
            nextDelay: (Int, Long) -> Long,
        ): StreamRetryPolicy =
            StreamRetryPolicy(
                    minRetries = minRetries,
                    maxRetries = maxRetries,
                    minBackoffMills = minBackoffMills,
                    maxBackoffMills = maxBackoffMills,
                    initialDelayMillis = initialDelayMillis,
                    giveUpFunction = giveUp,
                    nextBackOffDelayFunction = { attempt, prev ->
                        nextDelay(attempt, prev).coerceIn(minBackoffMills, maxBackoffMills)
                    },
                )
                .validate()

        private fun StreamRetryPolicy.validate(): StreamRetryPolicy {
            require(minRetries > 0) { "minRetries must be ≥ 0" }
            require(maxRetries >= minRetries) { "maxRetries must be ≥ minRetries" }
            require(minBackoffMills >= 0) { "minBackoffMills must be ≥ 0" }
            require(maxBackoffMills >= minBackoffMills) {
                "maxBackoffMills must be ≥ minBackoffMills"
            }
            require(initialDelayMillis >= 0) { "initialDelayMillis must be ≥ 0" }
            return this
        }
    }
}
