package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

internal data class Match(val fn: JSFunction, val isFactory: Boolean)

internal class MatchPopupStep(
    aliasName: String,
    matches: List<Match>,
) : BaseListPopupStep<Match>("Implementations of $aliasName", matches) {

    override fun getTextFor(value: Match): String {
        val name = value.fn.name ?: "<anonymous>"
        val file = value.fn.containingFile?.name ?: "?"
        val prefix = if (value.isFactory) "[factory] " else ""
        return "$prefix$name  ($file)"
    }

    override fun onChosen(selected: Match, finalChoice: Boolean): PopupStep<*>? {
        ApplicationManager.getApplication().invokeLater {
            selected.fn.navigate(true)
        }
        return FINAL_CHOICE
    }
}

internal fun collectMatches(project: Project, alias: TypeScriptTypeAlias): List<Match> {
    val key = SignatureKey.of(alias) ?: return emptyList()
    val aliasName = alias.name ?: return emptyList()

    val implementations = ImplementationStubIndex.findFunctions(project, key)
        .map { Match(it, isFactory = false) }
    val factories = FactoryStubIndex.findFactories(project, aliasName)
        .map { Match(it, isFactory = true) }
    return implementations + factories
}

class FindImplementationsAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = aliasUnderCaret(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val alias = aliasUnderCaret(e) ?: return
        val project = e.project ?: return
        val aliasName = alias.name ?: return
        val matches = collectMatches(project, alias)

        if (matches.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No implementations found")
                .showInBestPositionFor(e.dataContext)
            return
        }

        val step = MatchPopupStep(aliasName, matches)
        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showInBestPositionFor(e.dataContext)
    }

    private fun aliasUnderCaret(e: AnActionEvent): TypeScriptTypeAlias? {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? PsiFile ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val alias = PsiTreeUtil.getParentOfType(element, TypeScriptTypeAlias::class.java) ?: return null
        return if (SignatureKey.of(alias) != null) alias else null
    }
}
