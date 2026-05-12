# Architecture

How the plugin is wired into the IDE, what runs when, and how the pieces connect.

## Entry point

The IntelliJ Platform discovers the plugin via its manifest:

- `ts-typed-functions/src/main/resources/META-INF/plugin.xml`

There is no `main()` — the platform reads the manifest on plugin load, then instantiates the registered classes lazily as their extension points fire. The manifest declares three file-based indexes, two line-marker providers (each registered for both `TypeScript` and `TypeScript JSX`), and one editor action.

## Components

### Indexes (background)

All four indexes implement `FileBasedIndexExtension<String, Void>`. `SignatureStubIndex` and `ImplementationStubIndex` key entries by the canonical `SignatureKey`; `FactoryStubIndex` and `AnnotatedImplementationStubIndex` key by alias name (the bare identifier text). They share `INDEX_VERSION` (`IndexVersion.kt`) so any change to canonicalization or visited PSI nodes invalidates all of them at once.

- **`SignatureStubIndex`** (`SignatureStubIndex.kt`) — visits every `TypeScriptTypeAlias` in TS/TSX and stores its `SignatureKey`. `findAliases(project, key)` looks up matching files, then re-validates each alias against the live PSI to defend against stale entries.
- **`ImplementationStubIndex`** (`ImplementationStubIndex.kt`) — visits module-scope `JSFunction`s only: top-level `function` declarations and arrow/function expressions assigned to `const`/`let`. Nested functions, class methods, and object-literal methods are excluded by walking the ancestor chain. `findFunctions(project, key)` mirrors the lookup-then-revalidate pattern.
- **`FactoryStubIndex`** (`FactoryStubIndex.kt`) — visits the same module-scope `JSFunction` set as `ImplementationStubIndex` (via the shared `moduleScopeFunctions` helper in `ModuleScope.kt`) but keys entries by the function's return-type-element text rather than its `SignatureKey`. `findFactories(project, aliasName)` looks up functions whose explicit return type names a signature alias — i.e., factories that produce an implementation. Used by the signature-side gutter and the `Find Implementations` action to surface factories alongside direct implementations.
- **`AnnotatedImplementationStubIndex`** (`AnnotatedImplementationStubIndex.kt`) — visits every `JSVariable` in the file (no module-scope filter) and indexes those whose type annotation is a single, unqualified identifier and whose initializer is a function literal. Keyed by the alias name. `findAnnotatedImplementations(project, aliasName)` returns the matching `JSVariable`s. This is the path that catches nested adapters inside repository-style factories, where the arrow function has no parameter or return type annotations of its own (they're inferred from the variable's annotation), so structural matching has nothing to canonicalize.

### Line marker providers (daemon, background thread)

Both providers extend `RelatedItemLineMarkerProvider` and override `getLineMarkerInfo` to return `null`, deferring all work to `collectNavigationMarkers` so index lookups and PSI re-validation never run on the EDT.

- **`SignatureLineMarkerProvider`** (`SignatureLineMarkerProvider.kt`) — fires on the *name identifier* of a `TypeScriptTypeAlias`. Computes its `SignatureKey`, queries `ImplementationStubIndex.findFunctions`, and adds a gutter icon (`AllIcons.Gutter.ImplementedMethod`) whose click target is the matching implementation functions.
- **`ImplementationLineMarkerProvider`** (`ImplementationLineMarkerProvider.kt`) — symmetric. Fires on the name identifier of a module-scope function declaration or `const x = (arrow|fn-expr)` initializer (it shares the `isAtModuleScope` predicate from `ModuleScope.kt` with the indexes). Queries `SignatureStubIndex.findAliases` and renders `AllIcons.Gutter.ImplementingMethod` pointing at the matching signatures.

### Action (user-triggered)

- **`FindImplementationsAction`** (`FindImplementationsAction.kt`) — registered in `EditorPopupMenu` with `Ctrl+Alt+Shift+P` (default) / `Cmd+Alt+Shift+P` (macOS). `getActionUpdateThread()` returns `BGT` so `update` can call into the PSI safely. `update` enables the action when the caret is on an alias whose `SignatureKey.of` is non-null. `actionPerformed` queries `ImplementationStubIndex` and shows a `JBPopupFactory` list popup; choosing a result calls `JSFunction.navigate(true)` inside `invokeLater`.

### Shared signature canonicalization

`SignatureKey.kt` produces the canonical key used by both indexes and both providers. It is the single point of truth for "do these signatures match?" — whitespace, comments, and parameter names are normalized away, while referenced type names are preserved. See `README.md` for the matching strategy and known limitations.

## Typical flow

1. **Plugin load.** Platform reads `plugin.xml`, registers the three indexes, the two line-marker providers, and the action.
2. **Indexing.** As TS/TSX files are added or modified, `SignatureStubIndex` and `ImplementationStubIndex` compute `SignatureKey`s for qualifying elements and store them keyed by file, while `FactoryStubIndex` keys module-scope functions by their declared return-type text.
3. **Editor open.** The daemon code analyzer walks the open file's PSI and calls `collectNavigationMarkers` on each provider per element. Matches result in gutter icons; clicking them navigates via `NavigationGutterIconBuilder`.
4. **Explicit lookup.** With the caret on a signature alias, the user invokes the shortcut or right-click action → `FindImplementationsAction` resolves the alias, runs an `ImplementationStubIndex` lookup, and presents a navigation popup.

Both directions converge on `SignatureKey` as the match key, and `INDEX_VERSION` keeps all three indexes versioned together.
