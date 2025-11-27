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
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Ensures that MutableStateFlow is not exposed directly as StateFlow.
 *
 * Problematic: private val _state = MutableStateFlow(initial) val state: StateFlow<State> = _state
 * // can be downcast to MutableStateFlow and mutated val state: StateFlow<State> get() = _state
 *
 * Correct: private val _state = MutableStateFlow(initial) val state: StateFlow<State> =
 * _state.asStateFlow() val state: StateFlow<State> get() = _state.asStateFlow()
 */
class ExposeAsStateFlowDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(
            UField::class.java, // Kotlin/Java fields (including backing fields of properties)
            UVariable::class.java, // top-level vals
            UMethod::class.java, // property getters and Java-style getters
        )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {

            override fun visitField(node: UField) {
                // Kotlin `val state: StateFlow<T> = ...` compiles to a field with initializer
                checkPropertyInitializer(context, node, node.type, node.uastInitializer, node)
            }

            override fun visitVariable(node: UVariable) {
                // top-level Kotlin `val state: StateFlow<T> = ...`
                checkPropertyInitializer(context, node, node.type, node.uastInitializer, node)
            }

            override fun visitMethod(node: UMethod) {
                // Kotlin property getter or Java getter
                if (!isApiVisible(node)) return
                val returnType = node.returnType ?: return
                if (!isStateFlowType(context, returnType)) return

                val returnExpr = findReturnExpression(node) ?: return

                // Ignore already-safe: receiver.asStateFlow()
                if (isAlreadyAsStateFlowCall(returnExpr)) return

                // If returned expr resolves to MutableStateFlow (ref or construction), flag
                val rhsType = returnExpr.getExpressionType()
                val mutable =
                    (rhsType != null && isMutableStateFlowType(context, rhsType)) ||
                        isClearlyMutableQualified(returnExpr, context)

                if (!mutable) return

                val location = context.getLocation(returnExpr)
                val shortMsg = "Expose read-only StateFlow by calling asStateFlow()."

                val initText = returnExpr.sourcePsi?.text ?: return
                val fix =
                    LintFix.create()
                        .name("Wrap returned expression with .asStateFlow()")
                        .replace()
                        .range(location)
                        .with("$initText.asStateFlow()")
                        .reformat(true)
                        .autoFix()
                        .build()

                context.report(ISSUE, node, location, shortMsg, fix)
            }
        }

    // ---- property initializer path ----

    private fun checkPropertyInitializer(
        context: JavaContext,
        node: UDeclaration,
        declaredType: PsiType?,
        initializer: UExpression?,
        reportNode: UElement,
    ) {
        if (!isApiVisible(node)) return
        val type = declaredType ?: return
        if (!isStateFlowType(context, type)) return
        val init = initializer ?: return

        // Ignore already-safe: receiver.asStateFlow()
        if (isAlreadyAsStateFlowCall(init)) return

        // RHS mutable?
        val rhsType = init.getExpressionType()
        val mutable =
            (rhsType != null && isMutableStateFlowType(context, rhsType)) ||
                isClearlyMutableQualified(init, context)

        if (!mutable) return

        val location = context.getLocation(init)
        val shortMsg = "Expose read-only StateFlow by calling asStateFlow()."

        val initText =
            init.sourcePsi?.text
                ?: run {
                    context.report(ISSUE, reportNode, location, shortMsg, null)
                    return
                }

        val fix =
            LintFix.create()
                .name("Wrap with .asStateFlow()")
                .replace()
                .range(location)
                .with("$initText.asStateFlow()")
                .reformat(true)
                .autoFix()
                .build()

        context.report(ISSUE, reportNode, location, shortMsg, fix)
    }

    // ---- helpers ----

    /** Treat any non-private member/top-level as in scope (public, internal, protected). */
    private fun isApiVisible(node: UDeclaration): Boolean {
        return node.visibility != UastVisibility.PRIVATE
    }

    private fun isAlreadyAsStateFlowCall(expr: UExpression): Boolean {
        // Match: <receiver>.asStateFlow() or (<receiver>)?.asStateFlow()
        return when (expr) {
            is UCallExpression -> expr.methodName == "asStateFlow"
            is UQualifiedReferenceExpression -> {
                val call = expr.selector as? UCallExpression
                call?.methodName == "asStateFlow"
            }
            else -> false
        }
    }

    private fun isClearlyMutableQualified(expr: UExpression, context: JavaContext): Boolean {
        // Qualified reference like holder._state, where receiver or selector type is
        // MutableStateFlow
        val qualified = expr as? UQualifiedReferenceExpression ?: return false

        // If this is X.asStateFlow(), it's already safe → don't mark as mutable
        val selCall = qualified.selector as? UCallExpression
        if (selCall?.methodName == "asStateFlow") return false

        val selType = qualified.selector.getExpressionType()
        val recvType = qualified.receiver.getExpressionType()
        return (selType != null && isMutableStateFlowType(context, selType)) ||
            (recvType != null && isMutableStateFlowType(context, recvType))
    }

    private fun isStateFlowType(context: JavaContext, t: PsiType): Boolean {
        val fq = "kotlinx.coroutines.flow.StateFlow"
        if (context.evaluator.typeMatches(t, fq)) return true
        val cls = (t as? PsiClassType)?.resolve() ?: return false
        return context.evaluator.extendsClass(cls, fq, false) ||
            context.evaluator.implementsInterface(cls, fq, false)
    }

    private fun isMutableStateFlowType(context: JavaContext, t: PsiType): Boolean {
        val fq = "kotlinx.coroutines.flow.MutableStateFlow"
        if (context.evaluator.typeMatches(t, fq)) return true
        val cls = (t as? PsiClassType)?.resolve() ?: return false
        return context.evaluator.extendsClass(cls, fq, false) ||
            context.evaluator.implementsInterface(cls, fq, false)
    }

    /**
     * Extracts the returned expression from a getter or method. Handles both block bodies (`return
     * expr`) and expression-bodied getters (`get() = expr`).
     */
    private fun findReturnExpression(method: UMethod): UExpression? {
        val body = method.uastBody ?: return null
        return when (body) {
            is UBlockExpression -> {
                var last: UExpression? = null
                body.accept(
                    object : AbstractUastVisitor() {
                        override fun visitReturnExpression(node: UReturnExpression): Boolean {
                            last = node.returnExpression
                            return super.visitReturnExpression(node)
                        }
                    }
                )
                last
            }
            else -> body as? UExpression // expression-bodied getter: get() = _state.asStateFlow()
        }
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(ExposeAsStateFlowDetector::class.java, Scope.JAVA_FILE_SCOPE)

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "ExposeAsStateFlow",
                briefDescription = "Expose MutableStateFlow as read-only via asStateFlow()",
                explanation =
                    """
                        Exposing a MutableStateFlow directly (even when typed as StateFlow) allows consumers to downcast
                        and mutate your internal state. Wrap the internal MutableStateFlow with asStateFlow() when you
                        expose it.

                        Problematic:
                            private val _state = MutableStateFlow(initial)
                            val state: StateFlow<State> = _state
                            val state: StateFlow<State> get() = _state

                        Correct:
                            private val _state = MutableStateFlow(initial)
                            val state: StateFlow<State> = _state.asStateFlow()
                            val state: StateFlow<State> get() = _state.asStateFlow()
                    """
                        .trimIndent(),
                category = Category.CORRECTNESS,
                priority = 7,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION,
            )
    }
}
