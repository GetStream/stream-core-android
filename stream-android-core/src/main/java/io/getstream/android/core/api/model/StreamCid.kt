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

import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * Represents a Configuration Identifier (CID) in the Stream platform.
 *
 * A CID uniquely identifies a resource by combining a configuration type and a resource ID in the
 * format `"configuration:identifier"`. This is the primary identifier used throughout the Stream
 * SDK for referencing various resources such as channels, calls, feeds, and other configuration
 * entities.
 *
 * ## Format
 *
 * CIDs follow the pattern: `"<configuration>:<identifier>"`
 * - **configuration**: The resource configuration type (e.g., "messaging", "livestream", "call",
 *   "feed")
 * - **identifier**: The unique identifier for the resource within that configuration (e.g.,
 *   "general", "support", "user-123")
 *
 * ## Examples
 *
 * ```kotlin
 * // Parse from formatted string
 * val cid1 = StreamCid.parse("messaging:general")
 * println(cid1.configuration)  // "messaging"
 * println(cid1.id)    // "general"
 * println(cid1.formatted())  // "messaging:general"
 *
 * // Create from configuration and id
 * val cid2 = StreamCid.fromTypeAndId("call", "video-room-1")
 * println(cid2.formatted())  // "call:video-room-1"
 *
 * // Different configuration types
 * val channelCid = StreamCid.parse("messaging:support")
 * val callCid = StreamCid.parse("call:team-meeting")
 * val feedCid = StreamCid.parse("feed:user-timeline")
 *
 * // Use in equality comparisons
 * val cid3 = StreamCid.parse("messaging:general")
 * println(cid1 == cid3)  // true (data class equality)
 * ```
 *
 * ## Thread Safety
 *
 * This class is immutable and thread-safe. All instances are created via factory methods in the
 * companion object, and the constructor is private to enforce validation.
 *
 * ## Validation
 *
 * Both [configuration] and [id] must be non-empty strings. Attempting to create a CID with empty
 * values will throw [IllegalArgumentException].
 *
 * @property configuration The configuration type (non-empty, e.g., "messaging", "call", "feed")
 * @property id The resource identifier within the configuration (non-empty, e.g., "general",
 *   "support")
 * @see parse Factory method for creating a CID from a formatted string
 * @see fromTypeAndId Factory method for creating a CID from separate configuration and id
 *   components
 * @see formatted Returns the string representation in "configuration:id" format
 */
@ConsistentCopyVisibility
@StreamPublishedApi
public data class StreamCid
private constructor(
    /**
     * The configuration type component of the CID.
     *
     * Common configuration types include:
     * - `"messaging"` - Messaging channels
     * - `"livestream"` - Live streaming channels
     * - `"call"` - Audio/video calls
     * - `"feed"` - Activity feeds
     * - `"team"` - Team collaboration
     *
     * This value is guaranteed to be non-empty.
     *
     * @see id The resource identifier component
     */
    public val configuration: String,
    /**
     * The unique identifier for the resource within its [configuration].
     *
     * This can be any non-empty string that uniquely identifies the resource within its
     * configuration type. Common patterns include:
     * - User IDs: `"user-123"`
     * - Resource names: `"general"`, `"support"`, `"random"`
     * - Generated IDs: `"ch-abc123"`, `UUID.randomUUID().toString()`
     *
     * This value is guaranteed to be non-empty.
     *
     * @see configuration The configuration type component
     */
    public val id: String,
) {

    public companion object {

        /**
         * Parses a formatted CID string into a [StreamCid] instance.
         *
         * This method parses a CID from its standard string representation `"configuration:id"` and
         * creates a [StreamCid] instance with the extracted configuration and id components.
         *
         * ## Format Requirements
         *
         * The input string must:
         * - Be non-empty
         * - Contain exactly one colon (`:`) separator
         * - Have non-empty configuration and id components on both sides of the colon
         *
         * ## Examples
         *
         * ```kotlin
         * // Valid CIDs
         * val cid1 = StreamCid.parse("messaging:general")
         * val cid2 = StreamCid.parse("livestream:sports-2023")
         * val cid3 = StreamCid.parse("call:team-meeting")
         *
         * // Invalid CIDs (will throw IllegalArgumentException)
         * StreamCid.parse("")  // Empty string
         * StreamCid.parse("messaging")  // Missing colon separator
         * StreamCid.parse("messaging:")  // Empty id
         * StreamCid.parse(":general")  // Empty configuration
         * StreamCid.parse("messaging:general:extra")  // Too many colons
         * ```
         *
         * @param cid The formatted CID string in "configuration:id" format
         * @return A new [StreamCid] instance with the parsed configuration and id
         * @throws IllegalArgumentException if the input is empty, doesn't contain exactly one
         *   colon, or has empty configuration/id components
         * @see fromTypeAndId Alternative factory method for creating from separate components
         * @see formatted Returns the inverse transformation (CID → String)
         */
        public fun parse(cid: String): StreamCid {
            require(cid.isNotEmpty()) { "CID string cannot be empty" }
            val split = cid.split(":")
            require(split.size == 2) {
                "CID must be in format 'configuration:id' with exactly one colon separator, got: '$cid'"
            }
            return fromTypeAndId(split[0], split[1])
        }

        /**
         * Creates a [StreamCid] from separate configuration and id components.
         *
         * This method constructs a CID directly from its constituent parts without requiring a
         * pre-formatted string. This is useful when you already have the configuration and id as
         * separate values.
         *
         * ## Examples
         *
         * ```kotlin
         * // Create CID from components
         * val cid = StreamCid.fromTypeAndId(type = "messaging", id = "general")
         * println(cid.formatted())  // "messaging:general"
         *
         * // Useful when building CIDs dynamically
         * val configurationType = "call"
         * val resourceId = UUID.randomUUID().toString()
         * val dynamicCid = StreamCid.fromTypeAndId(configurationType, resourceId)
         * ```
         *
         * @param type The configuration type (must be non-empty)
         * @param id The resource identifier (must be non-empty)
         * @return A new [StreamCid] instance with the given configuration and id
         * @throws IllegalArgumentException if either [type] or [id] is empty
         * @see parse Alternative factory method for parsing from formatted strings
         */
        public fun fromTypeAndId(type: String, id: String): StreamCid {
            require(type.isNotEmpty()) { "Configuration type cannot be empty" }
            require(id.isNotEmpty()) { "Resource id cannot be empty" }
            return StreamCid(type, id)
        }
    }

    /**
     * Returns the formatted string representation of this CID.
     *
     * Converts the CID back to its canonical string format `"configuration:id"`. This is the
     * inverse operation of [parse].
     *
     * ## Examples
     *
     * ```kotlin
     * val cid = StreamCid.fromTypeAndId("messaging", "general")
     * println(cid.formatted())  // "messaging:general"
     *
     * // Round-trip parsing
     * val original = "livestream:sports"
     * val parsed = StreamCid.parse(original)
     * val formatted = parsed.formatted()
     * println(original == formatted)  // true
     * ```
     *
     * ## Use Cases
     * - Logging and debugging: Display CIDs in human-readable format
     * - API requests: Send CID as string parameter to backend
     * - Serialization: Convert CID to JSON/XML string representation
     * - UI display: Show resource identifiers to users
     *
     * @return The CID in "configuration:id" format
     * @see parse The inverse operation that creates a CID from this string format
     */
    public fun formatted(): String = "$configuration:$id"
}
