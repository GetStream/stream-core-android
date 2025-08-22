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
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Flags when a factory/constructor-like call that yields a type with observable or long-lived state
 * is used inline and not kept (stored or returned).
 *
 * WRONG: SomeType(config).state.collect { ... } // call on a temporary instance SomeType(config) //
 * created and discarded
 *
 * OK: val obj = SomeType(config); obj.state.collect { ... } return SomeType(config)
 *
 * Configure one or more interface/class FQNs via lint.xml:
 *
 * <issue id="NotKeepingInstance"> <option name="keepInstanceOf" value="com.example.SomeType,
 * io.getstream.video.android.core.StreamClient"/> </issue>
 *
 * Matching is by assignability: the call's expression type (or resolved return type) must be the
 * same as or a subtype/implementation of ANY listed FQN.
 */
class KeepInstanceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(
            UQualifiedReferenceExpression::class.java, // e.g., SomeType(...).state / .someApi()
            UCallExpression::class.java, // e.g., SomeType(...)
        )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                // Case A: SomeType(config).state / .someApi() — receiver must be the factory call
                val call = node.receiver as? UCallExpression ?: return
                if (!isKeptTypeFactoryCall(context, call)) return

                val location = context.getLocation(node)
                val shortMsg = "Store the created instance; avoid calling members on a temporary."

                val factoryText = call.sourcePsi?.text ?: "/* factory(...) */"
                val selectorText = node.selector?.sourcePsi?.text ?: "/* member */"

                val localBase = suggestLocalNameFromType(call) ?: "instance"
                val localName = ensureUniqueName(localBase, node)

                // When this qualified expression is a standalone statement, offer a 2-line refactor
                if (isStandaloneStatement(node)) {
                    val fix =
                        LintFix.create()
                            .name("Extract instance to a local variable and use it")
                            .replace()
                            .range(location)
                            .with("val $localName = $factoryText\n$localName.$selectorText")
                            .reformat(true)
                            .autoFix()
                            .build()
                    context.report(ISSUE, node, location, shortMsg, fix)
                } else {
                    // Otherwise, just report; the user can still refactor manually.
                    context.report(ISSUE, node, location, shortMsg, null)
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Case B: SomeType(config) used as a standalone statement (discarded)
                if (!isKeptTypeFactoryCall(context, node)) return

                // Allowed usages: assigned/kept or returned, or used as receiver of a qualified
                // call
                val p = node.uastParent
                if (p is UVariable || p is UReturnExpression || p is UQualifiedReferenceExpression)
                    return

                // Also allow plain assignments (psi-level)
                val psiParent = node.sourcePsi?.parent
                if (psiParent is PsiAssignmentExpression) return

                val location = context.getLocation(node)
                val shortMsg = "The created instance is discarded. Store it or return it."

                if (isStandaloneStatement(node)) {
                    val factoryText = node.sourcePsi?.text ?: "/* factory(...) */"
                    val localBase = suggestLocalNameFromType(node) ?: "instance"
                    val localName = ensureUniqueName(localBase, node)

                    val fix =
                        LintFix.create()
                            .name("Store instance in a local variable")
                            .replace()
                            .range(location)
                            .with("val $localName = $factoryText")
                            .reformat(true)
                            .autoFix()
                            .build()
                    context.report(ISSUE, node, location, shortMsg, fix)
                } else {
                    context.report(ISSUE, node, location, shortMsg, null)
                }
            }
        }

    // ---- Helpers ----

    private fun isStandaloneStatement(u: UElement): Boolean {
        // Expression used as its own statement (Java) or directly in a block/file (Kotlin)
        val psiParent = u.sourcePsi?.parent
        return psiParent is PsiExpressionStatement ||
            u.uastParent is UBlockExpression ||
            u.uastParent is UFile
    }

    /** True if the call expression evaluates to ANY configured type (or subtype). */
    private fun isKeptTypeFactoryCall(context: JavaContext, call: UCallExpression): Boolean {
        val targets = configuredTypeFqns(context)
        if (targets.isEmpty()) return false

        // Prefer expression type (handles Kotlin nicely)
        call.getExpressionType()?.let { exprType ->
            if (targets.any { context.evaluator.typeMatches(exprType, it) }) return true
            val exprClass = (exprType as? PsiClassType)?.resolve()
            if (
                exprClass != null &&
                    targets.any { context.evaluator.inheritsFrom(exprClass, it, false) }
            ) {
                return true
            }
        }

        // Fallback: resolved method return type (helps some Java interop or constructor cases)
        val resolved = call.resolve() as? PsiMethod ?: return false
        val retType: PsiType = resolved.returnType ?: return false
        if (targets.any { context.evaluator.typeMatches(retType, it) }) return true
        val retClass = (retType as? PsiClassType)?.resolve()
        return retClass != null &&
            targets.any { context.evaluator.inheritsFrom(retClass, it, false) }
    }

    private fun configuredTypeFqns(context: JavaContext): List<String> {
        val raw = context.configuration.getOption(ISSUE, OPTION_KEEP_INSTANCE_OF.name).orEmpty()
        val fromConfig = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return if (fromConfig.isNotEmpty()) fromConfig
        else
            listOf(
                // defaults so integrators don't need lint.xml
                "io.getstream.video.android.core.StreamClient" // add more as needed
            )
    }

    /**
     * Suggest a readable local variable name from the constructed type, e.g. `streamClient`. Falls
     * back to "instance".
     */
    private fun suggestLocalNameFromType(call: UCallExpression): String? {
        // Try expression type first
        call.getExpressionType()?.let { t ->
            simpleNameOfType(t)?.let {
                return decapitalizeAscii(sanitizeIdentifier(it))
            }
        }
        // Fallback: method return type
        val m = call.resolve() as? PsiMethod
        val t = m?.returnType
        val n = t?.let { simpleNameOfType(it) }
        return n?.let { decapitalizeAscii(sanitizeIdentifier(it)) }
    }

    private fun simpleNameOfType(t: PsiType): String? {
        val cls = (t as? PsiClassType)?.resolve() ?: return null
        return cls.name
    }

    private fun sanitizeIdentifier(simple: String): String {
        // If it starts with a non-letter, prefix with "instance"
        val cleaned = simple.trim().ifEmpty { "instance" }
        val head = cleaned.firstOrNull() ?: return "instance"
        return if (head.isLetter()) cleaned else "instance$cleaned"
    }

    private fun decapitalizeAscii(s: String): String =
        if (s.isEmpty()) s else s[0].lowercaseChar() + s.substring(1)

    /**
     * Ensures the suggested name does not collide with names visible in the nearest scope. If
     * taken, appends numeric suffixes: name2, name3, ...
     */
    private fun ensureUniqueName(base: String, at: UElement): String {
        val reserved = kotlinKeywords
        val used = collectIdentifiersInScope(at) + reserved
        if (base !in used) return base
        var i = 2
        while (true) {
            val candidate = base + i.toString()
            if (candidate !in used) return candidate
            i++
        }
    }

    /**
     * Collects names of parameters and local variables visible near the insertion site. This is a
     * best-effort scan of the nearest block/method/lambda.
     */
    private fun collectIdentifiersInScope(at: UElement): Set<String> {
        val names = LinkedHashSet<String>()

        // Walk up to find a reasonable scope root
        val scopeRoot = findNearestScope(at)

        // Method/constructor parameters
        (scopeRoot as? UMethod)?.uastParameters?.forEach { it.name?.let(names::add) }

        // Lambda parameters
        (scopeRoot as? ULambdaExpression)?.valueParameters?.forEach { it.name?.let(names::add) }

        // Collect variables declared in the scope
        if (scopeRoot is UBlockExpression) {
            collectVariableNames(scopeRoot, names)
        } else if (scopeRoot is UMethod) {
            scopeRoot.uastBody?.let { body -> collectVariableNames(body, names) }
        } else if (scopeRoot is ULambdaExpression) {
            scopeRoot.body?.let { body -> collectVariableNames(body, names) }
        }

        return names
    }

    private fun collectVariableNames(root: UElement, out: MutableSet<String>) {
        root.accept(
            object : AbstractUastVisitor() {
                override fun visitVariable(node: UVariable): Boolean {
                    node.name?.let(out::add)
                    return super.visitVariable(node)
                }
            }
        )
    }

    private fun findNearestScope(at: UElement): UElement {
        // Prefer the nearest block; otherwise method; otherwise lambda; otherwise class
        var cur: UElement? = at
        var method: UMethod? = null
        var lambda: ULambdaExpression? = null
        var cls: UClass? = null
        while (cur != null) {
            when (cur) {
                is UBlockExpression -> return cur
                is UMethod -> method = method ?: cur
                is ULambdaExpression -> lambda = lambda ?: cur
                is UClass -> cls = cls ?: cur
            }
            cur = cur.uastParent
        }
        return method ?: lambda ?: cls ?: at
    }

    private val kotlinKeywords: Set<String> =
        setOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "val",
            "var",
            "when",
            "while",
            "by",
            "catch",
            "constructor",
            "delegate",
            "dynamic",
            "field",
            "file",
            "finally",
            "get",
            "import",
            "init",
            "param",
            "property",
            "receiver",
            "set",
            "setparam",
            "where",
            "actual",
            "abstract",
            "annotation",
            "companion",
            "const",
            "crossinline",
            "data",
            "enum",
            "expect",
            "external",
            "final",
            "infix",
            "inline",
            "inner",
            "internal",
            "lateinit",
            "noinline",
            "open",
            "operator",
            "out",
            "override",
            "private",
            "protected",
            "public",
            "reified",
            "sealed",
            "suspend",
            "tailrec",
            "vararg",
            "it",
        )

    companion object {
        private val IMPLEMENTATION =
            Implementation(KeepInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE)

        private val OPTION_KEEP_INSTANCE_OF =
            StringOption(
                name = "keepInstanceOf",
                description =
                    "Comma-separated FQNs of stateful types that must not be used as temporaries",
                explanation =
                    """
                Any constructor/factory call whose expression type is the same as, or a subtype of, one of these
                fully-qualified names will be treated as a stateful instance that needs to be kept (stored or returned),
                not created and immediately used or discarded.

                Example:
                com.example.SomeType, io.getstream.video.android.core.StreamClient
            """
                        .trimIndent(),
            )

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                    id = "NotKeepingInstance",
                    briefDescription =
                        "Don’t use stateful instances as temporaries or discard them",
                    explanation =
                        """
                Types that expose observable or long-lived state (for example, via StateFlow) must be kept.
                Creating an instance inline and immediately chaining a call, or creating it as a standalone
                expression and discarding the value, can stop updates from being delivered and may leak resources.

                Flags:
                • SomeType(...).state.collect { ... }     // calling members on a temporary instance
                • SomeType(...)                            // created and discarded

                How to fix:
                • Store the instance in a variable and use it, or
                • Return the instance from the current function.

                Correct:
                    val client = StreamClient(config)
                    client.connectionState.collect { /* ... */ }

                Problematic:
                    StreamClient(config).connectionState.collect { /* ... */ }

                Configuration:
                You can configure which types are considered stateful via lint.xml:
                    <issue id="NotKeepingInstance">
                      <option name="keepInstanceOf"
                              value="com.example.SomeType, io.getstream.video.android.core.StreamClient"/>
                    </issue>
            """
                            .trimIndent(),
                    category = Category.CORRECTNESS,
                    priority = 7,
                    severity = Severity.WARNING, // softer signal per your request
                    implementation = IMPLEMENTATION,
                )
                .setOptions(listOf(OPTION_KEEP_INSTANCE_OF))
    }
}
