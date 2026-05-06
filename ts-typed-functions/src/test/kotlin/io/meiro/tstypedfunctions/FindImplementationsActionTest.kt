package io.meiro.tstypedfunctions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FindImplementationsActionTest : BasePlatformTestCase() {

    fun testActionEnabledOnSignatureAlias() {
        myFixture.addFileToProject(
            "implementation.ts",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.configureByText(
            "signature.ts",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        val action = ActionManager.getInstance()
            .getAction("io.meiro.tstypedfunctions.FindImplementations")
        assertNotNull("action must be registered in plugin.xml", action)

        val event = TestActionEvent.createTestEvent(action, dataContextFromEditor())
        action.update(event)
        assertTrue("action must be enabled when caret on a function-typed alias", event.presentation.isEnabled)
    }

    fun testActionDisabledOnNonFunctionAlias() {
        myFixture.configureByText(
            "signature.ts",
            "export type Fo<caret>o = { id: string };",
        )
        val action = ActionManager.getInstance()
            .getAction("io.meiro.tstypedfunctions.FindImplementations")
        val event = TestActionEvent.createTestEvent(action, dataContextFromEditor())
        action.update(event)
        assertFalse(event.presentation.isEnabled)
    }

    fun testActionResolvesImplementationsByKey() {
        myFixture.addFileToProject(
            "implementation.ts",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.configureByText(
            "signature.ts",
            "export type Signa<caret>ture = (input: Foo) => Promise<Bar>;",
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val alias = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            element,
            com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias::class.java,
        )!!
        val key = SignatureKey.of(alias)!!
        val implementations = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
            ImplementationStubIndex.findFunctions(project, key)
        }
        assertEquals(1, implementations.size)
    }

    fun testActionResolvesFactoriesAndImplementationsTogether() {
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

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val alias = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            element,
            com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias::class.java,
        )!!

        val matches = com.intellij.openapi.application.ReadAction.compute<List<Match>, RuntimeException> {
            collectMatches(project, alias)
        }
        assertEquals(2, matches.size)
        assertEquals(1, matches.count { !it.isFactory })
        assertEquals(1, matches.count { it.isFactory })

        // Row text for the factory match must carry the [factory] prefix.
        val step = MatchPopupStep(alias.name!!, matches)
        val factoryMatch = matches.single { it.isFactory }
        assertEquals(
            "[factory] makeImpl  (factory.ts)",
            step.getTextFor(factoryMatch),
        )
        val implMatch = matches.single { !it.isFactory }
        assertEquals(
            "impl  (impl.ts)",
            step.getTextFor(implMatch),
        )
    }

    private fun dataContextFromEditor() = MapDataContext().apply {
        put(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
        put(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR, myFixture.editor)
        put(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE, myFixture.file)
    }
}
