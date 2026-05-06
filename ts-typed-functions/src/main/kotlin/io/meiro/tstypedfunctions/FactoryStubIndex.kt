package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.TypeScriptJSXFileType
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.VoidDataExternalizer

class FactoryStubIndex : FileBasedIndexExtension<String, Void>() {

    override fun getName(): ID<String, Void> = NAME

    /**
     * Bumped via the shared [INDEX_VERSION] constant so all three indexes
     * (Signature, Implementation, Factory) always change versions together —
     * they all depend on canonicalization rules in SignatureKey and on the
     * shared module-scope filter in ModuleScope.kt.
     */
    override fun getVersion(): Int = INDEX_VERSION

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = VoidDataExternalizer.INSTANCE
    override fun getInputFilter() =
        DefaultFileTypeSpecificInputFilter(
            TypeScriptFileType.INSTANCE,
            TypeScriptJSXFileType.INSTANCE,
        )
    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        val keys = mutableMapOf<String, Void?>()
        moduleScopeFunctions(content.psiFile).forEach { fn ->
            returnTypeKey(fn)?.let { keys[it] = null }
        }
        keys
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.FactoryStubIndex")

        /**
         * The factory's return-type-element text with all whitespace stripped.
         * For `function makeFoo(): CreateIdentifierType { ... }`, this is
         * `"CreateIdentifierType"`. For functions without an explicit return
         * type, returns null (such functions are not factories).
         */
        private fun returnTypeKey(fn: JSFunction): String? {
            val ret = fn.returnTypeElement ?: return null
            return ret.text.replace(Regex("\\s+"), "")
        }

        fun findFactories(
            project: Project,
            aliasName: String,
            scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
        ): List<JSFunction> {
            val files = FileBasedIndex.getInstance().getContainingFiles(NAME, aliasName, scope)
            val psiManager = PsiManager.getInstance(project)
            val out = mutableListOf<JSFunction>()
            for (vf in files) {
                val psiFile = psiManager.findFile(vf) ?: continue
                moduleScopeFunctions(psiFile)
                    // Re-validate the key against current PSI. Defends against
                    // stale index entries (file edited after indexing).
                    .filter { returnTypeKey(it) == aliasName }
                    .forEach { out += it }
            }
            return out
        }
    }
}
