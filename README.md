# FileRush — Create Files Fast

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/31818)](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/31818)](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)

**[Install from JetBrains Marketplace →](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)**

---

![FileRush Screenshot](https://plugins.jetbrains.com/files/31818/screenshot_b06372f8-2b01-400a-9739-53d57a202118)

---

Keyboard-first, blazingly fast file creation and navigation for IntelliJ IDEs. Inspired by [oil.nvim](https://github.com/stevearc/oil.nvim) — no mouse needed.

## Usage

Press `Ctrl+Alt+Shift+N` to open the quick file dialog.

1. **Type** any part of a path to fuzzy-find matching directories
2. **Navigate** suggestions with `Ctrl+N` / `Ctrl+P` (or `↓` / `↑`)
3. **Autocomplete** the path with `Tab`
4. **Type** your filename at the end
5. **Hit `Enter`** — file created and opened instantly

## IdeaVim

Remap the `FileRush.Open` action in your `.ideavimrc`:

```vim
nnoremap <leader>fc :action FileRush.Open<CR>
```

## Installation

- **Marketplace:** [FileRush — Create Files Fast](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)
- **IDE:** `Settings → Plugins → Marketplace` → search `FileRush`

Works on all IntelliJ IDEs.
