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
package io.getstream.android.core.internal.serialization

import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

class StreamClientEventSerializationImplTest {
    private val jsonSerialization: StreamJsonSerialization = mockk()

    private val parser = StreamClientEventSerializationImpl(jsonSerialization)

    @Test
    fun `serialize delegates to jsonParser success`() {
        val event = mockk<StreamClientWsEvent>()
        every { jsonSerialization.toJson(event) } returns Result.success("""{"type":"ok"}""")

        val result = parser.serialize(event)

        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals("""{"type":"ok"}""", result.getOrNull())
    }

    @Test
    fun `serialize delegates to jsonParser failure`() {
        val event = mockk<StreamClientWsEvent>()
        val exception = RuntimeException("boom")
        every { jsonSerialization.toJson(event) } returns Result.failure(exception)

        val result = parser.serialize(event)

        Assert.assertTrue(result.isFailure)
        Assert.assertSame(exception, result.exceptionOrNull())
    }

    @Test
    fun `deserialize delegates to jsonParser success`() {
        val json = """{"type":"connection.ok"}"""
        val event = mockk<StreamClientWsEvent>()
        every { jsonSerialization.fromJson(json, StreamClientWsEvent::class.java) } returns
            Result.success(event)

        val result = parser.deserialize(json)

        Assert.assertTrue(result.isSuccess)
        Assert.assertSame(event, result.getOrNull())
    }

    @Test
    fun `deserialize delegates to jsonParser failure`() {
        val json = """{"type":"bad"}"""
        val exception = IllegalArgumentException("bad input")
        every { jsonSerialization.fromJson(json, StreamClientWsEvent::class.java) } returns
            Result.failure(exception)

        val result = parser.deserialize(json)

        Assert.assertTrue(result.isFailure)
        Assert.assertSame(exception, result.exceptionOrNull())
    }
}
