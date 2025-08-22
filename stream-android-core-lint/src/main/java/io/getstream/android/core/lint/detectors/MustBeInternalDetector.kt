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
package io.getstream.android.core.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.StringOption
import com.android.tools.lint.detector.api.TextFormat
import kotlin.text.iterator
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile

class MustBeInternalDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                // Only top-level declarations
                val uFile = node.uastParent as? UFile ?: return

                // Kotlin sources only
                val kt = node.sourcePsi as? KtClassOrObject ?: return

                // Match package against configured glob patterns
                val pkg = uFile.packageName
                val patterns = configuredPackageGlobs(context)
                if (patterns.isEmpty()) return
                if (!patterns.any { pkgMatchesGlob(pkg, it) }) return

                // Visibility:
                // - Allowed: internal, private
                // - Flag: explicit public OR default (no modifier → public)
                val isInternal = kt.hasModifier(KtTokens.INTERNAL_KEYWORD)
                val isPrivate = kt.hasModifier(KtTokens.PRIVATE_KEYWORD)
                val isPublic = kt.hasModifier(KtTokens.PUBLIC_KEYWORD)
                val isProtected =
                    kt.hasModifier(
                        KtTokens.PROTECTED_KEYWORD
                    ) // top-level protected is invalid in Kotlin, but just in
                // case

                if (isInternal || isPrivate) return // OK
                // Default (no modifier) behaves as public in Kotlin → flag
                if (!isPublic && !isProtected) {
                    reportViolation(context, node, kt, /* explicitPublic= */ false)
                    return
                }
                // Explicit public or protected (protected top-level is also not allowed, but treat
                // as violation)
                reportViolation(context, node, kt, /* explicitPublic= */ true)
            }
        }

    private fun reportViolation(
        context: JavaContext,
        node: UClass,
        kt: KtClassOrObject,
        explicitPublic: Boolean,
    ) {
        val explanation = ISSUE.getExplanation(TextFormat.HTML)
        // Use full decl range for robust replacements/insertions
        val declLocation = context.getLocation(kt)

        // Determine keyword to insert before (when no explicit visibility)
        val keyword =
            when (kt) {
                is KtClass ->
                    when {
                        kt.isInterface() -> "interface"
                        kt.isEnum() -> "enum class"
                        else -> "class"
                    }

                is KtObjectDeclaration -> "object"
                else -> "class"
            }

        // Quick-fix A: make internal
        val fixInternal =
            if (explicitPublic) {
                LintFix.create()
                    .name("Make internal")
                    .replace()
                    .range(declLocation)
                    .text("public")
                    .with("internal")
                    .autoFix()
                    .build()
            } else {
                // No explicit visibility → insert
                LintFix.create()
                    .name("Make internal")
                    .replace()
                    .range(declLocation)
                    .pattern("\\b$keyword\\b")
                    .with("internal $keyword")
                    .autoFix()
                    .build()
            }

        // Quick-fix B: make private (allowed)
        val fixPrivate =
            if (explicitPublic) {
                LintFix.create()
                    .name("Make private")
                    .replace()
                    .range(declLocation)
                    .text("public")
                    .with("private")
                    .autoFix()
                    .build()
            } else {
                LintFix.create()
                    .name("Make private")
                    .replace()
                    .range(declLocation)
                    .pattern("\\b$keyword\\b")
                    .with("private $keyword")
                    .autoFix()
                    .build()
            }

        val compositeFix = LintFix.create().alternatives(fixInternal, fixPrivate)

        context.report(ISSUE, node, declLocation, explanation, compositeFix)
    }

    // ----- Configuration -----

    private fun configuredPackageGlobs(context: JavaContext): List<String> {
        val raw =
            context.configuration
                .getOption(ISSUE, OPTION_PACKAGES.name, "*internal*")
                ?.trim()
                .orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Simple glob matcher: `*` → `.*`, `?` → `.`, dot is escaped. Anchored (^…$). */
    private fun pkgMatchesGlob(pkg: String, glob: String): Boolean {
        val regex = globToRegex(glob)
        return regex.matches(pkg)
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        for (ch in glob) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                '.' -> sb.append("\\.")
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        sb.append('$')
        return sb.toString().toRegex()
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(MustBeInternalDetector::class.java, Scope.JAVA_FILE_SCOPE)

        private val OPTION_PACKAGES =
            StringOption(
                name = "packages",
                description =
                    "Comma-separated package **glob** patterns that represent internal packages; declarations here must not be public.",
                explanation =
                    """
                Supports wildcards: '*' matches any sequence (including dots), '?' matches a single char. " +
                Examples: 'io.getstream.core.internal', 'io.getstream.*.internal', 'com.example.internal*'."
            """
                        .trimIndent(),
            )

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                    id = "NoPublicInInternalPackages",
                    briefDescription = "Disallow `public` in internal packages",
                    explanation =
                        """
                Declarations located in packages marked as internal must not be `public`. \
                Use `internal` (preferred) or `private`.
            """
                            .trimIndent(),
                    category = Category.CORRECTNESS,
                    priority = 7,
                    severity = Severity.ERROR,
                    implementation = IMPLEMENTATION,
                )
                .setOptions(listOf(OPTION_PACKAGES))
    }
}
