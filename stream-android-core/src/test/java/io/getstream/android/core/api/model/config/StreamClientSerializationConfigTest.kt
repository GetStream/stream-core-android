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

import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.serialization.StreamEventSerialization
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class StreamClientSerializationConfigTest {

    @Test
    fun `json builder injects json implementation`() {
        val json = FakeJsonSerialization()
        val productEvents = FakeEventSerialization<Any>()
        val alsoExternal = setOf("custom:event")

        val config = StreamClientSerializationConfig.json(json, productEvents, alsoExternal)

        assertSame(json, config.json)
        assertNull(config.eventParser)
        assertSame(productEvents, config.productEventSerializers)
        assertEquals(alsoExternal, config.alsoExternal)
    }

    @Test
    fun `event builder injects event parser`() {
        val eventParser = FakeEventSerialization<StreamClientWsEvent>()
        val productEvents = FakeEventSerialization<Any>()
        val alsoExternal = setOf("product:event")

        val config = StreamClientSerializationConfig.event(eventParser, productEvents, alsoExternal)

        assertSame(eventParser, config.eventParser)
        assertNull(config.json)
        assertSame(productEvents, config.productEventSerializers)
        assertEquals(alsoExternal, config.alsoExternal)
    }

    private class FakeJsonSerialization : StreamJsonSerialization {
        override fun toJson(any: Any): Result<String> = Result.success("{}")

        override fun <T : Any> fromJson(raw: String, clazz: Class<T>): Result<T> =
            Result.failure(UnsupportedOperationException())
    }

    private class FakeEventSerialization<T> : StreamEventSerialization<T> {
        override fun serialize(data: T): Result<String> = Result.success("event")

        override fun deserialize(raw: String): Result<T> =
            Result.failure(UnsupportedOperationException())
    }
}
