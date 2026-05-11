package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.TypeScriptJSXFileType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.openapi.project.Project
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

class SignatureStubIndex : FileBasedIndexExtension<String, Void>() {

    override fun getName(): ID<String, Void> = NAME

    /**
     * Bump when ANY of the following changes:
     *  - SignatureKey canonicalization rules (whitespace, comments, params, etc.)
     *  - The set of PSI nodes the indexer visits
     *  - The set of accepted/rejected aliases (e.g. generic handling)
     *
     * Stale entries from a prior version produce silent "no match" results
     * with no error, so over-bumping is much cheaper than under-bumping.
     *
     * Shared with ImplementationStubIndex via [INDEX_VERSION] — both indexes
     * depend on SignatureKey and must bump together.
     */
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
        val psiFile = content.psiFile
        val keys = mutableMapOf<String, Void?>()
        PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptTypeAlias::class.java).forEach { alias ->
            SignatureKey.of(alias)?.let { keys[it] = null }
        }
        keys
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.SignatureStubIndex")

        fun findAliases(
            project: Project,
            key: String,
            scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
        ): List<TypeScriptTypeAlias> {
            val files = FileBasedIndex.getInstance().getContainingFiles(NAME, key, scope)
            val psiManager = PsiManager.getInstance(project)
            val out = mutableListOf<TypeScriptTypeAlias>()
            for (vf in files) {
                val psiFile = psiManager.findFile(vf) ?: continue
                PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptTypeAlias::class.java)
                    // Re-validate the key against current PSI. Defends against stale
                    // index entries (file edited after indexing) and against files
                    // containing other aliases under different keys.
                    .filter { SignatureKey.of(it) == key }
                    .forEach { out += it }
            }
            return out
        }
    }
}
