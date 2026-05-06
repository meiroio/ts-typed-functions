package io.meiro.tstypedfunctions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ImplementationLineMarkerProviderTest : BasePlatformTestCase() {

    fun testGutterOnImplementationPointsToSignature() {
        myFixture.addFileToProject(
            "signature.ts",
            "export type Signature = (input: Foo) => Promise<Bar>;",
        )
        myFixture.configureByText(
            "implementation.ts",
            "export const im<caret>pl = async (input: Foo): Promise<Bar> => null as any;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }

    fun testGutterOnFunctionDeclaration() {
        myFixture.addFileToProject(
            "signature.ts",
            "export type Signature = (input: Foo) => Promise<Bar>;",
        )
        myFixture.configureByText(
            "implementation.ts",
            "export function im<caret>pl(input: Foo): Promise<Bar> { return null as any; }",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }

    fun testNoGutterWhenNoSignature() {
        myFixture.configureByText(
            "implementation.ts",
            "export const im<caret>pl = async (input: Foo): Promise<Bar> => null as any;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(0, gutters.size)
    }

    fun testNoGutterOnNestedImplementation() {
        myFixture.addFileToProject(
            "signature.ts",
            "export type Signature = (input: Foo) => Promise<Bar>;",
        )
        myFixture.configureByText(
            "outer.ts",
            """
            export function outer(input: Foo): Promise<Bar> {
                function nes<caret>ted(input: Foo): Promise<Bar> { return null as any; }
                return nested(input);
            }
            """.trimIndent(),
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(0, gutters.size)
    }
}
