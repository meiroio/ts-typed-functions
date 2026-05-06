# Rename port/adapter → signature/implementation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hexagonal-architecture vocabulary ("port", "adapter") across the codebase with neutral terms ("signature", "implementation"). Pure rename — no behavior change.

**Architecture:** Five tasks. Tasks 1-2 rename the four main classes and their on-disk index IDs, plugin.xml registrations, and cross-file call sites. Task 3 sweeps all remaining identifiers (local variables, KDocs/comments, user-facing strings, test method names). Tasks 4-5 update the README and the original design spec. Every task ends with all 30 tests passing.

**Tech Stack:** Kotlin 2.x, IntelliJ Platform Gradle plugin 2.x. Tests: `BasePlatformTestCase` via `./gradlew test`.

**Reference spec:** `docs/superpowers/specs/2026-05-05-rename-port-adapter-design.md`

**Working directory for plugin code:** `/home/odis/meiro/ts-typed-functions/`. Plugin sources in inner `ts-typed-functions/`. Branch `implement-plugin`. `JAVA_HOME=$HOME/.jdks/jdk-21.0.11` is required for gradle commands.

**Already done (skip in plan):** `docs/architecture.md` was renamed inline during cleanup.

---

## File structure

After all tasks complete, the source-file layout becomes:

```
ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/
├── SignatureKey.kt                          (unchanged file name)
├── IndexVersion.kt                          (KDoc updated)
├── SignatureStubIndex.kt                    (was PortStubIndex.kt)
├── ImplementationStubIndex.kt               (was AdapterStubIndex.kt)
├── SignatureLineMarkerProvider.kt           (was PortLineMarkerProvider.kt)
├── ImplementationLineMarkerProvider.kt      (was AdapterLineMarkerProvider.kt)
└── FindImplementationsAction.kt             (unchanged file name; vars/strings updated)

ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/
├── SmokeTest.kt
├── SignatureKeyAliasTest.kt                 (was SignatureKeyPortTest.kt)
├── SignatureKeyFunctionTest.kt              (was SignatureKeyAdapterTest.kt)
├── SignatureStubIndexTest.kt                (was PortStubIndexTest.kt)
├── ImplementationStubIndexTest.kt           (was AdapterStubIndexTest.kt)
├── SignatureLineMarkerProviderTest.kt       (was PortLineMarkerProviderTest.kt)
├── ImplementationLineMarkerProviderTest.kt  (was AdapterLineMarkerProviderTest.kt)
└── FindImplementationsActionTest.kt
```

---

## Task 1: Port → Signature (class & file rename)

Rename the two `Port*` main-source classes (and their three test files), update the on-disk index ID, the plugin.xml registration, the IndexVersion KDoc, and the one cross-file call site. Identifier-level updates inside the renamed files (KDocs, local variables, user-facing strings, test method names) are deferred to Task 3 — Task 1 keeps the patch surgical.

**Files renamed:**
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/PortStubIndex.kt` → `SignatureStubIndex.kt`
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/PortLineMarkerProvider.kt` → `SignatureLineMarkerProvider.kt`
- `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/PortStubIndexTest.kt` → `SignatureStubIndexTest.kt`
- `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/PortLineMarkerProviderTest.kt` → `SignatureLineMarkerProviderTest.kt`
- `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyPortTest.kt` → `SignatureKeyAliasTest.kt`

**Files modified (no rename):**
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/IndexVersion.kt` — KDoc reference to `PortStubIndex`
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProvider.kt` — call to `PortStubIndex.findAliases`
- `ts-typed-functions/src/main/resources/META-INF/plugin.xml` — `<fileBasedIndex>` and 2× `<codeInsight.lineMarkerProvider>` `implementationClass` attributes

- [ ] **Step 1: Move the five files via `git mv`**

```bash
cd /home/odis/meiro/ts-typed-functions
git mv ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/PortStubIndex.kt \
       ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureStubIndex.kt
git mv ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/PortLineMarkerProvider.kt \
       ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureLineMarkerProvider.kt
git mv ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/PortStubIndexTest.kt \
       ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureStubIndexTest.kt
git mv ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/PortLineMarkerProviderTest.kt \
       ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureLineMarkerProviderTest.kt
git mv ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyPortTest.kt \
       ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyAliasTest.kt
```

- [ ] **Step 2: Rename the class declarations inside each renamed file**

In `SignatureStubIndex.kt`: replace `class PortStubIndex` with `class SignatureStubIndex` and `companion object` references that say `NAME` stay as-is. Also replace the on-disk ID string:

```kotlin
val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.PortStubIndex")
```

becomes:

```kotlin
val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.SignatureStubIndex")
```

In `SignatureLineMarkerProvider.kt`: replace `class PortLineMarkerProvider` with `class SignatureLineMarkerProvider`.

In each test file (3 files), update both the `class` declaration and any explicit class-name references in the test code:

| File | Old class name | New class name |
|---|---|---|
| `SignatureStubIndexTest.kt` | `class PortStubIndexTest` | `class SignatureStubIndexTest` |
| `SignatureLineMarkerProviderTest.kt` | `class PortLineMarkerProviderTest` | `class SignatureLineMarkerProviderTest` |
| `SignatureKeyAliasTest.kt` | `class SignatureKeyPortTest` | `class SignatureKeyAliasTest` |

In each test file, also replace any in-body references to `PortStubIndex.findAliases(...)` / `PortStubIndex.NAME` with `SignatureStubIndex.findAliases(...)` / `SignatureStubIndex.NAME`.

- [ ] **Step 3: Update `IndexVersion.kt` KDoc**

Open `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/IndexVersion.kt`. The KDoc references both index classes. Replace `PortStubIndex` with `SignatureStubIndex` (leave `AdapterStubIndex` for Task 2).

- [ ] **Step 4: Update the cross-file call site in `AdapterLineMarkerProvider.kt`**

In `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProvider.kt`, replace:

```kotlin
val ports = PortStubIndex.findAliases(fn.project, key)
```

with:

```kotlin
val ports = SignatureStubIndex.findAliases(fn.project, key)
```

(The local variable `ports` keeps its name in this task — it gets renamed to `signatures` in Task 3.)

- [ ] **Step 5: Update `plugin.xml`**

In `ts-typed-functions/src/main/resources/META-INF/plugin.xml`, replace three string occurrences:

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.PortStubIndex"/>
```

→

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.SignatureStubIndex"/>
```

And both:

```xml
<codeInsight.lineMarkerProvider language="TypeScript" implementationClass="io.meiro.tstypedfunctions.PortLineMarkerProvider"/>
<codeInsight.lineMarkerProvider language="TypeScript JSX" implementationClass="io.meiro.tstypedfunctions.PortLineMarkerProvider"/>
```

→

```xml
<codeInsight.lineMarkerProvider language="TypeScript" implementationClass="io.meiro.tstypedfunctions.SignatureLineMarkerProvider"/>
<codeInsight.lineMarkerProvider language="TypeScript JSX" implementationClass="io.meiro.tstypedfunctions.SignatureLineMarkerProvider"/>
```

- [ ] **Step 6: Run the full test suite**

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test
```

Expected: BUILD SUCCESSFUL, 30 tests pass.

If a test fails because of a stale reference like `PortStubIndex.findAliases` — search the codebase for any remaining `PortStubIndex` / `PortLineMarkerProvider` symbols and update them. The Adapter side intentionally still uses the old names; only Port-side references should be updated in this task.

```bash
grep -rn "PortStubIndex\|PortLineMarkerProvider" ts-typed-functions/src ts-typed-functions/src/main/resources
```

Should return zero matches after this step.

- [ ] **Step 7: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Rename PortStubIndex/PortLineMarkerProvider classes to Signature*

File and class rename only — IDs, plugin.xml registrations, the
IndexVersion KDoc, and the one cross-file call site update. Local
variables, KDocs, user-facing strings, and test method names are
deferred to a follow-up sweep so this commit stays surgical."
```

---

## Task 2: Adapter → Implementation (class & file rename)

Mirror of Task 1 on the Adapter side. Renames the two `Adapter*` main-source classes and their three test files, updates the second on-disk index ID, the plugin.xml registrations, the IndexVersion KDoc, two cross-file call sites, and the `resolveAdapter` method name.

**Files renamed:**
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterStubIndex.kt` → `ImplementationStubIndex.kt`
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProvider.kt` → `ImplementationLineMarkerProvider.kt`
- `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/AdapterStubIndexTest.kt` → `ImplementationStubIndexTest.kt`
- `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProviderTest.kt` → `ImplementationLineMarkerProviderTest.kt`
- `ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyAdapterTest.kt` → `SignatureKeyFunctionTest.kt`

**Files modified (no rename):**
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/IndexVersion.kt` — KDoc reference to `AdapterStubIndex`
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureLineMarkerProvider.kt` — call to `AdapterStubIndex.findFunctions` *(this file was renamed in Task 1)*
- `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/FindImplementationsAction.kt` — call to `AdapterStubIndex.findFunctions`
- `ts-typed-functions/src/main/resources/META-INF/plugin.xml` — three `implementationClass` attributes

- [ ] **Step 1: Move the five files via `git mv`**

```bash
cd /home/odis/meiro/ts-typed-functions
git mv ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterStubIndex.kt \
       ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/ImplementationStubIndex.kt
git mv ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProvider.kt \
       ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/ImplementationLineMarkerProvider.kt
git mv ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/AdapterStubIndexTest.kt \
       ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/ImplementationStubIndexTest.kt
git mv ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/AdapterLineMarkerProviderTest.kt \
       ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/ImplementationLineMarkerProviderTest.kt
git mv ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyAdapterTest.kt \
       ts-typed-functions/src/test/kotlin/io/meiro/tstypedfunctions/SignatureKeyFunctionTest.kt
```

- [ ] **Step 2: Rename class declarations inside the renamed files**

In `ImplementationStubIndex.kt`: replace `class AdapterStubIndex` with `class ImplementationStubIndex`. Replace the on-disk ID string:

```kotlin
val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.AdapterStubIndex")
```

→

```kotlin
val NAME: ID<String, Void> = ID.create("io.meiro.tstypedfunctions.ImplementationStubIndex")
```

In `ImplementationLineMarkerProvider.kt`: replace `class AdapterLineMarkerProvider` with `class ImplementationLineMarkerProvider`. Also rename the private helper method `resolveAdapter` to `resolveImplementation` (declaration site at the bottom of the class plus the one call site near the top of `collectNavigationMarkers`):

```kotlin
private fun resolveAdapter(element: PsiElement): Pair<JSFunction, PsiElement>? { ... }
```

→

```kotlin
private fun resolveImplementation(element: PsiElement): Pair<JSFunction, PsiElement>? { ... }
```

And the call site:

```kotlin
val (fn, anchor) = resolveAdapter(element) ?: return
```

→

```kotlin
val (fn, anchor) = resolveImplementation(element) ?: return
```

In each test file (3 files):

| File | Old class name | New class name |
|---|---|---|
| `ImplementationStubIndexTest.kt` | `class AdapterStubIndexTest` | `class ImplementationStubIndexTest` |
| `ImplementationLineMarkerProviderTest.kt` | `class AdapterLineMarkerProviderTest` | `class ImplementationLineMarkerProviderTest` |
| `SignatureKeyFunctionTest.kt` | `class SignatureKeyAdapterTest` | `class SignatureKeyFunctionTest` |

In each test file, also replace `AdapterStubIndex.findFunctions(...)` / `AdapterStubIndex.NAME` with `ImplementationStubIndex.findFunctions(...)` / `ImplementationStubIndex.NAME`.

- [ ] **Step 3: Update `IndexVersion.kt` KDoc**

Replace `AdapterStubIndex` with `ImplementationStubIndex` in the KDoc.

- [ ] **Step 4: Update cross-file call sites**

In `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/SignatureLineMarkerProvider.kt`, replace:

```kotlin
val adapters = AdapterStubIndex.findFunctions(parent.project, key)
```

with:

```kotlin
val adapters = ImplementationStubIndex.findFunctions(parent.project, key)
```

(Local variable `adapters` keeps its name in this task — Task 3 renames it to `implementations`.)

In `ts-typed-functions/src/main/kotlin/io/meiro/tstypedfunctions/FindImplementationsAction.kt`, replace:

```kotlin
val adapters = AdapterStubIndex.findFunctions(project, key)
```

with:

```kotlin
val adapters = ImplementationStubIndex.findFunctions(project, key)
```

- [ ] **Step 5: Update `plugin.xml`**

Replace three more occurrences (mirror of Task 1 Step 5):

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.AdapterStubIndex"/>
<codeInsight.lineMarkerProvider language="TypeScript" implementationClass="io.meiro.tstypedfunctions.AdapterLineMarkerProvider"/>
<codeInsight.lineMarkerProvider language="TypeScript JSX" implementationClass="io.meiro.tstypedfunctions.AdapterLineMarkerProvider"/>
```

→

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.ImplementationStubIndex"/>
<codeInsight.lineMarkerProvider language="TypeScript" implementationClass="io.meiro.tstypedfunctions.ImplementationLineMarkerProvider"/>
<codeInsight.lineMarkerProvider language="TypeScript JSX" implementationClass="io.meiro.tstypedfunctions.ImplementationLineMarkerProvider"/>
```

- [ ] **Step 6: Run the full test suite**

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test
```

Expected: 30 tests pass.

Verify no `Adapter*` symbols remain:

```bash
grep -rn "AdapterStubIndex\|AdapterLineMarkerProvider\|resolveAdapter" \
  ts-typed-functions/src ts-typed-functions/src/main/resources
```

Should return zero matches.

- [ ] **Step 7: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src ts-typed-functions/src/main/resources
git commit -m "Rename AdapterStubIndex/AdapterLineMarkerProvider classes to Implementation*

File and class rename mirroring the Port -> Signature commit.
Updates the second on-disk index ID, plugin.xml registrations,
IndexVersion KDoc, two cross-file call sites, and renames the
private resolveAdapter helper to resolveImplementation. Local
variables, KDocs, user-facing strings, and test method names
remain as a follow-up sweep."
```

---

## Task 3: Identifier sweep (variables, KDocs, comments, user-facing strings, test methods)

After Tasks 1-2, the codebase compiles and tests pass, but it still contains "port" and "adapter" in: local variable names, KDoc/comment prose, user-facing tooltip and popup-title strings, and test method names. This task sweeps them all in one pass and ends with zero `port` / `adapter` matches across `src/`.

**Files modified:** every `.kt` file in `ts-typed-functions/src/`. No file renames.

**Concrete substitution rules.** Apply per-file using the exact replacements below. Where a string contained both terms (e.g., `"Adapter implementations of this port"`), the FINAL form from the spec is given — apply it as a single edit, not as two sequential substitutions.

### Step 1: Update `SignatureLineMarkerProvider.kt` (was `PortLineMarkerProvider.kt`)

Two user-facing strings and one local variable:

```kotlin
val adapters = ImplementationStubIndex.findFunctions(parent.project, key)
if (adapters.isEmpty()) return

val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
    .setTargets(adapters)
    .setTooltipText("Adapter implementations of this port")
    .setPopupTitle("Adapters of ${parent.name ?: "port"}")
```

→

```kotlin
val implementations = ImplementationStubIndex.findFunctions(parent.project, key)
if (implementations.isEmpty()) return

val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
    .setTargets(implementations)
    .setTooltipText("Implementations of this signature")
    .setPopupTitle("Implementations of ${parent.name ?: "signature"}")
```

Also: any comment in the file mentioning "port" should be rephrased to "signature."

### Step 2: Update `ImplementationLineMarkerProvider.kt` (was `AdapterLineMarkerProvider.kt`)

Local variable `ports`, two user-facing strings, KDoc on `resolveImplementation`, and the `isModuleScope` doc comment if it mentions "adapter":

```kotlin
val ports = SignatureStubIndex.findAliases(fn.project, key)
if (ports.isEmpty()) return

val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
    .setTargets(ports)
    .setTooltipText("Port type aliases matching this signature")
    .setPopupTitle("Ports for ${fn.name ?: "adapter"}")
```

→

```kotlin
val signatures = SignatureStubIndex.findAliases(fn.project, key)
if (signatures.isEmpty()) return

val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
    .setTargets(signatures)
    .setTooltipText("Signatures implemented by this function")
    .setPopupTitle("Signatures implemented by ${fn.name ?: "function"}")
```

The KDoc on `resolveImplementation` currently reads:

```kotlin
/**
 * Returns (function, identifier-element) when [element] is the *name identifier* of either:
 *   - a `function name(...)` declaration, or
 *   - a `const name = (arrow|fn-expr)` initializer.
 * Otherwise null. We anchor the gutter to the identifier so it lands on a leaf.
 */
```

This is already neutral. Leave it.

The `isModuleScope` doc comment currently reads:

```kotlin
/**
 * Mirrors AdapterStubIndex.candidateFunctions: only module-scope functions count
 * as adapters. Excludes nested functions, class methods, and object-literal methods.
 */
```

Update to:

```kotlin
/**
 * Mirrors ImplementationStubIndex.candidateFunctions: only module-scope functions
 * count as implementations. Excludes nested functions, class methods, and
 * object-literal methods.
 */
```

### Step 3: Update `FindImplementationsAction.kt`

Local variable `adapters`, popup title, empty message:

```kotlin
val adapters = ImplementationStubIndex.findFunctions(project, key)

if (adapters.isEmpty()) {
    JBPopupFactory.getInstance()
        .createMessage("No matching adapters found")
        .showInBestPositionFor(e.dataContext)
    return
}

val step = object : BaseListPopupStep<JSFunction>(
    "Adapters of ${alias.name}",
    adapters,
) { ... }
```

→

```kotlin
val implementations = ImplementationStubIndex.findFunctions(project, key)

if (implementations.isEmpty()) {
    JBPopupFactory.getInstance()
        .createMessage("No implementations found")
        .showInBestPositionFor(e.dataContext)
    return
}

val step = object : BaseListPopupStep<JSFunction>(
    "Implementations of ${alias.name}",
    implementations,
) { ... }
```

### Step 4: Update `SignatureKey.kt`

Search for any "port" or "adapter" in comments; rephrase to "signature" or "implementation" as appropriate. The publicly-visible KDoc on `SignatureKey` itself (if any) should describe what the key represents in neutral terms — "the canonical lookup string for a function signature" — without mentioning ports or adapters.

### Step 5: Update `SignatureStubIndex.kt` (was `PortStubIndex.kt`)

Search for "port" in KDoc/comments. Replace with "signature" where it refers to the indexed entity. Specifically the KDoc on `findAliases` and the comment on the `.filter { SignatureKey.of(it) == key }` line:

```kotlin
PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptTypeAlias::class.java)
    // Re-validate the key against current PSI. Defends against stale
    // index entries (file edited after indexing) and against files
    // containing other aliases under different keys.
    .filter { SignatureKey.of(it) == key }
```

This is already neutral. Leave it.

The `getVersion` KDoc was updated in Task 1. Verify no remaining `port` references.

### Step 6: Update `ImplementationStubIndex.kt` (was `AdapterStubIndex.kt`)

Search for "adapter" in KDoc/comments. Notably:

```kotlin
/**
 * Module-scope functions: top-level `function` declarations and arrow/function
 * expressions assigned to `const`/`let`. Excludes nested functions and class
 * methods by rejecting any function whose ancestor chain contains another
 * `JSFunction` or a `JSClass`.
 */
```

This is already neutral. Leave it.

The `getVersion` KDoc — verify it's neutral after Task 2.

### Step 7: Update `IndexVersion.kt`

The KDoc currently reads (after Tasks 1-2):

```kotlin
/**
 * Shared version stamp for FileBasedIndex extensions in this plugin.
 *
 * Bump when [SignatureKey] canonicalization changes, when the set of
 * PSI nodes the indexers visit changes, or when accept/reject rules
 * change. Both SignatureStubIndex and ImplementationStubIndex use this
 * constant because they both depend on SignatureKey.
 */
internal const val INDEX_VERSION: Int = 2
```

This is already neutral after the class renames. No edits needed.

### Step 8: Update test method names

Apply these renames mechanically across the relevant test files:

| File (post-rename) | Old method | New method |
|---|---|---|
| `SignatureLineMarkerProviderTest.kt` | `testGutterAppearsOnPortWithMatchingAdapter` | `testGutterAppearsOnSignatureWithMatchingImplementation` |
| `SignatureLineMarkerProviderTest.kt` | `testNoGutterWhenNoMatchingAdapter` | `testNoGutterWhenNoMatchingImplementation` |
| `SignatureLineMarkerProviderTest.kt` | `testGutterListsMultipleAdapters` | `testGutterListsMultipleImplementations` |
| `SignatureLineMarkerProviderTest.kt` | `testGutterAppearsOnPortInTsxFileWithTsxAdapter` | `testGutterAppearsOnSignatureInTsxFileWithTsxImplementation` |
| `ImplementationLineMarkerProviderTest.kt` | `testGutterOnAdapterPointsToPort` | `testGutterOnImplementationPointsToSignature` |
| `ImplementationLineMarkerProviderTest.kt` | `testNoGutterWhenNoPort` | `testNoGutterWhenNoSignature` |
| `ImplementationLineMarkerProviderTest.kt` | `testNoGutterOnNestedAdapter` | `testNoGutterOnNestedImplementation` |
| `ImplementationStubIndexTest.kt` | `testFindsAdapterByKey` | `testFindsImplementationByKey` |
| `ImplementationStubIndexTest.kt` | `testAdaptersMissingTypesAreNotIndexed` | `testImplementationsMissingTypesAreNotIndexed` |
| `ImplementationStubIndexTest.kt` | `testIndexesAdapterInTsxFile` | `testIndexesImplementationInTsxFile` |
| `SignatureStubIndexTest.kt` | `testFindsPortByKey` | `testFindsSignatureByKey` |
| `SignatureStubIndexTest.kt` | `testFindsMultiplePortsWithSameKey` | `testFindsMultipleSignaturesWithSameKey` |
| `SignatureKeyFunctionTest.kt` | `testPortAndAdapterProduceEqualKeys` | `testSignatureAndImplementationProduceEqualKeys` |

Method names already neutral and unchanged: `testGutterOnFunctionDeclaration`, `testNestedFunctionMethodAndObjectLiteralExcluded`, `testFindAliasesHelperReturnsMatchingAliases`, all `SignatureKeyAliasTest` methods (`testBasicSingleParamPort` becomes `testBasicSingleParamSignature` — see below), and `SmokeTest`'s single method.

Additional methods in `SignatureKeyAliasTest.kt` (was `SignatureKeyPortTest.kt`) that contain "Port" in their name:

| Old | New |
|---|---|
| `testBasicSingleParamPort` | `testBasicSingleParamSignature` |

Other methods in that file (`testReturnTypeIsPromise`, `testOptionalAndRestParams`, `testGenericAliasReturnsNull`, `testNonFunctionAliasReturnsNull`, `testWhitespaceAndCommentsAreStripped`) are already neutral.

In `SignatureKeyFunctionTest.kt` (was `SignatureKeyAdapterTest.kt`), method names other than `testPortAndAdapterProduceEqualKeys` referencing "Adapter":

| Old | New |
|---|---|
| `testFunctionDeclaration` | unchanged |
| `testAsyncArrowAdapter` | `testAsyncArrowImplementation` |
| `testAdapterWithoutReturnTypeReturnsNull` | `testImplementationWithoutReturnTypeReturnsNull` |
| `testAdapterWithUntypedParamReturnsNull` | `testImplementationWithUntypedParamReturnsNull` |

### Step 9: Sweep test-internal variable names and fixture comments

Inside the test bodies, search for `port`, `Port`, `adapter`, `Adapter` and rename per the same convention:

- Variable `val ports = ...` → `val signatures = ...`
- Variable `val adapters = ...` → `val implementations = ...`
- Fixture filenames in `myFixture.configureByText("port.ts", ...)` → `"signature.ts"`, etc.
- Fixture filenames `"adapter.ts"` → `"implementation.ts"` (or simply `"function.ts"` — both work; pick one and stay consistent)
- Inline test prose / comments mentioning "port" / "adapter"

### Step 10: Run the full test suite

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew test
```

Expected: 30 tests pass.

### Step 11: Verify zero `port` / `adapter` matches in source

```bash
cd /home/odis/meiro/ts-typed-functions
grep -rIn -E "\b[Pp]ort\b|\b[Aa]dapter\b" ts-typed-functions/src
```

Expected: zero matches. (`Port` as part of an unrelated word — e.g., `import` — is excluded by the `\b` word boundaries. If you see false positives like the inside of `import`, double-check the regex; the `\b` should prevent that.)

### Step 12: Commit

```bash
cd /home/odis/meiro/ts-typed-functions
git add ts-typed-functions/src
git commit -m "Sweep port/adapter from identifiers, KDocs, strings, test methods

Local variables, user-facing strings (tooltips, popup titles, empty
message), KDoc/comments, and test method names all swept to the new
signature/implementation vocabulary. After this commit no source
file under src/ contains 'port' or 'adapter' as a whole word."
```

---

## Task 4: Update README.md

Rewrite the README so all prose and identifiers use the new vocabulary. The README is short enough to handle as one mechanical sweep.

**Files modified:**
- `README.md` (repo root)

- [ ] **Step 1: Read the current README**

Read `/home/odis/meiro/ts-typed-functions/README.md` end-to-end.

- [ ] **Step 2: Apply prose replacements**

Substitutions:

| Old phrase | New phrase |
|---|---|
| `port type alias` | `function-typed alias` (or just `signature` when the noun fits) |
| `the port` | `the signature` |
| `(the port)` | `(the signature)` |
| `adapter` (noun) | `implementation` |
| `adapters` | `implementations` |
| `Adapter` (start of sentence) | `Implementation` |
| `Adapters` | `Implementations` |
| `port type aliases` | `function-typed aliases` |
| `Generic ports` (in non-goals heading) | `Generic signatures` |
| `is treated as an implementation of a port type alias` | `is treated as an implementation of a function-typed alias` |

The **Edge cases** bullet currently says:

```
- Adapters missing a type on any parameter or return type are skipped (no guessed matches).
```

→

```
- Implementations missing a type on any parameter or return type are skipped (no guessed matches).
```

The **Non-goals** bullet:

```
- **Class methods.** Only top-level functions and `const`/`let` arrow/function-expression initializers are considered adapters.
```

→

```
- **Class methods.** Only top-level functions and `const`/`let` arrow/function-expression initializers are considered implementations.
```

The **Usage** section:

```
A gutter icon appears on the *name* of a function-typed type alias (the port) when at least one adapter in the project matches its signature. Clicking the icon shows a popup of matching adapters with their file and line.

A reverse gutter icon appears on adapter function names — top-level `function` declarations and `const`/`let` initializers that are arrow functions or function expressions — when at least one matching port exists. Clicking it shows a popup of matching ports.
```

→

```
A gutter icon appears on the *name* of a function-typed type alias when at least one implementation in the project matches its signature. Clicking the icon shows a popup of matching implementations with their file and line.

A reverse gutter icon appears on implementation function names — top-level `function` declarations and `const`/`let` initializers that are arrow functions or function expressions — when at least one matching signature exists. Clicking it shows a popup of matching signatures.
```

- [ ] **Step 3: Verify zero `port` / `adapter` matches in the README**

```bash
grep -nE "\b[Pp]ort\b|\b[Aa]dapter\b" /home/odis/meiro/ts-typed-functions/README.md
```

Expected: zero matches.

- [ ] **Step 4: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add README.md
git commit -m "Rewrite README to use signature/implementation vocabulary"
```

---

## Task 5: Update spec doc

Update the original implementation spec (`docs/superpowers/specs/2026-05-04-ts-typed-functions-design.md`) to use the new vocabulary. The plan doc (`docs/superpowers/plans/2026-05-04-ts-typed-functions.md`) is intentionally left as historical record per the rename spec.

**Files modified:**
- `docs/superpowers/specs/2026-05-04-ts-typed-functions-design.md`

- [ ] **Step 1: Apply prose replacements**

Same substitution table as Task 4 Step 2. Plus inside the spec's internal class-name references:

| Old | New |
|---|---|
| `PortStubIndex` | `SignatureStubIndex` |
| `AdapterStubIndex` | `ImplementationStubIndex` |
| `PortLineMarkerProvider` | `SignatureLineMarkerProvider` |
| `AdapterLineMarkerProvider` | `ImplementationLineMarkerProvider` |
| `Port type aliases matching this signature` (tooltip example) | `Signatures implemented by this function` |
| `Adapter implementations of this port` (tooltip example) | `Implementations of this signature` |
| `Adapters of` (popup title example) | `Implementations of` |
| `Ports for` (popup title example) | `Signatures implemented by` |

The "Architecture" section's diagram block:

```
SignatureKey (pure function)
    ↑                   ↑
PortStubIndex     AdapterStubIndex     ← StubIndex<String, PsiElement>
    ↑                   ↑
PortLineMarkerProvider     ← gutter on `type X = (...) => ...`
AdapterLineMarkerProvider  ← gutter on adapter functions
FindImplementationsAction  ← shortcut/menu on a type alias
```

→

```
SignatureKey (pure function)
    ↑                          ↑
SignatureStubIndex     ImplementationStubIndex     ← FileBasedIndex<String, Void>
    ↑                          ↑
SignatureLineMarkerProvider        ← gutter on `type X = (...) => ...`
ImplementationLineMarkerProvider   ← gutter on implementation functions
FindImplementationsAction          ← shortcut/menu on a type alias
```

(Also fix the inaccurate `StubIndex<String, PsiElement>` in the diagram to `FileBasedIndex<String, Void>` — that was a known stale label predating the implementation choice; this is the natural place to correct it.)

The Non-goals section's bullet:

```
- **Class methods.** Only top-level functions and `const`/`let` arrow/function-expression initializers are indexed as adapters.
```

→

```
- **Class methods.** Only top-level functions and `const`/`let` arrow/function-expression initializers are indexed as implementations.
```

- [ ] **Step 2: Verify zero `port` / `adapter` matches in the spec**

```bash
grep -nE "\b[Pp]ort\b|\b[Aa]dapter\b" docs/superpowers/specs/2026-05-04-ts-typed-functions-design.md
```

Expected: zero matches.

- [ ] **Step 3: Final full-test run**

```bash
cd /home/odis/meiro/ts-typed-functions/ts-typed-functions
JAVA_HOME=$HOME/.jdks/jdk-21.0.11 ./gradlew clean test verifyPlugin buildPlugin
```

Expected: BUILD SUCCESSFUL across all four phases. 30 tests pass. `verifyPlugin` reports Compatible. `build/distributions/ts-typed-functions-0.1.0.zip` produced.

- [ ] **Step 4: Commit**

```bash
cd /home/odis/meiro/ts-typed-functions
git add docs/superpowers/specs/2026-05-04-ts-typed-functions-design.md
git commit -m "Rewrite original spec doc to use signature/implementation vocabulary

Sweeps the May 4 design doc for the same rename applied to the
codebase. The May 4 implementation plan is intentionally untouched
as historical record."
```

---

## Done

When all task checkboxes are checked:

- No `.kt`, `.xml`, `README.md`, or `2026-05-04-ts-typed-functions-design.md` file under the repo contains `port` or `adapter` as a whole word.
- `./gradlew clean test verifyPlugin buildPlugin` is green end-to-end.
- The plugin's behavior is unchanged from before the rename — only the names changed.
