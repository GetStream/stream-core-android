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

@file:OptIn(StreamInternalApi::class)

package io.getstream.android.core.api.log

import android.util.Log
import io.getstream.android.core.annotations.StreamInternalApi
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class StreamLoggerProviderTest {

    private val tag = "TestTag"

    @Before
    fun setUp() {
        mockkStatic(Log::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun stubLogDefaults() {
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.getStackTraceString(any()) } answers
            {
                "stack:${firstArg<Throwable>().message}"
            }
        every { Log.println(any(), any(), any()) } returns 0
    }

    @Test
    fun `default logger filters messages below min level`() {
        // Given
        stubLogDefaults()
        val provider =
            StreamLoggerProvider.defaultAndroidLogger(minLevel = StreamLogger.LogLevel.Info)
        val logger = provider.taggedLogger(tag)

        // When
        logger.d { "debug" }

        // Then
        verify(exactly = 0) { Log.println(any(), any(), any()) }
    }

    @Test
    fun `default logger honors isLoggable gate`() {
        // Given
        stubLogDefaults()
        every { Log.isLoggable(tag, Log.DEBUG) } returns false
        val provider = StreamLoggerProvider.defaultAndroidLogger(honorAndroidIsLoggable = true)
        val logger = provider.taggedLogger(tag)

        // When
        logger.d { "blocked" }

        // Then
        verify(exactly = 0) { Log.println(any(), any(), any()) }
    }

    @Test
    fun `default logger splits long messages into chunks`() {
        // Given
        val messages = mutableListOf<String>()
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.getStackTraceString(any()) } answers { "" }
        every { Log.println(any(), any(), any()) } answers
            {
                messages += thirdArg<String>()
                0
            }
        val provider = StreamLoggerProvider.defaultAndroidLogger()
        val logger = provider.taggedLogger(tag)
        val longMessage = buildString(9005) { repeat(9005) { append('x') } }

        // When
        logger.i { longMessage }

        // Then
        assertEquals(3, messages.size)
        assertEquals(4000, messages[0].length)
        assertEquals(4000, messages[1].length)
        assertEquals(1005, messages[2].length)
    }

    @Test
    fun `default logger appends stack trace when throwable provided`() {
        // Given
        val captured = mutableListOf<String>()
        stubLogDefaults()
        every { Log.println(any(), any(), any()) } answers
            {
                captured += thirdArg<String>()
                0
            }
        val provider = StreamLoggerProvider.defaultAndroidLogger()
        val logger = provider.taggedLogger(tag)
        val throwable = IllegalStateException("boom")

        // When
        logger.e(throwable, message = { "failure" })

        // Then
        val logged = captured.single()
        assertTrue(logged.contains("failure"))
        assertTrue(logged.contains("stack:boom"))
    }

    @Test
    fun `default logger recovers from message supplier exception`() {
        // Given
        val captured = mutableListOf<String>()
        stubLogDefaults()
        every { Log.println(any(), any(), any()) } answers
            {
                captured += thirdArg<String>()
                0
            }
        val provider = StreamLoggerProvider.defaultAndroidLogger()
        val logger = provider.taggedLogger(tag)

        // When
        logger.w { throw IllegalArgumentException("bad supplier") }

        // Then
        assertEquals("Log message supplier threw: bad supplier", captured.single())
    }

    @Test
    fun `default logger rethrows cancellation`() {
        // Given
        stubLogDefaults()
        val provider = StreamLoggerProvider.defaultAndroidLogger()
        val logger = provider.taggedLogger(tag)

        // When & Then
        assertFailsWith<CancellationException> {
            logger.log(StreamLogger.LogLevel.Debug, null) { throw CancellationException("cancel") }
        }
    }

    @Test
    fun `stream logger convenience methods delegate to log`() {
        // Given
        val recorded = mutableListOf<Pair<StreamLogger.LogLevel, String>>()
        val logger =
            object : StreamLogger {
                override fun log(
                    level: StreamLogger.LogLevel,
                    throwable: Throwable?,
                    message: () -> String,
                ) {
                    recorded += level to message()
                }
            }
        val throwable = IllegalArgumentException("fail")

        // When
        logger.v { "verbose" }
        logger.d { "debug" }
        logger.i { "info" }
        logger.w { "warn" }
        logger.e { "error" }
        logger.e(throwable, message = { "failure" })

        // Then
        val levels = recorded.map { it.first }
        assertEquals(
            listOf(
                StreamLogger.LogLevel.Verbose,
                StreamLogger.LogLevel.Debug,
                StreamLogger.LogLevel.Info,
                StreamLogger.LogLevel.Warning,
                StreamLogger.LogLevel.Error,
                StreamLogger.LogLevel.Error,
            ),
            levels,
        )
        assertEquals("failure", recorded.last().second)
    }
}
