package com.contextcondenser.toolwindow

import com.contextcondenser.services.CondenserManager
import com.contextcondenser.services.CondenserSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class CondenserToolWindow(private val project: Project) {

    val content = JPanel(BorderLayout())
    private val settings get() = CondenserSettings.getInstance().state

    // Syntax-highlighted read-only editor using Python file type
    private val pythonFileType = FileTypeManager.getInstance().getFileTypeByExtension("py")
    private val editorField = EditorTextField("", project, pythonFileType).apply {
        setOneLineMode(false)
        isViewer = true
        addSettingsProvider { editor ->
            editor.settings.isLineNumbersShown = true
            editor.settings.isFoldingOutlineShown = true
            editor.colorsScheme.editorFontName =
                EditorColorsManager.getInstance().globalScheme
                    .getFont(EditorFontType.PLAIN).fontName
            if (settings.enableSmartNavigation) {
                editor.addEditorMouseListener(SmartNavigationListener(editor, project))
            }
        }
    }

    // Header controls
    private val scopeCombo = ComboBox(arrayOf("Current File", "Full Project")).apply {
        toolTipText = "Switch between per-file and project-wide skeletonization"
    }

    private val refreshButton = JButton("\u21BB Refresh").apply {
        toolTipText = "Re-condense the active Python file now"
        addActionListener { triggerRefresh() }
    }

    private val copyButton = JButton("\u2398 Copy Prompt").apply {
        toolTipText = "Copy a formatted LLM prompt to clipboard (Ctrl+Shift+Alt+C)"
        addActionListener { copyPromptToClipboard() }
    }

    // Footer token badge
    private val tokenLabel = JBLabel("Open a .py file to see its condensed skeleton.", JBLabel.RIGHT)

    init {
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            border = JBUI.Borders.emptyBottom(2)
            add(JBLabel("Scope:"))
            add(scopeCombo)
            add(refreshButton)
            add(copyButton)
        }

        val footer = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
            add(tokenLabel, BorderLayout.EAST)
        }

        // Wrap in JBScrollPane so mouse wheel scrolling works
        val scrollPane = JBScrollPane(editorField).apply {
            border = JBUI.Borders.empty()
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        content.add(header, BorderLayout.NORTH)
        content.add(scrollPane, BorderLayout.CENTER)
        content.add(footer, BorderLayout.SOUTH)

        scopeCombo.addActionListener { onScopeChanged() }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    fun updateUI(result: CondenserManager.CondensationResult) {
        ApplicationManager.getApplication().invokeLater {
            editorField.text = result.content

            val savingColor = when {
                result.error != null      -> JBUI.CurrentTheme.Label.disabledForeground()
                result.savingsPercent >= 50 -> Color(0x4CAF50)   // green — good savings
                result.savingsPercent >= 20 -> JBUI.CurrentTheme.Label.foreground()
                else                       -> JBUI.CurrentTheme.Label.disabledForeground()
            }

            tokenLabel.foreground = savingColor
            tokenLabel.text = when {
                result.error != null -> "\u26A0 ${result.error}"
                settings.showTokenBadge ->
                    "Tokens: ${result.rawTokens.fmt()} raw \u2192 " +
                    "${result.condensedTokens.fmt()} condensed  " +
                    "(${result.savingsPercent}% saved)"
                else -> ""
            }
        }
    }

    fun showPlaceholder(message: String) {
        ApplicationManager.getApplication().invokeLater {
            editorField.text = "# $message"
            tokenLabel.text = message
            tokenLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }
    }

    fun updateWithMarkdown(markdown: String, rawTokens: Int, condensedTokens: Int) {
        updateUI(CondenserManager.CondensationResult(markdown, rawTokens, condensedTokens))
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun triggerRefresh() {
        val manager = project.service<CondenserManager>()
        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        when {
            selected == null           -> showPlaceholder("No file open. Open a .py file first.")
            selected.extension == "py" -> manager.triggerCondensationAsync(selected)
            else                       -> showPlaceholder("Not a Python file: ${selected.name}")
        }
    }

    private fun onScopeChanged() {
        if (scopeCombo.selectedItem == "Full Project") {
            val skeletonService = project.service<
                com.contextcondenser.services.ProjectSkeletonService>()
            com.intellij.openapi.progress.ProgressManager.getInstance().run(
                object : com.intellij.openapi.progress.Task.Backgroundable(
                    project, "Context Condenser: Building Project Skeleton", false
                ) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        val result = skeletonService.generateProjectSkeleton(indicator)
                        updateWithMarkdown(
                            result.markdown,
                            result.totalRawTokens,
                            result.totalCondensedTokens
                        )
                    }
                }
            )
        } else {
            triggerRefresh()
        }
    }

    private fun copyPromptToClipboard() {
        val condensed = editorField.text
        if (condensed.isBlank() || condensed.startsWith("# ")) {
            showPlaceholder("Nothing to copy yet — open a .py file first.")
            return
        }
        val header = when (settings.preferredLLM) {
            "claude" -> "I am using Context Condenser LVM. Code marked '...' is condensed. " +
                "Ask me for the full body of any function by name if needed. " +
                "Never guess logic inside condensed blocks.\n\n"
            "gpt4"   -> "Context Condenser LVM skeleton. Treat '...' as implementation placeholders. " +
                "Request specific function bodies if needed.\n\n"
            else     -> "Context Condenser LVM skeleton below. Ask for full bodies by name.\n\n"
        }
        val prompt = "--- CONTEXT START ---\n${header}${condensed}\n--- CONTEXT END ---"
        com.intellij.openapi.ide.CopyPasteManager.getInstance()
            .setContents(java.awt.datatransfer.StringSelection(prompt))
        tokenLabel.text = "\u2705 Prompt copied to clipboard!"
    }

    private fun Int.fmt(): String = String.format("%,d", this)
}

// ── Smart Navigation — opens the source file and jumps to the symbol ──────────
// Uses VFS text search instead of Java PSI to avoid the com.intellij.java
// plugin dependency, making it work in both PyCharm and IntelliJ Community.

private class SmartNavigationListener(
    private val editor: com.intellij.openapi.editor.Editor,
    private val project: Project,
) : EditorMouseListener {

    override fun mouseClicked(e: EditorMouseEvent) {
        if (e.mouseEvent.clickCount != 2) return

        val logicalPos = editor.xyToLogicalPosition(e.mouseEvent.point)
        val lines = editor.document.text.split("\n")
        if (logicalPos.line >= lines.size) return

        val line = lines[logicalPos.line].trim()
        val symbolName = extractSymbolName(line) ?: return

        // Search open editors for the symbol, then fall back to project files
        navigateInOpenEditors(symbolName) || navigateInProjectFiles(symbolName)
    }

    private fun extractSymbolName(line: String): String? = when {
        line.startsWith("def ")       -> line.removePrefix("def ").substringBefore("(").trim()
        line.startsWith("async def ") -> line.removePrefix("async def ").substringBefore("(").trim()
        line.startsWith("class ")     -> line.removePrefix("class ")
            .substringBefore("(").substringBefore(":").trim()
        else -> null
    }

    /** Try to navigate within already-open editors first (fast path). */
    private fun navigateInOpenEditors(name: String): Boolean {
        val fem = FileEditorManager.getInstance(project)
        for (file in fem.openFiles) {
            if (file.extension != "py") continue
            val doc = com.intellij.openapi.application.ReadAction.compute<
                com.intellij.openapi.editor.Document?, Throwable> {
                com.intellij.openapi.fileEditor.FileDocumentManager
                    .getInstance().getDocument(file)
            } ?: continue
            val line = findSymbolLine(doc.text, name) ?: continue
            ApplicationManager.getApplication().invokeLater {
                fem.openFile(file, true)
                val editors = fem.getEditors(file)
                val textEditor = editors.filterIsInstance<
                    com.intellij.openapi.fileEditor.TextEditor>().firstOrNull()
                textEditor?.editor?.caretModel?.moveToLogicalPosition(
                    com.intellij.openapi.editor.LogicalPosition(line, 0)
                )
                textEditor?.editor?.scrollingModel?.scrollToCaret(
                    com.intellij.openapi.editor.ScrollType.CENTER
                )
            }
            return true
        }
        return false
    }

    /** Walk project source files to find the symbol definition. */
    private fun navigateInProjectFiles(name: String): Boolean {
        val roots = com.intellij.openapi.roots.ProjectRootManager
            .getInstance(project).contentSourceRoots
        for (root in roots) {
            val found = findInDirectory(root, name)
            if (found) return true
        }
        return false
    }

    private fun findInDirectory(dir: VirtualFile, name: String): Boolean {
        for (child in dir.children) {
            if (child.isDirectory) {
                if (findInDirectory(child, name)) return true
            } else if (child.extension == "py") {
                val text = com.intellij.openapi.application.ReadAction.compute<
                    String?, Throwable> {
                    com.intellij.openapi.fileEditor.FileDocumentManager
                        .getInstance().getDocument(child)?.text
                } ?: continue
                val line = findSymbolLine(text, name) ?: continue
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).openFile(child, true)
                    val editors = FileEditorManager.getInstance(project).getEditors(child)
                    val textEditor = editors.filterIsInstance<
                        com.intellij.openapi.fileEditor.TextEditor>().firstOrNull()
                    textEditor?.editor?.caretModel?.moveToLogicalPosition(
                        com.intellij.openapi.editor.LogicalPosition(line, 0)
                    )
                    textEditor?.editor?.scrollingModel?.scrollToCaret(
                        com.intellij.openapi.editor.ScrollType.CENTER
                    )
                }
                return true
            }
        }
        return false
    }

    private fun findSymbolLine(text: String, name: String): Int? {
        val patterns = listOf("def $name(", "async def $name(", "class $name(", "class $name:")
        return text.lines().indexOfFirst { line ->
            patterns.any { pattern -> line.trimStart().startsWith(pattern) }
        }.takeIf { it >= 0 }
    }
}
