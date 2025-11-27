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

package io.getstream.android.core.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*

class StreamApiExplicitMarkerDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() =
        listOf(UClass::class.java, UMethod::class.java, UField::class.java, UFile::class.java)

    override fun createUastHandler(context: JavaContext) =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val uFile = node.getContainingUFileOrNull() ?: return
                if (!context.packageMatchesConfig(uFile.packageName)) return
                checkUAnnotated(context, node, node.sourcePsi as? KtDeclaration)
            }

            override fun visitMethod(node: UMethod) {
                val uFile = node.getContainingUFileOrNull() ?: return
                if (!context.packageMatchesConfig(uFile.packageName)) return
                checkUAnnotated(context, node, node.sourcePsi as? KtNamedFunction)
            }

            override fun visitField(node: UField) {
                val uFile = node.getContainingUFileOrNull() ?: return
                if (!context.packageMatchesConfig(uFile.packageName)) return
                checkUAnnotated(context, node, node.sourcePsi as? KtProperty)
            }

            override fun visitFile(node: UFile) {
                val ktFile = node.sourcePsi as? KtFile ?: return
                val pkg = ktFile.packageFqName.asString()
                if (!context.packageMatchesConfig(pkg)) return
                ktFile.declarations.filterIsInstance<KtTypeAlias>().forEach { alias ->
                    checkTypeAlias(context, alias)
                }
            }
        }

    private fun checkUAnnotated(context: JavaContext, u: UElement, kt: KtDeclaration?) {
        kt ?: return
        if (!kt.isTopLevelPublic()) return
        val annotated = (u as? UAnnotated)?.hasAnyAnnotation(MARKERS) ?: false
        if (annotated) return
        reportWithDualFix(context, u, kt)
    }

    private fun checkTypeAlias(context: JavaContext, alias: KtTypeAlias) {
        if (!alias.isTopLevelPublic()) return
        val annotated = alias.hasAnyAnnotationPsi(MARKERS_SIMPLE)
        if (annotated) return
        reportWithDualFix(context, alias, alias)
    }

    private fun reportWithDualFix(context: JavaContext, node: KtTypeAlias, decl: KtDeclaration) {
        val loc = context.getLocation(decl)
        val fixPublished =
            LintFix.create()
                .name("Annotate with @$PUBLISHED_SIMPLE")
                .replace()
                .range(loc)
                .pattern("^")
                .with("@$PUBLISHED_FQ\n")
                .reformat(true)
                .shortenNames()
                .autoFix()
                .build()
        val fixInternal =
            LintFix.create()
                .name("Annotate with @$INTERNAL_SIMPLE")
                .replace()
                .range(loc)
                .pattern("^")
                .with("@$INTERNAL_FQ\n")
                .reformat(true)
                .shortenNames()
                .autoFix()
                .build()

        context.report(
            ISSUE,
            node,
            loc,
            "Public API must be explicitly marked with @$PUBLISHED_SIMPLE or @$INTERNAL_SIMPLE.",
            LintFix.create().group(fixPublished, fixInternal),
        )
    }

    private fun reportWithDualFix(context: JavaContext, node: UElement, decl: KtDeclaration) {
        val loc = context.getLocation(decl)
        val fixPublished =
            LintFix.create()
                .name("Annotate with @$PUBLISHED_SIMPLE")
                .replace()
                .range(loc)
                .pattern("^")
                .with("@$PUBLISHED_FQ\n")
                .reformat(true)
                .shortenNames()
                .autoFix()
                .build()
        val fixInternal =
            LintFix.create()
                .name("Annotate with @$INTERNAL_SIMPLE")
                .replace()
                .range(loc)
                .pattern("^")
                .with("@$INTERNAL_FQ\n")
                .reformat(true)
                .shortenNames()
                .autoFix()
                .build()

        context.report(
            ISSUE,
            node,
            loc,
            "Public API must be explicitly marked with @$PUBLISHED_SIMPLE or @$INTERNAL_SIMPLE.",
            LintFix.create().group(fixPublished, fixInternal),
        )
    }

    // ----- package filtering helpers -----

    private fun JavaContext.packageMatchesConfig(pkg: String): Boolean {
        val patterns = configuredPackageGlobs()

        // Default if not configured → io.getstream.android.core.api and its subpackages.
        val effectivePatterns =
            patterns.ifEmpty {
                listOf("io.getstream.android.core.api", "io.getstream.android.core.api.*")
            }

        val included = effectivePatterns.any { pkgMatchesGlob(pkg, it) }
        val excluded = packageMatchesExcludeConfig(pkg)
        return included && !excluded
    }

    private fun JavaContext.packageMatchesExcludeConfig(pkg: String): Boolean {
        val raw = configuration.getOption(ISSUE, OPTION_PACKAGES_EXCLUDE.name, "")?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val patterns = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return patterns.any { pkgMatchesGlob(pkg, it) }
    }

    private fun JavaContext.configuredPackageGlobs(): List<String> {
        val raw = configuration.getOption(ISSUE, OPTION_PACKAGES.name, "")?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Simple glob matcher: `*` → `.*`, `?` → `.`, dot escaped; anchored. */
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

    // ----- misc helpers -----

    private fun UAnnotated.hasAnyAnnotation(qns: Set<String>) =
        qns.any { findAnnotation(it) != null || findAnnotation(it.substringAfterLast('.')) != null }

    private fun KtAnnotated.hasAnyAnnotationPsi(simpleNames: Set<String>): Boolean =
        annotationEntries.any { entry ->
            entry.shortName?.asString() in simpleNames ||
                entry.typeReference?.text in simpleNames // handles rare fully-qualified usage
        }

    private fun UElement.getContainingUFileOrNull(): UFile? {
        var cur: UElement? = this
        while (cur != null) {
            if (cur is UFile) return cur
            cur = cur.uastParent
        }
        return null
    }

    private fun KtDeclaration.isTopLevelPublic(): Boolean {
        if (parent !is KtFile) return false
        val mods = modifierList
        val isPublic =
            mods?.hasModifier(KtTokens.PUBLIC_KEYWORD) == true ||
                !(mods?.hasModifier(KtTokens.PRIVATE_KEYWORD) == true ||
                    mods?.hasModifier(KtTokens.PROTECTED_KEYWORD) == true ||
                    mods?.hasModifier(KtTokens.INTERNAL_KEYWORD) == true)
        return isPublic
    }

    companion object {
        private const val PUBLISHED_FQ = "io.getstream.android.core.annotations.StreamPublishedApi"
        private const val PUBLISHED_SIMPLE = "StreamPublishedApi"
        private const val INTERNAL_FQ = "io.getstream.android.core.annotations.StreamInternalApi"
        private const val INTERNAL_SIMPLE = "StreamInternalApi"
        private val MARKERS = setOf(PUBLISHED_FQ, INTERNAL_FQ)
        private val MARKERS_SIMPLE = setOf(PUBLISHED_SIMPLE, INTERNAL_SIMPLE)

        private val OPTION_PACKAGES =
            StringOption(
                name = "packages",
                description = "Comma-separated package **glob** patterns where the rule applies.",
                explanation =
                    """
                        Supports wildcards: '*' (any sequence) and '?' (single char).
                        Examples:
                        - 'io.getstream.android.core.api'
                        - 'io.getstream.android.core.*.api'
                        - 'io.getstream.android.*'
                    """
                        .trimIndent(),
            )

        private val OPTION_PACKAGES_EXCLUDE =
            StringOption(
                name = "exclude_packages",
                description = "Comma-separated package **glob** patterns to exclude from the rule.",
                explanation =
                    """
                        Same glob syntax as 'packages'. Evaluated after includes.
                    """
                        .trimIndent(),
            )

        private val IMPLEMENTATION =
            Implementation(StreamApiExplicitMarkerDetector::class.java, Scope.JAVA_FILE_SCOPE)

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                    "StreamApiExplicitMarkerMissing",
                    "Public API must be explicitly marked",
                    """
                    To prevent accidental exposure, all top-level public declarations must be explicitly \
                    marked as @StreamPublishedApi (allowed to leak) or @StreamInternalApi (not allowed to leak).
                    """
                        .trimIndent(),
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION,
                )
                .setOptions(listOf(OPTION_PACKAGES, OPTION_PACKAGES_EXCLUDE))
    }
}
