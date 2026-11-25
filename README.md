# Claude Inline

IntelliJ plugin. Press Ctrl+A in an editor, type an instruction, press Enter. A headless Claude Code process edits the current line (or the selected lines) in place, and the editor shows the change.

- The combo box selects the model: Opus, Fable, Sonnet or Haiku. The plugin remembers the last one.
- Opus runs with fast mode on. The other models run at standard speed.
- Claude can only use the Read, Edit, Glob and Grep tools. MCP servers are off. Runs do not show in `/resume`.
- Cancel the background task to kill the Claude process.

## Requirements

- IntelliJ IDEA 2026.2 (build 262) or later.
- The `claude` CLI on your login shell PATH. The plugin resolves `claude` from the login shell PATH, so an IDE started from the Dock finds `~/.local/bin/claude` when `~/.zprofile` or `~/.zshrc` adds `~/.local/bin`.
- A macOS keymap ("macOS" or a copy of it). Ctrl+A is bound only in "Mac OS X 10.5+" and "Mac OS X". In an editor it runs before "Move Caret to Line Start". Other keymaps: assign a shortcut to "Claude Inline Edit" in Settings, Keymap.

## Install

1. `./gradlew buildPlugin`
2. Settings, Plugins, gear icon, Install Plugin from Disk.
3. Select `build/distributions/claude-inline-0.1.0.zip`.

No restart is necessary. The plugin is dynamic.

## Develop

```
./gradlew build          # compile and tests
./gradlew verifyPlugin   # Plugin Verifier against the target IDE
./gradlew runIde         # sandbox IDE with the plugin
CLAUDE_INLINE_E2E=1 ./gradlew test --tests '*real_claude_cli*'   # calls the real CLI, spends tokens
```
