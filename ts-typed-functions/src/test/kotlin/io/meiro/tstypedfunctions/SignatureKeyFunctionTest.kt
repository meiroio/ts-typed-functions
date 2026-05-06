package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SignatureKeyFunctionTest : BasePlatformTestCase() {

    private fun keyOfFirstFunction(text: String): String? {
        myFixture.configureByText("a.ts", text)
        val fn = PsiTreeUtil.findChildOfType(myFixture.file, JSFunction::class.java)
            ?: error("no function in fixture")
        return SignatureKey.of(fn)
    }

    fun testFunctionDeclaration() {
        val key = keyOfFirstFunction(
            """
            export function impl(input: Foo): Promise<Bar> {
              return null as any;
            }
            """.trimIndent(),
        )
        assertEquals("(:Foo)=>Promise<Bar>", key)
    }

    fun testAsyncArrowImplementation() {
        val key = keyOfFirstFunction(
            """
            export const impl = async (input: Foo): Promise<Bar> => {
              return null as any;
            };
            """.trimIndent(),
        )
        assertEquals("(:Foo)=>Promise<Bar>", key)
    }

    fun testImplementationWithoutReturnTypeReturnsNull() {
        assertNull(
            keyOfFirstFunction(
                """
                export const impl = (input: Foo) => {
                  return null as any;
                };
                """.trimIndent(),
            ),
        )
    }

    fun testImplementationWithUntypedParamReturnsNull() {
        assertNull(
            keyOfFirstFunction(
                """
                export const impl = (input, opts: Opts): Promise<Bar> => {
                  return null as any;
                };
                """.trimIndent(),
            ),
        )
    }

    fun testSignatureAndImplementationProduceEqualKeys() {
        myFixture.configureByText(
            "a.ts",
            """
            export type Signature = (input: Foo) => Promise<Bar>;

            export const impl = async (input: Foo): Promise<Bar> => {
              return null as any;
            };
            """.trimIndent(),
        )
        val alias = PsiTreeUtil.findChildOfType(
            myFixture.file,
            com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias::class.java,
        )!!
        val fn = PsiTreeUtil.findChildOfType(myFixture.file, JSFunction::class.java)!!
        assertEquals(SignatureKey.of(alias), SignatureKey.of(fn))
        assertNotNull(SignatureKey.of(alias))
    }
}
