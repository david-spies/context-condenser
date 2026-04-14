package com.contextcondenser.actions

import com.contextcondenser.services.CondenserManager
import com.contextcondenser.services.CondenserSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.awt.datatransfer.StringSelection

/**
 * Editor action: condenses the current Python file and copies a
 * well-formatted LLM prompt to the system clipboard.
 *
 * Keyboard shortcut: Ctrl+Shift+Alt+C
 * Also available via: right-click menu → "Send Condensed to AI"
 */
class SendToAIChatAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Only show the action for Python files
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file?.extension == "py"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val manager = project.service<CondenserManager>()
        val settings = CondenserSettings.getInstance().state

        val rawCode = editor.document.text
        val fileName = file.name

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Context Condenser: Preparing AI Prompt...", false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val result = manager.condenseContent(rawCode)

                val llmHint = when (settings.preferredLLM) {
                    "claude" -> "Never guess logic inside condensed blocks. " +
                        "Ask me for the full body of any function by name if you need it."
                    "gpt4"   -> "Treat '...' as implementation placeholders. " +
                        "Request specific function bodies if needed."
                    else     -> ""
                }

                val prompt = buildString {
                    appendLine("--- CONTEXT START: $fileName ---")
                    appendLine("Condensed with Context Condenser LVM. " +
                        "Bodies marked '...' are omitted to save tokens. $llmHint")
                    appendLine()
                    appendLine(result.content)
                    appendLine("--- CONTEXT END ---")
                    appendLine()
                    appendLine(
                        "Token budget: ${result.rawTokens} raw → " +
                        "${result.condensedTokens} condensed " +
                        "(${result.savingsPercent}% saved)"
                    )
                }

                CopyPasteManager.getInstance().setContents(StringSelection(prompt))

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Context Condenser")
                    .createNotification(
                        "Condensed context (${result.condensedTokens} tokens) copied to clipboard!",
                        NotificationType.INFORMATION
                    )
                    .notify(project)
            }
        })
    }
}
