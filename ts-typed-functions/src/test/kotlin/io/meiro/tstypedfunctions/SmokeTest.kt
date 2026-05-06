package io.meiro.tstypedfunctions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmokeTest : BasePlatformTestCase() {
    fun testProjectAvailable() {
        assertNotNull(project)
    }
}
