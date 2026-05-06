# ts-typed-functions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a JetBrains plugin that finds TypeScript functions whose signature matches a given port type alias, even when the function does not name the alias.

**Architecture:** A pure `SignatureKey` function canonicalizes TypeScript function types into a normalized string. Two `StubIndex` extensions (`PortStubIndex`, `AdapterStubIndex`) index type aliases and adapter functions by that key. Two `RelatedItemLineMarkerProvider`s render gutter icons in both directions, and an `AnAction` exposes the same lookup via keyboard shortcut. All on top of WebStorm's existing TypeScript PSI.

**Tech Stack:** Kotlin 2.x · Gradle Kotlin DSL · `org.jetbrains.intellij.platform` Gradle plugin 2.x · IntelliJ Platform 2024.3 (build 243) · WebStorm/IDEA Ultimate JavaScript plugin · `BasePlatformTestCase` for tests.

**Reference spec:** `docs/superpowers/specs/2026-05-04-ts-typed-functions-design.md`

**Working directory for plugin code:** `ts-typed-functions/` (the inner directory at the repo root). All paths in this plan are relative to `/home/odis/meiro/ts-typed-functions/` unless otherwise noted.

---

## File structure

After all tasks complete, the plugin project will look like this:

```
ts-typed-functions/                                       (inner project root)
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/                                       (Gradle wrapper files)
├── src/
│   ├── main/
│   │   ├── kotlin/io/meiro/tstypedfunctions/
│   │   │   ├── SignatureKey.kt                           (pure canonicalizer)
│   │   │   ├── PortStubIndex.kt                          (type alias index)
│   │   │   ├── AdapterStubIndex.kt                       (function index)
│   │   │   ├── PortLineMarkerProvider.kt                 (port → adapter gutter)
│   │   │   ├── AdapterLineMarkerProvider.kt              (adapter → port gutter)
│   │   │   └── FindImplementationsAction.kt              (action)
│   │   └── resources/META-INF/plugin.xml                 (plugin descriptor)
│   └── test/
│       ├── kotlin/io/meiro/tstypedfunctions/
│       │   ├── SignatureKeyPortTest.kt
│       │   ├── SignatureKeyAdapterTest.kt
│       │   ├── PortStubIndexTest.kt
│       │   ├── AdapterStubIndexTest.kt
│       │   ├── PortLineMarkerProviderTest.kt
│       │   ├── AdapterLineMarkerProviderTest.kt
│       │   └── FindImplementationsActionTest.kt
│       └── testData/
│           └── (.ts fixtures used by tests)
└── .github/workflows/build.yml                           (CI)
```

Each `.kt` file in `main/` has one responsibility. Tests live alongside in parallel paths.

---

## Task 1: Bootstrap Gradle / Kotlin plugin scaffold

Goal: a buildable, runnable empty plugin that depends on the JavaScript plugin and ships a smoke-test that runs in CI-style headless mode. No business logic yet.

**Files:**
- Create: `ts-typed-functions/.gitignore`
- Create: `ts-typed-functions/settings.gradle.kts`
- Create: `ts-typed-functions/gradle.properties`
- Create: `ts-typed-functions/build.gradle.kts`
- Create: `ts-typed-functions/src/main/resources/META-INF/plugin.xml`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SmokeTest.kt`

- [ ] **Step 1: Replace the placeholder `.gitignore`**

Overwrite `ts-typed-functions/.gitignore` with:

```gitignore
.gradle/
build/
out/
.idea/
*.iml
.kotlin/
local.properties
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "ts-typed-functions"
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
pluginGroup=io.meiro.tstypedfunctions
pluginName=ts-typed-functions
pluginVersion=0.1.0

pluginSinceBuild=243
pluginUntilBuild=251.*

platformType=IU
platformVersion=2024.3.5

platformBundledPlugins=JavaScript

gradleVersion=8.10.2

org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC
org.gradle.caching=true
org.gradle.configuration-cache=false
kotlin.stdlib.default.dependency=false
```

- [ ] **Step 4: Create `build.gradle.kts`**

```kotlin
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins").map { it.split(",") },
        )
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
    systemProperty("idea.force.use.core.classloader", "true")
}
```

- [ ] **Step 5: Create the plugin descriptor**

`ts-typed-functions/src/main/resources/META-INF/plugin.xml`:

```xml
<idea-plugin>
    <id>io.meiro.ts-typed-functions</id>
    <name>TS Typed Functions</name>
    <vendor>Meiro</vendor>

    <description><![CDATA[
        Find TypeScript functions whose signature structurally matches a port type alias,
        even when the function does not name the alias.
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>JavaScript</depends>

    <extensions defaultExtensionNs="com.intellij">
    </extensions>

    <actions>
    </actions>
</idea-plugin>
```

- [ ] **Step 6: Generate the Gradle wrapper**

```bash
cd ts-typed-functions
gradle wrapper --gradle-version 8.10.2
```

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

If `gradle` is not on `PATH`, install via SDKMAN: `sdk install gradle 8.10.2`.

- [ ] **Step 7: Write a smoke test**

`ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SmokeTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmokeTest : BasePlatformTestCase() {
    fun testProjectAvailable() {
        assertNotNull(project)
    }
}
```

- [ ] **Step 8: Run the smoke test to confirm the test framework wires up**

```bash
cd ts-typed-functions
./gradlew test --tests "io.meiro.tstypedfunctions.SmokeTest" --info
```

Expected: BUILD SUCCESSFUL, one test passes. First run downloads the IntelliJ Platform (several hundred MB) — be patient.

If it fails on `TestFrameworkType.Platform` not resolving: the `org.jetbrains.intellij.platform` Gradle plugin's API names may have shifted across 2.x patch versions. Check the [plugin docs](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) and adjust the `testFramework(...)` call. The correct method exists; only the enum constant may need adjustment.

- [ ] **Step 9: Verify `verifyPlugin`**

```bash
./gradlew verifyPlugin
```

Expected: BUILD SUCCESSFUL with no compatibility warnings. Confirms the descriptor is well-formed against `sinceBuild=243`.

- [ ] **Step 10: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/
git commit -m "Bootstrap IntelliJ plugin scaffold

Gradle Kotlin DSL with the IntelliJ Platform Gradle plugin 2.x,
targeting IDEA Ultimate / WebStorm 2024.3+. Empty plugin.xml,
JavaScript bundled-plugin dependency, and a smoke test that
exercises the test framework end-to-end."
```

---

## Task 2: `SignatureKey` — port type aliases

Goal: implement the canonicalizer for `TypeScriptFunctionType` (the RHS of port type aliases). Returns `null` for generic aliases or anything that isn't a function type.

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureKey.kt`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyPortTest.kt`

**Test design notes:**
- Use `myFixture.configureByText("a.ts", source)` to get a TS file with PSI.
- Walk the PSI to the relevant element with `PsiTreeUtil.findChildOfType`.
- TS PSI types live in package `com.intellij.lang.javascript.psi.ecma6` — `TypeScriptTypeAlias`, `TypeScriptFunctionType`, `TypeScriptFunction`, etc.

- [ ] **Step 1: Write failing test for the basic single-param port**

`SignatureKeyPortTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SignatureKeyPortTest : BasePlatformTestCase() {

    private fun keyOfFirstAlias(text: String): String? {
        myFixture.configureByText("a.ts", text)
        val alias = PsiTreeUtil.findChildOfType(myFixture.file, TypeScriptTypeAlias::class.java)
            ?: error("no type alias in fixture")
        return SignatureKey.of(alias)
    }

    fun testBasicSingleParamPort() {
        val key = keyOfFirstAlias(
            """
            type Port = (input: Foo) => Promise<Bar>;
            """.trimIndent(),
        )
        assertEquals("(:Foo)=>Promise<Bar>", key)
    }
}
```

- [ ] **Step 2: Run the test, expect it to fail**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyPortTest" -i
```

Expected: compile failure on `SignatureKey.of(alias)` — class not yet defined.

- [ ] **Step 3: Implement minimal `SignatureKey.of` for type aliases**

`SignatureKey.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunctionType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

object SignatureKey {

    fun of(alias: TypeScriptTypeAlias): String? {
        if (alias.typeParameters.isNotEmpty()) return null
        val fnType = alias.typeDeclaration as? TypeScriptFunctionType ?: return null
        return ofFunctionType(fnType)
    }

    private fun ofFunctionType(fn: TypeScriptFunctionType): String? {
        val params = fn.parameters.joinToString(",") { p ->
            val type = p.typeElement ?: return null
            buildString {
                if (p.isOptional) append("?")
                if (p.isRest) append("...")
                append(":")
                append(normalize(type))
            }
        }
        val ret = fn.returnTypeElement ?: return null
        return "($params)=>${normalize(ret)}"
    }

    private fun normalize(element: PsiElement): String {
        val withoutComments = stripComments(element)
        return withoutComments.replace(Regex("\\s+"), "")
    }

    private fun stripComments(element: PsiElement): String {
        val sb = StringBuilder()
        element.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(e: PsiElement) {
                if (e is PsiComment) return
                if (e.firstChild == null) sb.append(e.text)
                super.visitElement(e)
            }
        })
        return sb.toString()
    }
}
```

- [ ] **Step 4: Run the test, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyPortTest.testBasicSingleParamPort" -i
```

Expected: 1 passed.

If it fails because `TypeScriptFunctionType.parameters` / `.returnTypeElement` / `.typeElement` API names differ in the platform version: open the JS PSI module in the IDE (Ctrl+N → `TypeScriptFunctionType`) and adjust property names. The shape (parameters list + return type element + per-parameter type element + per-parameter `isOptional`/`isRest`) exists; only the accessor names may shift.

- [ ] **Step 5: Add failing test — `async` port alternative form**

Append to `SignatureKeyPortTest.kt`:

```kotlin
fun testReturnTypeIsPromise() {
    val key = keyOfFirstAlias(
        """
        type Port = (input: CreateIdentifierTypeInput) => Promise<CreateIdentifierTypeResult>;
        """.trimIndent(),
    )
    assertEquals("(:CreateIdentifierTypeInput)=>Promise<CreateIdentifierTypeResult>", key)
}
```

- [ ] **Step 6: Run, expect PASS** (already covered by minimal impl)

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyPortTest"
```

- [ ] **Step 7: Add failing test — optional and rest params**

```kotlin
fun testOptionalAndRestParams() {
    val keyOptional = keyOfFirstAlias("type P = (a: A, b?: B) => void;")
    assertEquals("(:A,?:B)=>void", keyOptional)

    val keyRest = keyOfFirstAlias("type P = (...xs: T[]) => U;")
    assertEquals("(...:T[])=>U", keyRest)
}
```

- [ ] **Step 8: Run, expect PASS or FAIL — fix as needed**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyPortTest.testOptionalAndRestParams" -i
```

If the rest-param key comes out as `(...args:T[])=>U` (parameter name leaking through) or `:T[]` (missing `...`), that means the `isRest` accessor differs. Inspect `JSParameter` / `TypeScriptParameter` in the IDE and fix the accessor name. The test must end green.

- [ ] **Step 9: Add failing test — generic alias returns null**

```kotlin
fun testGenericAliasReturnsNull() {
    assertNull(keyOfFirstAlias("type Handler<T> = (x: T) => void;"))
}
```

- [ ] **Step 10: Run, expect PASS** (already covered by `typeParameters.isNotEmpty()` check)

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyPortTest"
```

- [ ] **Step 11: Add failing test — non-function alias returns null**

```kotlin
fun testNonFunctionAliasReturnsNull() {
    assertNull(keyOfFirstAlias("type Foo = { id: string };"))
}
```

- [ ] **Step 12: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyPortTest"
```

- [ ] **Step 13: Add failing test — multi-line / commented type is normalized**

```kotlin
fun testWhitespaceAndCommentsAreStripped() {
    val key = keyOfFirstAlias(
        """
        type Port = (
          /* the input */ input: Foo,
        ) => Promise<
          Bar
        >;
        """.trimIndent(),
    )
    assertEquals("(:Foo)=>Promise<Bar>", key)
}
```

- [ ] **Step 14: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyPortTest"
```

If it fails because the parameter trailing comma is preserved or comment text leaks through, refine `normalize`/`stripComments`. Iterate until green.

- [ ] **Step 15: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src
git commit -m "Add SignatureKey canonicalization for port type aliases

Pure function turning a TypeScriptTypeAlias whose RHS is a function
type into a canonical signature string with parameter names dropped
and whitespace/comments normalized. Skips generic aliases."
```

---

## Task 3: `SignatureKey` — adapter functions

Goal: extend `SignatureKey` to accept fully-typed adapter functions (`TypeScriptFunction` declarations and arrow / function expressions assigned to `const`/`let`). Adapters with any missing type return `null` and are not indexed.

**Files:**
- Modify: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureKey.kt`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyAdapterTest.kt`

**Test design notes:**
- For `function fn(...) { ... }`: PSI is `TypeScriptFunction` directly under the file.
- For `const fn = (...) => ...`: PSI is `JSVariable` whose `initializer` is `JSFunctionExpression` / `JSArrowFunctionExpression` (which is also a `TypeScriptFunction` when parameters are typed).

- [ ] **Step 1: Write failing test — function declaration**

`SignatureKeyAdapterTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SignatureKeyAdapterTest : BasePlatformTestCase() {

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
}
```

- [ ] **Step 2: Run test, expect compile failure**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyAdapterTest" -i
```

Expected: compile error — `SignatureKey.of(JSFunction)` overload doesn't exist.

- [ ] **Step 3: Add `JSFunction` overload**

Edit `SignatureKey.kt`. Add this method to the `object`:

```kotlin
fun of(fn: com.intellij.lang.javascript.psi.JSFunction): String? {
    if (fn.typeParameters.isNotEmpty()) return null
    val params = fn.parameters.joinToString(",") { p ->
        val type = p.typeElement ?: return null
        buildString {
            if (p.isOptional) append("?")
            if (p.isRest) append("...")
            append(":")
            append(normalize(type))
        }
    }
    val ret = fn.returnTypeElement ?: return null
    return "($params)=>${normalize(ret)}"
}
```

The body is structurally identical to `ofFunctionType`. Refactor to share:

```kotlin
private fun build(
    typeParams: List<*>,
    parameters: Array<out com.intellij.lang.javascript.psi.JSParameterListElement>,
    returnType: PsiElement?,
): String? {
    if (typeParams.isNotEmpty()) return null
    val params = parameters.joinToString(",") { p ->
        val type = (p as? com.intellij.lang.javascript.psi.JSParameter)?.typeElement ?: return null
        buildString {
            if (p.isOptional) append("?")
            if (p.isRest) append("...")
            append(":")
            append(normalize(type))
        }
    }
    val ret = returnType ?: return null
    return "($params)=>${normalize(ret)}"
}
```

then have both `of(TypeScriptTypeAlias)` (via `ofFunctionType`) and `of(JSFunction)` delegate. Final shape (replace the body of `SignatureKey.kt` with this consolidated version):

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSParameter
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunctionType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor

object SignatureKey {

    fun of(alias: TypeScriptTypeAlias): String? {
        if (alias.typeParameters.isNotEmpty()) return null
        val fnType = alias.typeDeclaration as? TypeScriptFunctionType ?: return null
        return build(fnType.parameters.toList(), fnType.returnTypeElement)
    }

    fun of(fn: JSFunction): String? {
        if (fn.typeParameters.isNotEmpty()) return null
        return build(fn.parameters.toList(), fn.returnTypeElement)
    }

    private fun build(parameters: List<JSParameter>, returnType: PsiElement?): String? {
        val ret = returnType ?: return null
        val params = parameters.joinToString(",") { p ->
            val type = p.typeElement ?: return null
            buildString {
                if (p.isOptional) append("?")
                if (p.isRest) append("...")
                append(":")
                append(normalize(type))
            }
        }
        return "($params)=>${normalize(ret)}"
    }

    private fun normalize(element: PsiElement): String =
        stripComments(element).replace(Regex("\\s+"), "")

    private fun stripComments(element: PsiElement): String {
        val sb = StringBuilder()
        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(e: PsiElement) {
                if (e is PsiComment) return
                if (e.firstChild == null) sb.append(e.text)
                super.visitElement(e)
            }
        })
        return sb.toString()
    }
}
```

Note: `TypeScriptFunctionType.parameters` and `JSFunction.parameters` may have different element types in the platform — if so, change the helper to take `Array<JSParameterListElement>` and cast to `JSParameter` inside. Verify by running the existing port tests after refactor.

- [ ] **Step 4: Run all `SignatureKey` tests, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKey*"
```

Expected: all port tests still green; `testFunctionDeclaration` green.

- [ ] **Step 5: Add failing test — `async` arrow function adapter matches sync `Promise<T>` port**

```kotlin
fun testAsyncArrowAdapter() {
    val key = keyOfFirstFunction(
        """
        export const impl = async (input: Foo): Promise<Bar> => {
          return null as any;
        };
        """.trimIndent(),
    )
    assertEquals("(:Foo)=>Promise<Bar>", key)
}
```

- [ ] **Step 6: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyAdapterTest"
```

If it fails: the `JSFunction.returnTypeElement` for arrow functions may be exposed differently than for declarations. Inspect `JSArrowFunctionExpression` in the IDE; if it overrides `returnTypeElement` — good, no change needed. Otherwise, special-case it.

- [ ] **Step 7: Add failing test — adapter without explicit return type returns null**

```kotlin
fun testAdapterWithoutReturnTypeReturnsNull() {
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
```

- [ ] **Step 8: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyAdapterTest.testAdapterWithoutReturnTypeReturnsNull"
```

- [ ] **Step 9: Add failing test — adapter with one untyped parameter returns null**

```kotlin
fun testAdapterWithUntypedParamReturnsNull() {
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
```

- [ ] **Step 10: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyAdapterTest.testAdapterWithUntypedParamReturnsNull"
```

- [ ] **Step 11: Add failing test — port + adapter produce equal keys (the property we actually care about)**

```kotlin
fun testPortAndAdapterProduceEqualKeys() {
    myFixture.configureByText(
        "a.ts",
        """
        export type Port = (input: Foo) => Promise<Bar>;

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
```

- [ ] **Step 12: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.SignatureKeyAdapterTest.testPortAndAdapterProduceEqualKeys"
```

This is the most important test — it locks the port↔adapter symmetry that the rest of the plugin depends on. If anything is off here, fix `SignatureKey` until green.

- [ ] **Step 13: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src
git commit -m "Extend SignatureKey to adapter functions

Adds a JSFunction overload sharing canonicalization with the type
alias path. Adapters missing any parameter type or the return type
yield null and will not be indexed. Tests confirm port/adapter
symmetry of the resulting key."
```

---

## Task 4: `PortStubIndex`

Goal: a `StringStubIndexExtension<TypeScriptTypeAlias>` keyed by `SignatureKey`, populated via a `StubIndexExtension` that extracts the key during stub building.

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/PortStubIndex.kt`
- Modify: `ts-typed-functions/src/main/resources/META-INF/plugin.xml`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/PortStubIndexTest.kt`

**Background:** WebStorm already builds stubs for TS files. The cheapest way to add a custom index over those stubs is via `StubIndexExtension` + a hook into the existing stub-building pipeline. JS stubs expose extension points through `JSElementIndexingDataFactory` / `IndexSink`. For our use case the simplest mechanism is a `StringStubIndexExtension` plus a `JSStubElementType.indexStub` override... which we don't have. The portable approach: implement `FileBasedIndexExtension<String, Void>` that scans `.ts`/`.tsx` files via PSI on each indexing pass. This is lighter than rebuilding stubs and doesn't require subclassing JS stub element types.

We use **`FileBasedIndexExtension`** for both `PortStubIndex` and `AdapterStubIndex`. (The class names retain the word "Stub" only as a project naming convention — the implementation is `FileBasedIndexExtension`.)

- [ ] **Step 1: Write failing test**

`PortStubIndexTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

class PortStubIndexTest : BasePlatformTestCase() {

    fun testFindsPortByKey() {
        myFixture.configureByText(
            "ports.ts",
            """
            export type Port = (input: Foo) => Promise<Bar>;
            export type Other = (x: number) => string;
            """.trimIndent(),
        )

        val key = "(:Foo)=>Promise<Bar>"
        val files = ReadAction.compute<Collection<*>, RuntimeException> {
            FileBasedIndex.getInstance().getContainingFiles(
                PortStubIndex.NAME,
                key,
                GlobalSearchScope.projectScope(project),
            )
        }
        assertEquals(1, files.size)
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.PortStubIndexTest" -i
```

Expected: `PortStubIndex` not defined.

- [ ] **Step 3: Implement `PortStubIndex`**

`PortStubIndex.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class PortStubIndex : FileBasedIndexExtension<String, Void>() {

    override fun getName(): ID<String, Void> = NAME

    override fun getVersion(): Int = 1

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer() = com.intellij.util.io.VoidDataExternalizer.INSTANCE

    override fun getInputFilter() =
        DefaultFileTypeSpecificInputFilter(TypeScriptFileType.INSTANCE)

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        val psiFile = content.psiFile
        val keys = mutableMapOf<String, Void?>()
        PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptTypeAlias::class.java).forEach { alias ->
            SignatureKey.of(alias)?.let { keys[it] = null }
        }
        keys
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.PortStubIndex")
    }
}
```

- [ ] **Step 4: Register the index in `plugin.xml`**

In the `<extensions>` block of `plugin.xml`, add:

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.PortStubIndex"/>
```

- [ ] **Step 5: Run the test, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.PortStubIndexTest"
```

Expected: 1 passed. The fixture indexes `ports.ts`, the test queries by key, gets back 1 file.

If it fails with "VoidDataExternalizer.INSTANCE not found": replace with `object : DataExternalizer<Void> { override fun save(out: DataOutput, value: Void?) {}; override fun read(input: DataInput): Void? = null }`.

If it fails because `TypeScriptFileType.INSTANCE` isn't found: try `com.intellij.lang.javascript.dialects.TypeScriptLanguageDialect` and `LanguageFileType` lookups, or use `JavaScriptFileType.INSTANCE` and filter by extension inside the indexer.

- [ ] **Step 6: Add failing test — multiple ports with the same key**

```kotlin
fun testFindsMultiplePortsWithSameKey() {
    myFixture.addFileToProject(
        "a.ts",
        "export type A = (input: Foo) => Promise<Bar>;",
    )
    myFixture.addFileToProject(
        "b.ts",
        "export type B = (input: Foo) => Promise<Bar>;",
    )

    val files = com.intellij.openapi.application.ReadAction.compute<Collection<*>, RuntimeException> {
        com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(
            PortStubIndex.NAME,
            "(:Foo)=>Promise<Bar>",
            com.intellij.psi.search.GlobalSearchScope.projectScope(project),
        )
    }
    assertEquals(2, files.size)
}
```

- [ ] **Step 7: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.PortStubIndexTest.testFindsMultiplePortsWithSameKey"
```

- [ ] **Step 8: Add a helper that returns aliases (not just files)**

In `PortStubIndex.kt`, append to the `companion object`:

```kotlin
fun findAliases(
    project: com.intellij.openapi.project.Project,
    key: String,
    scope: com.intellij.psi.search.GlobalSearchScope = com.intellij.psi.search.GlobalSearchScope.projectScope(project),
): List<TypeScriptTypeAlias> {
    val files = FileBasedIndex.getInstance().getContainingFiles(NAME, key, scope)
    val psiManager = PsiManager.getInstance(project)
    val out = mutableListOf<TypeScriptTypeAlias>()
    for (vf in files) {
        val psiFile = psiManager.findFile(vf) ?: continue
        PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptTypeAlias::class.java)
            .filter { SignatureKey.of(it) == key }
            .forEach { out += it }
    }
    return out
}
```

- [ ] **Step 9: Add failing test for `findAliases` helper**

```kotlin
fun testFindAliasesHelperReturnsMatchingAliases() {
    myFixture.configureByText(
        "ports.ts",
        """
        export type Port = (input: Foo) => Promise<Bar>;
        export type Other = (x: number) => string;
        """.trimIndent(),
    )

    val results = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
        PortStubIndex.findAliases(project, "(:Foo)=>Promise<Bar>")
    }
    assertEquals(1, results.size)
    val alias = results.single() as com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
    assertEquals("Port", alias.name)
}
```

- [ ] **Step 10: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.PortStubIndexTest.testFindAliasesHelperReturnsMatchingAliases"
```

- [ ] **Step 11: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Add PortStubIndex over TypeScript type aliases

FileBasedIndex keyed by SignatureKey over .ts files, registered in
plugin.xml. Includes a findAliases(project, key) helper that does
the file-to-alias resolution and re-validates each candidate's key
to defend against stale index entries."
```

---

## Task 5: `AdapterStubIndex`

Goal: same shape as `PortStubIndex` but indexes adapter functions (top-level `function` declarations + `const`/`let` arrow/function-expression initializers).

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterStubIndex.kt`
- Modify: `ts-typed-functions/src/main/resources/META-INF/plugin.xml`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/AdapterStubIndexTest.kt`

- [ ] **Step 1: Write failing test**

`AdapterStubIndexTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

class AdapterStubIndexTest : BasePlatformTestCase() {

    fun testFindsAdapterByKey() {
        myFixture.configureByText(
            "adapter.ts",
            """
            export const implA = async (input: Foo): Promise<Bar> => null as any;
            export function implB(input: Foo): Promise<Bar> { return null as any; }
            export const unrelated = (n: number): string => "x";
            """.trimIndent(),
        )

        val files = ReadAction.compute<Collection<*>, RuntimeException> {
            FileBasedIndex.getInstance().getContainingFiles(
                AdapterStubIndex.NAME,
                "(:Foo)=>Promise<Bar>",
                GlobalSearchScope.projectScope(project),
            )
        }
        assertEquals(1, files.size)

        val fns = ReadAction.compute<List<*>, RuntimeException> {
            AdapterStubIndex.findFunctions(project, "(:Foo)=>Promise<Bar>")
        }
        assertEquals(2, fns.size) // implA + implB
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.AdapterStubIndexTest" -i
```

- [ ] **Step 3: Implement `AdapterStubIndex`**

`AdapterStubIndex.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.VoidDataExternalizer

class AdapterStubIndex : FileBasedIndexExtension<String, Void>() {

    override fun getName(): ID<String, Void> = NAME
    override fun getVersion(): Int = 1
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = VoidDataExternalizer.INSTANCE
    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(TypeScriptFileType.INSTANCE)
    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        val psiFile = content.psiFile
        val keys = mutableMapOf<String, Void?>()
        candidateFunctions(psiFile).forEach { fn ->
            SignatureKey.of(fn)?.let { keys[it] = null }
        }
        keys
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.AdapterStubIndex")

        /**
         * Module-scope functions: top-level `function` declarations and arrow/function
         * expressions assigned to `const`/`let`. Excludes nested functions and class
         * methods by rejecting any function whose ancestor chain contains another
         * `JSFunction` or a `JSClass`.
         */
        private fun candidateFunctions(file: com.intellij.psi.PsiFile): List<JSFunction> =
            PsiTreeUtil.findChildrenOfType(file, JSFunction::class.java).filter { fn ->
                var p: com.intellij.psi.PsiElement? = fn.parent
                while (p != null && p !== file) {
                    if (p is JSFunction) return@filter false
                    if (p is com.intellij.lang.javascript.psi.ecmal4.JSClass) return@filter false
                    p = p.parent
                }
                true
            }

        fun findFunctions(
            project: Project,
            key: String,
            scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
        ): List<JSFunction> {
            val files = FileBasedIndex.getInstance().getContainingFiles(NAME, key, scope)
            val psiManager = PsiManager.getInstance(project)
            val out = mutableListOf<JSFunction>()
            for (vf in files) {
                val psiFile = psiManager.findFile(vf) ?: continue
                candidateFunctions(psiFile)
                    .filter { SignatureKey.of(it) == key }
                    .forEach { out += it }
            }
            return out
        }
    }
}
```

- [ ] **Step 4: Register in `plugin.xml`**

Add inside the `<extensions>` block, after the port index registration:

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.AdapterStubIndex"/>
```

- [ ] **Step 5: Run the test, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.AdapterStubIndexTest"
```

Expected: green. If `JSEmbeddedContent` isn't a thing in the platform you target, drop that branch — for a `.ts` file the parent of a top-level function declaration is the `PsiFile` itself.

- [ ] **Step 6: Add failing test — adapters with missing types are not indexed**

```kotlin
fun testAdaptersMissingTypesAreNotIndexed() {
    myFixture.configureByText(
        "adapter.ts",
        """
        export const a = (input) => null as any;
        export const b = (input: Foo) => null as any;
        export const c = (input: Foo): Promise<Bar> => null as any;
        """.trimIndent(),
    )

    val files = com.intellij.openapi.application.ReadAction.compute<Collection<*>, RuntimeException> {
        com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(
            AdapterStubIndex.NAME,
            "(:Foo)=>Promise<Bar>",
            com.intellij.psi.search.GlobalSearchScope.projectScope(project),
        )
    }
    assertEquals(1, files.size) // only c is indexed under this key

    val fns = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
        AdapterStubIndex.findFunctions(project, "(:Foo)=>Promise<Bar>")
    }
    assertEquals(1, fns.size)
    val fn = fns.single() as com.intellij.lang.javascript.psi.JSFunction
    assertEquals("c", (fn.parent as? com.intellij.lang.javascript.psi.JSVariable)?.name)
}
```

- [ ] **Step 7: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.AdapterStubIndexTest.testAdaptersMissingTypesAreNotIndexed"
```

- [ ] **Step 8: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Add AdapterStubIndex over TypeScript adapter functions

Indexes top-level function declarations and const/let initializers
that are arrow/function expressions, only when fully typed. Pairs
with PortStubIndex via the shared SignatureKey, enabling O(1)
port↔adapter lookup."
```

---

## Task 6: `PortLineMarkerProvider` (port → adapters gutter)

Goal: gutter icon on the *name* of a function-typed type alias. Click → popup of all matching adapters.

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/PortLineMarkerProvider.kt`
- Modify: `ts-typed-functions/src/main/resources/META-INF/plugin.xml`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/PortLineMarkerProviderTest.kt`

**Test design notes:** `myFixture.findGuttersAtCaret()` returns gutter icons at the caret. Place the caret on the alias *name identifier* with `<caret>` markers in the fixture text.

- [ ] **Step 1: Write failing test**

`PortLineMarkerProviderTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PortLineMarkerProviderTest : BasePlatformTestCase() {

    fun testGutterAppearsOnPortWithMatchingAdapter() {
        myFixture.addFileToProject(
            "adapter.ts",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.configureByText(
            "port.ts",
            "export type Po<caret>rt = (input: Foo) => Promise<Bar>;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }

    fun testNoGutterWhenNoMatchingAdapter() {
        myFixture.configureByText(
            "port.ts",
            "export type Po<caret>rt = (input: Foo) => Promise<Bar>;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(0, gutters.size)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.PortLineMarkerProviderTest" -i
```

Expected: 0 gutters found in the first test.

- [ ] **Step 3: Implement the provider**

`PortLineMarkerProvider.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.PsiElement

class PortLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Trigger only on the identifier so the gutter lands on a leaf, not a composite.
        val parent = element.parent as? TypeScriptTypeAlias ?: return
        if (parent.nameIdentifier !== element) return

        val key = SignatureKey.of(parent) ?: return
        val adapters = AdapterStubIndex.findFunctions(parent.project, key)
        if (adapters.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(adapters)
            .setTooltipText("Adapter implementations of this port")
            .setPopupTitle("Adapters of ${parent.name ?: "port"}")

        result.add(builder.createLineMarkerInfo(element))
    }
}
```

- [ ] **Step 4: Register in `plugin.xml`**

Add to `<extensions>`:

```xml
<codeInsight.lineMarkerProvider
    language="TypeScript"
    implementationClass="io.meiro.tstypedfunctions.PortLineMarkerProvider"/>
```

- [ ] **Step 5: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.PortLineMarkerProviderTest"
```

If it fails because `language="TypeScript"` isn't recognized: try `language="ECMAScript 6"` or rely on the file type filter inside `collectNavigationMarkers` (early-return if the file is not `.ts`/`.tsx`).

- [ ] **Step 6: Add failing test — multiple adapters appear in the popup**

```kotlin
fun testGutterListsMultipleAdapters() {
    myFixture.addFileToProject(
        "a.ts",
        "export const a = async (input: Foo): Promise<Bar> => null as any;",
    )
    myFixture.addFileToProject(
        "b.ts",
        "export function b(input: Foo): Promise<Bar> { return null as any; }",
    )
    myFixture.configureByText(
        "port.ts",
        "export type Po<caret>rt = (input: Foo) => Promise<Bar>;",
    )

    val gutters = myFixture.findGuttersAtCaret()
    assertEquals(1, gutters.size)
    // The targets aren't directly exposed, but the tooltip contains the count when configured.
    // Smoke-check: marker exists and SignatureKey lookup returns 2 functions.
    val key = "(:Foo)=>Promise<Bar>"
    val fns = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
        AdapterStubIndex.findFunctions(project, key)
    }
    assertEquals(2, fns.size)
}
```

- [ ] **Step 7: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.PortLineMarkerProviderTest.testGutterListsMultipleAdapters"
```

- [ ] **Step 8: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Add gutter icon on port type aliases linking to adapters

RelatedItemLineMarkerProvider triggers on the alias name identifier,
queries AdapterStubIndex by SignatureKey, and renders a navigation
gutter icon when at least one adapter matches."
```

---

## Task 7: `AdapterLineMarkerProvider` (adapter → ports gutter)

Goal: reverse of Task 6 — gutter on adapter function name → popup of matching ports.

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProvider.kt`
- Modify: `ts-typed-functions/src/main/resources/META-INF/plugin.xml`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProviderTest.kt`

- [ ] **Step 1: Write failing test**

`AdapterLineMarkerProviderTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AdapterLineMarkerProviderTest : BasePlatformTestCase() {

    fun testGutterOnAdapterPointsToPort() {
        myFixture.addFileToProject(
            "ports.ts",
            "export type Port = (input: Foo) => Promise<Bar>;",
        )
        myFixture.configureByText(
            "adapter.ts",
            "export const im<caret>pl = async (input: Foo): Promise<Bar> => null as any;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }

    fun testGutterOnFunctionDeclaration() {
        myFixture.addFileToProject(
            "ports.ts",
            "export type Port = (input: Foo) => Promise<Bar>;",
        )
        myFixture.configureByText(
            "adapter.ts",
            "export function im<caret>pl(input: Foo): Promise<Bar> { return null as any; }",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(1, gutters.size)
    }

    fun testNoGutterWhenNoPort() {
        myFixture.configureByText(
            "adapter.ts",
            "export const im<caret>pl = async (input: Foo): Promise<Bar> => null as any;",
        )

        val gutters = myFixture.findGuttersAtCaret()
        assertEquals(0, gutters.size)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.AdapterLineMarkerProviderTest" -i
```

- [ ] **Step 3: Implement the provider**

`AdapterLineMarkerProvider.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.psi.PsiElement

class AdapterLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        val (fn, anchor) = resolveAdapter(element) ?: return
        val key = SignatureKey.of(fn) ?: return
        val ports = PortStubIndex.findAliases(fn.project, key)
        if (ports.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
            .setTargets(ports)
            .setTooltipText("Port type aliases matching this signature")
            .setPopupTitle("Ports for this adapter")

        result.add(builder.createLineMarkerInfo(anchor))
    }

    /**
     * Returns (function, identifier-element) when [element] is the *name identifier* of either:
     *   - a `function name(...)` declaration, or
     *   - a `const name = (arrow|fn-expr)` initializer.
     * Otherwise null. We anchor the gutter to the identifier so it lands on a leaf.
     */
    private fun resolveAdapter(element: PsiElement): Pair<JSFunction, PsiElement>? {
        val parent = element.parent ?: return null

        if (parent is JSFunction && parent.nameIdentifier === element) {
            return parent to element
        }
        if (parent is JSVariable && parent.nameIdentifier === element) {
            val init = parent.initializer as? JSFunction ?: return null
            return init to element
        }
        return null
    }
}
```

- [ ] **Step 4: Register in `plugin.xml`**

```xml
<codeInsight.lineMarkerProvider
    language="TypeScript"
    implementationClass="io.meiro.tstypedfunctions.AdapterLineMarkerProvider"/>
```

- [ ] **Step 5: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.AdapterLineMarkerProviderTest"
```

- [ ] **Step 6: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Add reverse gutter on adapter functions linking to ports

Mirrors PortLineMarkerProvider in the opposite direction. Triggers
on the function or const-variable name identifier, queries
PortStubIndex by SignatureKey, renders an implementing-method
gutter icon when at least one matching port exists."
```

---

## Task 8: `FindImplementationsAction`

Goal: keyboard-driven equivalent of the port gutter. With the caret on a port type alias name, hitting `Ctrl+Alt+Shift+P` shows a popup of matching adapters.

**Files:**
- Create: `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/FindImplementationsAction.kt`
- Modify: `ts-typed-functions/src/main/resources/META-INF/plugin.xml`
- Create: `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/FindImplementationsActionTest.kt`

- [ ] **Step 1: Write failing test**

`FindImplementationsActionTest.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FindImplementationsActionTest : BasePlatformTestCase() {

    fun testActionEnabledOnPortAlias() {
        myFixture.addFileToProject(
            "adapter.ts",
            "export const impl = async (input: Foo): Promise<Bar> => null as any;",
        )
        myFixture.configureByText(
            "port.ts",
            "export type Po<caret>rt = (input: Foo) => Promise<Bar>;",
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
            "port.ts",
            "export type Fo<caret>o = { id: string };",
        )
        val action = ActionManager.getInstance()
            .getAction("io.meiro.tstypedfunctions.FindImplementations")
        val event = TestActionEvent.createTestEvent(action, dataContextFromEditor())
        action.update(event)
        assertFalse(event.presentation.isEnabled)
    }

    private fun dataContextFromEditor() = MapDataContext().apply {
        put(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
        put(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR, myFixture.editor)
        put(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE, myFixture.file)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.FindImplementationsActionTest" -i
```

Expected: action not registered (null) → `assertNotNull` fails.

- [ ] **Step 3: Implement the action**

`FindImplementationsAction.kt`:

```kotlin
package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.javascript.psi.JSFunction

class FindImplementationsAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = aliasUnderCaret(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val alias = aliasUnderCaret(e) ?: return
        val key = SignatureKey.of(alias) ?: return
        val project = e.project ?: return
        val adapters = AdapterStubIndex.findFunctions(project, key)

        if (adapters.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No matching adapters found")
                .showInBestPositionFor(e.dataContext)
            return
        }

        val step = object : BaseListPopupStep<JSFunction>(
            "Adapters of ${alias.name}",
            adapters,
        ) {
            override fun getTextFor(value: JSFunction): String {
                val name = (value.name ?: "<anonymous>")
                val file = value.containingFile?.name ?: "?"
                return "$name  ($file)"
            }

            override fun onChosen(selected: JSFunction, finalChoice: Boolean): PopupStep<*>? {
                ApplicationManager.getApplication().invokeLater {
                    selected.navigate(true)
                }
                return FINAL_CHOICE
            }
        }

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

- [ ] **Step 4: Register the action in `plugin.xml`**

In the `<actions>` block of `plugin.xml`, add:

```xml
<action id="io.meiro.tstypedfunctions.FindImplementations"
        class="io.meiro.tstypedfunctions.FindImplementationsAction"
        text="Find Implementations of Type Signature"
        description="Find all functions whose signature matches this type alias">
    <add-to-group group-id="EditorPopupMenu" anchor="last"/>
    <keyboard-shortcut keymap="$default" first-keystroke="control alt shift P"/>
</action>
```

- [ ] **Step 5: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.FindImplementationsActionTest"
```

If `TestActionEvent.createTestEvent` has a different signature in your platform version, use the older form `TestActionEvent(dataContext)` or `AnActionEvent.createFromDataContext`. Adjust until both tests are green.

- [ ] **Step 6: Add failing test — popup behavior end-to-end is hard to assert; assert lookup correctness instead**

```kotlin
fun testActionResolvesAdaptersByKey() {
    myFixture.addFileToProject(
        "adapter.ts",
        "export const impl = async (input: Foo): Promise<Bar> => null as any;",
    )
    myFixture.configureByText(
        "port.ts",
        "export type Po<caret>rt = (input: Foo) => Promise<Bar>;",
    )

    val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
    val alias = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
        element,
        com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias::class.java,
    )!!
    val key = SignatureKey.of(alias)!!
    val adapters = com.intellij.openapi.application.ReadAction.compute<List<*>, RuntimeException> {
        AdapterStubIndex.findFunctions(project, key)
    }
    assertEquals(1, adapters.size)
}
```

This duplicates the integration check — useful as a regression net for the action's resolve path even if popup display itself isn't directly assertable.

- [ ] **Step 7: Run, expect PASS**

```bash
./gradlew test --tests "io.meiro.tstypedfunctions.FindImplementationsActionTest"
```

- [ ] **Step 8: Run the full test suite to confirm no regressions**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Add Find Implementations of Type Signature action

AnAction registered under EditorPopupMenu with default shortcut
Ctrl+Alt+Shift+P. Resolves the type alias under caret, queries
AdapterStubIndex by SignatureKey, displays results in a JBPopup
chooser. Disabled when caret is not on a function-typed alias."
```

---

## Task 9: CI workflow + final manual check

Goal: GitHub Actions runs `./gradlew check verifyPlugin` on push and PR. Then a one-time manual smoke check inside a real IDE.

**Files:**
- Create: `.github/workflows/build.yml` (at the *repo* root, not the inner project)

- [ ] **Step 1: Create the CI workflow**

`.github/workflows/build.yml`:

```yaml
name: build

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: ts-typed-functions
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - name: Run tests
        run: ./gradlew check --no-daemon
      - name: Verify plugin
        run: ./gradlew verifyPlugin --no-daemon
      - name: Build plugin distribution
        run: ./gradlew buildPlugin --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: plugin-zip
          path: ts-typed-functions/build/distributions/*.zip
```

- [ ] **Step 2: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add .github
git commit -m "Add CI workflow for build, test, and plugin verification"
```

- [ ] **Step 3: Manual smoke check in a sandbox IDE**

```bash
cd ts-typed-functions
./gradlew runIde
```

Expected: a sandbox IDE opens. Open the user's TypeScript hexagonal project in it. Navigate to a port type alias and an adapter function and verify:

- Gutter icon appears on the port name; clicking it lists matching adapters.
- Gutter icon appears on the adapter name; clicking it lists matching ports.
- `Ctrl+Alt+Shift+P` on a port name opens the adapter chooser popup.
- An adapter without an explicit return type does NOT get a reverse gutter (correct: `SignatureKey` returns `null`).

If any of these fail, write a regression test reproducing the failure and fix it before claiming done.

- [ ] **Step 4: Final verification commands**

```bash
cd ts-typed-functions
./gradlew clean test verifyPlugin buildPlugin
```

Expected: all green; `build/distributions/ts-typed-functions-0.1.0.zip` produced.

- [ ] **Step 5: Final commit (only if any fixes were made during step 3)**

```bash
git add -A
git commit -m "Address smoke-check findings"
```

---

## Done

When all task checkboxes are checked, the plugin:

- Compiles, passes all unit tests, and passes `verifyPlugin` against IntelliJ Platform 2024.3.
- Renders bidirectional gutter icons between port type aliases and matching adapter functions.
- Exposes a keyboard-driven action with `Ctrl+Alt+Shift+P`.
- Honors all v1 limitations documented in the spec (generic ports skipped, untyped adapters not indexed, `node_modules` excluded, import-aliased types not cross-matched).
- Produces a distributable `.zip` ready for `Install Plugin from Disk` or JetBrains Marketplace upload.
