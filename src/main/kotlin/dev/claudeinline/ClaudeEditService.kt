package dev.claudeinline

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class ClaudeEditService(private val project: Project, private val scope: CoroutineScope) {

    fun edit(
        request: EditRequest,
        claude: Path? = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("claude")?.toPath(),
    ): Job = scope.launch {
        if (claude == null) {
            notify("claude CLI not found on PATH", NotificationType.ERROR)
            return@launch
        }
        withContext(Dispatchers.EDT) { FileDocumentManager.getInstance().saveAllDocuments() }
        withBackgroundProgress(project, "Claude (${request.model.label}): ${request.instruction}", cancellable = true) {
            val process = command(request, claude).createProcess()
            val output = async(Dispatchers.IO) { process.inputStream.bufferedReader().readText() }
            val exitCode = try {
                runInterruptible(Dispatchers.IO) { process.waitFor() }
            } catch (e: CancellationException) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                throw e
            }
            val lines = output.await().lines().filter { it.isNotBlank() }
            if (exitCode == 0) {
                LocalFileSystem.getInstance().findFileByNioFile(request.file)
                    ?.let { VfsUtil.markDirtyAndRefresh(false, false, false, it) }
                notify(lines.lastOrNull() ?: "Done", NotificationType.INFORMATION)
            } else {
                notify("claude exited with $exitCode\n" + lines.takeLast(20).joinToString("\n"), NotificationType.ERROR)
            }
        }
    }

    private fun command(request: EditRequest, claude: Path): GeneralCommandLine {
        val arguments = buildList {
            addAll(listOf("-p", prompt(request), "--model", request.model.alias, "--effort", "medium"))
            if (request.model.fast) addAll(listOf("--settings", """{"fastMode":true}"""))
            addAll(listOf("--permission-mode", "acceptEdits", "--tools", "Read,Edit,Glob,Grep"))
            addAll(listOf("--strict-mcp-config", "--no-session-persistence"))
        }
        return GeneralCommandLine(listOf(claude.toString()) + arguments)
            .withWorkingDirectory(Path.of(project.basePath!!))
            .withInput(File("/dev/null"))
            .withRedirectErrorStream(true)
    }

    private fun prompt(request: EditRequest): String {
        val lines = request.lines
        val target = if (lines.first == lines.last) "line ${lines.first}" else "lines ${lines.first} to ${lines.last}"
        return """
            Edit the file ${request.file}, $target.

            Instruction: ${request.instruction}

            Apply it in place with the Edit tool. Change only what the instruction requires; leave the rest of the file alone. Do not add comments. Reply with a single short line describing what you changed.
        """.trimIndent()
    }

    private fun notify(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("Claude Inline")
            .createNotification(StringUtil.escapeXmlEntities(content).replace("\n", "<br>"), type)
            .notify(project)
    }
}
