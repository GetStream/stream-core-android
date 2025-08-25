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
package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.serialization.StreamClientEventSerialization
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.serialization.StreamProductEventSerialization

/**
 * Configuration for serialization and deserialization in the Stream client.
 *
 * @param json The JSON serialization implementation.
 * @param eventParser The event parsing implementation.
 */
@StreamCoreApi
@ConsistentCopyVisibility
data class StreamClientSerializationConfig
private constructor(
    val json: StreamJsonSerialization? = null,
    val eventParser: StreamClientEventSerialization? = null,
    val productEventSerializers: StreamProductEventSerialization<*>,
    val internalTypes: Set<String> = setOf("connection.ok", "connection.error", "health.check"),
    val alsoExternal: Set<String> = emptySet(),
) {
    companion object {
        /**
         * Creates a default [StreamClientSerializationConfig]. Using the internal implementations.
         *
         * @param productEvents The product event serializers.
         * @param alsoExternal The event types to also parse as external.
         * @return A default [StreamClientSerializationConfig].
         */
        fun <T> default(productEvents: StreamProductEventSerialization<T>, alsoExternal: Set<String> = emptySet()) =
            StreamClientSerializationConfig(productEventSerializers = productEvents, alsoExternal = alsoExternal)

        /**
         * Creates a [StreamClientSerializationConfig] with the given JSON serialization.
         *
         * @param serialization The JSON serialization implementation.
         * @param productEvents The product event serializers.
         * @param alsoExternal The event types to also parse as external.
         * @return A [StreamClientSerializationConfig] with the given JSON serialization.
         */
        fun <T> json(serialization: StreamJsonSerialization, productEvents: StreamProductEventSerialization<T>, alsoExternal: Set<String> = emptySet()) =
            StreamClientSerializationConfig(json = serialization, productEventSerializers = productEvents, alsoExternal = alsoExternal)

        /**
         * Creates a [StreamClientSerializationConfig] with the given event parsing.
         *
         * @param serialization The event parsing implementation.
         * @param productEvents The product event serializers.
         * @param alsoExternal The event types to also parse as external.
         * @return A [StreamClientSerializationConfig] with the given event parsing.
         */
        fun <T> event(serialization: StreamClientEventSerialization, productEvents: StreamProductEventSerialization<T>, alsoExternal: Set<String> = emptySet()) =
            StreamClientSerializationConfig(eventParser = serialization, productEventSerializers = productEvents, alsoExternal = alsoExternal)
    }
}
