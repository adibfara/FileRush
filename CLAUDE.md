# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is FileRush

IntelliJ Platform plugin inspired by Oil (neovim). Lets users create/open files via fuzzy path picker without touching the mouse. Triggered by `Ctrl+Alt+Shift+P`.

## Commands

```powershell
# Run plugin in sandbox IDE
./gradlew runIde

# Build
./gradlew build

# Run tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin
```

Target platform: IntelliJ IDEA 2023.3+ (build 233+), JVM 17, Kotlin 2.1.

## Architecture

Five files, clean separation between logic and UI:

- **`OpenQuickFileAction`** — `AnAction` entry point. Triggered by keyboard shortcut, opens `QuickFileDialog`.
- **`QuickFileView`** — interface decoupling service from UI. `QuickFileDialog` implements it.
- **`QuickFileService`** — all logic: fuzzy file search via `FilenameIndex`/`NameUtil`, path completion, file creation, navigation. Calls back to `QuickFileView`.
- **`QuickFileDialog`** — `DialogWrapper` + `QuickFileView`. Owns Swing UI (input field + `JBList`). Delegates all key events to `QuickFileService`.
- **`QuickFileEntry`** — data class: `path`, `isDirectory`, `existing`.

**Flow:** user types → `QuickFileDialog` calls `service.updateSuggestions()` → service searches via `FilenameIndex`, falls back to a "Create ->" entry if no matches → `view.showEntries()` updates the list. Enter opens or creates; Tab completes path.

File search capped at 50 results. Uses `ReadAction` for `FilenameIndex` access (required by IntelliJ threading rules).
