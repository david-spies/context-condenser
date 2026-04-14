package com.contextcondenser.toolwindow

import com.contextcondenser.python.PythonEnvironmentService
import com.contextcondenser.services.CondenserManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CondenserToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val condenserToolWindow = CondenserToolWindow(project)

        val content = ContentFactory.getInstance()
            .createContent(condenserToolWindow.content, "", false)
        toolWindow.contentManager.addContent(content)

        // Wire tool window reference into the manager
        val manager = project.service<CondenserManager>()
        manager.toolWindow = condenserToolWindow

        // Subscribe to future file-switch events
        manager.setupEditorListener()

        // Immediately condense whatever file is currently open
        // This fires on first panel open so the user sees content right away
        val editorManager = FileEditorManager.getInstance(project)
        val currentFile = editorManager.selectedFiles.firstOrNull()
        if (currentFile != null && currentFile.extension == "py") {
            manager.triggerCondensationAsync(currentFile)
        } else {
            // No Python file open yet — show a helpful placeholder
            condenserToolWindow.showPlaceholder(
                "Open a .py file to see its condensed skeleton here."
            )
        }

        // Warn if tiktoken is missing (non-blocking)
        val pythonEnv = project.service<PythonEnvironmentService>()
        val env = pythonEnv.probe()
        if (!env.hasTiktoken) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Context Condenser")
                .createNotification(
                    "Context Condenser: tiktoken not found in ${env.executable}. " +
                    "Token counts will be estimated. " +
                    "Run Tools > Context Condenser: Setup Python Environment to install it.",
                    NotificationType.WARNING
                )
                .notify(project)
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
