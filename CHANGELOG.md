# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
