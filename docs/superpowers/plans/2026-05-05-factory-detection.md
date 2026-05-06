# Factory detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect functions whose explicit return type names a known signature alias (factories) and surface them alongside direct implementations in the signature gutter and the `Find Implementations` action.

**Architecture:** A new `FactoryStubIndex` (`FileBasedIndexExtension<String, Void>`) keyed by the function's return-type-element text. The signature line marker provider and the action both query Implementation+Factory together; the action popup labels factory rows with a `[factory]` prefix. A small refactor extracts the module-scope filter into a shared helper (`ModuleScope.kt`) used by both indexes and the existing line marker provider.

**Tech Stack:** Kotlin 2.x, IntelliJ Platform 2024.3.5, `BasePlatformTestCase` for tests. `JAVA_HOME=$HOME/.jdks/jdk-21.0.11` for gradle commands.

**Reference spec:** `docs/superpowers/specs/2026-05-05-factory-detection-design.md`

**Working directory:** `/home/odis/meiro/ts-typed-functions/`. Plugin sources in inner `ts-typed-functions/`. Branch: `implement-plugin`.

---

## File structure

After all tasks complete:

```
ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/
├── ModuleScope.kt                            (new — shared helpers)
├── FactoryStubIndex.kt                       (new)
├── SignatureKey.kt                           (unchanged)
├── IndexVersion.kt                           (unchanged)
├── SignatureStubIndex.kt                     (unchanged)
├── ImplementationStubIndex.kt                (refactored — uses ModuleScope)
├── SignatureLineMarkerProvider.kt            (extended — queries Factory too)
├── ImplementationLineMarkerProvider.kt       (refactored — uses ModuleScope)
└── FindImplementationsAction.kt              (extended — Match wrapper, [factory] prefix)

ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/
├── FactoryStubIndexTest.kt                   (new)
├── (existing test files; SignatureLineMarkerProviderTest and FindImplementationsActionTest extended)
```

---

## Task 1: Extract `ModuleScope` shared helpers

Pure refactor — no behavior change. The current code has two near-duplicate ancestor walks:

1. `ImplementationStubIndex.candidateFunctions` (private companion-object) — finds module-scope functions in a file.
2. `ImplementationLineMarkerProvider.isModuleScope` (private method) — checks one function.

Both reject any function whose ancestor chain contains another `JSFunction`, a `JSClass`, or a `JSObjectLiteralExpression`. We extract a single source of truth.

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/ModuleScope.kt`
- Modify: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/ImplementationStubIndex.kt`
- Modify: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/ImplementationLineMarkerProvider.kt`

- [ ] **Step 1: Create `ModuleScope.kt`**

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * True when [fn] is at module scope: a top-level `function` declaration, or
 * an arrow/function expression assigned directly to a `const`/`let` variable.
 *
 * Excludes:
 *  - nested functions (any `JSFunction` ancestor)
 *  - class methods (any [JSClass] ancestor)
 *  - object-literal methods (any [JSObjectLiteralExpression] ancestor)
 *
 * Used by both [SignatureStubIndex]/[ImplementationStubIndex]/[FactoryStubIndex] at
 * indexing time and by [ImplementationLineMarkerProvider] at gutter-render time
 * so the indexer's view of "what counts" stays in lockstep with the provider's.
 */
internal fun isAtModuleScope(fn: JSFunction): Boolean {
    val file = fn.containingFile ?: return false
    var p: PsiElement? = fn.parent
    while (p != null && p !== file) {
        if (p is JSFunction) return false
        if (p is JSClass) return false
        if (p is JSObjectLiteralExpression) return false
        p = p.parent
    }
    return true
}

/**
 * All module-scope functions in [file]. Convenience for indexers that want to
 * iterate every candidate; built on top of [isAtModuleScope].
 */
internal fun moduleScopeFunctions(file: PsiFile): List<JSFunction> =
    PsiTreeUtil.findChildrenOfType(file, JSFunction::class.java).filter(::isAtModuleScope)
```

- [ ] **Step 2: Refactor `ImplementationStubIndex.kt`**

Read the current file at `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/ImplementationStubIndex.kt`. The companion object currently contains a private `candidateFunctions(file: PsiFile): List<JSFunction>` that does the ancestor walk inline.

Replace the body of `candidateFunctions` with a call to the shared helper:

```kotlin
private fun candidateFunctions(file: PsiFile): List<JSFunction> = moduleScopeFunctions(file)
```

Or, if you want to remove the wrapper entirely, replace the two callers (`getIndexer` body and `findFunctions` body) with direct calls to `moduleScopeFunctions(psiFile)` and delete the private helper.

Either form is acceptable. Pick the more readable one. Remove now-unused imports: `JSObjectLiteralExpression`, `JSClass`, `PsiElement` if no other code in the file uses them.

- [ ] **Step 3: Refactor `ImplementationLineMarkerProvider.kt`**

Read `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/ImplementationLineMarkerProvider.kt`. The current `resolveImplementation` method calls a private `isModuleScope(fn)` helper that does its own ancestor walk.

Replace the private helper with a delegation to the shared helper:

```kotlin
// inside resolveImplementation, replace
if (!isModuleScope(fn)) return null

// with
if (!isAtModuleScope(fn)) return null
```

Then delete the private `isModuleScope` method entirely. Remove now-unused imports: `JSObjectLiteralExpression`, `JSClass` if not otherwise used.

- [ ] **Step 4: Run the full test suite**

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test
```

Expected: BUILD SUCCESSFUL, 30 tests pass. No behavior change — the refactor preserves the existing rules exactly.

- [ ] **Step 5: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src
git commit -m "Extract module-scope helpers into ModuleScope.kt

Pure refactor. The ancestor-walk that decides what counts as a
module-scope adapter function lived in two places: the index's
candidateFunctions and the line marker provider's isModuleScope.
Promote a single isAtModuleScope predicate (and a moduleScope-
Functions helper for the indexer's iteration case) so the upcoming
FactoryStubIndex can share the same definition with no risk of
the three sources drifting."
```

---

## Task 2: Create `FactoryStubIndex` (TDD)

Add the third file-based index. Indexes module-scope functions whose return type element exists, keyed by the return type's text with whitespace stripped. Three tests covering the matching cases.

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/FactoryStubIndex.kt`
- Modify: `ts-typed-functions/src/main/resources/META-INF/plugin.xml`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/FactoryStubIndexTest.kt`

- [ ] **Step 1: Write failing test — single factory by alias name**

Create `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/FactoryStubIndexTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Run test, expect compile failure**

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.FactoryStubIndexTest" -i
```

Expected: compile error — `FactoryStubIndex` not defined.

- [ ] **Step 3: Implement `FactoryStubIndex.kt`**

Create `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/FactoryStubIndex.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.TypeScriptJSXFileType
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.VoidDataExternalizer

class FactoryStubIndex : FileBasedIndexExtension<String, Void>() {

    override fun getName(): ID<String, Void> = NAME

    /**
     * Bumped via the shared [INDEX_VERSION] constant so all three indexes
     * (Signature, Implementation, Factory) always change versions together —
     * they all depend on canonicalization rules in SignatureKey and on the
     * shared module-scope filter in ModuleScope.kt.
     */
    override fun getVersion(): Int = INDEX_VERSION

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = VoidDataExternalizer.INSTANCE
    override fun getInputFilter() =
        DefaultFileTypeSpecificInputFilter(
            TypeScriptFileType.INSTANCE,
            TypeScriptJSXFileType.INSTANCE,
        )
    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        val keys = mutableMapOf<String, Void?>()
        moduleScopeFunctions(content.psiFile).forEach { fn ->
            returnTypeKey(fn)?.let { keys[it] = null }
        }
        keys
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.FactoryStubIndex")

        /**
         * The factory's return-type-element text with all whitespace stripped.
         * For `function makeFoo(): CreateIdentifierType { ... }`, this is
         * `"CreateIdentifierType"`. For functions without an explicit return
         * type, returns null (such functions are not factories).
         */
        private fun returnTypeKey(fn: JSFunction): String? {
            val ret = fn.returnTypeElement ?: return null
            return ret.text.replace(Regex("\\s+"), "")
        }

        fun findFactories(
            project: Project,
            aliasName: String,
            scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
        ): List<JSFunction> {
            val files = FileBasedIndex.getInstance().getContainingFiles(NAME, aliasName, scope)
            val psiManager = PsiManager.getInstance(project)
            val out = mutableListOf<JSFunction>()
            for (vf in files) {
                val psiFile = psiManager.findFile(vf) ?: continue
                moduleScopeFunctions(psiFile)
                    // Re-validate the key against current PSI. Defends against
                    // stale index entries (file edited after indexing).
                    .filter { returnTypeKey(it) == aliasName }
                    .forEach { out += it }
            }
            return out
        }
    }
}
```

- [ ] **Step 4: Register the index in `plugin.xml`**

In `ts-typed-functions/src/main/resources/META-INF/plugin.xml`, find the `<extensions defaultExtensionNs="com.intellij">` block and add a third `<fileBasedIndex>` line after the existing two:

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.FactoryStubIndex"/>
```

- [ ] **Step 5: Run the test, expect PASS**

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.FactoryStubIndexTest"
```

Expected: 1 test passes.

- [ ] **Step 6: Add failing test — multiple factories with same return type**

Append to `FactoryStubIndexTest.kt`:

```kotlin
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
```

- [ ] **Step 7: Run, expect PASS**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.FactoryStubIndexTest.testFindsMultipleFactoriesWithSameReturnType"
```

Expected: pass (already covered by the implementation).

- [ ] **Step 8: Add failing test — module-scope filter excludes nested/method/object-literal factories**

Append:

```kotlin
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
```

- [ ] **Step 9: Run, expect PASS**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.FactoryStubIndexTest.testNestedFunctionMethodAndObjectLiteralFactoriesExcluded"
```

Expected: pass (the shared `moduleScopeFunctions` helper enforces the rule).

- [ ] **Step 10: Run the full test suite**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test
```

Expected: 33 tests pass (30 prior + 3 new).

- [ ] **Step 11: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Add FactoryStubIndex over functions returning a named signature

FileBasedIndex keyed by the return-type-element text of every
module-scope function. findFactories(project, aliasName) returns
functions whose explicit return type names the given alias —
i.e., factories that produce an implementation. Three tests cover
single-factory lookup, multiple-factory deduplication, and the
module-scope exclusion of nested/method/object-literal factories.
Reuses the shared moduleScopeFunctions helper introduced in the
prior commit."
```

---

## Task 3: Extend `SignatureLineMarkerProvider` to surface factories

The signature gutter currently shows only direct implementations. Extend it to merge factory matches into the same `NavigationGutterIconBuilder`. Two new tests.

**Files:**
- Modify: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureLineMarkerProvider.kt`
- Modify: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureLineMarkerProviderTest.kt`

- [ ] **Step 1: Write failing test — gutter includes factories alongside implementations**

Append to `SignatureLineMarkerProviderTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test, expect PASS or partial**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.SignatureLineMarkerProviderTest.testGutterIncludesFactoriesAlongsideImplementations"
```

Expected: the assertions on `findFunctions` / `findFactories` pass. The `findGuttersAtCaret` assertion may already pass (the gutter fires off the implementation match). The test passes either way at this stage — the regression net for the merge happens in the next test.

- [ ] **Step 3: Add failing test — gutter appears for factory-only signatures**

```kotlin
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
```

- [ ] **Step 4: Run, expect FAIL**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.SignatureLineMarkerProviderTest.testGutterAppearsOnSignatureWithOnlyAFactory"
```

Expected: FAIL — the current provider only checks implementations; with no implementation present, the gutter is suppressed.

- [ ] **Step 5: Update `SignatureLineMarkerProvider.kt` to query factories too**

Read `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureLineMarkerProvider.kt`. The current `collectNavigationMarkers` looks roughly like:

```kotlin
override fun collectNavigationMarkers(
    element: PsiElement,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
) {
    val parent = element.parent as? TypeScriptTypeAlias ?: return
    if (parent.nameIdentifier !== element) return

    val key = SignatureKey.of(parent) ?: return
    val implementations = ImplementationStubIndex.findFunctions(parent.project, key)
    if (implementations.isEmpty()) return

    val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
        .setTargets(implementations)
        .setTooltipText("Implementations of this signature")
        .setPopupTitle("Implementations of ${parent.name ?: "signature"}")

    result.add(builder.createLineMarkerInfo(element))
}
```

Change it to merge factory results:

```kotlin
override fun collectNavigationMarkers(
    element: PsiElement,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
) {
    val parent = element.parent as? TypeScriptTypeAlias ?: return
    if (parent.nameIdentifier !== element) return

    val key = SignatureKey.of(parent) ?: return
    val aliasName = parent.name ?: return

    val project = parent.project
    val implementations = ImplementationStubIndex.findFunctions(project, key)
    val factories = FactoryStubIndex.findFactories(project, aliasName)
    val targets = implementations + factories
    if (targets.isEmpty()) return

    val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
        .setTargets(targets)
        .setTooltipText("Implementations of this signature")
        .setPopupTitle("Implementations of $aliasName")

    result.add(builder.createLineMarkerInfo(element))
}
```

Note the changes:
- Resolve `aliasName` once (early-return if null).
- Query both indexes.
- Concatenate results.
- Build the gutter from the combined `targets`.

- [ ] **Step 6: Run, expect PASS**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.SignatureLineMarkerProviderTest"
```

Expected: all tests pass (the original 4 plus the 2 new).

- [ ] **Step 7: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src
git commit -m "Surface factories in the signature gutter

Merge FactoryStubIndex.findFactories results into the same
NavigationGutterIconBuilder used for direct implementations. The
gutter now appears for any signature that has at least one
implementation OR factory, and the popup lists both kinds. Two
new tests cover the merged path and the factory-only case."
```

---

## Task 4: Extend `FindImplementationsAction` with `[factory]` labeled rows

The keyboard-driven action mirrors the gutter but with custom row text. Add the `Match` wrapper, query factories, and prefix factory rows with `[factory]`.

**Files:**
- Modify: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/FindImplementationsAction.kt`
- Modify: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/FindImplementationsActionTest.kt`

- [ ] **Step 1: Write failing test — action surfaces both kinds with correct row text**

Append to `FindImplementationsActionTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run, expect compile failure**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.FindImplementationsActionTest.testActionResolvesFactoriesAndImplementationsTogether" -i
```

Expected: compile error — `Match`, `MatchPopupStep`, and `collectMatches` not defined.

- [ ] **Step 3: Restructure `FindImplementationsAction.kt`**

Read the current file at `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/FindImplementationsAction.kt`. Refactor so the popup-step construction and the match-collection are testable separately.

Replace the file contents with:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

internal data class Match(val fn: JSFunction, val isFactory: Boolean)

internal class MatchPopupStep(
    aliasName: String,
    matches: List<Match>,
) : BaseListPopupStep<Match>("Implementations of $aliasName", matches) {

    override fun getTextFor(value: Match): String {
        val name = value.fn.name ?: "<anonymous>"
        val file = value.fn.containingFile?.name ?: "?"
        val prefix = if (value.isFactory) "[factory] " else ""
        return "$prefix$name  ($file)"
    }

    override fun onChosen(selected: Match, finalChoice: Boolean): PopupStep<*>? {
        ApplicationManager.getApplication().invokeLater {
            selected.fn.navigate(true)
        }
        return FINAL_CHOICE
    }
}

internal fun collectMatches(project: Project, alias: TypeScriptTypeAlias): List<Match> {
    val key = SignatureKey.of(alias) ?: return emptyList()
    val aliasName = alias.name ?: return emptyList()

    val implementations = ImplementationStubIndex.findFunctions(project, key)
        .map { Match(it, isFactory = false) }
    val factories = FactoryStubIndex.findFactories(project, aliasName)
        .map { Match(it, isFactory = true) }
    return implementations + factories
}

class FindImplementationsAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = aliasUnderCaret(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val alias = aliasUnderCaret(e) ?: return
        val project = e.project ?: return
        val aliasName = alias.name ?: return
        val matches = collectMatches(project, alias)

        if (matches.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No implementations found")
                .showInBestPositionFor(e.dataContext)
            return
        }

        val step = MatchPopupStep(aliasName, matches)
        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showInBestPositionFor(e.dataContext)
    }

    private fun aliasUnderCaret(e: AnActionEvent): TypeScriptTypeAlias? {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? PsiFile ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val alias = PsiTreeUtil.getParentOfType(element, TypeScriptTypeAlias::class.java) ?: return null
        return if (SignatureKey.of(alias) != null) alias else null
    }
}
```

Key changes from the previous version:
- `Match` data class lifted to file scope (was an anonymous step).
- `MatchPopupStep` extracted as its own class for testability.
- `collectMatches(project, alias)` extracted so tests can verify the lookup pipeline directly.
- `actionPerformed` becomes a thin wrapper that delegates to `collectMatches` + `MatchPopupStep`.

- [ ] **Step 4: Run, expect PASS**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test --tests "io.meiro.tstypedfunctions.FindImplementationsActionTest"
```

Expected: all FindImplementationsActionTest tests pass (the 3 previous + the 1 new).

If the `internal` visibility on `Match` / `MatchPopupStep` / `collectMatches` causes the test (which lives in the same package) to fail to compile, double-check that the test is in package `io.meiro.tstypedfunctions` (no nested subpackage). Internal visibility is module-scoped in Kotlin, and tests are in the same module — the test should see `internal` symbols.

- [ ] **Step 5: Run the full test suite**

```bash
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test
```

Expected: 36 tests pass (33 prior + 3 from Task 2's tests + 2 from Task 3 + 1 from Task 4 = wait, Task 2 added 3, Task 3 added 2, Task 4 adds 1. So 30 + 3 + 2 + 1 = 36).

Actually: Task 2 added 3 tests, Task 3 added 2, Task 4 adds 1. Total new = 6. Total expected = 30 + 6 = 36. Confirm.

- [ ] **Step 6: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src
git commit -m "Surface factories in FindImplementationsAction with [factory] prefix

Restructure the action so Match, MatchPopupStep, and the
collectMatches lookup pipeline live at file scope (internal) and
can be tested directly. The popup list now includes both direct
implementations and factories, with factory rows prefixed
[factory] to disambiguate. One new test verifies row text and
match-set composition."
```

---

## Task 5: Update README and architecture doc

Surface the new behavior to users and to anyone reading the architecture doc.

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`

- [ ] **Step 1: Update README "Usage" section**

Read `/home/odis/meiro/ts-typed-functions/README.md`. The current Usage section ends with:

```
The action **Find Implementations of Type Signature** runs from the caret on a function-typed alias. Default shortcut: `Ctrl+Alt+Shift+P` (Windows/Linux) or `Cmd+Alt+Shift+P` (macOS). Also accessible via the editor's right-click menu.
```

Add a paragraph after it:

```
Both the gutter and the action also surface **factories** — top-level functions whose explicit return type names the signature alias. For example, `function makeCreateIdentifierType(): CreateIdentifierType { ... }` will appear among the matches for `CreateIdentifierType`, marked `[factory]` in the action's popup. The factory's own parameter shape is not checked; only its declared return type matters.
```

- [ ] **Step 2: Update `docs/architecture.md`**

Read `/home/odis/meiro/ts-typed-functions/docs/architecture.md`. Find the "### Indexes (background)" section. After the bullet for `ImplementationStubIndex`, add a third bullet:

```
- **`FactoryStubIndex`** (`FactoryStubIndex.kt`) — visits the same module-scope `JSFunction` set as `ImplementationStubIndex` (via the shared `moduleScopeFunctions` helper in `ModuleScope.kt`) but keys entries by the function's return-type-element text rather than its `SignatureKey`. `findFactories(project, aliasName)` looks up functions whose explicit return type names a signature alias — i.e., factories that produce an implementation. Used by the signature-side gutter and the `Find Implementations` action to surface factories alongside direct implementations.
```

Also update the "Components" intro paragraph that says "two file-based indexes" — change to "three file-based indexes" if such a phrase exists (search for "two" near the top of the file).

In the typical-flow numbered list, find step 2 ("Indexing.") and update it to mention all three indexes if it currently says only two.

- [ ] **Step 3: Run the full pipeline as a final check**

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew clean test verifyPlugin buildPlugin
```

Expected: BUILD SUCCESSFUL. 36 tests pass. `verifyPlugin` reports Compatible. ZIP at `ts-typed-functions/build/distributions/ts-typed-functions-0.1.0.zip`.

- [ ] **Step 4: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add README.md docs/architecture.md
git commit -m "Document factory detection in README and architecture doc

README's Usage section gains a paragraph describing the factory
detection rule and the [factory] prefix in the action popup.
Architecture doc gains a FactoryStubIndex bullet and updates
the index count from two to three."
```

---

## Done

When all task checkboxes are checked:

- A new `FactoryStubIndex` indexes module-scope functions by their declared return-type-element text.
- `SignatureLineMarkerProvider` and `FindImplementationsAction` query both `ImplementationStubIndex` and `FactoryStubIndex`, surfacing factories alongside direct implementations.
- The action's popup labels factory rows with `[factory] ` to distinguish them.
- The shared `moduleScopeFunctions` / `isAtModuleScope` helpers in `ModuleScope.kt` are the single source of truth for what counts as "module-scope," used by all three indexes and by the implementation line marker provider.
- 36 tests pass end-to-end. `verifyPlugin` Compatible. Plugin ZIP produced.
- README and architecture doc reflect the new behavior.
