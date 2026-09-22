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

@file:Suppress("MagicNumber", "LongMethod", "LongParameterList")

package io.getstream.android.core.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.getstream.android.core.ui.design.StreamDesign.Colors.Companion.default
import io.getstream.android.core.ui.design.StreamDesign.Colors.Companion.defaultDark

/**
 * The Stream Design System namespace: the token layer shared by the Stream SDKs.
 * - [ColorScale] is the brand (accent) color ramp.
 * - [ChromeScale] is the chrome (neutral gray) color ramp.
 * - [Colors] holds the semantic color tokens derived from the scales above.
 * - [Typography] holds the text styles.
 *
 * Product-specific tokens live in each SDK and derive from these.
 *
 * Use through the SDK theme, for example `VideoTheme` or `ChatTheme`:
 * ```
 * VideoTheme(
 *     colors = StreamDesign.Colors.default().copy(accentPrimary = ...),
 *     typography = StreamDesign.Typography.default(fontFamily = ...),
 * ) { content() }
 * ```
 */
public object StreamDesign {

    /**
     * Semantic color tokens. Customize through [default] / [defaultDark] parameters or [copy].
     *
     * @param brand The brand (accent) color scale. See [ColorScale].
     * @param chrome The chrome (neutral gray) color scale. See [ChromeScale].
     * @param accentPrimary The main brand color. Used for interactive elements, buttons, links, and
     *   primary actions. Override this to apply your brand color across the SDK.
     * @param accentSuccess Indicates a positive or completed state. Used for confirmations, sent
     *   indicators, and success feedback.
     * @param accentWarning Indicates a cautionary state that requires attention. Used for
     *   moderation flags and non-critical alerts.
     * @param accentError Indicates a failure or destructive state. Used for failed messages,
     *   validation errors, and deletions.
     * @param accentNeutral A mid-tone gray for de-emphasized UI elements.
     * @param textPrimary Main body text. Used for message content, titles, and any text that
     *   carries primary meaning.
     * @param textSecondary Supporting metadata text. Used for timestamps, subtitles, and secondary
     *   labels.
     * @param textTertiary De-emphasized text. Used for hints, placeholders, and lowest-priority
     *   supporting information.
     * @param textOnInverse Text on inverse-colored surfaces. Flips between light and dark to
     *   maintain legibility when the background inverts.
     * @param textOnAccent Text on accent-colored surfaces. Stays white in both light and dark mode
     *   since the accent background doesn't invert.
     * @param textDisabled Text for non-interactive or unavailable states. Communicates that an
     *   element can't be interacted with.
     * @param textLink Hyperlinks and inline actions. Uses the brand color to signal interactivity
     *   within text content.
     * @param backgroundCoreApp The outermost application background. Sits behind all surfaces and
     *   is generally not overridden directly.
     * @param backgroundCoreElevation0 The base layer. Always white, used as the reference point for
     *   the elevation scale. Steps above this gain depth in dark mode through progressively lighter
     *   backgrounds.
     * @param backgroundCoreElevation1 Slightly raised surfaces. Used for content containers that
     *   sit directly on the base layer, such as the message list and channel list.
     * @param backgroundCoreElevation2 Floating and modal surfaces. Used for popovers, dropdowns,
     *   dialogs, and any element that interrupts the content flow.
     * @param backgroundCoreElevation3 Used for badge counts that float above other UI elements.
     * @param backgroundCoreOnElevation Background for controls placed on an elevated surface.
     *   Matches the surface in light mode and lifts in dark mode so the control keeps its edge.
     * @param backgroundCoreSurfaceDefault Background for sectioned content areas. Used for grouped
     *   containers, and distinct content regions.
     * @param backgroundCoreSurfaceSubtle A slightly receded background. Used for secondary
     *   containers or to create soft visual separation.
     * @param backgroundCoreSurfaceCard Background for contained, card-style elements. Matches the
     *   surface in light mode but lifts slightly in dark mode to maintain visual separation.
     * @param backgroundCoreSurfaceStrong A more prominent background. Used for elements that need
     *   to stand out from the main surface.
     * @param backgroundCoreInverse The opposite of the primary surface. Used for tooltips,
     *   snackbars, and high-contrast floating elements.
     * @param backgroundCoreOnAccent Background for elements placed on an accent-colored surface.
     *   Ensures legibility against brand colors.
     * @param backgroundCoreHighlight A tint for drawing attention to content. Used for highlights
     *   and pinned messages.
     * @param backgroundCoreOverlayLight A light semi-transparent layer. Used to lighten surfaces
     *   and for hover states on dark backgrounds.
     * @param backgroundCoreOverlayDark A dark semi-transparent layer. Used for image overlays.
     * @param backgroundCoreScrim A heavy semi-transparent layer. Used behind sheets, drawers, and
     *   modals to separate them from content.
     * @param backgroundCoreOverlayDarkStrong A dark semi-transparent layer. Used for image
     *   overlays.
     * @param backgroundUtilityHover A semi-transparent overlay applied on pointer hover. Sits on
     *   top of any surface without replacing it.
     * @param backgroundUtilityPressed A slightly stronger overlay applied during an active press or
     *   tap. Provides tactile feedback on interactive elements.
     * @param backgroundUtilitySelected Indicates an active or selected state. Used for selected
     *   messages, active list items, and toggled controls.
     * @param backgroundUtilityDisabled Background for non-interactive elements. Flattens the
     *   element visually to signal unavailability.
     * @param backgroundUtilitySkeletonLoadingBase Base color for the default skeleton loading
     *   gradient. Used as the background tone for placeholder surfaces.
     * @param backgroundUtilitySkeletonLoadingHighlight Highlight color for the default skeleton
     *   loading gradient. Used for the moving shimmer to indicate loading activity.
     * @param borderUtilityHover Applied on pointer hover over bordered interactive elements.
     * @param borderUtilityPressed Applied during an active press on bordered interactive elements.
     * @param borderUtilitySelected Border for selected or active items. Used for highlighted list
     *   items and active controls.
     * @param borderUtilityFocused Focus ring for keyboard and accessibility navigation. Uses the
     *   brand color to signal input focus.
     * @param borderUtilityActive Active input border. Used for focused text fields and selected
     *   form elements.
     * @param borderUtilitySuccess Border for positive or confirmed states. Used on validated inputs
     *   and success indicators.
     * @param borderUtilityWarning Border for cautionary states. Used on inputs or elements
     *   requiring attention.
     * @param borderUtilityError Border for failure or invalid states. Used on failed inputs and
     *   destructive confirmations.
     * @param borderUtilityDisabled Border for non-interactive elements. Matches the disabled
     *   surface to visually flatten the element.
     * @param borderUtilityDisabledOnSurface Border for disabled elements on elevated surfaces.
     *   Stays visually distinct from the surface without drawing attention to a non-interactive
     *   element.
     * @param borderCoreDefault Standard border for surfaces and containers. Used for input fields,
     *   cards, and dividers on neutral backgrounds.
     * @param borderCoreSubtle A lighter border for minimal separation. Used where a full-strength
     *   border would feel too heavy.
     * @param borderCoreStrong An emphatic border for elements that need clear definition. Used for
     *   focused containers and prominent dividers.
     * @param borderCoreOnAccent Border on accent-colored surfaces. Stays white in both modes since
     *   the accent background doesn't invert.
     * @param borderCoreOnSurface Border for elements sitting on elevated surfaces. Uses a stronger
     *   value than default to maintain visible separation when the background is darker.
     * @param borderCoreOnElevation Border for controls placed on an elevated surface. Stronger in
     *   dark mode, where the lifted surface would otherwise close the contrast gap.
     * @param borderCoreOpacitySubtle A very light transparent border. Used as a frame treatment on
     *   images and media attachments.
     * @param borderCoreOpacityStrong A stronger transparent border for elements on colored or dark
     *   backgrounds. Used for waveform bars and similar treatments.
     * @param borderCoreOnInverse Border on inverse-colored surfaces. Stays legible when the
     *   background flips between light and dark mode.
     * @param avatarPresenceBorder The thin outline around the presence dot. Matches the local
     *   surface behind the avatar. In high-contrast it uses the base surface.
     * @param avatarBgDefault avatar bg default
     * @param avatarBgPlaceholder avatar bg placeholder
     * @param avatarTextDefault avatar text default
     * @param avatarTextPlaceholder avatar text placeholder
     * @param systemBgBlur system bg-blur
     * @param systemCaret system caret
     * @param systemScrollbar system scrollbar
     */
    @Immutable
    public data class Colors(
        public val brand: ColorScale,
        public val chrome: ChromeScale,
        public val accentPrimary: Color,
        public val accentSuccess: Color,
        public val accentWarning: Color,
        public val accentError: Color,
        public val accentNeutral: Color,
        public val textPrimary: Color,
        public val textSecondary: Color,
        public val textTertiary: Color,
        public val textOnInverse: Color,
        public val textOnAccent: Color,
        public val textDisabled: Color,
        public val textLink: Color,
        public val backgroundCoreApp: Color,
        public val backgroundCoreElevation0: Color,
        public val backgroundCoreElevation1: Color,
        public val backgroundCoreElevation2: Color,
        public val backgroundCoreElevation3: Color,
        public val backgroundCoreOnElevation: Color,
        public val backgroundCoreSurfaceDefault: Color,
        public val backgroundCoreSurfaceSubtle: Color,
        public val backgroundCoreSurfaceCard: Color,
        public val backgroundCoreSurfaceStrong: Color,
        public val backgroundCoreInverse: Color,
        public val backgroundCoreOnAccent: Color,
        public val backgroundCoreHighlight: Color,
        public val backgroundCoreOverlayLight: Color,
        public val backgroundCoreOverlayDark: Color,
        public val backgroundCoreScrim: Color,
        public val backgroundCoreOverlayDarkStrong: Color,
        public val backgroundUtilityHover: Color,
        public val backgroundUtilityPressed: Color,
        public val backgroundUtilitySelected: Color,
        public val backgroundUtilityDisabled: Color,
        public val backgroundUtilitySkeletonLoadingBase: Color,
        public val backgroundUtilitySkeletonLoadingHighlight: Color,
        public val borderUtilityHover: Color,
        public val borderUtilityPressed: Color,
        public val borderUtilitySelected: Color,
        public val borderUtilityFocused: Color,
        public val borderUtilityActive: Color,
        public val borderUtilitySuccess: Color,
        public val borderUtilityWarning: Color,
        public val borderUtilityError: Color,
        public val borderUtilityDisabled: Color,
        public val borderUtilityDisabledOnSurface: Color,
        public val borderCoreDefault: Color,
        public val borderCoreSubtle: Color,
        public val borderCoreStrong: Color,
        public val borderCoreOnAccent: Color,
        public val borderCoreOnSurface: Color,
        public val borderCoreOnElevation: Color,
        public val borderCoreOpacitySubtle: Color,
        public val borderCoreOpacityStrong: Color,
        public val borderCoreOnInverse: Color,
        public val avatarPresenceBorder: Color,
        public val avatarBgDefault: Color,
        public val avatarBgPlaceholder: Color,
        public val avatarTextDefault: Color,
        public val avatarTextPlaceholder: Color,
        public val systemBgBlur: Color,
        public val systemCaret: Color,
        public val systemScrollbar: Color,
    ) {

        /** Main text inside an input field. */
        public val inputTextDefault: Color = textPrimary

        /** Placeholder text for the input. Lower emphasis than main text. */
        public val inputTextPlaceholder: Color = textTertiary

        /** Placeholder text for the input. Lower emphasis than main text. */
        public val inputTextDisabled: Color = textDisabled

        /**
         * Icons inside the input area (attach, emoji, camera, send when idle). Matches secondary
         * text strength.
         */
        public val inputTextIcon: Color = textTertiary

        /**
         * Icons inside the input area in their active state. Steps up from the resting icon colour
         * to primary text.
         */
        public val inputTextIconActive: Color = textPrimary

        /** Background of the primary button. */
        public val buttonPrimaryBg: Color = accentPrimary

        /** Background of the primary button in its liquid glass variant. */
        public val buttonPrimaryBgLiquidGlass: Color = Color.Transparent

        /** Text of the primary button when it sits on a neutral background. */
        public val buttonPrimaryText: Color = accentPrimary

        /** Text of the primary button when it sits on an accent background. */
        public val buttonPrimaryTextOnAccent: Color = textOnAccent

        /** Text of the primary button when it sits on a dark background. */
        public val buttonPrimaryTextOnDark: Color = textOnInverse

        /** Border of the primary button. */
        public val buttonPrimaryBorder: Color = brand.s200

        /** Border of the primary button when it sits on a dark background. */
        public val buttonPrimaryBorderOnDark: Color = borderCoreOnInverse

        /** Background of the secondary button. */
        public val buttonSecondaryBg: Color = backgroundCoreSurfaceDefault

        /** Background of the secondary button in its liquid glass variant. */
        public val buttonSecondaryBgLiquidGlass: Color = backgroundCoreElevation0

        /** Text of the secondary button. */
        public val buttonSecondaryText: Color = textPrimary

        /** Text of the secondary button when it sits on an accent background. */
        public val buttonSecondaryTextOnAccent: Color = textPrimary

        /** Text of the secondary button when it sits on a dark background. */
        public val buttonSecondaryTextOnDark: Color = textOnInverse

        /** Border of the secondary button. */
        public val buttonSecondaryBorder: Color = borderCoreDefault

        /** Border of the secondary button when it sits on a dark background. */
        public val buttonSecondaryBorderOnDark: Color = borderCoreOnInverse

        /** Background of the destructive button. */
        public val buttonDestructiveBg: Color = accentError

        /** Background of the destructive button in its liquid glass variant. */
        public val buttonDestructiveBgLiquidGlass: Color = backgroundCoreElevation0

        /** Text of the destructive button when it sits on a neutral background. */
        public val buttonDestructiveText: Color = accentError

        /** Text of the destructive button when it sits on an accent background. */
        public val buttonDestructiveTextOnAccent: Color = textOnAccent

        /** Text of the destructive button when it sits on a dark background. */
        public val buttonDestructiveTextOnDark: Color = textOnInverse

        /** Border of the destructive button. */
        public val buttonDestructiveBorder: Color = accentError

        /** Border of the destructive button when it sits on a dark background. */
        public val buttonDestructiveBorderOnDark: Color = textOnInverse

        /**
         * The green online indicator. Uses success accent in normal themes. In high-contrast, color
         * is dropped and replaced with strong black for maximum clarity.
         */
        public val avatarPresenceBgOnline: Color = accentSuccess

        /**
         * The green online indicator. Uses success accent in normal themes. In high-contrast, color
         * is dropped and replaced with strong black for maximum clarity.
         */
        public val avatarPresenceBgOffline: Color = accentNeutral

        /** Text of system-level UI elements. */
        public val systemText: Color = chrome.s1000

        /** Background of the default badge. */
        public val badgeBgDefault: Color = backgroundCoreElevation3

        /** Background of the primary badge. */
        public val badgeBgPrimary: Color = accentPrimary

        /** Background of the neutral badge. */
        public val badgeBgNeutral: Color = accentNeutral

        /** Background of the error badge. */
        public val badgeBgError: Color = accentError

        /** Background of the inverse badge. */
        public val badgeBgInverse: Color = chrome.s1000

        /** Background of the overlay badge. */
        public val badgeBgOverlay: Color = Color(0xBF000000)

        /** Text of the default badge. */
        public val badgeText: Color = textPrimary

        /** Text of the inverse badge. */
        public val badgeTextOnInverse: Color = textOnInverse

        /** Text of a badge on an accent background. */
        public val badgeTextOnAccent: Color = textOnAccent

        /** Border of the badge. */
        public val badgeBorder: Color = borderCoreOnInverse

        /** Fill of the progress bar. */
        public val controlProgressBarFill: Color = accentNeutral

        /** Track of the progress bar. */
        public val controlProgressBarTrack: Color = backgroundCoreSurfaceStrong

        /** Progress fill for audio playback, distinct from the neutral fill used elsewhere. */
        public val controlProgressBarFillAudio: Color = accentPrimary

        /** Background of the toggle switch when it is off. */
        public val controlToggleSwitchBg: Color = accentNeutral

        /** Background of the toggle switch when it is on. */
        public val controlToggleSwitchBgSelected: Color = accentPrimary

        /** Background of the toggle switch when it is disabled. */
        public val controlToggleSwitchBgDisabled: Color = backgroundUtilityDisabled

        /** Knob of the toggle switch. */
        public val controlToggleSwitchKnob: Color = backgroundCoreOnAccent

        /** Background of the checkbox when it is unchecked. */
        public val controlCheckboxBg: Color = Color.Transparent

        /** Border of the checkbox. */
        public val controlCheckboxBorder: Color = borderCoreDefault

        /** Background of the checkbox when it is checked. */
        public val controlCheckboxBgSelected: Color = accentPrimary

        /** Check mark of the checkbox. */
        public val controlCheckboxIcon: Color = textOnAccent

        /** Background of the radio button when it is unselected. */
        public val controlRadioButtonBg: Color = Color.Transparent

        /** Border of the radio button. */
        public val controlRadioButtonBorder: Color = borderCoreDefault

        /** Background of the radio button when it is selected. */
        public val controlRadioButtonBgSelected: Color = accentPrimary

        /** Indicator dot of the selected radio button. */
        public val controlRadioButtonIndicator: Color = textOnAccent

        /** Background of the radio check when it is unselected. */
        public val controlRadioCheckBg: Color = Color.Transparent

        /** Border of the radio check. */
        public val controlRadioCheckBorder: Color = borderCoreDefault

        /** Background of the radio check when it is selected. */
        public val controlRadioCheckBgSelected: Color = accentPrimary

        /** Check mark of the selected radio check. */
        public val controlRadioCheckIcon: Color = textOnAccent

        /** Border of the chip. */
        public val controlChipBorder: Color = borderCoreDefault

        /** Text of the chip. */
        public val controlChipText: Color = textPrimary

        /** Background for a neutral label. */
        public val labelBgNeutral: Color = chrome.s150

        /** Background for a primary label. */
        public val labelBgPrimary: Color = brand.s150

        /** Text on a neutral label. */
        public val labelTextNeutral: Color = textPrimary

        /** Text on a primary label. */
        public val labelTextPrimary: Color = brand.s900

        /** The underline on the selected tab. */
        public val tabIndicator: Color = accentPrimary

        /** Label colour of an unselected tab. */
        public val tabText: Color = textSecondary

        /** Label colour of the selected tab. */
        public val tabTextSelected: Color = accentPrimary

        /** The line running under the tab bar. */
        public val tabTrack: Color = borderCoreDefault

        /** Factories for the default light and dark color sets. */
        public companion object {
            /**
             * The default colors for the light theme.
             *
             * @param brand The brand color scale. Defaults to [ColorScale.defaultLight].
             * @param chrome The chrome color scale. Defaults to [ChromeScale.defaultLight].
             */
            public fun default(
                brand: ColorScale = ColorScale.defaultLight(),
                chrome: ChromeScale = ChromeScale.defaultLight(),
            ): Colors =
                Colors(
                    brand = brand,
                    chrome = chrome,
                    accentPrimary = brand.s500,
                    accentSuccess = StreamPrimitiveColors.green400,
                    accentWarning = StreamPrimitiveColors.yellow200,
                    accentError = StreamPrimitiveColors.red500,
                    accentNeutral = chrome.s500,
                    textPrimary = chrome.s900,
                    textSecondary = chrome.s700,
                    textTertiary = chrome.s500,
                    textOnInverse = chrome.s0,
                    textOnAccent = chrome.s0,
                    textDisabled = chrome.s300,
                    textLink = brand.s500,
                    backgroundCoreApp = chrome.s0,
                    backgroundCoreElevation0 = chrome.s0,
                    backgroundCoreElevation1 = chrome.s0,
                    backgroundCoreElevation2 = chrome.s0,
                    backgroundCoreElevation3 = chrome.s0,
                    backgroundCoreOnElevation = chrome.s100,
                    backgroundCoreSurfaceDefault = chrome.s100,
                    backgroundCoreSurfaceSubtle = chrome.s50,
                    backgroundCoreSurfaceCard = chrome.s50,
                    backgroundCoreSurfaceStrong = chrome.s150,
                    backgroundCoreInverse = chrome.s1000,
                    backgroundCoreOnAccent = chrome.s0,
                    backgroundCoreHighlight = StreamPrimitiveColors.yellow50,
                    backgroundCoreOverlayLight = Color(0xBFFFFFFF),
                    backgroundCoreOverlayDark = Color(0x401A1B25),
                    backgroundCoreScrim = Color(0x801A1B25),
                    backgroundCoreOverlayDarkStrong = Color(0xBF1A1B25),
                    backgroundUtilityHover = Color(0x1A1A1B25),
                    backgroundUtilityPressed = Color(0x261A1B25),
                    backgroundUtilitySelected = Color(0x331A1B25),
                    backgroundUtilityDisabled = chrome.s100,
                    backgroundUtilitySkeletonLoadingBase = Color.Transparent,
                    backgroundUtilitySkeletonLoadingHighlight = Color(0xBFFFFFFF),
                    borderUtilityHover = Color(0x1A1A1B25),
                    borderUtilityPressed = Color(0x331A1B25),
                    borderUtilitySelected = Color(0x261A1B25),
                    borderUtilityFocused = brand.s150,
                    borderUtilityActive = brand.s500,
                    borderUtilitySuccess = StreamPrimitiveColors.green400,
                    borderUtilityWarning = StreamPrimitiveColors.yellow200,
                    borderUtilityError = StreamPrimitiveColors.red500,
                    borderUtilityDisabled = chrome.s100,
                    borderUtilityDisabledOnSurface = chrome.s150,
                    borderCoreDefault = chrome.s150,
                    borderCoreSubtle = chrome.s100,
                    borderCoreStrong = chrome.s300,
                    borderCoreOnAccent = chrome.s0,
                    borderCoreOnSurface = chrome.s300,
                    borderCoreOnElevation = chrome.s150,
                    borderCoreOpacitySubtle = Color(0x1A1A1B25),
                    borderCoreOpacityStrong = Color(0x401A1B25),
                    borderCoreOnInverse = chrome.s0,
                    avatarPresenceBorder = chrome.s0,
                    avatarBgDefault = brand.s150,
                    avatarBgPlaceholder = chrome.s150,
                    avatarTextDefault = brand.s900,
                    avatarTextPlaceholder = chrome.s500,
                    systemBgBlur = Color(0x03FFFFFF),
                    systemCaret = brand.s500,
                    systemScrollbar = Color(0x80000000),
                )

            /**
             * The default colors for the dark theme.
             *
             * @param brand The brand color scale. Defaults to [ColorScale.defaultDark].
             * @param chrome The chrome color scale. Defaults to [ChromeScale.defaultDark].
             */
            public fun defaultDark(
                brand: ColorScale = ColorScale.defaultDark(),
                chrome: ChromeScale = ChromeScale.defaultDark(),
            ): Colors =
                Colors(
                    brand = brand,
                    chrome = chrome,
                    accentPrimary = brand.s400,
                    accentSuccess = StreamPrimitiveColors.green300,
                    accentWarning = StreamPrimitiveColors.yellow150,
                    accentError = StreamPrimitiveColors.red400,
                    accentNeutral = chrome.s500,
                    textPrimary = chrome.s900,
                    textSecondary = chrome.s700,
                    textTertiary = chrome.s500,
                    textOnInverse = chrome.s0,
                    textOnAccent = chrome.s1000,
                    textDisabled = chrome.s300,
                    textLink = brand.s600,
                    backgroundCoreApp = chrome.s0,
                    backgroundCoreElevation0 = chrome.s0,
                    backgroundCoreElevation1 = chrome.s50,
                    backgroundCoreElevation2 = chrome.s100,
                    backgroundCoreElevation3 = chrome.s200,
                    backgroundCoreOnElevation = chrome.s150,
                    backgroundCoreSurfaceDefault = chrome.s100,
                    backgroundCoreSurfaceSubtle = chrome.s50,
                    backgroundCoreSurfaceCard = chrome.s100,
                    backgroundCoreSurfaceStrong = chrome.s150,
                    backgroundCoreInverse = chrome.s1000,
                    backgroundCoreOnAccent = chrome.s1000,
                    backgroundCoreHighlight = StreamPrimitiveColors.yellow800,
                    backgroundCoreOverlayLight = Color(0xBF000000),
                    backgroundCoreOverlayDark = Color(0x80000000),
                    backgroundCoreScrim = Color(0xBF000000),
                    backgroundCoreOverlayDarkStrong = Color(0xBF000000),
                    backgroundUtilityHover = Color(0x26FFFFFF),
                    backgroundUtilityPressed = Color(0x33FFFFFF),
                    backgroundUtilitySelected = Color(0x40FFFFFF),
                    backgroundUtilityDisabled = chrome.s100,
                    backgroundUtilitySkeletonLoadingBase = Color.Transparent,
                    backgroundUtilitySkeletonLoadingHighlight = Color(0xBF000000),
                    borderUtilityHover = Color(0x1AFFFFFF),
                    borderUtilityPressed = Color(0x33FFFFFF),
                    borderUtilitySelected = Color(0x26FFFFFF),
                    borderUtilityFocused = brand.s150,
                    borderUtilityActive = brand.s400,
                    borderUtilitySuccess = StreamPrimitiveColors.green300,
                    borderUtilityWarning = StreamPrimitiveColors.yellow150,
                    borderUtilityError = StreamPrimitiveColors.red400,
                    borderUtilityDisabled = chrome.s100,
                    borderUtilityDisabledOnSurface = chrome.s150,
                    borderCoreDefault = chrome.s200,
                    borderCoreSubtle = chrome.s100,
                    borderCoreStrong = chrome.s300,
                    borderCoreOnAccent = chrome.s1000,
                    borderCoreOnSurface = chrome.s300,
                    borderCoreOnElevation = chrome.s300,
                    borderCoreOpacitySubtle = Color(0x33FFFFFF),
                    borderCoreOpacityStrong = Color(0x40FFFFFF),
                    borderCoreOnInverse = chrome.s0,
                    avatarPresenceBorder = chrome.s0,
                    avatarBgDefault = brand.s150,
                    avatarBgPlaceholder = chrome.s150,
                    avatarTextDefault = brand.s900,
                    avatarTextPlaceholder = chrome.s500,
                    systemBgBlur = Color(0x03000000),
                    systemCaret = brand.s400,
                    systemScrollbar = Color(0x80FFFFFF),
                )
        }
    }

    /**
     * An 11-stop color ramp for the brand (accent) palette.
     *
     * Light themes use the natural order (s50 lightest, s900 darkest); dark themes invert the
     * mapping so perceived intensity stays consistent on dark backgrounds.
     *
     * Re-brand the whole UI from one color:
     * ```
     * val purple = StreamDesign.ColorScale.from(brandColor = Color(0xFF6200EE))
     * VideoTheme(colors = StreamDesign.Colors.default(brand = purple))
     * ```
     *
     * @param s50 The lightest stop in the natural order.
     * @param s100 The 100 stop.
     * @param s150 The 150 stop.
     * @param s200 The 200 stop.
     * @param s300 The 300 stop.
     * @param s400 The 400 stop.
     * @param s500 The base stop. [from] places the brand color here.
     * @param s600 The 600 stop.
     * @param s700 The 700 stop.
     * @param s800 The 800 stop.
     * @param s900 The darkest stop in the natural order.
     */
    @Immutable
    public data class ColorScale(
        public val s50: Color,
        public val s100: Color,
        public val s150: Color,
        public val s200: Color,
        public val s300: Color,
        public val s400: Color,
        public val s500: Color,
        public val s600: Color,
        public val s700: Color,
        public val s800: Color,
        public val s900: Color,
    ) {

        /**
         * Returns a new scale with the stops mirrored around the center (s50 with s900, s100 with
         * s800, and so on). Use it to derive a dark theme counterpart from a light brand ramp.
         */
        @Suppress("DataClassContainsFunctions")
        public fun inverted(): ColorScale =
            ColorScale(
                s50 = s900,
                s100 = s800,
                s150 = s700,
                s200 = s600,
                s300 = s500,
                s400 = s400,
                s500 = s300,
                s600 = s200,
                s700 = s150,
                s800 = s100,
                s900 = s50,
            )

        /** Factories for the default brand scales and for a scale derived from one color. */
        public companion object {
            /** The default brand scale for light themes. */
            public fun defaultLight(): ColorScale =
                ColorScale(
                    s50 = StreamPrimitiveColors.blue50,
                    s100 = StreamPrimitiveColors.blue100,
                    s150 = StreamPrimitiveColors.blue150,
                    s200 = StreamPrimitiveColors.blue200,
                    s300 = StreamPrimitiveColors.blue300,
                    s400 = StreamPrimitiveColors.blue400,
                    s500 = StreamPrimitiveColors.blue500,
                    s600 = StreamPrimitiveColors.blue600,
                    s700 = StreamPrimitiveColors.blue700,
                    s800 = StreamPrimitiveColors.blue800,
                    s900 = StreamPrimitiveColors.blue900,
                )

            /** The default brand scale for dark themes. */
            public fun defaultDark(): ColorScale =
                ColorScale(
                    s50 = StreamPrimitiveColors.blue900,
                    s100 = StreamPrimitiveColors.blue800,
                    s150 = StreamPrimitiveColors.blue700,
                    s200 = StreamPrimitiveColors.blue600,
                    s300 = StreamPrimitiveColors.blue500,
                    s400 = StreamPrimitiveColors.blue400,
                    s500 = StreamPrimitiveColors.blue300,
                    s600 = StreamPrimitiveColors.blue200,
                    s700 = StreamPrimitiveColors.blue150,
                    s800 = StreamPrimitiveColors.blue100,
                    s900 = StreamPrimitiveColors.blue50,
                )

            /**
             * Generates a brand scale from a single [brandColor], placed at [s500]. Lighter stops
             * are interpolated toward white and darker stops toward black. Use [inverted] for the
             * dark theme counterpart. For exact branding provide an explicit [ColorScale].
             */
            public fun from(brandColor: Color): ColorScale =
                ColorScale(
                    s50 = lerp(Color.White, brandColor, 0.04f),
                    s100 = lerp(Color.White, brandColor, 0.08f),
                    s150 = lerp(Color.White, brandColor, 0.16f),
                    s200 = lerp(Color.White, brandColor, 0.26f),
                    s300 = lerp(Color.White, brandColor, 0.42f),
                    s400 = lerp(Color.White, brandColor, 0.65f),
                    s500 = brandColor,
                    s600 = lerp(brandColor, Color.Black, 0.25f),
                    s700 = lerp(brandColor, Color.Black, 0.42f),
                    s800 = lerp(brandColor, Color.Black, 0.58f),
                    s900 = lerp(brandColor, Color.Black, 0.75f),
                )
        }
    }

    /**
     * A 13-stop neutral ramp for the chrome (structural gray) palette: text, backgrounds, borders,
     * surfaces.
     *
     * [s0] and [s1000] are the absolute endpoints (white and black in light, black and white in
     * dark). They absorb the light and dark polarity, so downstream tokens reference chrome stops
     * with the same expression in both themes.
     *
     * @param s0 The first endpoint: white in light themes, black in dark themes.
     * @param s50 The 50 stop.
     * @param s100 The 100 stop.
     * @param s150 The 150 stop.
     * @param s200 The 200 stop.
     * @param s300 The 300 stop.
     * @param s400 The 400 stop.
     * @param s500 The 500 stop.
     * @param s600 The 600 stop.
     * @param s700 The 700 stop.
     * @param s800 The 800 stop.
     * @param s900 The 900 stop.
     * @param s1000 The last endpoint: black in light themes, white in dark themes.
     */
    @Immutable
    public data class ChromeScale(
        public val s0: Color,
        public val s50: Color,
        public val s100: Color,
        public val s150: Color,
        public val s200: Color,
        public val s300: Color,
        public val s400: Color,
        public val s500: Color,
        public val s600: Color,
        public val s700: Color,
        public val s800: Color,
        public val s900: Color,
        public val s1000: Color,
    ) {
        /** Factories for the default chrome scales. */
        public companion object {
            /** The default chrome scale for light themes. */
            public fun defaultLight(): ChromeScale =
                ChromeScale(
                    s0 = StreamPrimitiveColors.baseWhite,
                    s50 = StreamPrimitiveColors.slate50,
                    s100 = StreamPrimitiveColors.slate100,
                    s150 = StreamPrimitiveColors.slate150,
                    s200 = StreamPrimitiveColors.slate200,
                    s300 = StreamPrimitiveColors.slate300,
                    s400 = StreamPrimitiveColors.slate400,
                    s500 = StreamPrimitiveColors.slate500,
                    s600 = StreamPrimitiveColors.slate600,
                    s700 = StreamPrimitiveColors.slate700,
                    s800 = StreamPrimitiveColors.slate800,
                    s900 = StreamPrimitiveColors.slate900,
                    s1000 = StreamPrimitiveColors.baseBlack,
                )

            /** The default chrome scale for dark themes. */
            public fun defaultDark(): ChromeScale =
                ChromeScale(
                    s0 = StreamPrimitiveColors.baseBlack,
                    s50 = StreamPrimitiveColors.neutral900,
                    s100 = StreamPrimitiveColors.neutral800,
                    s150 = StreamPrimitiveColors.neutral700,
                    s200 = StreamPrimitiveColors.neutral600,
                    s300 = StreamPrimitiveColors.neutral500,
                    s400 = StreamPrimitiveColors.neutral400,
                    s500 = StreamPrimitiveColors.neutral300,
                    s600 = StreamPrimitiveColors.neutral200,
                    s700 = StreamPrimitiveColors.neutral150,
                    s800 = StreamPrimitiveColors.neutral100,
                    s900 = StreamPrimitiveColors.neutral50,
                    s1000 = StreamPrimitiveColors.baseWhite,
                )
        }
    }

    /**
     * The text styles of the design system.
     *
     * @param bodyDefault Body text.
     * @param bodyEmphasis Emphasized body text.
     * @param bodyLink Body text that acts as a link.
     * @param bodyLinkEmphasis Emphasized body text that acts as a link.
     * @param captionDefault Captions and supplementary information.
     * @param captionEmphasis Emphasized captions.
     * @param captionLink Caption text that acts as a link.
     * @param captionLinkEmphasis Emphasized caption text that acts as a link.
     * @param headingExtraSmall Extra small headings and section labels.
     * @param headingSmall Small headings.
     * @param headingMedium Medium headings.
     * @param headingLarge Large, prominent headings.
     * @param metadataDefault Metadata and secondary information.
     * @param metadataEmphasis Emphasized metadata.
     * @param metadataLink Metadata text that acts as a link.
     * @param metadataLinkEmphasis Emphasized metadata text that acts as a link.
     * @param numericSmall Micro numeric indicators.
     * @param numericMedium Medium numeric indicators, like an unread count.
     * @param numericLarge Large numeric indicators.
     * @param numericExtraLarge Extra large numeric indicators.
     */
    @Immutable
    public data class Typography(
        public val bodyDefault: TextStyle,
        public val bodyEmphasis: TextStyle,
        public val bodyLink: TextStyle,
        public val bodyLinkEmphasis: TextStyle,
        public val captionDefault: TextStyle,
        public val captionEmphasis: TextStyle,
        public val captionLink: TextStyle,
        public val captionLinkEmphasis: TextStyle,
        public val headingExtraSmall: TextStyle,
        public val headingSmall: TextStyle,
        public val headingMedium: TextStyle,
        public val headingLarge: TextStyle,
        public val metadataDefault: TextStyle,
        public val metadataEmphasis: TextStyle,
        public val metadataLink: TextStyle,
        public val metadataLinkEmphasis: TextStyle,
        public val numericSmall: TextStyle,
        public val numericMedium: TextStyle,
        public val numericLarge: TextStyle,
        public val numericExtraLarge: TextStyle,
    ) {
        /** Factory for the default typography. */
        public companion object {
            /** The default typography, optionally with a custom [fontFamily]. */
            public fun default(fontFamily: FontFamily? = null): Typography =
                Typography(
                    bodyDefault =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightRegular,
                            fontSize = StreamTokens.fontSizeMd,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    bodyEmphasis =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeMd,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    bodyLink =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightRegular,
                            fontSize = StreamTokens.fontSizeMd,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    bodyLinkEmphasis =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeMd,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    captionDefault =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightRegular,
                            fontSize = StreamTokens.fontSizeSm,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    captionEmphasis =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeSm,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    captionLink =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightRegular,
                            fontSize = StreamTokens.fontSizeSm,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    captionLinkEmphasis =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeSm,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    headingExtraSmall =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeSm,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    headingSmall =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeMd,
                            lineHeight = StreamTokens.lineHeightNormal,
                        ),
                    headingMedium =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeLg,
                            lineHeight = StreamTokens.lineHeightRelaxed,
                        ),
                    headingLarge =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeXl,
                            lineHeight = StreamTokens.lineHeightRelaxed,
                        ),
                    metadataDefault =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightRegular,
                            fontSize = StreamTokens.fontSizeXs,
                            lineHeight = StreamTokens.lineHeightTight,
                        ),
                    metadataEmphasis =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeXs,
                            lineHeight = StreamTokens.lineHeightTight,
                        ),
                    metadataLink =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightRegular,
                            fontSize = StreamTokens.fontSizeXs,
                            lineHeight = StreamTokens.lineHeightTight,
                        ),
                    metadataLinkEmphasis =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightSemiBold,
                            fontSize = StreamTokens.fontSizeXs,
                            lineHeight = StreamTokens.lineHeightTight,
                        ),
                    numericSmall =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightBold,
                            fontSize = StreamTokens.fontSizeMicro,
                        ),
                    numericMedium =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightBold,
                            fontSize = StreamTokens.fontSize2xs,
                        ),
                    numericLarge =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightBold,
                            fontSize = StreamTokens.fontSizeXs,
                        ),
                    numericExtraLarge =
                        TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = StreamTokens.fontWeightBold,
                            fontSize = StreamTokens.fontSizeSm,
                        ),
                )
        }
    }
}
