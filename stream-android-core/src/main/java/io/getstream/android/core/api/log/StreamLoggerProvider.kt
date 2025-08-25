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
package io.getstream.android.core.api.log

import android.util.Log
import io.getstream.android.core.annotations.StreamCoreApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

/**
 * Provides a factory for creating [StreamLogger] instances.
 *
 * Implementations decide how loggers are created for specific tags and how log messages are
 * emitted.
 */
@StreamCoreApi
interface StreamLoggerProvider {
    /**
     * Creates a [StreamLogger] instance associated with the given tag.
     *
     * @param tag The tag to identify log messages. Typically corresponds to a class or module name.
     * @return A [StreamLogger] that will emit log messages using the given tag.
     */
    fun taggedLogger(tag: String): StreamLogger

    companion object {
        private const val MAX_LEN = 4000

        /**
         * Creates a default Android logger provider backed by [android.util.Log].
         *
         * This implementation:
         * - Filters out log messages below the specified [minLevel].
         * - Optionally respects [Log.isLoggable] checks if [honorAndroidIsLoggable] is true.
         * - Splits messages longer than 4000 characters into multiple lines to avoid truncation.
         * - Safely evaluates message suppliers and catches exceptions.
         *
         * @param minLevel The minimum log level (inclusive) that will be emitted. Defaults to
         *   [StreamLogger.LogLevel.Debug].
         * @param honorAndroidIsLoggable If true, also checks [Log.isLoggable] before logging a
         *   message.
         * @return A [StreamLoggerProvider] that produces Android-backed [StreamLogger] instances.
         */
        @JvmStatic
        fun defaultAndroidLogger(
            minLevel: StreamLogger.LogLevel = StreamLogger.LogLevel.Verbose,
            honorAndroidIsLoggable: Boolean = false,
        ): StreamLoggerProvider =
            object : StreamLoggerProvider {
                override fun taggedLogger(tag: String): StreamLogger =
                    object : StreamLogger {
                        /**
                         * Emits a log message at the given [level].
                         * - Drops the message if below [minLevel].
                         * - Honors [Log.isLoggable] if enabled.
                         * - Includes [throwable] stacktrace when provided.
                         * - Splits large messages into chunks of up to 4000 characters.
                         *
                         * @param level The [StreamLogger.LogLevel] to log at.
                         * @param throwable Optional throwable whose stacktrace will be appended.
                         * @param message Lazy-evaluated supplier of the log message.
                         */
                        override fun log(
                            level: StreamLogger.LogLevel,
                            throwable: Throwable?,
                            message: () -> String,
                        ) {
                            // Level gate first
                            if (level.level < minLevel.level) return

                            val priority =
                                when (level) {
                                    StreamLogger.LogLevel.Verbose -> Log.VERBOSE
                                    StreamLogger.LogLevel.Debug -> Log.DEBUG
                                    StreamLogger.LogLevel.Info -> Log.INFO
                                    StreamLogger.LogLevel.Warning -> Log.WARN
                                    StreamLogger.LogLevel.Error -> Log.ERROR
                                }

                            if (honorAndroidIsLoggable && !Log.isLoggable(tag, priority)) return

                            val msg =
                                try {
                                    message()
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (t: Throwable) {
                                    "Log message supplier threw: ${t.message ?: t::class.java.simpleName}"
                                }

                            val finalMsg =
                                if (throwable != null) {
                                    buildString(msg.length + 128) {
                                        append(msg)
                                        append('\n')
                                        append(Log.getStackTraceString(throwable))
                                    }
                                } else {
                                    msg
                                }

                            printChunked(priority, tag, finalMsg)
                        }
                    }

                /**
                 * Emits a log message in chunks if it exceeds [MAX_LEN].
                 *
                 * This avoids the 4k truncation limit in Logcat.
                 *
                 * @param priority The Android log priority (e.g., [Log.DEBUG]).
                 * @param tag The tag used for this log.
                 * @param msg The message to log.
                 */
                private fun printChunked(priority: Int, tag: String, msg: String) {
                    if (msg.length <= MAX_LEN) {
                        Log.println(priority, tag, msg)
                        return
                    }
                    var i = 0
                    val len = msg.length
                    while (i < len) {
                        val end = min(i + MAX_LEN, len)
                        Log.println(priority, tag, msg.substring(i, end))
                        i = end
                    }
                }
            }
    }
}
