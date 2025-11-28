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

package io.getstream.android.core.api.filter

import io.getstream.android.core.api.model.location.BoundingBox
import io.getstream.android.core.api.model.location.LocationCoordinate
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FilterMatchingTest {

    @Test
    fun `equal filter should match when field equals value`() {
        val filter = TestFilterField.id.equal("user-123")

        val itemWithMatchingId = TestData(id = "user-123")
        val itemWithDifferentId = TestData(id = "other-456")

        assertTrue(filter matches itemWithMatchingId)
        assertFalse(filter matches itemWithDifferentId)
    }

    @Test
    fun `greater filter should match when field is greater than value`() {
        val filter = TestFilterField.score.greater(80)

        val itemAboveThreshold = TestData(score = 85)
        val itemBelowThreshold = TestData(score = 75)

        assertTrue(filter matches itemAboveThreshold)
        assertFalse(filter matches itemBelowThreshold)
    }

    @Test
    fun `less filter should match when field is less than value`() {
        val filter = TestFilterField.score.less(90)

        val itemBelowThreshold = TestData(score = 85)
        val itemAboveThreshold = TestData(score = 95)

        assertTrue(filter matches itemBelowThreshold)
        assertFalse(filter matches itemAboveThreshold)
    }

    @Test
    fun `greaterOrEqual filter should match when field is greater than or equal to value`() {
        val filter = TestFilterField.score.greaterOrEqual(85)

        val itemExactlyAtThreshold = TestData(score = 85)
        val itemAboveThreshold = TestData(score = 90)
        val itemBelowThreshold = TestData(score = 80)

        assertTrue(filter matches itemExactlyAtThreshold)
        assertTrue(filter matches itemAboveThreshold)
        assertFalse(filter matches itemBelowThreshold)
    }

    @Test
    fun `lessOrEqual filter should match when field is less than or equal to value`() {
        val filter = TestFilterField.rating.lessOrEqual(4.5)

        val itemExactlyAtThreshold = TestData(rating = 4.5)
        val itemBelowThreshold = TestData(rating = 4.0)
        val itemAboveThreshold = TestData(rating = 4.8)

        assertTrue(filter matches itemExactlyAtThreshold)
        assertTrue(filter matches itemBelowThreshold)
        assertFalse(filter matches itemAboveThreshold)
    }

    @Test
    fun `in filter should match when field value is in the collection`() {
        val filter = TestFilterField.id.`in`("user-123", "user-456")

        val itemWithAllowedId = TestData(id = "user-123")
        val itemWithDifferentId = TestData(id = "user-789")
        val itemWithOtherAllowedId = TestData(id = "user-456")

        assertTrue(filter matches itemWithAllowedId)
        assertFalse(filter matches itemWithDifferentId)
        assertTrue(filter matches itemWithOtherAllowedId)
    }

    @Test
    fun `contains filter should match when list contains the value`() {
        val filter = TestFilterField.tags.contains("important")

        val itemWithTargetTag = TestData(tags = listOf("urgent", "important", "other"))
        val itemWithDifferentTags = TestData(tags = listOf("urgent", "normal"))
        val itemWithNoTags = TestData(tags = emptyList())

        assertTrue(filter matches itemWithTargetTag)
        assertFalse(filter matches itemWithDifferentTags)
        assertFalse(filter matches itemWithNoTags)
    }

    @Test
    fun `contains filter should match when map contains all key-value pairs including nested maps`() {
        val filter =
            TestFilterField.metadata.contains(
                mapOf("category" to "test", "config" to mapOf("enabled" to true))
            )

        val itemWithMatchingNestedData =
            TestData(
                metadata =
                    mapOf(
                        "category" to "test",
                        "priority" to 1,
                        "config" to mapOf("enabled" to true, "timeout" to 30),
                    )
            )
        val itemWithDifferentNestedValue =
            TestData(
                metadata =
                    mapOf(
                        "category" to "test",
                        "config" to mapOf("enabled" to false, "timeout" to 30),
                    )
            )
        val itemWithoutNestedMap = TestData(metadata = mapOf("category" to "test", "priority" to 1))

        assertTrue(filter matches itemWithMatchingNestedData)
        assertFalse(filter matches itemWithDifferentNestedValue)
        assertFalse(filter matches itemWithoutNestedMap)
    }

    @Test
    fun `query filter should not match empty query`() {
        val filter = TestFilterField.name.query("")

        val itemWithContent = TestData(name = "any content")
        val itemWithEmptyName = TestData(name = "")

        assertFalse(filter matches itemWithContent)
        assertFalse(filter matches itemWithEmptyName)
    }

    @Test
    fun `query filter should match partial words and case variations`() {
        val filter = TestFilterField.name.query("PROD")

        val itemWithLowercase = TestData(name = "production server")
        val itemWithMixedCase = TestData(name = "Development Production Environment")
        val itemWithPartialMatch = TestData(name = "reproduced issue")
        val itemWithoutMatch = TestData(name = "staging server")

        assertTrue(filter matches itemWithLowercase)
        assertTrue(filter matches itemWithMixedCase)
        assertTrue(filter matches itemWithPartialMatch)
        assertFalse(filter matches itemWithoutMatch)
    }

    @Test
    fun `autocomplete filter should not match empty query`() {
        val filter = TestFilterField.name.autocomplete("")

        val itemWithContent = TestData(name = "any content")
        val itemWithEmptyName = TestData(name = "")

        assertFalse(filter matches itemWithContent)
        assertFalse(filter matches itemWithEmptyName)
    }

    @Test
    fun `autocomplete filter should match word prefixes`() {
        val filter = TestFilterField.name.autocomplete("con")

        val itemWithDotSeparation = TestData(name = "app.config.json")
        val itemWithDashSeparation = TestData(name = "user-configuration-file")
        val itemWithMixedPunctuation = TestData(name = "system/container,settings.xml")
        val itemWithoutWordPrefix = TestData(name = "application")
        val itemWithInWordMatch = TestData(name = "reconstruction")

        assertTrue(filter matches itemWithDotSeparation)
        assertTrue(filter matches itemWithDashSeparation)
        assertTrue(filter matches itemWithMixedPunctuation)
        assertFalse(filter matches itemWithoutWordPrefix)
        assertFalse(filter matches itemWithInWordMatch)
    }

    @Test
    fun `pathExists filter should match when specified path exists in field`() {
        val filter = TestFilterField.metadata.pathExists("user.profile")

        val itemWithPath = TestData(metadata = mapOf("user" to mapOf("profile" to "data")))
        val itemWithoutPath = TestData(metadata = mapOf("other" to "data"))

        assertTrue(filter matches itemWithPath)
        assertFalse(filter matches itemWithoutPath)
    }

    @Test
    fun `exists filter should match when field exists (is not null)`() {
        val existsFilter = TestFilterField.metadata.exists()

        val itemWithMetadata = TestData(metadata = mapOf("key" to "value"))
        val itemWithoutMetadata = TestData(metadata = null)

        assertTrue(existsFilter matches itemWithMetadata)
        assertFalse(existsFilter matches itemWithoutMetadata)
    }

    @Test
    fun `doesNotExist filter should match when field does not exist (is null)`() {
        val doesNotExistFilter = TestFilterField.metadata.doesNotExist()

        val itemWithMetadata = TestData(metadata = mapOf("key" to "value"))
        val itemWithoutMetadata = TestData(metadata = null)

        assertFalse(doesNotExistFilter matches itemWithMetadata)
        assertTrue(doesNotExistFilter matches itemWithoutMetadata)
    }

    @Test
    fun `and filter should match when all filters match`() {
        val filter =
            Filters.and(TestFilterField.score.greater(80), TestFilterField.isActive.equal(true))

        val activeItemWithHighScore = TestData(score = 85, isActive = true)
        val inactiveItemWithHighScore = TestData(score = 92, isActive = false)
        val activeItemWithLowScore = TestData(score = 75, isActive = true)

        assertTrue(filter matches activeItemWithHighScore)
        assertFalse(filter matches inactiveItemWithHighScore)
        assertFalse(filter matches activeItemWithLowScore)
    }

    @Test
    fun `or filter should match when any filter matches`() {
        val filter =
            Filters.or(TestFilterField.score.greater(90), TestFilterField.isActive.equal(true))

        val activeItemWithLowScore = TestData(score = 75, isActive = true)
        val inactiveItemWithHighScore = TestData(score = 95, isActive = false)
        val inactiveItemWithLowScore = TestData(score = 75, isActive = false)

        assertTrue(filter matches activeItemWithLowScore)
        assertTrue(filter matches inactiveItemWithHighScore)
        assertFalse(filter matches inactiveItemWithLowScore)
    }

    @Test
    fun `nested filters should work correctly`() {
        val filter =
            Filters.and(
                TestFilterField.score.greater(75),
                Filters.or(
                    TestFilterField.name.equal("Special Item"),
                    TestFilterField.rating.greater(4.0),
                ),
            )

        val specialItemWithHighScore = TestData(score = 85, name = "Special Item", rating = 3.5)
        val regularItemWithHighScoreAndRating = TestData(score = 90, name = "Regular", rating = 4.2)
        val regularItemWithLowScoreAndRating = TestData(score = 70, name = "Regular", rating = 3.8)

        assertTrue(filter matches specialItemWithHighScore)
        assertTrue(filter matches regularItemWithHighScoreAndRating)
        assertFalse(filter matches regularItemWithLowScoreAndRating)
    }

    @Test
    fun `complex nested filters should work correctly`() {
        val filter =
            Filters.or(
                Filters.and(
                    TestFilterField.score.greaterOrEqual(90),
                    TestFilterField.isActive.equal(false),
                ),
                Filters.and(
                    TestFilterField.rating.lessOrEqual(4.0),
                    TestFilterField.isActive.equal(true),
                ),
            )

        val activeItemWithMediumScoreAndRating = TestData(score = 85, isActive = true, rating = 4.5)
        val inactiveItemWithHighScore = TestData(score = 95, isActive = false, rating = 4.2)
        val activeItemWithLowRating = TestData(score = 80, isActive = true, rating = 3.8)

        assertFalse(filter matches activeItemWithMediumScoreAndRating)
        assertTrue(filter matches inactiveItemWithHighScore)
        assertTrue(filter matches activeItemWithLowRating)
    }

    @Test
    fun `equal filter should match when LocationCoordinate is within BoundingBox`() {
        val northeast = LocationCoordinate(latitude = 41.91, longitude = 12.51)
        val southwest = LocationCoordinate(latitude = 41.87, longitude = 12.47)
        val boundingBox = BoundingBox(northeast = northeast, southwest = southwest)
        val filter = TestFilterField.withinBounds.equal(boundingBox)

        val itemWithLocationInside =
            TestData(location = LocationCoordinate(latitude = 41.89, longitude = 12.49))
        val itemWithLocationOutside =
            TestData(location = LocationCoordinate(latitude = 41.95, longitude = 12.49))

        assertTrue(filter matches itemWithLocationInside)
        assertFalse(filter matches itemWithLocationOutside)
    }

    @Test
    fun `equal filter should work with bounding box map format from API`() {
        val mapBounds =
            mapOf("ne_lat" to 41.91, "ne_lng" to 12.51, "sw_lat" to 41.87, "sw_lng" to 12.47)
        val filter = TestFilterField.withinBounds.equal(mapBounds)

        val itemWithLocationInside =
            TestData(location = LocationCoordinate(latitude = 41.89, longitude = 12.49))
        val itemWithLocationOutside =
            TestData(location = LocationCoordinate(latitude = 41.95, longitude = 12.49))

        assertTrue(filter matches itemWithLocationInside)
        assertFalse(filter matches itemWithLocationOutside)
    }

    private data class TestData(
        val id: String = "default-id",
        val name: String = "Default Name",
        val score: Int = 0,
        val rating: Double = 0.0,
        val tags: List<String> = emptyList(),
        val metadata: Map<String, Any>? = null,
        val isActive: Boolean = false,
        val location: LocationCoordinate? = null,
    )

    private data class TestFilterField<T>(
        override val remote: String,
        override val localValue: (TestData) -> T,
    ) : FilterField<TestData> {
        companion object {
            val id = TestFilterField("id", TestData::id)
            val name = TestFilterField("name", TestData::name)
            val score = TestFilterField("score", TestData::score)
            val rating = TestFilterField("rating", TestData::rating)
            val tags = TestFilterField("tags", TestData::tags)
            val metadata = TestFilterField("metadata", TestData::metadata)
            val isActive = TestFilterField("isActive", TestData::isActive)
            val withinBounds = TestFilterField("within_bounds", TestData::location)
        }
    }
}
