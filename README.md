# Claude Inline

Inline [Claude Code](https://claude.com/claude-code) prompt for IntelliJ IDEA. Press Ctrl+A in the editor, type an instruction, press Enter. Claude Code edits the current line, or the selected lines, in place.

Not affiliated with Anthropic or JetBrains.

## Requirements

- IntelliJ IDEA 2026.2 or later, with a macOS keymap.
- The Claude Code CLI (`claude`), logged in and on your login shell PATH.

## Install

1. Download `claude-inline-<version>.zip` from [Releases](https://github.com/xgrimauu/claude-inline-intellij/releases).
2. In IntelliJ, open Settings > Plugins > gear icon > Install Plugin from Disk.
3. Select the zip. You do not need to restart the IDE.

## How to use

1. Put the caret on a line, or select lines.
2. Press Ctrl+A. A prompt opens at the caret.
3. Select the model: Opus, Fable, Sonnet or Haiku. The plugin remembers the last one.
4. Type the instruction, for example `finish the setters and getters`.
5. Press Enter. To close the prompt without a run, press Esc.

Claude runs in the background. When it finishes, the editor reloads the file and a notification shows what changed. To stop Claude, cancel the task in the status bar.

## What it runs

```
claude -p "<instruction, file path and line>" --model opus --effort medium \
  --settings '{"fastMode":true}' --permission-mode acceptEdits \
  --tools Read,Edit,Glob,Grep --strict-mcp-config --no-session-persistence
```

- Claude can only read and edit files. It has no shell and no MCP servers.
- Fast mode is on for Opus only. If your organization has fast mode off, Opus runs at standard speed.
- Runs do not show in `claude --resume`.

## Shortcut

Ctrl+A is bound only in the macOS keymaps. In the editor it replaces "Move Caret to Line Start" (Cmd+Left still does that). On other keymaps, assign a shortcut to "Claude Inline Edit" in Settings > Keymap.

## Build from source

```
./gradlew buildPlugin    # zip in build/distributions
./gradlew build          # compile and tests
./gradlew verifyPlugin   # JetBrains Plugin Verifier
CLAUDE_INLINE_E2E=1 ./gradlew test   # adds a test against the real CLI (spends tokens)
./gradlew runIdeProof    # sandbox IDE run: Ctrl+A, type, Enter, screenshots (spends tokens)
```

## License

[MIT](LICENSE)
