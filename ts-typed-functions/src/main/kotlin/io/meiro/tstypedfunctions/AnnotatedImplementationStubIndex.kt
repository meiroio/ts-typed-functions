package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.TypeScriptJSXFileType
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.VoidDataExternalizer

/**
 * Indexes typed variable declarations whose initializer is a function literal,
 * keyed by the alias name in the type annotation. Catches implementations
 * declared as `const x: Signature = async (...) => ...`, including nested ones
 * inside enclosing factory functions where [ImplementationStubIndex] would not
 * pick them up: the arrow has no parameter or return type annotations of its
 * own (they're inferred from the variable's annotation), so structural matching
 * has nothing to canonicalize.
 */
class AnnotatedImplementationStubIndex : FileBasedIndexExtension<String, Void>() {

    override fun getName(): ID<String, Void> = NAME

    override fun getVersion(): Int = INDEX_VERSION

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer() = VoidDataExternalizer.INSTANCE

    override fun getInputFilter() =
        DefaultFileTypeSpecificInputFilter(
            TypeScriptFileType,
            TypeScriptJSXFileType,
        )

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        val keys = mutableMapOf<String, Void?>()
        candidateVariables(content.psiFile).forEach { variable ->
            annotationAliasName(variable)?.let { keys[it] = null }
        }
        keys
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.AnnotatedImplementationStubIndex")

        private val IDENTIFIER = Regex("[A-Za-z_$][\\w$]*")

        /**
         * Returns the alias name in [variable]'s type annotation when:
         *  - the annotation is a single, unqualified identifier (no generics,
         *    unions, intersections, or qualified names), and
         *  - the initializer is a function literal (arrow or function expression).
         *
         * The conservative filter keeps the index small and avoids surfacing
         * matches for anonymous function-typed annotations like `: () => void`.
         */
        private fun annotationAliasName(variable: JSVariable): String? {
            val initializer = variable.initializer ?: return null
            if (initializer !is JSFunction) return null
            val typeText = variable.typeElement?.text?.trim() ?: return null
            return typeText.takeIf { it.matches(IDENTIFIER) }
        }

        private fun candidateVariables(file: PsiFile): Collection<JSVariable> =
            PsiTreeUtil.findChildrenOfType(file, JSVariable::class.java)

        /**
         * Variables whose type annotation names [aliasName] and whose initializer
         * is a function literal. Re-validates against current PSI to defend
         * against stale index entries (file edited after indexing).
         */
        fun findAnnotatedImplementations(
            project: Project,
            aliasName: String,
            scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
        ): List<JSVariable> {
            val files = FileBasedIndex.getInstance().getContainingFiles(NAME, aliasName, scope)
            val psiManager = PsiManager.getInstance(project)
            val out = mutableListOf<JSVariable>()
            for (vf in files) {
                val psiFile = psiManager.findFile(vf) ?: continue
                candidateVariables(psiFile)
                    .filter { annotationAliasName(it) == aliasName }
                    .forEach { out += it }
            }
            return out
        }
    }
}
