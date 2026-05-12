package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AnnotatedImplementationStubIndexTest : BasePlatformTestCase() {

    fun testFindsAnnotatedVariableInsideFactoryFunction() {
        myFixture.configureByText(
            "repo.ts",
            """
            export function makeIdentifierTypesRepository(client: typeof db) {
                const findIdentifierTypeByName: FindIdentifierTypeByName = async (name) => {
                    return null;
                };
                return { findIdentifierTypeByName };
            }
            """.trimIndent(),
        )

        val matches = ReadAction.compute<List<*>, RuntimeException> {
            AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, "FindIdentifierTypeByName")
        }
        assertEquals(1, matches.size)
        val variable = matches.single() as JSVariable
        assertEquals("findIdentifierTypeByName", variable.name)
    }

    fun testFindsModuleScopeAnnotatedVariable() {
        myFixture.configureByText(
            "impl.ts",
            "export const findByName: FindIdentifierTypeByName = async (name) => null;",
        )

        val matches = ReadAction.compute<List<*>, RuntimeException> {
            AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, "FindIdentifierTypeByName")
        }
        assertEquals(1, matches.size)
    }

    fun testFindsMultipleAdaptersInSameFactory() {
        myFixture.configureByText(
            "repo.ts",
            """
            export function makeRepo() {
                const findByName: FindByName = async (n) => null;
                const findById: FindById = async (id) => null;
                return { findByName, findById };
            }
            """.trimIndent(),
        )

        val byName = ReadAction.compute<List<*>, RuntimeException> {
            AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, "FindByName")
        }
        val byId = ReadAction.compute<List<*>, RuntimeException> {
            AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, "FindById")
        }
        assertEquals(1, byName.size)
        assertEquals(1, byId.size)
    }

    fun testIgnoresVariableWithoutFunctionInitializer() {
        myFixture.configureByText(
            "x.ts",
            "export const value: FindIdentifierTypeByName = someFactory() as any;",
        )

        val matches = ReadAction.compute<List<*>, RuntimeException> {
            AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, "FindIdentifierTypeByName")
        }
        assertEquals(0, matches.size)
    }

    fun testIgnoresVariableWithoutTypeAnnotation() {
        myFixture.configureByText(
            "x.ts",
            "export const findByName = async (name: string) => null;",
        )

        // Without a type annotation, no alias name to key by — nothing to index.
        val matches = ReadAction.compute<List<*>, RuntimeException> {
            AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, "FindByName")
        }
        assertEquals(0, matches.size)
    }

    fun testIgnoresComplexTypeAnnotations() {
        myFixture.configureByText(
            "x.ts",
            """
            export const a: FindByName | null = async (n) => null;
            export const b: Promise<FindByName> = async (n) => null;
            export const c: FindByName & Logger = async (n) => null;
            """.trimIndent(),
        )

        // Only single, unqualified identifier annotations are indexed.
        val matches = ReadAction.compute<List<*>, RuntimeException> {
            AnnotatedImplementationStubIndex.findAnnotatedImplementations(project, "FindByName")
        }
        assertEquals(0, matches.size)
    }
}
