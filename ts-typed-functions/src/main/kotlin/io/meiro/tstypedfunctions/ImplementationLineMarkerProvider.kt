package io.meiro.tstypedfunctions

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.psi.PsiElement

class ImplementationLineMarkerProvider : RelatedItemLineMarkerProvider() {

    // Skip the EDT fast pass; the index lookup and PSI re-validation in
    // collectNavigationMarkers are too heavy for EDT. The default
    // collectSlowLineMarkers (background) still calls collectNavigationMarkers.
    override fun getLineMarkerInfo(element: PsiElement): RelatedItemLineMarkerInfo<*>? = null

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        val (fn, anchor) = resolveImplementation(element) ?: return
        val key = SignatureKey.of(fn) ?: return
        val signatures = SignatureStubIndex.findAliases(fn.project, key)
        if (signatures.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
            .setTargets(signatures)
            .setTooltipText("Signatures implemented by this function")
            .setPopupTitle("Signatures implemented by ${fn.name ?: "function"}")

        result.add(builder.createLineMarkerInfo(anchor))
    }

    /**
     * Returns (function, identifier-element) when [element] is the *name identifier* of either:
     *   - a `function name(...)` declaration, or
     *   - a `const name = (arrow|fn-expr)` initializer.
     * Otherwise null. We anchor the gutter to the identifier so it lands on a leaf.
     */
    private fun resolveImplementation(element: PsiElement): Pair<JSFunction, PsiElement>? {
        val parent = element.parent ?: return null

        val fn: JSFunction = when {
            parent is JSFunction && parent.nameIdentifier === element -> parent
            parent is JSVariable && parent.nameIdentifier === element -> {
                parent.initializer as? JSFunction ?: return null
            }
            else -> return null
        }

        if (!isAtModuleScope(fn)) return null
        return fn to element
    }
}
