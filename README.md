<p align="center">
  <img src="packaging/branding/readme-banner.png" alt="JetaProg" width="360">
</p>

A modern, extensible, cross-platform IDE built with Kotlin and Compose Multiplatform —
inspired by the power of JetBrains IntelliJ IDEA and the extensibility of VS Code, with an
**embedded MCP server** for first-class AI-agent integration.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.12.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Platforms](https://img.shields.io/badge/platforms-Windows%20%7C%20macOS%20%7C%20Linux-informational.svg)](#installation)

> **Status:** under active development. The core editor, language services, build integration,
> plugin system, debugging (DAP), and Git integration are working; the plugin marketplace
> and third-party plugin installation are on the roadmap.

---

## Features

### Code editor
- Syntax highlighting with incremental hand-written lexers and LSP semantic overlays
- Code completion, signature help, and hover information
- Find & replace with case / whole-word / regex options and live match highlighting
- Project-wide find in files, plus replace in files with a confirmation dialog
  (files open with unsaved changes are skipped and clean buffers reload automatically)
- Undo/redo with edit coalescing
- Smart typing: auto-indent, auto-closing brackets and quotes, comment toggling,
  indent/dedent, move-line, and bracket matching
- Inline diagnostics with severity highlighting and hover messages
- Per-language code formatting (format on demand)

### Language intelligence
- LSP client with a hybrid model: fast in-process providers with LSP fallback
- Bundled language plugins: **Kotlin, Java (+Spring), Python, Rust, Vala, Go,
  JavaScript/TypeScript, C/C++ (clangd), .NET (Roslyn)** — with framework
  sub-plugins for Spring Boot, Kotlin Multiplatform, Entity Framework Core, and DotProlog
- Editor syntax support for Markdown, TOML, XML, Meson, Prolog, MSBuild, and more

### Debugging & VCS
- DAP-based debugger with a bundled JVM/JDI adapter: breakpoints, stepping,
  stack frames, variables, watches, and inline value hints
- Git integration: status, staging, commit, push/pull, branches, diffs,
  commit history, and per-gutter line-change markers

### Build & tooling
- Build/run integration: **Gradle, CMake, Cargo, Meson, Python (Poetry / uv), Java, Go, Node.js, and .NET**
- Run-configuration system (JetBrains-style)
- Integrated terminal and build-output panels

### AI integration
- Embedded **Model Context Protocol (MCP)** server exposing IDE tools, resources,
  and prompts to external AI agents (e.g. Claude)

- ACP agent workspace with session history, recovery, approvals, queues, checkpoints,
  Git worktrees, and change review
- MCP tools cover file access, project context, diagnostics/inspections, search, debug,
  run-configuration discovery, and Gradle tasks; resources expose project/editor context

### Database tools
- PostgreSQL and StarRocks connection profiles, schema browsing, SQL execution,
  cancellation, and query results
- Passwords are supplied separately and are not persisted in connection profiles

### Workspace persistence
- Restores open tabs and their cursors, active tab, panel layout, tree expansion,
  recent files, and search/replace histories
- Unsaved document contents are not persisted as workspace state

### Extensibility
- VS Code-style plugin system with lazy activation and a typed plugin API
- Extension points and an application-wide message bus

---

## Tech stack

| Area | Technology |
|------|------------|
| Language | Kotlin 2.4.20 (JVM target 26) |
| UI | Compose Multiplatform 1.12.1 (Desktop) |
| Async | Kotlin Coroutines 1.11.0 |
| DI | Manual constructor injection |
| Serialization | kotlinx.serialization 1.11.0 (JSON settings/storage) |
| HTTP / WebSocket | Ktor 3.6.0 |
| Build | Gradle 9.8 (Kotlin DSL, version catalog, convention plugins) |
| Quality | ktlint, detekt, explicit API mode, all warnings as errors |

---

## Project structure

The codebase is a multi-module Gradle build organized by concern:

```
core/          common utilities, platform abstractions, project model,
               settings, lint, run configuration, database tools
editor/        text editing models, syntax highlighting, Tree-sitter abstractions
lsp/           Language Server Protocol — protocol types, client, embedded server
dap/           Debug Adapter Protocol — protocol, client, service
mcp/           embedded MCP server (desktop tools are registered by app/desktop)
plugins/       plugin API, runtime, support, and bundled language plugins
build-system/  Gradle, CMake, Cargo, .NET, Meson, Python, and server runners
app/desktop    Compose Desktop application (UI shell, editor, dialogs)
```

---

## Getting started

### Prerequisites
- **JDK 26** (the configured Gradle/Foojay toolchain provisions it automatically when needed)
- No separate Gradle install needed — use the included wrapper (`./gradlew`)

### Build & run

```bash
# Build the entire project
./gradlew build

# Run the desktop application
./gradlew :app:desktop:run

# Create a native distribution for the current OS (DMG / MSI / DEB)
./gradlew :app:desktop:packageDistributionForCurrentOS
```

### Testing & quality gates

```bash
# Run all tests
./gradlew test

# Run rendered Compose interaction tests and capture UI review artifacts
./gradlew :app:desktop:uiTest

# Lint and static analysis (both must pass — warnings are treated as errors)
./gradlew ktlintCheck detekt

# Auto-format
./gradlew ktlintFormat
```

---

## Architecture

- **MVI** (Model-View-Intent) for all UI state: immutable state, sealed intents/effects,
  and `StateFlow`-based view models.
- **Plugin system** modeled on VS Code: plugins implement an `activate` / `deactivate`
  lifecycle and register language features, commands, and build providers through a
  typed `PluginContext`.
- **Hybrid language services**: UI-centric features can run in-process for low latency
  while heavier analysis is delegated to an LSP server.
- **Desktop editor storage** currently uses whole strings and snapshot-based undo.
  Piece-table utilities exist in `editor/core` but are not used by the desktop editor.
  Highlighting is disabled for documents over 1,000,000 characters; large-file editing
  performance is not guaranteed.

---

## Feature limits

- Tree-sitter interfaces and lexer adapters exist, but no native parser is connected
  to the desktop editor. Highlighting currently uses the lexers described above.
- Tomcat and Docker configurations are retained for compatibility but cannot be run
  or debugged. Their editors and selectors label them unavailable.
- Plugin marketplace, third-party installation, and update delivery are not implemented.
  Update-policy controls are disabled; bundled plugins can be enabled or disabled.
- Symbol-aware rename is available for Kotlin. Extract/move/inline refactorings and
  super-symbol navigation are not implemented. External LSP call/type hierarchy
  fallback is not connected.
- Non-JVM debuggers and external language servers require separate installations.
  Availability of a plugin does not guarantee identical capabilities across languages.
- Remote development and multiple application windows are not implemented.

Normal verification includes Compose UI tests, but excludes real-provider agent recovery.
Use `./gradlew :app:desktop:liveAgentRecoveryTest` explicitly for that integration test;
external database/server/toolchain and cross-platform compatibility need separate validation.

## Roadmap

- [x] Full debugger UI on top of the DAP foundation
- [x] Version control (status, diff, stage, commit) integration
- [x] IDE test runner with a results tree
- [x] Additional language plugins (Go, TypeScript, C/C++)
- [ ] Symbol-aware refactorings beyond Kotlin rename (extract, move, inline)
- [ ] Plugin marketplace and third-party plugin installation
- [ ] Remote development and multi-window support

---

## License

Released under the [MIT License](LICENSE).

Copyright (c) 2026 Aleksandr Pavlov &lt;ckidoz@gmail.com&gt;
