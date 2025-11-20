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
package io.getstream.android.core.internal.recovery

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamTypedKey
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.model.connection.recovery.Recovery
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalTime::class)
class StreamConnectionRecoveryEvaluatorImplTest {

    @Test
    fun `reconnects when network returns while foreground`() = runTest {
        val evaluator = evaluator()
        val snapshot = StreamNetworkInfo.Snapshot()
        val newSnapshot = StreamNetworkInfo.Snapshot()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = available(snapshot),
            )
            .getOrThrow()
            .also { assertNull(it) }

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()
            .also { assertIs<Recovery.Disconnect<*>>(it) }

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Disconnected(),
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = available(newSnapshot),
                )
                .getOrThrow()

        val connect = assertIs<Recovery.Connect<*>>(recovery)
        assertEquals(newSnapshot, connect.why)
    }

    @Test
    fun `reconnects immediately when returning foreground with available network`() = runTest {
        val evaluator = evaluator()
        val snapshot = StreamNetworkInfo.Snapshot()
        val newSnapshot = StreamNetworkInfo.Snapshot()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = available(snapshot),
            )
            .getOrThrow()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Background,
                networkState = available(snapshot),
            )
            .getOrThrow()
            .also { assertIs<Recovery.Disconnect<*>>(it) }

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Disconnected(),
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = available(newSnapshot),
                )
                .getOrThrow()

        val connect = assertIs<Recovery.Connect<*>>(recovery)
        assertEquals(newSnapshot, connect.why)
    }

    @Test
    fun `waits for network after foregrounding if offline`() = runTest {
        val evaluator = evaluator()
        val initialSnapshot = StreamNetworkInfo.Snapshot()
        val restoredSnapshot = StreamNetworkInfo.Snapshot()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = available(initialSnapshot),
            )
            .getOrThrow()
            .also {
                // Do nothing
                assertNull(it)
            }

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Background,
                networkState = available(initialSnapshot),
            )
            .getOrThrow()
            .also { assertIs<Recovery.Disconnect<*>>(it) }

        evaluator
            .evaluate(
                connectionState = StreamConnectionState.Disconnected(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()
            .also { assertNull(it) }

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Disconnected(),
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = available(restoredSnapshot),
                )
                .getOrThrow()

        val connect = assertIs<Recovery.Connect<*>>(recovery)
        assertEquals(restoredSnapshot, connect.why)
    }

    @Test
    fun `reconnects when network returns without lifecycle change`() = runTest {
        val evaluator = evaluator()
        val initialSnapshot = StreamNetworkInfo.Snapshot()
        val restoredSnapshot = StreamNetworkInfo.Snapshot()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = available(initialSnapshot),
            )
            .getOrThrow()

        evaluator
            .evaluate(
                connectionState = StreamConnectionState.Disconnected(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()
            .also { assertNull(it) }

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Disconnected(),
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = available(restoredSnapshot),
                )
                .getOrThrow()

        val connect = assertIs<Recovery.Connect<*>>(recovery)
        assertEquals(restoredSnapshot, connect.why)
    }

    @Test
    fun `does not connect before the first successful connection`() = runTest {
        val evaluator = evaluator()
        val snapshot = StreamNetworkInfo.Snapshot()

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Disconnected(),
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = available(snapshot),
                )
                .getOrThrow()

        assertNull(recovery)
    }

    @Test
    fun `stays idle when returning foreground while already reconnecting`() = runTest {
        val evaluator = evaluator()
        val snapshot = StreamNetworkInfo.Snapshot()
        val networkLostSnapshot = StreamNetworkInfo.Snapshot()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = available(snapshot),
            )
            .getOrThrow()

        evaluator
            .evaluate(
                connectionState = StreamConnectionState.Disconnected(),
                lifecycleState = StreamLifecycleState.Background,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()

        evaluator
            .evaluate(
                connectionState = StreamConnectionState.Disconnected(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Connecting.Opening(TEST_USER_ID),
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = available(networkLostSnapshot),
                )
                .getOrThrow()

        assertNull(recovery)
    }

    @Test
    fun `does not reconnect while background even if network returns`() = runTest {
        val evaluator = evaluator()
        val snapshot = StreamNetworkInfo.Snapshot()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = available(snapshot),
            )
            .getOrThrow()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()

        evaluator
            .evaluate(
                connectionState = StreamConnectionState.Disconnected(),
                lifecycleState = StreamLifecycleState.Background,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Disconnected(),
                    lifecycleState = StreamLifecycleState.Background,
                    networkState = available(snapshot),
                )
                .getOrThrow()

        assertNull(recovery)
    }

    @Test
    fun `ignores reconnect signal while already connecting`() = runTest {
        val evaluator = evaluator()
        val snapshot = StreamNetworkInfo.Snapshot()
        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = available(snapshot),
            )
            .getOrThrow()

        evaluator
            .evaluate(
                connectionState = connectedState(),
                lifecycleState = StreamLifecycleState.Foreground,
                networkState = StreamNetworkState.Disconnected,
            )
            .getOrThrow()

        val recovery =
            evaluator
                .evaluate(
                    connectionState = StreamConnectionState.Connecting.Opening(TEST_USER_ID),
                    lifecycleState = StreamLifecycleState.Foreground,
                    networkState = available(StreamNetworkInfo.Snapshot()),
                )
                .getOrThrow()

        assertNull(recovery)
    }

    private fun evaluator(): StreamConnectionRecoveryEvaluatorImpl =
        StreamConnectionRecoveryEvaluatorImpl(NoopLogger, ImmediateSingleFlightProcessor())

    private fun connectedState(): StreamConnectionState.Connected =
        StreamConnectionState.Connected(testUser(), TEST_CONNECTION_ID)

    private fun testUser(): StreamConnectedUser =
        StreamConnectedUser(
            createdAt = Date(0L),
            id = TEST_USER_ID,
            language = "en",
            role = "user",
            updatedAt = Date(0L),
            teams = emptyList(),
        )

    private fun available(snapshot: StreamNetworkInfo.Snapshot): StreamNetworkState.Available =
        StreamNetworkState.Available(snapshot)

    private object NoopLogger : StreamLogger {
        override fun log(
            level: StreamLogger.LogLevel,
            throwable: Throwable?,
            message: () -> String,
        ) {
            // no-op
        }
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

    private companion object {
        private const val TEST_USER_ID = "user-id"
        private const val TEST_CONNECTION_ID = "connection-id"
    }
}
