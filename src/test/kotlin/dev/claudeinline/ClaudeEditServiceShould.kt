package dev.claudeinline

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val UNFINISHED = """
    public class Person {
        private String name;
        private int age;

        public String getName() {
        }
    }
""".trimIndent()

private val FINISHED = """
    public class Person {
        private String name;
        private int age;

        public String getName() {
            return name;
        }
    }
""".trimIndent()

private const val GETTER_LINE = 5

@TestApplication
class ClaudeEditServiceShould {

    private val project = projectFixture(openAfterCreation = true)
    private val module = project.moduleFixture()
    private val sourceRoot = module.sourceRootFixture(pathFixture = project.pathInProjectFixture(Path("src")))
    private val file = sourceRoot.psiFileFixture("Person.java", UNFINISHED)
    private val editor = file.editorFixture()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun applies_the_claude_edit_to_the_open_document(): Unit = timeoutRunBlocking {
        val finished = tempDir.resolve("Finished.java").apply { writeText(FINISHED) }
        val claude = fakeClaude("cp '$finished' '${target()}'\necho 'Finished getName'")

        service().edit(EditRequest(target(), GETTER_LINE..GETTER_LINE, "finish the getter", ClaudeModel.OPUS), claude).join()

        assertThat(readAction { editor.get().document.text }).isEqualTo(FINISHED)
    }

    @Test
    fun save_unsaved_changes_before_claude_reads_the_file(): Unit = timeoutRunBlocking {
        val seen = tempDir.resolve("Seen.java")
        val claude = fakeClaude("cp '${target()}' '$seen'")
        writeCommandAction(project.get(), "Draft") { editor.get().document.insertString(0, "// draft\n") }

        service().edit(EditRequest(target(), GETTER_LINE..GETTER_LINE, "finish the getter", ClaudeModel.OPUS), claude).join()

        assertThat(seen.readText()).startsWith("// draft\n")
    }

    @Test
    fun call_claude_with_the_model_effort_fast_mode_tools_and_target_line(): Unit = timeoutRunBlocking {
        val arguments = tempDir.resolve("arguments")
        val claude = fakeClaude("printf '%s\\0' \"\$@\" > '$arguments'")

        service().edit(EditRequest(target(), GETTER_LINE..GETTER_LINE, "finish the getter", ClaudeModel.OPUS), claude).join()

        val sent = arguments.readText().split('\u0000').dropLast(1)
        assertThat(sent[0]).isEqualTo("-p")
        assertThat(sent[1]).startsWith("Edit the file ${target()}, line 5.\n\nInstruction: finish the getter\n\n")
        assertThat(sent.drop(2)).containsExactly(
            "--model", "opus", "--effort", "medium", "--settings", """{"fastMode":true}""",
            "--permission-mode", "acceptEdits", "--tools", "Read,Edit,Glob,Grep",
            "--strict-mcp-config", "--no-session-persistence",
        )
    }

    @Test
    fun send_fast_mode_only_for_opus_and_name_the_selected_lines(): Unit = timeoutRunBlocking {
        val arguments = tempDir.resolve("arguments")
        val claude = fakeClaude("printf '%s\\0' \"\$@\" > '$arguments'")

        service().edit(EditRequest(target(), 2..3, "rename the fields", ClaudeModel.SONNET), claude).join()

        val sent = arguments.readText().split('\u0000').dropLast(1)
        assertThat(sent[1]).startsWith("Edit the file ${target()}, lines 2 to 3.")
        assertThat(sent).containsSubsequence("--model", "sonnet").doesNotContain("--settings")
    }

    @Test
    fun kill_the_claude_process_on_cancel(): Unit = timeoutRunBlocking {
        val pidFile = tempDir.resolve("pid")
        val claude = fakeClaude("echo \$\$ > '$pidFile'\nexec sleep 60")

        val job = service().edit(EditRequest(target(), GETTER_LINE..GETTER_LINE, "wait", ClaudeModel.HAIKU), claude)
        while (!pidFile.exists() || pidFile.readText().isBlank()) delay(50)
        val pid = pidFile.readText().trim().toLong()
        job.cancelAndJoin()

        withTimeout(5.seconds) {
            while (ProcessHandle.of(pid).map { it.isAlive }.orElse(false)) delay(50)
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CLAUDE_INLINE_E2E", matches = "1")
    fun finish_the_setters_and_getters_with_the_real_claude_cli(): Unit = timeoutRunBlocking(3.minutes) {
        service().edit(EditRequest(target(), GETTER_LINE..GETTER_LINE, "finish the setters and getters", ClaudeModel.OPUS)).join()

        assertThat(readAction { editor.get().document.text })
            .containsPattern("""String getName\(\)\s*\{\s*return (this\.)?name;""")
            .containsPattern("""void setName\(String \w+\)""")
            .containsPattern("""int getAge\(\)\s*\{\s*return (this\.)?age;""")
            .containsPattern("""void setAge\(int \w+\)""")
    }

    private fun service() = project.get().service<ClaudeEditService>()

    private fun target() = file.get().virtualFile.toNioPath()

    private fun fakeClaude(script: String): Path {
        val claude = tempDir.resolve("claude")
        claude.writeText("#!/bin/sh\n$script\n")
        claude.toFile().setExecutable(true)
        return claude
    }
}
