# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-05-12

### Added
- Annotation-based matching: typed `const`/`let` declarations whose type annotation names a
  signature alias and whose initializer is a function literal are now surfaced as
  implementations. Catches the repository-style pattern where adapters are nested inside a
  factory function (e.g. `const findX: FindX = async (...) => ...` inside
  `makeXRepository(...)`).
- Proximity-based ordering: matches are now sorted so implementations in the same directory
  as the signature appear first, then siblings, then more distant project files. Library /
  `node_modules` matches all sink to the bottom as a single bucket.

### Changed
- "No implementations found" popup message changed to "No matches found" (now covers
  factories and annotated implementations as well as direct structural matches).

## [0.1.0] - 2026-05-11

### Added
- Initial release.
- Gutter icons on TypeScript function-typed type aliases linking to matching implementations.
- Reverse gutter icons on top-level implementation functions linking back to matching signatures.
- `Find Implementations of Type Signature` action with default shortcut
  `Ctrl+Alt+Shift+P` (Windows/Linux) / `Cmd+Alt+Shift+P` (macOS).
- Factory detection: top-level functions whose explicit return type names a signature alias are
  surfaced alongside direct implementations, marked `[factory]`.

### Compatibility
- IntelliJ Platform build 261 (IntelliJ IDEA Ultimate / WebStorm 2026.1).
