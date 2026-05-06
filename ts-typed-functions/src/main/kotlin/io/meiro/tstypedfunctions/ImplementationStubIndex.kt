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

class ImplementationStubIndex : FileBasedIndexExtension<String, Void>() {

    override fun getName(): ID<String, Void> = NAME

    /**
     * Bumped via the shared [INDEX_VERSION] constant so SignatureStubIndex
     * and ImplementationStubIndex always change versions together — both
     * depend on SignatureKey.
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
        val psiFile = content.psiFile
        val keys = mutableMapOf<String, Void?>()
        moduleScopeFunctions(psiFile).forEach { fn ->
            SignatureKey.of(fn)?.let { keys[it] = null }
        }
        keys
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.ImplementationStubIndex")

        fun findFunctions(
            project: Project,
            key: String,
            scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
        ): List<JSFunction> {
            val files = FileBasedIndex.getInstance().getContainingFiles(NAME, key, scope)
            val psiManager = PsiManager.getInstance(project)
            val out = mutableListOf<JSFunction>()
            for (vf in files) {
                val psiFile = psiManager.findFile(vf) ?: continue
                moduleScopeFunctions(psiFile)
                    // Re-validate the key against current PSI. Defends against stale
                    // index entries (file edited after indexing) and against files
                    // containing other functions under different keys.
                    .filter { SignatureKey.of(it) == key }
                    .forEach { out += it }
            }
            return out
        }
    }
}
