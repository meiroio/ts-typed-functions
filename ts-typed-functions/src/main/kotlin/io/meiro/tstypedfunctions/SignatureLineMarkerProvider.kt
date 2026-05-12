package io.meiro.tstypedfunctions

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.PsiElement

class SignatureLineMarkerProvider : RelatedItemLineMarkerProvider() {

    // Skip the EDT fast pass; the index lookup and PSI re-validation in
    // collectNavigationMarkers are too heavy for EDT. The default
    // collectSlowLineMarkers (background) still calls collectNavigationMarkers.
    override fun getLineMarkerInfo(element: PsiElement): RelatedItemLineMarkerInfo<*>? = null

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Trigger only on the identifier so the gutter lands on a leaf, not a composite.
        val parent = element.parent as? TypeScriptTypeAlias ?: return
        if (parent.nameIdentifier !== element) return

        val key = SignatureKey.of(parent) ?: return
        val aliasName = parent.name ?: return

        val project = parent.project
        val implementations = ImplementationStubIndex.findFunctions(project, key)
        val factories = FactoryStubIndex.findFactories(project, aliasName)
        val annotated = AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, aliasName)
        val targets = buildList<PsiElement> {
            addAll(implementations)
            addAll(factories)
            addAll(annotated)
        }
        if (targets.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(targets)
            .setTooltipText("Implementations of this signature")
            .setPopupTitle("Implementations of $aliasName")

        result.add(builder.createLineMarkerInfo(element))
    }
}
