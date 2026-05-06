# Rename port/adapter to signature/implementation — design

**Status:** approved (brainstorm), pending implementation plan
**Date:** 2026-05-05

## Goal

Eliminate hexagonal-architecture vocabulary ("port", "adapter") from the `ts-typed-functions` plugin. Replace with neutral terminology — **signature** for the function-typed type alias, **implementation** for the function that matches it.

## Motivation

The plugin matches TypeScript functions whose signature is defined by a function-typed type alias. The original codebase used "port" and "adapter" because the motivating use case was hexagonal architecture, but the matching mechanism is general — the plugin works equally well for any TS codebase that separates type contracts from their implementations. Hexagonal vocabulary in class names, identifiers, and prose makes the plugin appear narrower in scope than it actually is.

## New vocabulary

- **Signature** — a function-typed `TypeScriptTypeAlias` (the entity that declares a function shape).
- **Implementation** — a top-level `function` declaration or `const`/`let` arrow/function-expression initializer whose signature matches a Signature.
- **SignatureKey** — the canonical lookup string. Same shape as a `User` / `UserId` pair: `Signature` is the entity, `SignatureKey` is its identifier in the index.

## Scope

### In scope

1. **Source files (4 renames)**
   - `PortStubIndex.kt` → `SignatureStubIndex.kt`
   - `AdapterStubIndex.kt` → `ImplementationStubIndex.kt`
   - `PortLineMarkerProvider.kt` → `SignatureLineMarkerProvider.kt`
   - `AdapterLineMarkerProvider.kt` → `ImplementationLineMarkerProvider.kt`

2. **Test files (6 renames)**
   - `SignatureKeyPortTest.kt` → `SignatureKeyAliasTest.kt` *(input is `TypeScriptTypeAlias`)*
   - `SignatureKeyAdapterTest.kt` → `SignatureKeyFunctionTest.kt` *(input is `JSFunction`)*
   - `PortStubIndexTest.kt` → `SignatureStubIndexTest.kt`
   - `AdapterStubIndexTest.kt` → `ImplementationStubIndexTest.kt`
   - `PortLineMarkerProviderTest.kt` → `SignatureLineMarkerProviderTest.kt`
   - `AdapterLineMarkerProviderTest.kt` → `ImplementationLineMarkerProviderTest.kt`

3. **Class names** inside the renamed files — match the file names. References to renamed classes from other files updated accordingly.

4. **`plugin.xml`** — `<fileBasedIndex>` and `<codeInsight.lineMarkerProvider>` `implementationClass` attributes updated to new fully-qualified names.

5. **On-disk index `ID.create("...")` strings:**
   - `io.meiro.tstypedfunctions.PortStubIndex` → `io.meiro.tstypedfunctions.SignatureStubIndex`
   - `io.meiro.tstypedfunctions.AdapterStubIndex` → `io.meiro.tstypedfunctions.ImplementationStubIndex`
   Safe because the plugin is unreleased — no users have orphaned indexes. `INDEX_VERSION` does not need a bump (the new ID makes old caches unreachable).

6. **User-facing strings:**

   | Where | Old | New |
   |---|---|---|
   | `SignatureLineMarkerProvider` tooltip | `"Adapter implementations of this port"` | `"Implementations of this signature"` |
   | `SignatureLineMarkerProvider` popup title | `"Adapters of <name>"` | `"Implementations of <name>"` |
   | `ImplementationLineMarkerProvider` tooltip | `"Port type aliases matching this signature"` | `"Signatures implemented by this function"` |
   | `ImplementationLineMarkerProvider` popup title | `"Ports for <name>"` | `"Signatures implemented by <name>"` |
   | `FindImplementationsAction` empty message | `"No matching adapters found"` | `"No implementations found"` |
   | `FindImplementationsAction` popup title | `"Adapters of <name>"` | `"Implementations of <name>"` |

   The action's text in `plugin.xml` (`"Find Implementations of Type Signature"`) is already neutral.

7. **Test method names** — full sweep (port → signature, adapter → implementation):

   | Old | New |
   |---|---|
   | `testGutterAppearsOnPortWithMatchingAdapter` | `testGutterAppearsOnSignatureWithMatchingImplementation` |
   | `testNoGutterWhenNoMatchingAdapter` | `testNoGutterWhenNoMatchingImplementation` |
   | `testGutterListsMultipleAdapters` | `testGutterListsMultipleImplementations` |
   | `testGutterAppearsOnPortInTsxFileWithTsxAdapter` | `testGutterAppearsOnSignatureInTsxFileWithTsxImplementation` |
   | `testGutterOnAdapterPointsToPort` | `testGutterOnImplementationPointsToSignature` |
   | `testNoGutterWhenNoPort` | `testNoGutterWhenNoSignature` |
   | `testNoGutterOnNestedAdapter` | `testNoGutterOnNestedImplementation` |
   | `testFindsAdapterByKey` | `testFindsImplementationByKey` |
   | `testAdaptersMissingTypesAreNotIndexed` | `testImplementationsMissingTypesAreNotIndexed` |
   | `testIndexesAdapterInTsxFile` | `testIndexesImplementationInTsxFile` |
   | `testFindsPortByKey` | `testFindsSignatureByKey` |
   | `testFindsMultiplePortsWithSameKey` | `testFindsMultipleSignaturesWithSameKey` |
   | `testPortAndAdapterProduceEqualKeys` | `testSignatureAndImplementationProduceEqualKeys` |
   | `testGutterOnFunctionDeclaration` | unchanged |
   | `testNestedFunctionMethodAndObjectLiteralExcluded` | unchanged |
   | `testFindAliasesHelperReturnsMatchingAliases` | unchanged |

8. **KDocs, comments, local variable names** — full mechanical pass. Variables like `val adapters = ...` → `val implementations = ...`, `val ports = ...` → `val signatures = ...`. Comments referring to "port" / "adapter" rephrased.

9. **README.md** — full pass with prose rephrased to read naturally under the new vocabulary. The motivating-example narrative becomes "the alias defines a Signature; the function is its Implementation."

10. **`docs/superpowers/specs/2026-05-04-ts-typed-functions-design.md`** — full pass. This is a living spec that must match the code.

### Already done (out of plan scope)

- **`docs/architecture.md`** — renamed inline as part of the cleanup commit that untracked `.idea/`. The implementation plan can skip this file.

### Out of scope

- **`docs/superpowers/plans/2026-05-04-ts-typed-functions.md`** — kept as historical record. The plan captured the design at implementation time, contains many code snippets that match the original code, and has no consumers other than as history. Updating it would be ~60 mechanical edits with no upside.
- **Renaming `*StubIndex` → `*Index`** — known naming inconsistency (the implementation is `FileBasedIndex`, not `StubIndex`), flagged in the previous final review. Out of scope for this rename to keep concerns separated.
- **Renaming `SignatureKey`** — the entity/identifier pair `Signature` / `SignatureKey` parallels `User` / `UserId` and reads naturally; no rename needed.
- **`findAliases` / `findFunctions` companion helpers** — already neutral. They name PSI input shapes, not hexagonal roles.

## Identifier renames inside source files

| Concept | Old identifier | New identifier |
|---|---|---|
| Variable holding result of `ImplementationStubIndex.findFunctions(...)` | `adapters` | `implementations` |
| Variable holding result of `SignatureStubIndex.findAliases(...)` | `ports` | `signatures` |
| Method-local PSI variable in `ImplementationLineMarkerProvider.resolveAdapter` | `resolveAdapter` | `resolveImplementation` |
| Method-local PSI variable in `SignatureLineMarkerProvider` (alias parent) | (already named `parent`) | unchanged |
| `aliasUnderCaret` in `FindImplementationsAction` | `aliasUnderCaret` | unchanged *(neutral)* |

## Compilation & test contract

- After every step in the implementation plan, the project compiles and all 30 tests pass.
- Final state: `./gradlew clean test verifyPlugin buildPlugin` produces a green build identical in behavior to the pre-rename build.

## Risk

Low. This is a pure rename with no behavior change. The only mildly risky element is the `ID.create("...")` string change, which would orphan on-disk indexes — but the plugin is unreleased, so there are none to orphan. Everything else is mechanical and verified by the existing test suite.
