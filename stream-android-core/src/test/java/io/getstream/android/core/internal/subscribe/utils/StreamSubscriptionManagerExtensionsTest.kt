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

package io.getstream.android.core.internal.subscribe.utils

import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options.Retention
import io.getstream.android.core.testing.TestLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.yield

class StreamSubscriptionManagerExtensionsTest {

    @Test
    fun `forEachSuspend invokes suspending block for each listener`() {
        val manager = StreamSubscriptionManager<(String) -> Unit>(TestLogger)
        val recorded = mutableListOf<String>()
        val options = Options(retention = Retention.KEEP_UNTIL_CANCELLED)

        val first = { value: String -> recorded += "first:$value" }
        val second = { value: String -> recorded += "second:$value" }

        manager.subscribe(first, options).getOrThrow()
        manager.subscribe(second, options).getOrThrow()

        manager.forEachSuspend { listener ->
            yield()
            listener("event")
        }

        assertEquals(setOf("first:event", "second:event"), recorded.toSet())
    }
}
