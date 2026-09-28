/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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

package io.getstream.android.core.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class StreamDesignTest {

    @Test
    fun `inverting the light brand scale gives the dark brand scale`() {
        assertEquals(
            StreamDesign.ColorScale.defaultDark(),
            StreamDesign.ColorScale.defaultLight().inverted(),
        )
    }

    @Test
    fun `inverting twice gives the original scale`() {
        val scale = StreamDesign.ColorScale.defaultLight()
        assertEquals(scale, scale.inverted().inverted())
    }

    @Test
    fun `from places the brand color at s500 and orders the stops from light to dark`() {
        val brand = Color(0xFF6200EE)
        val scale = StreamDesign.ColorScale.from(brand)
        assertEquals(brand, scale.s500)
        val stops =
            listOf(
                scale.s50,
                scale.s100,
                scale.s150,
                scale.s200,
                scale.s300,
                scale.s400,
                scale.s500,
                scale.s600,
                scale.s700,
                scale.s800,
                scale.s900,
            )
        stops.zipWithNext().forEach { (lighter, darker) ->
            assertTrue(
                lighter.luminance() > darker.luminance(),
                "$lighter is not lighter than $darker",
            )
        }
    }

    @Test
    fun `chrome endpoints flip between light and dark`() {
        val light = StreamDesign.ChromeScale.defaultLight()
        val dark = StreamDesign.ChromeScale.defaultDark()
        assertEquals(Color.White, light.s0)
        assertEquals(Color.Black, light.s1000)
        assertEquals(Color.Black, dark.s0)
        assertEquals(Color.White, dark.s1000)
    }

    @Test
    fun `default colors take the accent from the brand scale`() {
        assertEquals(
            StreamDesign.ColorScale.defaultLight().s500,
            StreamDesign.Colors.default().accentPrimary,
        )
        assertEquals(
            StreamDesign.ColorScale.defaultDark().s400,
            StreamDesign.Colors.defaultDark().accentPrimary,
        )
    }

    @Test
    fun `a custom brand scale flows into the derived component tokens`() {
        val brand = StreamDesign.ColorScale.from(Color(0xFF6200EE))
        val colors = StreamDesign.Colors.default(brand = brand)
        assertEquals(brand.s500, colors.buttonPrimaryBg)
        assertEquals(brand.s200, colors.buttonPrimaryBorder)
        assertEquals(brand.s150, colors.labelBgPrimary)
        assertEquals(brand.s500, colors.textLink)
    }

    @Test
    fun `copying a semantic token does not change the derived tokens built from other tokens`() {
        val colors = StreamDesign.Colors.default().copy(accentPrimary = Color.Red)
        assertEquals(Color.Red, colors.buttonPrimaryBg)
        assertEquals(StreamDesign.Colors.default().buttonSecondaryBg, colors.buttonSecondaryBg)
    }

    @Test
    fun `default typography applies the font family to every style`() {
        val fontFamily = FontFamily.Monospace
        val typography = StreamDesign.Typography.default(fontFamily = fontFamily)
        val styles =
            listOf(
                typography.bodyDefault,
                typography.bodyEmphasis,
                typography.bodyLink,
                typography.bodyLinkEmphasis,
                typography.captionDefault,
                typography.captionEmphasis,
                typography.captionLink,
                typography.captionLinkEmphasis,
                typography.headingExtraSmall,
                typography.headingSmall,
                typography.headingMedium,
                typography.headingLarge,
                typography.metadataDefault,
                typography.metadataEmphasis,
                typography.metadataLink,
                typography.metadataLinkEmphasis,
                typography.numericSmall,
                typography.numericMedium,
                typography.numericLarge,
                typography.numericExtraLarge,
            )
        styles.forEach { assertEquals(fontFamily, it.fontFamily) }
    }
}
