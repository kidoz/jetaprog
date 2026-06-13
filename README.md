# JetaProg

A modern, extensible, cross-platform IDE built with Kotlin and Compose Multiplatform —
inspired by the power of JetBrains IntelliJ IDEA and the extensibility of VS Code, with an
**embedded MCP server** for first-class AI-agent integration.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.11.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Platforms](https://img.shields.io/badge/platforms-Windows%20%7C%20macOS%20%7C%20Linux-informational.svg)](#installation)

> **Status:** under active development. The core editor, language services, build integration,
> and plugin system are working; debugging, VCS, and the plugin marketplace are on the roadmap.

---

## Features

### Code editor
- Syntax highlighting for many languages (hand-written lexers + Tree-sitter)
- Code completion, signature help, and hover information
- Find & replace with case / whole-word / regex options and live match highlighting
- Undo/redo with edit coalescing
- Smart typing: auto-indent, auto-closing brackets and quotes, comment toggling,
  indent/dedent, move-line, and bracket matching
- Inline diagnostics with severity highlighting and hover messages
- Per-language code formatting (format on demand)

### Language intelligence
- LSP client with a hybrid model: fast in-process providers with LSP fallback
- Bundled language plugins: **Kotlin, Python, Rust, Vala**
- Editor syntax support for Java, C++, Markdown, TOML, XML, Meson, and more

### Build & tooling
- Build-system runners: **Gradle, Cargo, Meson, Python (Poetry / uv)**
- Run-configuration system (JetBrains-style)
- Integrated terminal and build-output panels

### AI integration
- Embedded **Model Context Protocol (MCP)** server exposing IDE tools, resources,
  and prompts to external AI agents (e.g. Claude)

### Extensibility
- VS Code-style plugin system with lazy activation and a typed plugin API
- Extension points and an application-wide message bus

---

## Tech stack

| Area | Technology |
|------|------------|
| Language | Kotlin 2.4.0 (JVM target 25) |
| UI | Compose Multiplatform 1.11.1 (Desktop) |
| Async | Kotlin Coroutines 1.11.0 |
| DI | Koin 4.2.1 |
| Serialization | kotlinx.serialization 1.11.0 |
| HTTP / WebSocket | Ktor 3.5.0 |
| Persistence | SQLDelight 2.3.2 |
| Build | Gradle 9.5 (Kotlin DSL, version catalog, convention plugins) |
| Quality | ktlint, detekt, explicit API mode, all warnings as errors |

---

## Project structure

The codebase is a multi-module Gradle build organized by concern:

```
core/          common utilities, platform abstractions, project model,
               settings, lint, run configuration
editor/        text editor engine, syntax highlighting, Tree-sitter
lsp/           Language Server Protocol — protocol types, client, embedded server
dap/           Debug Adapter Protocol — protocol, client, service
mcp/           embedded MCP server and IDE services bridge
plugins/       plugin API, runtime, support, and bundled language plugins
build-system/  Gradle, Cargo, Meson, Python, and server runners
app/desktop    Compose Desktop application (UI shell, editor, dialogs)
```

---

## Getting started

### Prerequisites
- **JDK 25** (the build compiles against a JVM 25 toolchain)
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
- **Piece-table** text buffer for efficient editing of large files.

---

## Roadmap

- [ ] Full debugger UI on top of the DAP foundation
- [ ] Version control (status, diff, stage, commit) integration
- [ ] Symbol-aware refactorings (rename, extract, move)
- [ ] IDE test runner with a results tree
- [ ] Plugin marketplace and third-party plugin installation
- [ ] Additional language plugins (Go, TypeScript, C/C++)

---

## License

Released under the [MIT License](LICENSE).

Copyright (c) 2026 Aleksandr Pavlov &lt;ckidoz@gmail.com&gt;
