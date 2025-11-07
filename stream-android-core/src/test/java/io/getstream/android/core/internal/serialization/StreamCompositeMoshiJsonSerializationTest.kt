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

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.exceptions.StreamClientException
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class StreamCompositeMoshiJsonSerializationTest {

    data class Foo(val x: Int)

    data class Bar(val y: Int)

    @Test
    fun `internal succeeds directly`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.toJson(any()) } returns Result.success("{\"x\":1}")

        val composite = StreamCompositeMoshiJsonSerialization(logger, internal, external)

        val result = composite.toJson(Foo(1))

        assertTrue(result.isSuccess)
        assertEquals("{\"x\":1}", result.getOrThrow())
        verify(exactly = 0) { external.toJson(any()) }
    }

    @Test
    fun `fallback to external when internal fails`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.toJson(any()) } returns Result.failure(RuntimeException("boom"))
        every { external.toJson(any()) } returns Result.success("{\"x\":2}")

        val composite = StreamCompositeMoshiJsonSerialization(logger, internal, external)

        val result = composite.toJson(Foo(2))

        assertTrue(result.isSuccess)
        assertEquals("{\"x\":2}", result.getOrThrow())
        verify { logger.v(any()) }
        verify { external.toJson(any()) }
    }

    @Test
    fun `internalOnly class prevents fallback`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.toJson(any()) } returns Result.failure(RuntimeException("fail"))

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger,
                internal,
                external,
                setOf(Foo::class.java),
            )

        val result = composite.toJson(Foo(3))

        assertTrue(result.isFailure)
        verify { logger.v(any()) }
        verify(exactly = 0) { external.toJson(any()) }
    }

    @Test
    fun `fromJson falls back to external`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.fromJson(any(), Foo::class.java) } returns
            Result.failure(RuntimeException("fail"))
        every { external.fromJson(any(), Foo::class.java) } returns Result.success(Foo(42))

        val composite = StreamCompositeMoshiJsonSerialization(logger, internal, external)

        val result = composite.fromJson("{}", Foo::class.java)

        assertTrue(result.isSuccess)
        assertEquals(Foo(42), result.getOrThrow())
        verify { logger.v(any()) }
        verify { external.fromJson(any(), Foo::class.java) }
    }

    @Test
    fun `toJson internalOnly class fails without fallback`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.toJson(any()) } returns Result.failure(RuntimeException("fail"))

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger,
                internal,
                external,
                setOf(Bar::class.java),
            )

        val result = composite.toJson(Bar(1))

        assertTrue(result.isFailure)
        verify { logger.v(any()) }
        verify(exactly = 0) { external.toJson(any()) }
    }

    @Test
    fun `toJson internalOnly class succeeds with internal`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.toJson(any()) } returns Result.success("{\"y\":5}")

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger,
                internal,
                external,
                setOf(Bar::class.java),
            )

        val result = composite.toJson(Bar(5))

        assertTrue(result.isSuccess)
        assertEquals("{\"y\":5}", result.getOrThrow())
        verify(exactly = 0) { external.toJson(any()) }
    }

    @Test
    fun `fromJson internalOnly class fails without fallback`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.fromJson(any(), Bar::class.java) } returns
            Result.failure(RuntimeException("fail"))

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger,
                internal,
                external,
                setOf(Bar::class.java),
            )

        val result = composite.fromJson("{}", Bar::class.java)

        assertTrue(result.isFailure)
        verify { logger.v(any()) }
        verify(exactly = 0) { external.fromJson(any(), Bar::class.java) }
    }

    @Test
    fun `fromJson internalOnly class succeeds with internal`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val external = mockk<StreamJsonSerialization>()

        every { internal.fromJson(any(), Bar::class.java) } returns Result.success(Bar(9))

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger,
                internal,
                external,
                setOf(Bar::class.java),
            )

        val result = composite.fromJson("{}", Bar::class.java)

        assertTrue(result.isSuccess)
        assertEquals(Bar(9), result.getOrThrow())
        verify(exactly = 0) { external.fromJson(any(), Bar::class.java) }
    }

    @Test
    fun `toJson succeeds with internal when external is null`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        // external = null

        every { internal.toJson(any()) } returns Result.success("""{"x":10}""")

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger = logger,
                internal = internal,
                external = null, // <— no external
            )

        val result = composite.toJson(Foo(10))

        assertTrue(result.isSuccess)
        assertEquals("""{"x":10}""", result.getOrThrow())
    }

    @Test
    fun `toJson fails with StreamClientException when internal fails and external is null`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val boom = RuntimeException("internal fail")

        every { internal.toJson(any()) } returns Result.failure(boom)

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger = logger,
                internal = internal,
                external = null, // <— no external
            )

        val result = composite.toJson(Foo(11))

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is StreamClientException)
        assertEquals("No external serializer available", ex!!.message)
        assertEquals(boom, ex.cause)
        // we logged the fallback attempt
        verify { logger.v(any()) }
    }

    @Test
    fun `fromJson succeeds with internal when external is null`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()

        every { internal.fromJson(any(), Foo::class.java) } returns Result.success(Foo(42))

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger = logger,
                internal = internal,
                external = null, // <— no external
            )

        val result = composite.fromJson("""{"x":42}""", Foo::class.java)

        assertTrue(result.isSuccess)
        assertEquals(Foo(42), result.getOrThrow())
    }

    @Test
    fun `fromJson fails with StreamClientException when internal fails and external is null`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val boom = IllegalStateException("internal parse fail")

        every { internal.fromJson(any(), Foo::class.java) } returns Result.failure(boom)

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger = logger,
                internal = internal,
                external = null, // <— no external
            )

        val result = composite.fromJson("""{"x":-1}""", Foo::class.java)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is StreamClientException)
        assertEquals("No external serializer available", ex!!.message)
        assertEquals(boom, ex.cause)
        verify { logger.v(any()) }
    }

    @Test
    fun `fromJson internalOnly class still fails without fallback when external is null`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val internal = mockk<StreamJsonSerialization>()
        val boom = RuntimeException("strict internal only fail")

        every { internal.fromJson(any(), Bar::class.java) } returns Result.failure(boom)

        val composite =
            StreamCompositeMoshiJsonSerialization(
                logger = logger,
                internal = internal,
                external = null, // <— no external
                internalOnly = setOf(Bar::class.java),
            )

        val result = composite.fromJson("""{"y":1}""", Bar::class.java)

        // should surface the ORIGINAL internal exception (no fallback attempted)
        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
        verify { logger.v(any()) }
    }
}
