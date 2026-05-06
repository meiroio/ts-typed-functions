package io.meiro.tstypedfunctions

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

class SignatureStubIndexTest : BasePlatformTestCase() {

    fun testFindsSignatureByKey() {
        myFixture.configureByText(
            "signature.ts",
            """
            export type Signature = (input: Foo) => Promise<Bar>;
            export type Other = (x: number) => string;
            """.trimIndent(),
        )

        val key = "(:Foo)=>Promise<Bar>"
        val files = ReadAction.compute<Collection<*>, RuntimeException> {
            FileBasedIndex.getInstance().getContainingFiles(
                SignatureStubIndex.NAME,
                key,
                GlobalSearchScope.projectScope(project),
            )
        }
        assertEquals(1, files.size)
    }

    fun testFindsMultipleSignaturesWithSameKey() {
        myFixture.addFileToProject(
            "a.ts",
            "export type A = (input: Foo) => Promise<Bar>;",
        )
        myFixture.addFileToProject(
            "b.ts",
            "export type B = (input: Foo) => Promise<Bar>;",
        )

        val files = com.intellij.openapi.application.ReadAction.compute<Collection<*>, RuntimeException> {
            com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(
                SignatureStubIndex.NAME,
                "(:Foo)=>Promise<Bar>",
                com.intellij.psi.search.GlobalSearchScope.projectScope(project),
            )
        }
        assertEquals(2, files.size)
    }

    fun testFindAliasesHelperReturnsMatchingAliases() {
        myFixture.configureByText(
            "signature.ts",
            """
            export type Signature = (input: Foo) => Promise<Bar>;
            export type Other = (x: number) => string;
            """.trimIndent(),
        )

        val results = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
            SignatureStubIndex.findAliases(project, "(:Foo)=>Promise<Bar>")
        }
        assertEquals(1, results.size)
        val alias = results.single() as com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
        assertEquals("Signature", alias.name)
    }
}
