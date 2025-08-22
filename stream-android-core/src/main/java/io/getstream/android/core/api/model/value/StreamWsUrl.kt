package io.getstream.android.core.api.model.value

import io.getstream.android.core.annotations.StreamCoreApi
import java.net.URI

/**
 * Represents a WebSocket URL.
 *
 * @property rawValue The raw value of the WebSocket URL.
 */
@StreamCoreApi
@JvmInline
value class StreamWsUrl private constructor(val rawValue: String) {

    companion object {

        /**
         * Creates a new [StreamWsUrl] from a string.
         *
         * @param value The string value of the WS URL.
         * @return The created [StreamWsUrl].
         * @throws IllegalArgumentException If the value is blank or not a valid WS URL.
         */
        fun fromString(value: String): StreamWsUrl {
            require(value.isNotBlank()) { "WS URL must not be blank" }
            val validUrl = try {
                URI.create(value)
            } catch (e: Exception) {
                null
            }
            requireNotNull(validUrl) {
                "Invalid WS URL: $value"
            }
            return StreamWsUrl(value)
        }
    }
}
