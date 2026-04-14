package com.contextcondenser.actions

import com.contextcondenser.services.ProjectSkeletonService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Generates a full "Project Skeleton" — all Python files condensed
 * into a single Markdown document for architectural AI queries.
 *
 * Keyboard shortcut: Ctrl+Shift+Alt+P
 * Also available via: Tools > Generate Project Skeleton
 */
class ProjectSkeletonAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val skeletonService = project.service<ProjectSkeletonService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Context Condenser: Building Project Skeleton...", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val result = skeletonService.generateProjectSkeleton(indicator)

                com.intellij.openapi.application.ApplicationManager.getApplication()
                    .invokeLater {
                        ProjectSkeletonDialog(project, result).show()
                    }
            }
        })
    }
}

/**
 * Modal dialog showing the skeleton with a "Copy to Clipboard" button.
 * Allows the developer to review before sending to an LLM.
 */
class ProjectSkeletonDialog(
    project: com.intellij.openapi.project.Project,
    private val result: ProjectSkeletonService.ProjectSkeletonResult,
) : DialogWrapper(project) {

    private val textArea = JBTextArea(result.markdown).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = java.awt.Font("JetBrains Mono", java.awt.Font.PLAIN, 12)
    }

    init {
        title = "🧊 Project Skeleton — ${result.filesProcessed} files | " +
            "${result.totalCondensedTokens.fmt()} tokens | ${result.savingsPercent}% saved"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.preferredSize = Dimension(900, 600)

        val statsLabel = JBLabel(
            "  ${result.filesProcessed} files processed · " +
            "${result.totalRawTokens.fmt()} raw → " +
            "${result.totalCondensedTokens.fmt()} condensed tokens · " +
            "${result.savingsPercent}% saved" +
            if (result.filesTruncated > 0) " · ⚠️ ${result.filesTruncated} files omitted (budget)" else ""
        )

        panel.add(statsLabel, BorderLayout.NORTH)
        panel.add(JBScrollPane(textArea), BorderLayout.CENTER)
        return panel
    }

    override fun createActions() = arrayOf(
        object : DialogWrapperAction("Copy to Clipboard") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                CopyPasteManager.getInstance()
                    .setContents(StringSelection(result.markdown))
                close(OK_EXIT_CODE)
            }
        },
        cancelAction
    )

    private fun Int.fmt(): String = String.format("%,d", this)
}
