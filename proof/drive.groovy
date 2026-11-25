import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.components.JBTextField

import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

def project = IDE.project
def log = Logger.getInstance("proof")
def onEdt = { Closure work ->
    def result = null
    ApplicationManager.application.invokeAndWait({ result = work() }, ModalityState.any())
    result
}
def findPromptField
findPromptField = { Component component ->
    if (component instanceof JBTextField && component.emptyText.text.startsWith("Ask Claude")) return component
    if (component instanceof Container) {
        for (child in component.components) {
            def found = findPromptField(child)
            if (found != null) return found
        }
    }
    null
}
def press = { Component target, int keyCode, int modifiers ->
    def now = System.currentTimeMillis()
    IdeEventQueue.instance.postEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, now, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED))
    IdeEventQueue.instance.postEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, now, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED))
}

Thread.start("claude-inline-proof") {
    try {
        def editor = null
        for (i in 0..<600) {
            editor = onEdt { FileEditorManager.getInstance(project).selectedTextEditor }
            if (editor != null && editor.virtualFile.name == "Person.java" && onEdt { editor.caretModel.logicalPosition.line } == 4) break
            sleep 200
        }
        log.info("PROOF editor ready: ${editor?.virtualFile?.path}")
        onEdt {
            ProjectUtil.focusProjectWindow(project, true)
            editor.contentComponent.requestFocusInWindow()
        }
        sleep 1000
        def focusOwner = onEdt { KeyboardFocusManager.currentKeyboardFocusManager.focusOwner }
        log.info("PROOF focus owner before Ctrl+A: ${focusOwner?.class?.name}")
        press(editor.contentComponent, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)

        JBTextField field = null
        for (i in 0..<50) {
            field = onEdt { Window.windows.findAll { it.showing }.collect { findPromptField(it) }.find { it != null } }
            if (field != null) break
            sleep 100
        }
        log.info("PROOF prompt open after Ctrl+A: ${field != null}")
        if (field == null) return

        sleep 2000
        def instruction = "finish the setters and getters"
        boolean fieldFocused = onEdt { field.focusOwner }
        log.info("PROOF prompt field focused: $fieldFocused")
        if (!fieldFocused) return
        for (c in instruction.toCharArray()) {
            IdeEventQueue.instance.postEvent(new KeyEvent(field, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c))
            sleep 30
        }
        sleep 6000
        log.info("PROOF prompt text before Enter: ${onEdt { field.text }}")
        press(field, KeyEvent.VK_ENTER, 0)

        def before = onEdt { editor.document.text }
        for (i in 0..<600) {
            if (onEdt { editor.document.text } != before) break
            sleep 100
        }
        log.info("PROOF document after Claude:\n${onEdt { editor.document.text }}")
    } catch (Throwable e) {
        log.warn("PROOF driver failed", e)
    }
}
