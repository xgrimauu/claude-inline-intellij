package dev.claudeinline

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

private const val MODEL_KEY = "dev.claudeinline.model"

class InlinePromptAction : AnAction(), ActionPromoter {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null &&
            e.getData(CommonDataKeys.EDITOR) != null &&
            e.getData(CommonDataKeys.VIRTUAL_FILE)?.isInLocalFileSystem == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val editor = e.getData(CommonDataKeys.EDITOR)!!
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)!!.toNioPath()
        val lines = selectedLines(editor)
        showPrompt(editor) { instruction, model ->
            project.service<ClaudeEditService>().edit(EditRequest(file, lines, instruction, model))
        }
    }

    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> = listOf(this)

    private fun selectedLines(editor: Editor): IntRange {
        val document = editor.document
        val selection = editor.selectionModel
        val lastSelectedOffset = maxOf(selection.selectionStart, selection.selectionEnd - 1)
        return document.getLineNumber(selection.selectionStart) + 1..document.getLineNumber(lastSelectedOffset) + 1
    }

    private fun showPrompt(editor: Editor, onSubmit: (String, ClaudeModel) -> Unit) {
        val properties = PropertiesComponent.getInstance()
        val models = ComboBox(ClaudeModel.entries.toTypedArray())
        models.renderer = textListCellRenderer { it.label }
        models.item = ClaudeModel.valueOf(properties.getValue(MODEL_KEY, ClaudeModel.OPUS.name))
        val field = JBTextField(40)
        field.emptyText.text = "Ask Claude. Enter to run, Esc to cancel"
        val panel = JPanel(BorderLayout(JBUI.scale(4), 0))
        panel.border = JBUI.Borders.empty(4)
        panel.add(models, BorderLayout.WEST)
        panel.add(field, BorderLayout.CENTER)
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, field)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .createPopup()
        field.addActionListener {
            val instruction = field.text.trim()
            if (instruction.isEmpty()) return@addActionListener
            properties.setValue(MODEL_KEY, models.item.name)
            popup.closeOk(null)
            onSubmit(instruction, models.item)
        }
        popup.showInBestPositionFor(editor)
    }
}
