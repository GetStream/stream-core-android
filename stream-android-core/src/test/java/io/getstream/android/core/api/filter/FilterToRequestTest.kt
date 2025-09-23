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

import junit.framework.TestCase
import kotlin.test.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class FilterToRequestTest(
    private val filter: Filter<*, *>,
    private val expectedRequest: Map<String, Any>,
    private val testName: String,
) {

    @Test
    fun `toRequest should convert typed filter to correct request map`() {
        val result = filter.toRequest()
        TestCase.assertEquals("Test case: $testName", expectedRequest, result)
    }

    companion object {
        private data class TestField(override val remote: String) : FilterField<Any> {
            override val localValue: (Any) -> Any? = { fail("Shouldn't be called in these tests") }
        }

        private val idField = TestField("id")
        private val createdAtField = TestField("created_at")
        private val textField = TestField("text")
        private val filterTagsField = TestField("filter_tags")
        private val searchDataField = TestField("search_data")

        @JvmStatic
        @Parameterized.Parameters(name = "{2}")
        fun data(): Collection<Array<Any>> =
            listOf(
                arrayOf(
                    idField.equal("activity-123"),
                    mapOf("id" to mapOf("\$eq" to "activity-123")),
                    "Field equals value",
                ),
                arrayOf(
                    createdAtField.greater(1234567890),
                    mapOf("created_at" to mapOf("\$gt" to 1234567890)),
                    "Field greater than value",
                ),
                arrayOf(
                    createdAtField.greaterOrEqual(1000000000),
                    mapOf("created_at" to mapOf("\$gte" to 1000000000)),
                    "Field greater than or equal to value",
                ),
                arrayOf(
                    createdAtField.less(9999999999),
                    mapOf("created_at" to mapOf("\$lt" to 9999999999)),
                    "Field less than value",
                ),
                arrayOf(
                    createdAtField.lessOrEqual(8888888888),
                    mapOf("created_at" to mapOf("\$lte" to 8888888888)),
                    "Field less than or equal to value",
                ),
                arrayOf(
                    idField.`in`("id1", "id2", "id3"),
                    mapOf("id" to mapOf("\$in" to setOf("id1", "id2", "id3"))),
                    "Field in collection",
                ),
                arrayOf(
                    textField.query("search term"),
                    mapOf("text" to mapOf("\$q" to "search term")),
                    "Field query search",
                ),
                arrayOf(
                    textField.autocomplete("auto prefix"),
                    mapOf("text" to mapOf("\$autocomplete" to "auto prefix")),
                    "Field autocomplete search",
                ),
                arrayOf(idField.exists(), mapOf("id" to mapOf("\$exists" to true)), "Field exists"),
                arrayOf(
                    idField.doesNotExist(),
                    mapOf("id" to mapOf("\$exists" to false)),
                    "Field does not exist",
                ),
                arrayOf(
                    filterTagsField.contains("tag1"),
                    mapOf("filter_tags" to mapOf("\$contains" to "tag1")),
                    "Field contains value",
                ),
                arrayOf(
                    searchDataField.pathExists("user.profile"),
                    mapOf("search_data" to mapOf("\$path_exists" to "user.profile")),
                    "Field path exists",
                ),
                arrayOf(
                    Filters.and(idField.equal("test1"), createdAtField.greater(1234567890)),
                    mapOf(
                        "\$and" to
                            listOf(
                                mapOf("id" to mapOf("\$eq" to "test1")),
                                mapOf("created_at" to mapOf("\$gt" to 1234567890)),
                            )
                    ),
                    "AND filter combination",
                ),
                arrayOf(
                    Filters.or(textField.equal("content1"), textField.equal("content2")),
                    mapOf(
                        "\$or" to
                            listOf(
                                mapOf("text" to mapOf("\$eq" to "content1")),
                                mapOf("text" to mapOf("\$eq" to "content2")),
                            )
                    ),
                    "OR filter combination",
                ),
                arrayOf(
                    Filters.and(
                        idField.equal("main-id"),
                        Filters.or(textField.equal("option1"), textField.equal("option2")),
                    ),
                    mapOf(
                        "\$and" to
                            listOf(
                                mapOf("id" to mapOf("\$eq" to "main-id")),
                                mapOf(
                                    "\$or" to
                                        listOf(
                                            mapOf("text" to mapOf("\$eq" to "option1")),
                                            mapOf("text" to mapOf("\$eq" to "option2")),
                                        )
                                ),
                            )
                    ),
                    "Nested AND containing OR filters",
                ),
            )
    }
}
