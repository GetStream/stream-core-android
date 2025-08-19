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
package io.getstream.android.core.internal.serialization

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.serialization.StreamJsonSerialization

internal class StreamCompositeMoshiJsonSerialization(
    private val logger: StreamLogger,
    private val internal: StreamJsonSerialization,
    private val external: StreamJsonSerialization,
    private val internalOnly: Set<Class<*>> = emptySet(),
) : StreamJsonSerialization {

    override fun toJson(any: Any): Result<String> = runCatching {
        internal
            .toJson(any)
            .recover {
                if (internalOnly.contains(any.javaClass)) {
                    logger.v {
                        "Failed to serialize $any using internal (only) serializer. ${it.message}"
                    }
                    throw it
                } else {
                    logger.v {
                        "Failed to serialize $any using internal serializer, trying external. ${it.message}"
                    }
                    external.toJson(any).getOrThrow()
                }
            }
            .getOrThrow()
    }

    override fun <T : Any> fromJson(raw: String, clazz: Class<T>): Result<T> = runCatching {
        internal
            .fromJson(raw, clazz)
            .recover {
                logger.v {
                    "Failed to deserialize $raw using internal serializer, trying external. ${it.message}"
                }
                if (internalOnly.contains(clazz)) {
                    logger.v {
                        "Failed to deserialize $raw using internal (only) serializer. ${it.message}"
                    }
                    throw it
                } else {
                    external.fromJson(raw, clazz).getOrThrow()
                }
            }
            .getOrThrow()
    }
}
