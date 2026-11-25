package dev.claudeinline

import java.nio.file.Path

enum class ClaudeModel(val label: String, val alias: String, val fast: Boolean) {
    OPUS("Opus", "opus", true),
    FABLE("Fable", "fable", false),
    SONNET("Sonnet", "sonnet", false),
    HAIKU("Haiku", "haiku", false),
}

data class EditRequest(val file: Path, val lines: IntRange, val instruction: String, val model: ClaudeModel)
