package com.contextcondenser.actions

import com.contextcondenser.python.PythonEnvironmentService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

/**
 * Tools menu action: installs tiktoken into the project's Python interpreter.
 * Shows a non-blocking balloon notification on completion.
 */
class SetupEnvironmentAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<PythonEnvironmentService>().installTiktokenAsync()
    }
}
