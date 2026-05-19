# FileRush — Create Files Fast

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/31818)](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/31818)](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)

**[Install from JetBrains Marketplace →](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)**

---

![FileRush Screenshot](https://plugins.jetbrains.com/files/31818/screenshot_b06372f8-2b01-400a-9739-53d57a202118)

---

Keyboard-first, blazingly fast file creation and navigation for IntelliJ IDEs, no mouse needed.

## Usage

Press `Ctrl+Alt+Shift+N` to open the quick file dialog.

1. **Type** any part of a path to fuzzy-find matching directories
2. **Navigate** suggestions with `Ctrl+N` / `Ctrl+P` (or `↓` / `↑`)
3. **Autocomplete** the path with `Tab`
4. **Type** your filename at the end
5. **Hit `Enter`** — file created and opened instantly

## Use Cases

**Create a file in the current folder**
Open FileRush → press `Tab` to select the current folder → type the filename → `Enter`.

**Create a file in another folder**
Type part of the folder name (fuzzy search finds it) → `Tab` → type the filename → `Enter`.

**Create a folder**
Same as above, but skip the extension. FileRush creates a directory instead.

**Create a file beside an existing file**
Type part of an existing filename (e.g. `UserRepo` to find `UserRepository.kt`) → `Tab` → the extension is pre-filled, just type your new name (e.g. `CachedUserRepository`) → `Enter`.

## Customizing the Shortcut

- Go to **Settings → Keymap**, search for **FileRush**, and assign any shortcut.
- With **IdeaVim**, use the `FileRush.Open` action in your `.ideavimrc`:

```vim
nnoremap <leader>fc :action FileRush.Open<CR>
```

## Installation

- **Marketplace:** [FileRush — Create Files Fast](https://plugins.jetbrains.com/plugin/31818-filerush--create-files-fast)
- **IDE:** `Settings → Plugins → Marketplace` → search `FileRush`

Works on all IntelliJ IDEs.
