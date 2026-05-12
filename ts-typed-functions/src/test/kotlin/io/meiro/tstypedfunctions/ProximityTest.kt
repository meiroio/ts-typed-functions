package io.meiro.tstypedfunctions

import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ProximityTest : BasePlatformTestCase() {

    fun testSameDirectoryScoresBetterThanDistantDirectory() {
        val sig = myFixture.addFileToProject(
            "ports/MyPort.ts",
            "export type MyPort = () => string;",
        )
        val near = myFixture.addFileToProject(
            "ports/near.ts",
            "export const near: MyPort = () => '';",
        )
        val far = myFixture.addFileToProject(
            "elsewhere/far.ts",
            "export const far: MyPort = () => '';",
        )

        val nearScore = proximityScore(sig.virtualFile, near.virtualFile, project)
        val farScore = proximityScore(sig.virtualFile, far.virtualFile, project)
        assertTrue("same directory should score lower (closer) than distant", nearScore < farScore)
    }

    fun testNullCandidateScoresWorst() {
        val sig = myFixture.addFileToProject(
            "ports/MyPort.ts",
            "export type MyPort = () => string;",
        )
        assertEquals(Int.MAX_VALUE, proximityScore(sig.virtualFile, null, project))
    }

    fun testCollectMatchesSortsByProximity() {
        val sig = myFixture.addFileToProject(
            "ports/MyPort.ts",
            "export type MyPort = () => string;",
        )
        myFixture.addFileToProject(
            "elsewhere/far.ts",
            "export const farImpl: MyPort = () => '';",
        )
        myFixture.addFileToProject(
            "ports/near.ts",
            "export const nearImpl: MyPort = () => '';",
        )

        myFixture.openFileInEditor(sig.virtualFile)
        val alias = com.intellij.psi.util.PsiTreeUtil.findChildOfType(
            myFixture.file,
            com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias::class.java,
        )!!

        val matches = ReadAction.compute<List<Match>, RuntimeException> {
            collectMatches(project, alias)
        }
        assertEquals(2, matches.size)
        assertEquals("nearImpl", matches[0].displayName)
        assertEquals("farImpl", matches[1].displayName)
    }
}
