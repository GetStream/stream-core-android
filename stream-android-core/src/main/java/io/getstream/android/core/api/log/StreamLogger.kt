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

import io.getstream.android.core.annotations.StreamInternalApi

/**
 * Defines a logging contract for the Stream SDK.
 *
 * Implementations of this interface provide a way to log messages and exceptions at different
 * severity levels. By default, convenience functions are provided for each log level (e.g., [d],
 * [e], [i], [w], [v]).
 *
 * @see StreamLoggerProvider for the global logger accessor.
 */
@StreamInternalApi
public interface StreamLogger {
    /**
     * Represents the severity of a log message.
     *
     * @property level The integer value of the severity, where higher numbers represent more severe
     *   log levels.
     */
    public sealed class LogLevel(public val level: Int) {
        /** Verbose log messages, typically for detailed debugging. */
        public object Verbose : LogLevel(1)

        /** Debug log messages, used for general debugging. */
        public object Debug : LogLevel(2)

        /** Informational log messages, representing normal operation. */
        public object Info : LogLevel(3)

        /** Warning log messages, representing non-fatal issues. */
        public object Warning : LogLevel(4)

        /** Error log messages, representing recoverable failures. */
        public object Error : LogLevel(5)
    }

    /**
     * Logs a debug message.
     *
     * @param message A lambda returning the message to log.
     */
    public fun d(message: () -> String): Unit = log(LogLevel.Debug, null, message)

    /**
     * Logs an error message.
     *
     * @param message A lambda returning the message to log.
     */
    public fun e(message: () -> String): Unit = log(LogLevel.Error, null, message)

    /**
     * Logs an error with an optional message.
     *
     * @param throwable The error or exception to log.
     * @param message An optional lambda returning a message to include.
     */
    public fun e(throwable: Throwable, message: (() -> String)?): Unit =
        log(LogLevel.Error, throwable) { message?.invoke() ?: "${throwable.message}" }

    /**
     * Logs a warning message.
     *
     * @param message A lambda returning the message to log.
     */
    public fun w(message: () -> String): Unit = log(LogLevel.Warning, null, message)

    /**
     * Logs an informational message.
     *
     * @param message A lambda returning the message to log.
     */
    public fun i(message: () -> String): Unit = log(LogLevel.Info, null, message)

    /**
     * Logs a verbose message.
     *
     * @param message A lambda returning the message to log.
     */
    public fun v(message: () -> String): Unit = log(LogLevel.Verbose, null, message)

    /**
     * Logs a message at the given severity level.
     *
     * @param level The log level at which the message should be logged.
     * @param throwable An optional [Throwable] associated with the log message.
     * @param message A lambda returning the message to log.
     */
    public fun log(level: LogLevel, throwable: Throwable?, message: () -> String)
}
