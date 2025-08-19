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
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.*

/**
 * Flags calls to `kotlin.runCatching { ... }` inside **suspend** contexts. Suggests replacing with
 * `runCatchingCancellable { ... }` to propagate cancellation.
 */
class SuspendRunCatchingDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val callee = node.methodIdentifier?.name ?: return
                if (callee != "runCatching") return
                if (!isKotlinRunCatching(context, node)) return

                // Are we inside a suspend function or a suspend lambda?
                if (!isInsideSuspendContext(node)) return

                val nameLocation =
                    node.methodIdentifier?.let { context.getLocation(it) }
                        ?: context.getLocation(node)

                val fix =
                    LintFix.create()
                        .name("Use runCatchingCancellable")
                        .replace()
                        .range(nameLocation)
                        .with("runCatchingCancellable")
                        .reformat(true)
                        .autoFix()
                        .build()

                context.report(
                    ISSUE,
                    node,
                    nameLocation,
                    "Use `runCatchingCancellable` in suspend contexts to propagate cancellation.",
                    fix,
                )
            }
        }

    // ---- helpers ----------------------------------------------------------------

    /** True if this call resolves to stdlib kotlin.runCatching (not a user-defined overload). */
    private fun isKotlinRunCatching(context: JavaContext, call: UCallExpression): Boolean {
        val resolved =
            call.resolve() as? PsiMethod ?: return true // be permissive if resolution fails
        val ownerFqn = resolved.containingClass?.qualifiedName ?: return true
        // kotlin.runCatching is on a stdlib facade (e.g., kotlin.ResultKt). Package check is
        // enough.
        return ownerFqn.startsWith("kotlin.")
    }

    /** Walks up UAST to see if we’re inside a suspend function or a suspend lambda. */
    private fun isInsideSuspendContext(start: UElement): Boolean {
        var current: UElement? = start
        while (current != null) {
            when (current) {
                is UMethod -> {
                    val kt = current.sourcePsi as? KtFunction
                    if (kt != null && kt.hasModifier(KtTokens.SUSPEND_KEYWORD)) return true
                }
                is ULambdaExpression -> {
                    // For suspend lambdas, UAST exposes a function type in
                    // kotlin.coroutines.SuspendFunction*
                    val type = current.getExpressionType()?.canonicalText.orEmpty()
                    if (type.contains("kotlin.coroutines.SuspendFunction")) return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(SuspendRunCatchingDetector::class.java, Scope.JAVA_FILE_SCOPE)

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "SuspendRunCatching",
                briefDescription = "Use runCatchingCancellable in suspend contexts",
                explanation =
                    """
                Using `kotlin.runCatching { ... }` inside a suspend \
                function or a suspend lambda, cancellation is not propagated as expected. \
                Prefer `runCatchingCancellable { ... }`, which rethrows `CancellationException` while \
                still returning `Result` for other failures.
            """
                        .trimIndent(),
                category = Category.CORRECTNESS,
                priority = 7,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION,
            )
    }
}
