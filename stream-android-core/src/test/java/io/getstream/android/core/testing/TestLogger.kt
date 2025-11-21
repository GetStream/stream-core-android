package io.getstream.android.core.testing

import io.getstream.android.core.api.log.StreamLogger

internal object TestLogger : StreamLogger {
    override fun log(level: StreamLogger.LogLevel, throwable: Throwable?, message: () -> String) {
        // Swallow logs during tests
    }
}
