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
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod

/**
 * Flags any **top-level public** declaration in configured packages that is **not annotated**
 * with @StreamCoreApi.
 *
 * Config: <issue id="StreamCoreApiMissing"> <option name="packages"
 * value="io.getstream.android.core.api,io.getstream.other.api" /> </issue>
 */
class StreamCoreApiDetector : Detector(), Detector.UastScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UClass::class.java, UMethod::class.java, UField::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                // Only top-level declarations
                val uFile = node.uastParent as? UFile ?: return
                if (!context.packageMatchesConfig(uFile.packageName)) return

                val kt = node.sourcePsi as? KtClassOrObject ?: return
                if (!kt.isTopLevel()) return
                if (!kt.isPublicTopLevel()) return
                if (node.isAnnotatedWithCoreApi()) return

                reportMissingCoreApi(context, node, kt, declKeyword(kt))
            }

            override fun visitMethod(node: UMethod) {
                val uFile = node.getContainingUFileOrNull() ?: return
                if (!context.packageMatchesConfig(uFile.packageName)) return

                val kt = node.sourcePsi as? KtNamedFunction ?: return
                if (!kt.isTopLevel) return
                if (!kt.isPublicTopLevel()) return
                if ((node as UAnnotated).isAnnotatedWithCoreApi()) return

                reportMissingCoreApi(context, node, kt, "fun")
            }

            override fun visitField(node: UField) {
                val uFile = node.getContainingUFileOrNull() ?: return
                if (!context.packageMatchesConfig(uFile.packageName)) return

                val kt = node.sourcePsi as? KtProperty ?: return
                if (!kt.isTopLevel) return
                if (!kt.isPublicTopLevel()) return
                if ((node as UAnnotated).isAnnotatedWithCoreApi()) return

                val keyword = if (kt.isVar) "var" else "val"
                reportMissingCoreApi(context, node, kt, keyword)
            }
        }

    // ----- Reporting & Fix -----

    private fun reportMissingCoreApi(
        context: JavaContext,
        node: UElement,
        psiDecl: org.jetbrains.kotlin.psi.KtDeclaration,
        declKeyword: String,
    ) {
        val explanation = ISSUE.getExplanation(TextFormat.TEXT)
        val declLocation = context.getLocation(psiDecl)

        // Insert the FQ annotation and let Lint shorten the import automatically.
        val fix =
            LintFix.create()
                .name("Annotate with @StreamCoreApi")
                .replace()
                .range(declLocation)
                // Prepend the annotation at the very start of the declaration text
                .pattern("^")
                .with("@$CORE_API_FQ\n")
                .reformat(true)
                .shortenNames()
                .autoFix()
                .build()

        context.report(ISSUE, node, declLocation, explanation, fix)
    }

    // ----- Helpers -----

    private fun JavaContext.packageMatchesConfig(pkg: String): Boolean {
        val patterns = configuredPackageGlobs()
        if (patterns.isEmpty()) return false
        val included = patterns.any { pkgMatchesGlob(pkg, it) }
        val excluded = packageMatchesExcludeConfig(pkg)
        return included && !excluded
    }

    private fun JavaContext.packageMatchesExcludeConfig(pkg: String): Boolean {
        val raw = configuration.getOption(ISSUE, OPTION_PACKAGES_EXCLUDE.name, "")?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val patterns = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (patterns.isEmpty()) return false
        return patterns.any { pkgMatchesGlob(pkg, it) }
    }

    private fun JavaContext.configuredPackageGlobs(): List<String> {
        val raw = configuration.getOption(ISSUE, OPTION_PACKAGES.name, "")?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Simple glob matcher: `*` → `.*`, `?` → `.`, dot is escaped; anchored. */
    private fun pkgMatchesGlob(pkg: String, glob: String): Boolean = globToRegex(glob).matches(pkg)

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

    private fun UAnnotated.isAnnotatedWithCoreApi(): Boolean =
        findAnnotation(CORE_API_FQ) != null || findAnnotation(CORE_API_SIMPLE) != null

    private fun UMethod.getContainingUFileOrNull(): UFile? {
        var cur: UElement? = this
        while (cur != null) {
            if (cur is UFile) return cur
            cur = cur.uastParent
        }
        return null
    }

    private fun UField.getContainingUFileOrNull(): UFile? {
        var cur: UElement? = this
        while (cur != null) {
            if (cur is UFile) return cur
            cur = cur.uastParent
        }
        return null
    }

    private fun KtClassOrObject.isTopLevel(): Boolean =
        this.parent is org.jetbrains.kotlin.psi.KtFile

    private fun org.jetbrains.kotlin.psi.KtDeclaration.isPublicTopLevel(): Boolean {
        // top-level: public if no visibility modifier or explicit `public`
        val isPublic =
            hasModifier(KtTokens.PUBLIC_KEYWORD) ||
                (!hasModifier(KtTokens.INTERNAL_KEYWORD) &&
                    !hasModifier(KtTokens.PRIVATE_KEYWORD) &&
                    !hasModifier(KtTokens.PROTECTED_KEYWORD))
        return isPublic
    }

    private fun declKeyword(kt: KtClassOrObject): String =
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

    companion object {
        private const val CORE_API_FQ = "io.getstream.android.core.annotations.StreamCoreApi"
        private const val CORE_API_SIMPLE = "StreamCoreApi"

        private val IMPLEMENTATION =
            Implementation(StreamCoreApiDetector::class.java, Scope.JAVA_FILE_SCOPE)

        private val OPTION_PACKAGES =
            StringOption(
                name = "packages",
                description =
                    "Comma-separated package **glob** patterns where top-level public APIs must be annotated with @StreamCoreApi.",
                explanation =
                    """
                    Supports wildcards: '*' (any sequence) and '?' (single char). 
                    Examples:
                    - 'io.getstream.android.core.api'
                    - 'io.getstream.android.core.*.api'
                    - 'io.getstream.android.core.api*'
                    """
                        .trimIndent(),
            )

        private val OPTION_PACKAGES_EXCLUDE =
            StringOption(
                name = "exclude_packages",
                description =
                    "Comma-separated package **glob** patterns where top-level public APIs are excluded from the check.",
                explanation =
                    """
                    Supports wildcards: '*' (any sequence) and '?' (single char). 
                    Examples:
                    - 'io.getstream.android.core.api'
                    - 'io.getstream.android.core.*.api'
                    - 'io.getstream.android.core.api*'
                    """
                        .trimIndent(),
            )

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                    id = "StreamCoreApiMissing",
                    briefDescription = "Missing @StreamCoreApi on public API",
                    explanation =
                        """
                        Top-level public declarations in configured packages must be annotated \
                        with @StreamCoreApi to indicate they are part of the Stream Core API surface.
                        """
                            .trimIndent(),
                    category = Category.CORRECTNESS,
                    priority = 7,
                    severity = Severity.ERROR,
                    implementation = IMPLEMENTATION,
                )
                .setOptions(listOf(OPTION_PACKAGES, OPTION_PACKAGES_EXCLUDE))
    }
}
