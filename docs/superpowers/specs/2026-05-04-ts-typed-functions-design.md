# ts-typed-functions — design

**Status:** approved (brainstorm), pending implementation plan
**Date:** 2026-05-04

## Goal

A JetBrains plugin that, given a TypeScript type alias whose right-hand side is a function type (a "signature"), navigates to all functions in the project whose signature matches that alias — even when those functions do not name the type alias.

The motivating use case is hexagonal architecture in TypeScript. Driven signatures are expressed as type aliases:

```ts
export type CreateIdentifierType = (
  input: CreateIdentifierTypeInput,
) => Promise<CreateIdentifierTypeResult>;
```

Implementations satisfy the contract structurally without referencing the alias:

```ts
export const createIdentifierTypeImpl = async (
  input: CreateIdentifierTypeInput,
): Promise<CreateIdentifierTypeResult> => { ... };
```

WebStorm's built-in "Find Usages" cannot find such implementations because there is no syntactic reference to the alias. This plugin closes that gap.

## Matching strategy

**Loose matching by type-name shape.** Two signatures match if their canonical signature keys (defined below) are byte-equal. The codebase convention of one named input type and one named result type per signature (`<Signature>Input`, `<Signature>Result`) makes this strategy ~100% accurate in practice.

Strict TypeScript structural compatibility (real assignability) is **out of scope for v1**.

## Target IDEs

- IntelliJ IDEA Ultimate (`platformType=IU`).
- WebStorm (same plugin artifact; relies on the bundled JavaScript/TypeScript plugin).

Plugin language: Kotlin. Build: Gradle Kotlin DSL via the JetBrains [intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template).

## Architecture

```
SignatureKey (pure function)
    ↑                          ↑
SignatureStubIndex     ImplementationStubIndex     ← FileBasedIndex<String, Void>
    ↑                          ↑
SignatureLineMarkerProvider        ← gutter on `type X = (...) => ...`
ImplementationLineMarkerProvider   ← gutter on implementation functions
FindImplementationsAction          ← shortcut/menu on a type alias
```

### Components

**`SignatureKey`** — pure function `(JSElement) -> String?`. Returns `null` for elements that are not a fully-typed function. Used by both indexers and lookups, guaranteeing identical canonicalization on both sides of the match.

**`SignatureStubIndex`** — `StringStubIndexExtension<TypeScriptTypeAlias>`. Keys = signature strings. Values = type alias declarations whose RHS is a function type and which declare no generic type parameters.

**`ImplementationStubIndex`** — `StringStubIndexExtension<JSElement>`. Keys = signature strings. Values = implementation functions: `function` declarations and `const`/`let` declarations whose initializer is an arrow function or function expression with explicit types on every parameter and an explicit return type.

**`SignatureLineMarkerProvider`** — `RelatedItemLineMarkerProvider`. Triggers on the identifier of a type alias whose RHS is a function type. Computes the key, queries `ImplementationStubIndex.getElements(key, project, projectScope)`, filters out `node_modules`. If matches exist, registers a gutter icon (`AllIcons.Gutter.ImplementedMethod`); click opens a popup of matching implementations with file/line.

**`ImplementationLineMarkerProvider`** — `RelatedItemLineMarkerProvider`. Triggers on implementation function name identifiers. Computes the key, queries `SignatureStubIndex`. Uses `AllIcons.Gutter.ImplementingMethod` (reverse arrow). Same popup UX.

Both providers do work in `collectSlowLineMarkers` (background thread).

**`FindImplementationsAction`** — `AnAction` registered:

- In `<actions>` under `EditorPopupMenu` ("Find Implementations of Type Signature").
- With default keymap binding `ctrl alt shift P` (`Cmd+Alt+Shift+P` on macOS).

`actionPerformed` resolves the PSI element under caret to a `TypeScriptTypeAlias`, computes the key, queries the index, displays a `JBPopupFactory.createPopupChooserBuilder` listing matches. `update` disables the action when the caret is not on a function-typed alias.

### Data flow

- On file edit: WebStorm rebuilds the file's stub tree. The two `StubIndex` extensions repopulate automatically.
- On gutter render: line marker providers query the indexes — O(1) lookups, no scanning.
- On action invoke: the action computes the key from the alias under caret, queries the index, navigates.

## Signature key algorithm

Given a fully-typed function or function type, produce a canonical string. Two signatures match iff their keys are byte-equal.

**Accepted PSI inputs:**

- `TypeScriptFunctionType` — RHS of a signature type alias.
- `TypeScriptFunction` / `JSFunctionExpression` / `JSArrowFunctionExpression` — implementation functions, only when every parameter and the return type is explicitly typed. If anything is missing, `SignatureKey` returns `null`.

**Canonical form:**

```
(<param>,<param>,...)=><return>
```

Each `<param>` is `[?][...]:<type-text>`:

- `?` if the parameter is optional (`x?: T`).
- `...` if the parameter is rest (`...args: T[]`).
- `<type-text>` is the parameter type's PSI text with whitespace and comments stripped.

`<return>` is the return type's PSI text with the same normalization.

**Parameter names are dropped.** `(input: Foo)` and `(x: Foo)` produce the same key — parameter naming should not affect a signature↔implementation match.

**Examples:**

| Source | Key |
|---|---|
| `(input: CreateIdentifierTypeInput) => Promise<CreateIdentifierTypeResult>` | `(:CreateIdentifierTypeInput)=>Promise<CreateIdentifierTypeResult>` |
| `async (input: CreateIdentifierTypeInput): Promise<CreateIdentifierTypeResult> => {...}` | `(:CreateIdentifierTypeInput)=>Promise<CreateIdentifierTypeResult>` |
| `(a: A, b?: B) => void` | `(:A,?:B)=>void` |
| `(...xs: T[]) => U` | `(...:T[])=>U` |

The `async` implementation and the non-async signature produce the same key. This is intentional: TypeScript treats `async (): Promise<T>` as having return type `Promise<T>`.

**Type-text normalization rules:**

- Strip all whitespace (collapsed to nothing).
- Strip line and block comments.
- Preserve everything else verbatim, including imported aliases.

## Edge cases

Handled correctly by design:

- **Implementation missing types** on any parameter or return → `SignatureKey` returns `null` → not indexed → never produces a guessed match.
- **`node_modules`** excluded via `GlobalSearchScope.projectScope(project)`.
- **Multi-line types, comments, varied whitespace** — all flattened by canonicalization.
- **`async` vs sync returning `Promise<T>`** — produce the same key.

## Non-goals (v1 limitations)

Documented as known limitations in `README.md`:

- **Renamed imports.** `import { Foo as Bar }` plus `Bar` will not cross-match a signature using `Foo`. Resolving imports requires resolving symbols, which is much heavier than text canonicalization.
- **Generic signatures.** Type aliases that declare type parameters are skipped (`type Handler<T> = (x: T) => void`). Can be revisited if needed.
- **Strict structural compatibility.** Real TypeScript assignability is out of scope.
- **Class methods.** Only top-level functions and `const`/`let` arrow/function-expression initializers are indexed as implementations.

## Testing

Use `BasePlatformTestCase` from the IntelliJ test framework with JavaScript/TypeScript test fixtures.

- **`SignatureKey` unit tests.** Parse small TS snippets via `myFixture.configureByText`, walk to the relevant PSI, assert the produced key. Cover all rows of the example table plus negatives (untyped param → `null`, generic alias → `null`).
- **Index tests.** Index a multi-file fixture; query by key; assert the right elements come back.
- **Gutter tests.** `myFixture.findGuttersAtCaret()` to confirm gutter icons appear at the right positions, with the right icon (implemented vs implementing).
- **Action test.** Simulate action invocation; capture popup contents.

CI: `./gradlew check verifyPlugin` (provided by the plugin template) in GitHub Actions.

## Project scaffold

Bootstrap from `intellij-platform-plugin-template` (Gradle Kotlin DSL, modern `org.jetbrains.intellij.platform` plugin). Customize:

- `gradle.properties`: `pluginName=ts-typed-functions`, `platformType=IU`, recent stable `platformVersion`.
- `plugin.xml`: `<id>io.meiro.ts-typed-functions</id>`, `<depends>JavaScript</depends>`, register the two stub indexes, two line marker providers, and the action.
- `sinceBuild` of recent stable (e.g., `243` = 2024.3) to keep API surface current.

## Future work (explicitly deferred)

- Strict structural matching via the TypeScript compiler API as a Node-process backend; the existing UI surfaces would query the new backend through the same `SignatureKey` abstraction (replaced by an opaque match-set lookup).
- Generic signature support.
- Class method implementations.
- Cross-import-alias resolution.
