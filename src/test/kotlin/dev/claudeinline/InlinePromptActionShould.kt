package dev.claudeinline

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.rearrangeByPromoters
import com.intellij.openapi.application.EDT
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.KeyStroke
import kotlin.io.path.Path

@TestApplication
class InlinePromptActionShould {

    private val project = projectFixture(openAfterCreation = true)
    private val module = project.moduleFixture()
    private val sourceRoot = module.sourceRootFixture(pathFixture = project.pathInProjectFixture(Path("src")))
    private val file = sourceRoot.psiFileFixture("Notes.txt", "one\ntwo\n")
    private val editor = file.editorFixture()

    @Test
    fun run_before_editor_line_start_on_ctrl_a_in_the_mac_keymap(): Unit = timeoutRunBlocking {
        withContext(Dispatchers.EDT) {
            val actionManager = ActionManager.getInstance()
            val keymap = KeymapManager.getInstance().getKeymap("Mac OS X 10.5+")!!
            val bound = keymap.getActionIdList(KeyboardShortcut(KeyStroke.getKeyStroke("control A"), null))
                .map { actionManager.getAction(it) }
            val context = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project.get())
                .add(CommonDataKeys.EDITOR, editor.get())
                .add(CommonDataKeys.VIRTUAL_FILE, file.get().virtualFile)
                .build()

            val enabled = rearrangeByPromoters(bound, context).filter { action ->
                TestActionEvent.createTestEvent(action, context).also { action.update(it) }.presentation.isEnabled
            }

            assertThat(enabled.map { actionManager.getId(it) }).containsExactly("dev.claudeinline.InlinePrompt", "EditorLineStart")
        }
    }
}
