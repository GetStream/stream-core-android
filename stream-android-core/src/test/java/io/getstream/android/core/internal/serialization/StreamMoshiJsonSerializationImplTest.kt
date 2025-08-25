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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class StreamMoshiJsonSerializationImplTest {

    @JsonClass(generateAdapter = true)
    data class Foo2(@Json(name = "x") val x: Int, @Json(name = "y") val y: String)

    private val moshi = Moshi.Builder().build()
    private val serializer = StreamMoshiJsonSerializationImpl(moshi)

    @Test
    fun `toJson serializes successfully`() {
        val foo = Foo2(42, "hello")

        val result = serializer.toJson(foo)

        assertTrue(result.isSuccess)
        assertEquals("""{"x":42,"y":"hello"}""", result.getOrThrow())
    }

    @Test
    fun `fromJson deserializes successfully`() {
        val json = """{"x":42,"y":"world"}"""

        val result = serializer.fromJson(json, Foo2::class.java)

        assertTrue(result.isSuccess)
        assertEquals(Foo2(42, "world"), result.getOrThrow())
    }

    @Test
    fun `fromJson failure propagates`() {
        // Use invalid JSON to force Moshi to throw
        val json = """{ invalid-json """

        val result = serializer.fromJson(json, Foo2::class.java)

        assertTrue(result.isFailure)
        val exceptionOrNull = result.exceptionOrNull()
        assertNotNull(exceptionOrNull)
    }

    @Test
    fun `fromJson returns null the result is IllegalStateException propagates`() {
        // Use invalid JSON to force Moshi to throw
        val json = """{"x":42,"y":"world"}"""
        val mockMoshi = mockk<Moshi>(relaxed = true)
        every { mockMoshi.adapter(Foo2::class.java).fromJson(any<String>()) } returns null
        val serializer = StreamMoshiJsonSerializationImpl(mockMoshi)

        val result = serializer.fromJson(json, Foo2::class.java)

        assertTrue(result.isFailure)
        val exceptionOrNull = result.exceptionOrNull()
        assertNotNull(exceptionOrNull)

        assertEquals(IllegalArgumentException::class.java, exceptionOrNull!!::class.java)
    }
}
