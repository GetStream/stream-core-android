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

package io.getstream.android.core.api.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Test suite for [StreamCid] covering parsing, creation, validation, and formatting.
 *
 * This test suite verifies:
 * - Successful parsing from valid formatted strings
 * - Proper validation and error handling for invalid inputs
 * - Creation from separate type and id components
 * - Formatted string output
 * - Data class equality and hash code behavior
 * - Round-trip parsing consistency
 */
class StreamCidTest {

    // ========================================
    // Parsing Tests - Valid Inputs
    // ========================================

    @Test
    fun `parse creates StreamCid from valid formatted string`() {
        val cid = StreamCid.parse("messaging:general")

        assertEquals("messaging", cid.configuration)
        assertEquals("general", cid.id)
    }

    @Test
    fun `parse handles various configuration types correctly`() {
        val testCases =
            mapOf(
                "messaging:support" to ("messaging" to "support"),
                "livestream:sports" to ("livestream" to "sports"),
                "team:project-alpha" to ("team" to "project-alpha"),
                "call:lobby-123" to ("call" to "lobby-123"),
            )

        testCases.forEach { (input, expected) ->
            val cid = StreamCid.parse(input)
            assertEquals(
                expected.first,
                cid.configuration,
                "Configuration mismatch for input: $input",
            )
            assertEquals(expected.second, cid.id, "ID mismatch for input: $input")
        }
    }

    @Test
    fun `parse handles IDs with special characters`() {
        val testCases =
            listOf(
                "messaging:user-123",
                "livestream:sports-2023",
                "team:project_alpha",
                "gaming:lobby.456",
            )

        testCases.forEach { input ->
            val cid = StreamCid.parse(input)
            assertEquals(input, cid.formatted(), "Round-trip failed for: $input")
        }
    }

    @Test
    fun `parse handles long IDs`() {
        val longId = "a".repeat(1000)
        val input = "messaging:$longId"

        val cid = StreamCid.parse(input)

        assertEquals("messaging", cid.configuration)
        assertEquals(longId, cid.id)
    }

    @Test
    fun `parse handles single character components`() {
        val cid = StreamCid.parse("a:b")

        assertEquals("a", cid.configuration)
        assertEquals("b", cid.id)
    }

    // ========================================
    // Parsing Tests - Invalid Inputs
    // ========================================

    @Test
    fun `parse throws IllegalArgumentException for empty string`() {
        val exception = assertFailsWith<IllegalArgumentException> { StreamCid.parse("") }

        assertTrue(
            exception.message?.contains("CID string cannot be empty") == true,
            "Expected error message about empty string, got: ${exception.message}",
        )
    }

    @Test
    fun `parse throws IllegalArgumentException for missing colon`() {
        val exception = assertFailsWith<IllegalArgumentException> { StreamCid.parse("messaging") }

        assertTrue(
            exception.message?.contains("exactly one colon separator") == true,
            "Expected error message about missing colon, got: ${exception.message}",
        )
    }

    @Test
    fun `parse throws IllegalArgumentException for empty configuration`() {
        val exception = assertFailsWith<IllegalArgumentException> { StreamCid.parse(":general") }

        assertTrue(
            exception.message?.contains("Configuration type cannot be empty") == true,
            "Expected error message about empty configuration, got: ${exception.message}",
        )
    }

    @Test
    fun `parse throws IllegalArgumentException for empty id`() {
        val exception = assertFailsWith<IllegalArgumentException> { StreamCid.parse("messaging:") }

        assertTrue(
            exception.message?.contains("Resource id cannot be empty") == true,
            "Expected error message about empty id, got: ${exception.message}",
        )
    }

    @Test
    fun `parse throws IllegalArgumentException for too many colons`() {
        val exception =
            assertFailsWith<IllegalArgumentException> { StreamCid.parse("messaging:general:extra") }

        assertTrue(
            exception.message?.contains("exactly one colon separator") == true,
            "Expected error message about too many colons, got: ${exception.message}",
        )
    }

    @Test
    fun `parse throws IllegalArgumentException for only colon`() {
        val exception = assertFailsWith<IllegalArgumentException> { StreamCid.parse(":") }

        assertTrue(
            exception.message?.contains("Configuration type cannot be empty") == true ||
                exception.message?.contains("Resource id cannot be empty") == true,
            "Expected error message about empty components, got: ${exception.message}",
        )
    }

    // ========================================
    // fromTypeAndId Tests - Valid Inputs
    // ========================================

    @Test
    fun `fromTypeAndId creates StreamCid from valid components`() {
        val cid = StreamCid.fromTypeAndId("messaging", "general")

        assertEquals("messaging", cid.configuration)
        assertEquals("general", cid.id)
    }

    @Test
    fun `fromTypeAndId handles various configuration types`() {
        val testCases =
            listOf(
                "messaging" to "support",
                "livestream" to "sports",
                "team" to "project-alpha",
                "call" to "lobby-123",
            )

        testCases.forEach { (configuration, id) ->
            val cid = StreamCid.fromTypeAndId(configuration, id)
            assertEquals(
                configuration,
                cid.configuration,
                "Configuration mismatch for: $configuration:$id",
            )
            assertEquals(id, cid.id, "ID mismatch for: $configuration:$id")
        }
    }

    @Test
    fun `fromTypeAndId handles single character components`() {
        val cid = StreamCid.fromTypeAndId("a", "b")

        assertEquals("a", cid.configuration)
        assertEquals("b", cid.id)
    }

    @Test
    fun `fromTypeAndId handles long strings`() {
        val longConfiguration = "a".repeat(500)
        val longId = "b".repeat(500)

        val cid = StreamCid.fromTypeAndId(longConfiguration, longId)

        assertEquals(longConfiguration, cid.configuration)
        assertEquals(longId, cid.id)
    }

    // ========================================
    // fromTypeAndId Tests - Invalid Inputs
    // ========================================

    @Test
    fun `fromTypeAndId throws IllegalArgumentException for empty configuration`() {
        val exception =
            assertFailsWith<IllegalArgumentException> { StreamCid.fromTypeAndId("", "general") }

        assertTrue(
            exception.message?.contains("Configuration type cannot be empty") == true,
            "Expected error message about empty configuration, got: ${exception.message}",
        )
    }

    @Test
    fun `fromTypeAndId throws IllegalArgumentException for empty id`() {
        val exception =
            assertFailsWith<IllegalArgumentException> { StreamCid.fromTypeAndId("messaging", "") }

        assertTrue(
            exception.message?.contains("Resource id cannot be empty") == true,
            "Expected error message about empty id, got: ${exception.message}",
        )
    }

    @Test
    fun `fromTypeAndId throws IllegalArgumentException for both empty`() {
        val exception =
            assertFailsWith<IllegalArgumentException> { StreamCid.fromTypeAndId("", "") }

        assertTrue(
            exception.message?.contains("Configuration type cannot be empty") == true,
            "Expected error message about empty configuration, got: ${exception.message}",
        )
    }

    // ========================================
    // formatted() Tests
    // ========================================

    @Test
    fun `formatted returns correct string representation`() {
        val cid = StreamCid.fromTypeAndId("messaging", "general")

        assertEquals("messaging:general", cid.formatted())
    }

    @Test
    fun `formatted handles various channel types`() {
        val testCases =
            listOf(
                "messaging:support",
                "livestream:sports",
                "team:project-alpha",
                "gaming:lobby-123",
            )

        testCases.forEach { expected ->
            val cid = StreamCid.parse(expected)
            assertEquals(expected, cid.formatted(), "Formatted output mismatch for: $expected")
        }
    }

    @Test
    fun `formatted is consistent across multiple calls`() {
        val cid = StreamCid.fromTypeAndId("messaging", "general")

        val formatted1 = cid.formatted()
        val formatted2 = cid.formatted()

        assertEquals(formatted1, formatted2)
        assertEquals("messaging:general", formatted1)
    }

    // ========================================
    // Round-Trip Parsing Tests
    // ========================================

    @Test
    fun `parse and formatted are inverse operations`() {
        val original = "messaging:general"

        val cid = StreamCid.parse(original)
        val roundTrip = cid.formatted()

        assertEquals(original, roundTrip)
    }

    @Test
    fun `round-trip parsing works for various inputs`() {
        val testCases =
            listOf(
                "messaging:general",
                "livestream:sports-2023",
                "team:project_alpha",
                "gaming:lobby.456",
                "a:b",
            )

        testCases.forEach { original ->
            val cid = StreamCid.parse(original)
            val roundTrip = cid.formatted()
            assertEquals(original, roundTrip, "Round-trip failed for: $original")
        }
    }

    @Test
    fun `fromTypeAndId and formatted are inverse operations`() {
        val configuration = "messaging"
        val id = "general"

        val cid = StreamCid.fromTypeAndId(configuration, id)
        val formatted = cid.formatted()
        val reparsed = StreamCid.parse(formatted)

        assertEquals(configuration, reparsed.configuration)
        assertEquals(id, reparsed.id)
    }

    // ========================================
    // Data Class Equality Tests
    // ========================================

    @Test
    fun `StreamCid instances with same values are equal`() {
        val cid1 = StreamCid.parse("messaging:general")
        val cid2 = StreamCid.parse("messaging:general")

        assertEquals(cid1, cid2)
    }

    @Test
    fun `StreamCid instances created differently but with same values are equal`() {
        val cid1 = StreamCid.parse("messaging:general")
        val cid2 = StreamCid.fromTypeAndId("messaging", "general")

        assertEquals(cid1, cid2)
    }

    @Test
    fun `StreamCid instances with different configurations are not equal`() {
        val cid1 = StreamCid.fromTypeAndId("messaging", "general")
        val cid2 = StreamCid.fromTypeAndId("livestream", "general")

        assertNotEquals(cid1, cid2)
    }

    @Test
    fun `StreamCid instances with different ids are not equal`() {
        val cid1 = StreamCid.fromTypeAndId("messaging", "general")
        val cid2 = StreamCid.fromTypeAndId("messaging", "support")

        assertNotEquals(cid1, cid2)
    }

    @Test
    fun `StreamCid instances with different configuration and id are not equal`() {
        val cid1 = StreamCid.fromTypeAndId("messaging", "general")
        val cid2 = StreamCid.fromTypeAndId("livestream", "support")

        assertNotEquals(cid1, cid2)
    }

    // ========================================
    // Hash Code Tests
    // ========================================

    @Test
    fun `equal StreamCid instances have equal hash codes`() {
        val cid1 = StreamCid.parse("messaging:general")
        val cid2 = StreamCid.parse("messaging:general")

        assertEquals(cid1.hashCode(), cid2.hashCode())
    }

    @Test
    fun `hash code is consistent across multiple calls`() {
        val cid = StreamCid.fromTypeAndId("messaging", "general")

        val hash1 = cid.hashCode()
        val hash2 = cid.hashCode()

        assertEquals(hash1, hash2)
    }

    @Test
    fun `StreamCid can be used as map key`() {
        val cid1 = StreamCid.parse("messaging:general")
        val cid2 = StreamCid.parse("messaging:general")
        val cid3 = StreamCid.parse("livestream:sports")

        val map = mutableMapOf<StreamCid, String>()
        map[cid1] = "value1"
        map[cid3] = "value3"

        assertEquals("value1", map[cid2]) // cid2 equals cid1
        assertEquals("value3", map[cid3])
        assertEquals(2, map.size)
    }

    @Test
    fun `StreamCid can be used in set`() {
        val cid1 = StreamCid.parse("messaging:general")
        val cid2 = StreamCid.parse("messaging:general")
        val cid3 = StreamCid.parse("livestream:sports")

        val set = mutableSetOf<StreamCid>()
        set.add(cid1)
        set.add(cid2) // Should not add duplicate
        set.add(cid3)

        assertEquals(2, set.size)
        assertTrue(set.contains(cid1))
        assertTrue(set.contains(cid2))
        assertTrue(set.contains(cid3))
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun `configuration and id can contain numbers`() {
        val cid = StreamCid.parse("config123:id456")

        assertEquals("config123", cid.configuration)
        assertEquals("id456", cid.id)
    }

    @Test
    fun `configuration and id can contain hyphens and underscores`() {
        val cid = StreamCid.parse("my-config_1:my-id_2")

        assertEquals("my-config_1", cid.configuration)
        assertEquals("my-id_2", cid.id)
    }

    @Test
    fun `configuration and id can contain dots`() {
        val cid = StreamCid.parse("my.config:my.id")

        assertEquals("my.config", cid.configuration)
        assertEquals("my.id", cid.id)
    }

    @Test
    fun `id can contain multiple colons after split`() {
        // Note: This tests that only the FIRST colon is used as separator
        // Everything after the first colon should be part of the split
        // But our current implementation requires exactly one colon
        val exception =
            assertFailsWith<IllegalArgumentException> { StreamCid.parse("config:id:with:colons") }

        assertTrue(
            exception.message?.contains("exactly one colon separator") == true,
            "Expected error for multiple colons",
        )
    }

    @Test
    fun `configuration and id are accessible as properties`() {
        val cid = StreamCid.fromTypeAndId("messaging", "general")

        // Verify properties are accessible
        val configuration: String = cid.configuration
        val id: String = cid.id

        assertEquals("messaging", configuration)
        assertEquals("general", id)
    }
}
