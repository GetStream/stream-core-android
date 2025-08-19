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
package io.getstream.android.core.api.model.value

import io.getstream.android.core.annotations.StreamCoreApi
import java.text.Normalizer
import kotlin.text.iterator

/**
 * Value class representing the `X-Stream-Client` header value.
 *
 * @property rawValue The raw value of the header.
 */
@StreamCoreApi
@JvmInline
value class StreamHttpClientInfoHeader private constructor(val rawValue: String) {

    companion object {

        /**
         * Creates a new [StreamHttpClientInfoHeader] with the given values.
         *
         * @param product The name of the product.
         * @param productVersion The version of the product.
         * @param os The name of the operating system.
         * @param apiLevel The API level of the operating system.
         * @param deviceModel The model of the device.
         * @param app The name of the app.
         * @param appVersion The version of the app.
         * @return A new [StreamHttpClientInfoHeader] with the given values.
         */
        @JvmStatic
        fun create(
            product: String,
            productVersion: String,
            os: String,
            apiLevel: Int,
            deviceModel: String,
            app: String = "unknown",
            appVersion: String = "unknown",
        ): StreamHttpClientInfoHeader {
            val v = buildString {
                append("${sanitize(product)}-${sanitize(productVersion)}")
                append("|os=${sanitize(os)}")
                append("|api_version=$apiLevel")
                append("|device_model=${sanitize(deviceModel)}")
                append("|app=${sanitize(app)}")
                append("|app_version=${sanitize(appVersion)}")
            }
            return StreamHttpClientInfoHeader(v)
        }

        private fun Char.isAsciiSafe(): Boolean =
            when (this) {
                in '0'..'9',
                in 'A'..'Z',
                in 'a'..'z',
                '.',
                '_',
                '-' -> true
                else -> false
            }

        private fun sanitize(raw: String): String =
            runCatching {
                    // Use NFD to avoid compatibility expansions (e.g., ™ -> "TM")
                    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                    val sb = StringBuilder(normalized.length)
                    var lastWasDash = false

                    for (c0 in normalized) {
                        // Skip combining marks
                        if (Character.getType(c0).toByte() == Character.NON_SPACING_MARK) {
                            continue
                        }

                        // Keep only visible ASCII; treat others as space for collapse
                        val c = if (c0.code in 32..126) c0 else ' '

                        when {
                            c.isWhitespace() -> {
                                if (!lastWasDash) {
                                    sb.append('-')
                                    lastWasDash = true
                                }
                            }
                            c.isAsciiSafe() -> {
                                sb.append(c)
                                lastWasDash = false
                            }
                            else -> {
                                sb.append('_')
                                lastWasDash = false
                            }
                        }
                    }

                    var out = sb.toString().trim('-')
                    if (out.isBlank()) {
                        out = "unknown"
                    }
                    out
                }
                .getOrElse { "unknown" }
    }
}
