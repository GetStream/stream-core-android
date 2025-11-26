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

package io.getstream.android.core.api.recovery

import io.getstream.android.core.api.model.StreamTypedKey
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.testing.TestLogger
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class StreamConnectionRecoveryEvaluatorFactoryTest {

    @Test
    fun `factory wires a working evaluator`() = runTest {
        val evaluator =
            StreamConnectionRecoveryEvaluator(TestLogger, ImmediateSingleFlightProcessor())

        val result =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Idle,
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = StreamNetworkState.Unknown,
                )
                .getOrThrow()

        assertNull(result)
        assertIs<StreamConnectionRecoveryEvaluator>(evaluator)
    }

    private class ImmediateSingleFlightProcessor : StreamSingleFlightProcessor {
        override suspend fun <T> run(key: StreamTypedKey<T>, block: suspend () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (t: Throwable) {
                Result.failure(t)
            }

        override fun <T> has(key: StreamTypedKey<T>): Boolean = false

        override fun <T> cancel(key: StreamTypedKey<T>): Result<Unit> = Result.success(Unit)

        override fun clear(cancelRunning: Boolean): Result<Unit> = Result.success(Unit)

        override fun stop(): Result<Unit> = Result.success(Unit)
    }
}
