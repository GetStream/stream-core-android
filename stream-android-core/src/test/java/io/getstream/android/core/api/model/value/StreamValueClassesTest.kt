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
package io.getstream.android.core.api.model.value

import kotlin.test.assertFailsWith
import org.junit.Test

class StreamValueClassesTest {
    @Test
    fun `StreamApiKey fromString throws when blank`() {
        assertFailsWith<IllegalArgumentException> { StreamApiKey.fromString("") }
    }

    @Test
    fun `StreamUserId fromString throws when blank`() {
        assertFailsWith<IllegalArgumentException> { StreamUserId.fromString("") }
    }

    @Test
    fun `StreamToken fromString throws when blank`() {
        assertFailsWith<IllegalArgumentException> { StreamToken.fromString("") }
    }
}
