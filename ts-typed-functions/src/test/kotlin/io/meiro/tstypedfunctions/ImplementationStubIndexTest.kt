package io.meiro.tstypedfunctions

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

class ImplementationStubIndexTest : BasePlatformTestCase() {

    fun testFindsImplementationByKey() {
        myFixture.configureByText(
            "implementation.ts",
            """
            export const implA = async (input: Foo): Promise<Bar> => null as any;
            export function implB(input: Foo): Promise<Bar> { return null as any; }
            export const unrelated = (n: number): string => "x";
            """.trimIndent(),
        )

        val files = ReadAction.compute<Collection<*>, RuntimeException> {
            FileBasedIndex.getInstance().getContainingFiles(
                ImplementationStubIndex.NAME,
                "(:Foo)=>Promise<Bar>",
                GlobalSearchScope.projectScope(project),
            )
        }
        assertEquals(1, files.size)

        val fns = ReadAction.compute<List<*>, RuntimeException> {
            ImplementationStubIndex.findFunctions(project, "(:Foo)=>Promise<Bar>")
        }
        assertEquals(2, fns.size) // implA + implB
    }

    fun testNestedFunctionMethodAndObjectLiteralExcluded() {
        myFixture.configureByText(
            "x.ts",
            """
            export function outer(input: Foo): Promise<Bar> {
                function inner(input: Foo): Promise<Bar> { return null as any; }
                return null as any;
            }
            class C {
                method(input: Foo): Promise<Bar> { return null as any; }
            }
            export const obj = {
                method(input: Foo): Promise<Bar> { return null as any; },
            };
            """.trimIndent(),
        )

        val fns = ReadAction.compute<List<*>, RuntimeException> {
            ImplementationStubIndex.findFunctions(project, "(:Foo)=>Promise<Bar>")
        }
        assertEquals(1, fns.size)
        val fn = fns.single() as com.intellij.lang.javascript.psi.JSFunction
        assertEquals("outer", fn.name)
    }

    fun testIndexesImplementationInTsxFile() {
        myFixture.configureByText(
            "implementation.tsx",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        val fns = ReadAction.compute<List<*>, RuntimeException> {
            ImplementationStubIndex.findFunctions(project, "(:Foo)=>Promise<Bar>")
        }
        assertEquals(1, fns.size)
    }

    fun testImplementationsMissingTypesAreNotIndexed() {
        myFixture.configureByText(
            "implementation.ts",
            """
            export const a = (input) => null as any;
            export const b = (input: Foo) => null as any;
            export const c = (input: Foo): Promise<Bar> => null as any;
            """.trimIndent(),
        )

        val files = com.intellij.openapi.application.ReadAction.compute<Collection<*>, RuntimeException> {
            com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(
                ImplementationStubIndex.NAME,
                "(:Foo)=>Promise<Bar>",
                com.intellij.psi.search.GlobalSearchScope.projectScope(project),
            )
        }
        assertEquals(1, files.size) // only c is indexed under this key

        val fns = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
            ImplementationStubIndex.findFunctions(project, "(:Foo)=>Promise<Bar>")
        }
        assertEquals(1, fns.size)
        val fn = fns.single() as com.intellij.lang.javascript.psi.JSFunction
        assertEquals("c", (fn.parent as? com.intellij.lang.javascript.psi.JSVariable)?.name)
    }
}
