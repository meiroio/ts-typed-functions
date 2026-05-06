package io.meiro.tstypedfunctions

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

class FactoryStubIndexTest : BasePlatformTestCase() {

    fun testFindsFactoryByAliasName() {
        myFixture.configureByText(
            "factory.ts",
            """
            export function makeCreateIdentifierType(): CreateIdentifierType {
                return null as any;
            }
            export function makeOther(): SomeOther {
                return null as any;
            }
            """.trimIndent(),
        )

        val files = ReadAction.compute<Collection<*>, RuntimeException> {
            FileBasedIndex.getInstance().getContainingFiles(
                FactoryStubIndex.NAME,
                "CreateIdentifierType",
                GlobalSearchScope.projectScope(project),
            )
        }
        assertEquals(1, files.size)

        val factories = ReadAction.compute<List<*>, RuntimeException> {
            FactoryStubIndex.findFactories(project, "CreateIdentifierType")
        }
        assertEquals(1, factories.size)
        val fn = factories.single() as com.intellij.lang.javascript.psi.JSFunction
        assertEquals("makeCreateIdentifierType", fn.name)
    }

    fun testFindsMultipleFactoriesWithSameReturnType() {
        myFixture.addFileToProject(
            "a.ts",
            "export function makeFooA(): CreateIdentifierType { return null as any; }",
        )
        myFixture.addFileToProject(
            "b.ts",
            "export function makeFooB(): CreateIdentifierType { return null as any; }",
        )

        val factories = ReadAction.compute<List<*>, RuntimeException> {
            FactoryStubIndex.findFactories(project, "CreateIdentifierType")
        }
        assertEquals(2, factories.size)
    }

    fun testNestedFunctionMethodAndObjectLiteralFactoriesExcluded() {
        myFixture.configureByText(
            "x.ts",
            """
            export function outer(): CreateIdentifierType {
                function inner(): CreateIdentifierType { return null as any; }
                return null as any;
            }
            class C {
                method(): CreateIdentifierType { return null as any; }
            }
            export const obj = {
                method(): CreateIdentifierType { return null as any; },
            };
            """.trimIndent(),
        )

        val factories = ReadAction.compute<List<*>, RuntimeException> {
            FactoryStubIndex.findFactories(project, "CreateIdentifierType")
        }
        assertEquals(1, factories.size)
        val fn = factories.single() as com.intellij.lang.javascript.psi.JSFunction
        assertEquals("outer", fn.name)
    }

    fun testReturnTypeWhitespaceIsStripped() {
        myFixture.configureByText(
            "factory.ts",
            "export function makeFoo():\n  CreateIdentifierType { return null as any; }",
        )
        val factories = ReadAction.compute<List<*>, RuntimeException> {
            FactoryStubIndex.findFactories(project, "CreateIdentifierType")
        }
        assertEquals(1, factories.size)
    }
}
