package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SignatureKeyAliasTest : BasePlatformTestCase() {

    private fun keyOfFirstAlias(text: String): String? {
        myFixture.configureByText("a.ts", text)
        val alias = PsiTreeUtil.findChildOfType(myFixture.file, TypeScriptTypeAlias::class.java)
            ?: error("no type alias in fixture")
        return SignatureKey.of(alias)
    }

    fun testBasicSingleParamSignature() {
        val key = keyOfFirstAlias(
            """
            type Signature = (input: Foo) => Promise<Bar>;
            """.trimIndent(),
        )
        assertEquals("(:Foo)=>Promise<Bar>", key)
    }

    fun testReturnTypeIsPromise() {
        val key = keyOfFirstAlias(
            """
            type Signature = (input: CreateIdentifierTypeInput) => Promise<CreateIdentifierTypeResult>;
            """.trimIndent(),
        )
        assertEquals("(:CreateIdentifierTypeInput)=>Promise<CreateIdentifierTypeResult>", key)
    }

    fun testOptionalAndRestParams() {
        val keyOptional = keyOfFirstAlias("type P = (a: A, b?: B) => void;")
        assertEquals("(:A,?:B)=>void", keyOptional)

        val keyRest = keyOfFirstAlias("type P = (...xs: T[]) => U;")
        assertEquals("(...:T[])=>U", keyRest)
    }

    fun testGenericAliasReturnsNull() {
        assertNull(keyOfFirstAlias("type Handler<T> = (x: T) => void;"))
    }

    fun testNonFunctionAliasReturnsNull() {
        assertNull(keyOfFirstAlias("type Foo = { id: string };"))
    }

    fun testWhitespaceAndCommentsAreStripped() {
        val key = keyOfFirstAlias(
            """
            type Signature = (
              /* the input */ input: Foo,
            ) => Promise<
              Bar
            >;
            """.trimIndent(),
        )
        assertEquals("(:Foo)=>Promise<Bar>", key)
    }
}
