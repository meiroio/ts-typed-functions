package io.meiro.tstypedfunctions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SignatureLineMarkerProviderTest : BasePlatformTestCase() {

    fun testGutterAppearsOnSignatureWithMatchingImplementation() {
        myFixture.addFileToProject(
            "implementation.ts",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.configureByText(
            "signature.ts",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }

    fun testNoGutterWhenNoMatchingImplementation() {
        myFixture.configureByText(
            "signature.ts",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(0, gutters.size)
    }

    fun testGutterAppearsOnSignatureInTsxFileWithTsxImplementation() {
        myFixture.addFileToProject(
            "implementation.tsx",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.configureByText(
            "signature.tsx",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }

    fun testGutterListsMultipleImplementations() {
        myFixture.addFileToProject(
            "a.ts",
            "export const a = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.addFileToProject(
            "b.ts",
            "export function b(input: Foo): Promise<Bar> { return null as any; }",
        )
        myFixture.configureByText(
            "signature.ts",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
        // The targets aren't directly exposed, but the tooltip contains the count when configured.
        // Smoke-check: marker exists and SignatureKey lookup returns 2 functions.
        val key = "(:Foo)=>Promise<Bar>"
        val fns = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
            ImplementationStubIndex.findFunctions(project, key)
        }
        assertEquals(2, fns.size)
    }

    fun testGutterIncludesFactoriesAlongsideImplementations() {
        myFixture.addFileToProject(
            "impl.ts",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.addFileToProject(
            "factory.ts",
            "export function makeImpl(): Signature { return null as any; }",
        )
        myFixture.configureByText(
            "signature.ts",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        // Confirm a single gutter icon is rendered (one icon, both directions of nav).
        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)

        // Verify the merged result set: 1 direct impl + 1 factory = 2 total.
        val key = "(:Foo)=>Promise<Bar>"
        val implementations = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
            ImplementationStubIndex.findFunctions(project, key)
        }
        assertEquals(1, implementations.size)
        val factories = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
            FactoryStubIndex.findFactories(project, "Signature")
        }
        assertEquals(1, factories.size)
    }

    fun testGutterAppearsOnSignatureWithOnlyAFactory() {
        myFixture.addFileToProject(
            "factory.ts",
            "export function makeImpl(): Signature { return null as any; }",
        )
        myFixture.configureByText(
            "signature.ts",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }
}
