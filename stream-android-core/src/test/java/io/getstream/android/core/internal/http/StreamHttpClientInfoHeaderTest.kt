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

@file:Suppress("SpellCheckingInspection")

package io.getstream.android.core.internal.http

import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import org.junit.Assert.*
import org.junit.Test

class StreamHttpClientInfoHeaderTest {

    @Test
    fun `create builds header with sanitized values`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "android-core",
                productVersion = "1.2.3",
                os = "Android",
                apiLevel = 34,
                deviceModel = "Pixel 7 Pro",
                app = "My App",
                appVersion = "2.0.100",
            )

        val expected =
            "android-core-1.2.3|os=Android|api_version=34|device_model=Pixel-7-Pro|app=My-App|app_version=2.0.100"
        assertEquals(expected, header.rawValue)
    }

    @Test
    fun `diacritics and symbols are normalized and collapsed`() {
        // "Café™ SDK" -> "Cafe-SDK"
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "Café™ SDK",
                productVersion = "1.0",
                os = "Android",
                apiLevel = 34,
                deviceModel = "Model X",
            )

        assertTrue(header.rawValue.startsWith("Cafe-SDK-1.0|"))
    }

    @Test
    fun `non-ascii-only value becomes unknown`() {
        // Chinese chars + space -> replaced by spaces -> collapsed -> trimmed -> blank -> "unknown"
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "p",
                productVersion = "1",
                os = "Android",
                apiLevel = 34,
                deviceModel = "设备 型号", // non-ASCII with a space
            )

        assertTrue(header.rawValue.contains("|device_model=unknown|"))
    }

    @Test
    fun `invalid punctuation is replaced with underscores`() {
        // ":" and "#" are not in the safe set -> "_"
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "product",
                productVersion = "v1:beta#2",
                os = "Android",
                apiLevel = 34,
                deviceModel = "M",
            )

        assertTrue(header.rawValue.startsWith("product-v1_beta_2|"))
    }

    @Test
    fun `defaults for app and appVersion are unknown`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "prod",
                productVersion = "0.0.1",
                os = "Android",
                apiLevel = 30,
                deviceModel = "Pixel",
                // app/appVersion use defaults
            )

        assertTrue(header.rawValue.contains("|app=unknown|"))
        assertTrue(header.rawValue.contains("|app_version=unknown"))
    }

    @Test
    fun `ascii safe characters pass through unchanged`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "lib.name-abc_123",
                productVersion = "0.1.2",
                os = "Linux",
                apiLevel = 1,
                deviceModel = "ABC-123_DEF",
            )

        assertTrue(header.rawValue.startsWith("lib.name-abc_123-0.1.2|"))
        assertTrue(header.rawValue.contains("|device_model=ABC-123_DEF|"))
    }

    @Test
    fun `blank product and version sanitize to unknown-unknown`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "   ",
                productVersion = "",
                os = "Android",
                apiLevel = 34,
                deviceModel = "Pixel",
            )

        assertTrue(header.rawValue.startsWith("unknown-unknown|"))
    }

    @Test
    fun `multiple whitespaces collapse to single dash`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "My    Library",
                productVersion = "1.0.0",
                os = "Android",
                apiLevel = 34,
                deviceModel = "Pixel   7    Pro",
            )

        assertTrue(header.rawValue.startsWith("My-Library-1.0.0|"))
        assertTrue(header.rawValue.contains("|device_model=Pixel-7-Pro|"))
    }

    @Test
    fun `unsafe ascii becomes underscore while non-visible becomes dash`() {
        // '+' is unsafe ASCII -> '_'; emoji is non-visible ASCII -> space -> '-'
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "Prod+One 🤖",
                productVersion = "v+2",
                os = "🤖Android",
                apiLevel = 33,
                deviceModel = "SM:123/ABC", // ':' and '/' -> underscores
            )

        assertTrue(header.rawValue.startsWith("Prod_One-"))
        assertTrue(header.rawValue.contains("-v_2|"))
        assertTrue(header.rawValue.contains("|os=Android|"))
        assertTrue(header.rawValue.contains("|device_model=SM_123_ABC|"))
    }

    @Test
    fun `app name and version are included and sanitized`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "android-core",
                productVersion = "1.2.3",
                os = "Android",
                apiLevel = 34,
                deviceModel = "Pixel 7 Pro",
                app = "My Super App!™",
                appVersion = "v1.0.0-beta+exp",
            )
        assertTrue(header.rawValue.contains("|app=My-Super-App_|"))
        assertTrue(header.rawValue.contains("|app_version=v1.0.0-beta_exp"))
    }

    @Test
    fun `app name with only non-ascii becomes unknown`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "android-core",
                productVersion = "1.2.3",
                os = "Android",
                apiLevel = 34,
                deviceModel = "Pixel 7 Pro",
                app = "🤖🤖🤖",
                appVersion = "2.0.0",
            )
        assertTrue(header.rawValue.contains("|app=unknown|"))
    }

    @Test
    fun `app version with unsafe characters is sanitized`() {
        val header =
            StreamHttpClientInfoHeader.Companion.create(
                product = "android-core",
                productVersion = "1.2.3",
                os = "Android",
                apiLevel = 34,
                deviceModel = "Pixel 7 Pro",
                app = "MyApp",
                appVersion = "v1.0.0/rc+exp",
            )
        assertTrue(header.rawValue.contains("|app_version=v1.0.0_rc_exp"))
    }
}
