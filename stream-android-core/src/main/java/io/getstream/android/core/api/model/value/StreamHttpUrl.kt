package io.getstream.android.core.api.model.value

import android.annotation.SuppressLint
import io.getstream.android.core.annotations.StreamCoreApi
import java.net.URI

/**
 * Represents an HTTP URL.
 *
 * @property rawValue The raw value of the HTTP URL.
 */
@StreamCoreApi
@JvmInline
value class StreamHttpUrl(val rawValue: String) {

    companion object {

        /**
         * Creates a new [StreamHttpUrl] from a string.
         *
         * @param value The string value of the HTTP URL.
         * @return The created [StreamHttpUrl].
         * @throws IllegalArgumentException If the value is blank or not a valid URL.
         */
        fun fromString(value: String): StreamHttpUrl {
            require(value.isNotBlank()) { "HTTP URL must not be blank" }
            val validURl = try {
                URI.create(value)
            } catch (e: Exception) {
                null
            }
            requireNotNull(validURl) { "Invalid HTTP URL: $value" }
            return StreamHttpUrl(value)
        }
    }
}
