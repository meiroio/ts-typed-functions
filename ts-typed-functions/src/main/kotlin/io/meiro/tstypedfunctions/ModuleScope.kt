package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * True when [fn] is at module scope: a top-level `function` declaration, or
 * an arrow/function expression assigned directly to a `const`/`let` variable.
 *
 * Excludes:
 *  - nested functions (any `JSFunction` ancestor)
 *  - class methods (any [JSClass] ancestor)
 *  - object-literal methods (any [JSObjectLiteralExpression] ancestor)
 *
 * Used by the index extensions at indexing time and by [ImplementationLineMarkerProvider]
 * at gutter-render time so the indexer's view of "what counts" stays in lockstep with
 * the provider's.
 */
internal fun isAtModuleScope(fn: JSFunction): Boolean {
    val file = fn.containingFile ?: return false
    var p: PsiElement? = fn.parent
    while (p != null && p !== file) {
        if (p is JSFunction) return false
        if (p is JSClass) return false
        if (p is JSObjectLiteralExpression) return false
        p = p.parent
    }
    return true
}

/**
 * All module-scope functions in [file]. Convenience for indexers that want to
 * iterate every candidate; built on top of [isAtModuleScope].
 */
internal fun moduleScopeFunctions(file: PsiFile): List<JSFunction> =
    PsiTreeUtil.findChildrenOfType(file, JSFunction::class.java).filter(::isAtModuleScope)
