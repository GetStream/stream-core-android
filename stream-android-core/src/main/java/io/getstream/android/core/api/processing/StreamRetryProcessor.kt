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
import io.getstream.android.core.api.model.StreamRetryPolicy
import io.getstream.android.core.internal.processing.StreamRetryProcessorImpl

/**
 * Strategy-based retry executor used inside the Stream SDK.
 *
 * A concrete implementation applies the supplied [policy] to repeatedly invoke the suspending
 * [operation] until the call succeeds, the retry policy decides to give up, or the surrounding
 * coroutine is cancelled.
 *
 * ### Behaviour
 * * **Success** → returns `Result.success(value)` from the first successful attempt.
 * * **Give-up / max retries** → returns `Result.failure(originalThrowable)`.
 * * **Cancellation** → re-throws `CancellationException`; the caller is responsible for handling
 *   it.
 *
 * Implementations **must** honour the timing and stop-conditions described by [StreamRetryPolicy]
 * (initial delay, back-off function, `giveUpFunction`, etc.).
 *
 * @param T The value type produced by [block] on success.
 * @param policy Retry/back-off parameters (exponential, linear, custom).
 * @param block The suspending operation to execute. It should throw on failure; the processor
 *   handles re-invocation.
 * @return A [Result] wrapping either the successful value or the final error after all retries have
 *   been exhausted.
 */
@StreamCoreApi
interface StreamRetryProcessor {
    /**
     * Executes [block] with the supplied [policy], retrying on failure.
     *
     * @param T The value type produced by [block] on success.
     * @param policy Retry/back-off parameters (exponential, linear, custom).
     * @param block The suspending operation to execute. It should throw on failure; the processor
     *   handles re-invocation.
     */
    suspend fun <T> retry(policy: StreamRetryPolicy, block: suspend () -> T): Result<T>
}

/**
 * Creates a [StreamRetryProcessor] instance.
 *
 * @param logger The logger to use for logging retry attempts.
 * @return A new [StreamRetryProcessor] instance.
 */
@StreamCoreApi
fun StreamRetryProcessor(logger: StreamLogger): StreamRetryProcessor =
    StreamRetryProcessorImpl(logger)
