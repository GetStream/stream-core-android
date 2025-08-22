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
import io.getstream.android.core.api.model.StreamRetryPolicy
import io.getstream.android.core.api.processing.StreamRetryProcessor
import io.getstream.android.core.api.utils.runCatchingCancellable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

internal class StreamRetryProcessorImpl(private val logger: StreamLogger) : StreamRetryProcessor {

    override suspend fun <T> retry(policy: StreamRetryPolicy, block: suspend () -> T): Result<T> =
        runCatchingCancellable {
            var delayMs = policy.initialDelayMillis
            var attempt = 1
            while (attempt <= policy.maxRetries) {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                try {
                    return@runCatchingCancellable block()
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    logger.v { "Retry attempt $attempt failed: ${t.message}" }
                    val checkGiveUp = attempt >= policy.minRetries
                    val shouldGiveUp = checkGiveUp && policy.giveUpFunction(attempt, t)
                    val isLastAttempt = attempt == policy.maxRetries
                    if (shouldGiveUp || isLastAttempt) {
                        logger.v { "Retry attempt $attempt failed: ${t.message}. Giving up." }
                        throw t
                    }
                    delayMs =
                        policy
                            .nextBackOffDelayFunction(attempt, delayMs)
                            .coerceIn(policy.minBackoffMills, policy.maxBackoffMills)
                }
                attempt++
            }

            logger.e { "Retry loop completed without success or failure. Policy: $policy" }
            throw IllegalStateException("Check your policy: $policy")
        }
}
