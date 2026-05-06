# Factory detection — design

**Status:** approved (brainstorm), pending implementation plan
**Date:** 2026-05-05

## Goal

Extend the plugin to detect *factories* — functions whose explicit return type names a known signature alias — alongside the direct implementations the plugin already finds. After this work, the signature-side gutter and the `Find Implementations` action surface both kinds of producers in one popup.

## Motivation

The hexagonal codebase the plugin targets uses two ways to build an implementation of a signature:

1. **Direct implementation** (already supported):

   ```ts
   export async function createIdentifierTypeImpl(
     input: CreateIdentifierTypeInput,
   ): Promise<CreateIdentifierTypeResult> { ... }
   ```

2. **Factory** (this feature):

   ```ts
   export function makeCreateIdentifierType(): CreateIdentifierType {
     return async (input) => { ... };
   }
   ```

The factory has a different signature than the alias (`() => CreateIdentifierType` vs `(input) => Promise<...>`), so the existing structural match misses it. But the factory explicitly names the alias in its return type — that's the signal we use to detect it.

## What counts as a factory

A function is a factory for signature alias `X` if and only if:

1. It is module-scope: a top-level `function` declaration, or a `const`/`let` initializer that is an arrow function or function expression. Same module-scope rule the implementation index uses (no nested functions, no class methods, no object-literal methods).
2. It has an explicit return type annotation.
3. After whitespace-stripping, the return type element's text equals the name of the signature alias `X`.

That's the entire detection rule. Loose, name-based, fast — same spirit as the existing signature-key matching.

## What is NOT a factory (v1 limitations)

- **Promise-wrapped return:** `function makeFoo(): Promise<CreateIdentifierType>`. Rare in hexagonal codebases; can be added later if needed.
- **Typed-const variants:** `const makeFoo: () => CreateIdentifierType = () => { ... }`. Detection would require resolving the *variable's* type annotation rather than the function's own return type — significantly more complex.
- **Class methods, object-literal methods, nested functions:** consistent with the existing implementation rule.
- **Generic signatures:** generic type aliases are already excluded from the signature index, so factories of generic aliases naturally don't match anything.
- **Renamed imports:** `import { CreateIdentifierType as Foo } from ...; (): Foo` — the indexer keys by `"Foo"`, the lookup uses `"CreateIdentifierType"`, so they don't match. Same limitation as the existing implementation match. Documented but not fixed.

## Architecture

```
SignatureKey (pure function)
    ↑                  ↑                          ↑
SignatureStubIndex   ImplementationStubIndex   FactoryStubIndex   ← FileBasedIndex<String, Void>
    ↑                  ↑                          ↑
SignatureLineMarkerProvider        ← gutter on `type X = (...) => ...`, queries Implementation + Factory
ImplementationLineMarkerProvider   ← gutter on implementation functions (unchanged)
FindImplementationsAction          ← shortcut/menu on a type alias, queries Implementation + Factory
```

### New component: `FactoryStubIndex`

`FileBasedIndexExtension<String, Void>`. Indexes module-scope `JSFunction`s that have an explicit return type, keyed by the return type element's text with whitespace stripped (e.g., `"CreateIdentifierType"`).

Companion API:

- `val NAME: ID<String, Void>` — index identifier `"io.meiro.tstypedfunctions.FactoryStubIndex"`.
- `fun findFactories(project: Project, aliasName: String, scope: GlobalSearchScope = projectScope): List<JSFunction>` — looks up factories by alias name, re-validates each candidate's return type (defends against stale entries), returns matching `JSFunction`s.

Reuses `INDEX_VERSION` — the factory index depends on the same canonicalization (whitespace stripping) as the existing two indexes.

Reuses the same module-scope `candidateFunctions` rule as `ImplementationStubIndex`. Implementation note: the helper currently lives privately inside `ImplementationStubIndex.companion`. To avoid duplication, extract it into a new internal top-level function in the package — `internal fun moduleScopeFunctions(file: PsiFile): List<JSFunction>` — and have both indexes call it. This is a targeted refactor that addresses the previously-flagged duplication between the index and the line-marker provider's `isModuleScope`.

### Wiring changes

**`SignatureLineMarkerProvider`** — queries `ImplementationStubIndex.findFunctions(signatureKey)` AND `FactoryStubIndex.findFactories(aliasName)`. Merges results into a single `NavigationGutterIconBuilder`. Gutter renders one icon; the popup shows all matches together (file:line, no factory label — the gutter popup uses `NavigationGutterIconBuilder`'s default rendering).

**`FindImplementationsAction`** — same merge. Wraps each match in an internal `Match` value class (`fn: JSFunction`, `isFactory: Boolean`) and renders in the popup via `BaseListPopupStep.getTextFor(Match)`. Factory rows are prefixed `[factory] ` to disambiguate.

**`ImplementationLineMarkerProvider`** — unchanged. No reverse gutter on factories (built-in `Ctrl+B` already navigates from a factory's named return type to the alias).

### plugin.xml

One new entry inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<fileBasedIndex implementation="io.meiro.tstypedfunctions.FactoryStubIndex"/>
```

No new line-marker provider registrations, no new action registrations.

## Edge cases

- **Multiple factories for the same signature** — handled by the index returning a list.
- **Both a direct implementation and a factory present** — both rendered in the same popup (the action popup distinguishes them via `[factory]` prefix; the gutter popup relies on file/line context).
- **Whitespace-only differences in return type** (`():\n  CreateIdentifierType`) — keyed identically by the whitespace-stripped index key.
- **Empty factory body** (`function makeFoo(): CreateIdentifierType { throw new Error() }`) — still indexed. The plugin trusts the type annotation; it does not validate that the function actually returns a matching value.
- **Factories with non-empty parameters** (`function makeFoo(deps: Dependencies): CreateIdentifierType`) — indexed; the factory's own parameter shape doesn't affect detection.
- **`async function makeFoo(): CreateIdentifierType`** — would be a type error in TypeScript (an `async` function's return type must be `Promise<...>`), so this won't appear in real code. Not a concern.

## Testing

- **`FactoryStubIndexTest`** (new, ~3 tests):
  - Finds a single factory by alias name.
  - Finds multiple factories sharing the same return type.
  - Excludes nested functions, class methods, object-literal methods (one combined test mirroring `ImplementationStubIndexTest.testNestedFunctionMethodAndObjectLiteralExcluded`).

- **`SignatureLineMarkerProviderTest`** (extended, ~2 tests):
  - Gutter target list includes both implementations and factories.
  - Gutter appears when only a factory exists (no direct implementation).

- **`FindImplementationsActionTest`** (extended, ~1 test):
  - Lookup-side regression check that confirms factory results merge with implementation results.

- **`SignatureKey*Test`** — unchanged (factory detection doesn't go through `SignatureKey`).

## User-facing changes

- README "Usage" section gains a sentence noting that factories are also detected.
- Architecture doc gets a paragraph for `FactoryStubIndex`.

## Risk

Low. Pure additive feature: new index + small merges into two existing files. Existing 30 tests continue to pass unchanged. Failure modes are local (factory list empty when it shouldn't be, or extra rows in popup) — none affect the existing direct-implementation behavior.
