package com.contextcondenser.services

import com.contextcondenser.python.PythonEnvironmentService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.contextcondenser.toolwindow.CondenserToolWindow

@Service(Service.Level.PROJECT)
class CondenserManager(private val project: Project) {

    private val log = thisLogger()
    private val gson = Gson()
    private val settings get() = CondenserSettings.getInstance().state

    @Volatile
    var toolWindow: CondenserToolWindow? = null

    /**
     * Resolves the python_adapter.py path from the installed plugin directory.
     * PyCharm installs the plugin to:
     *   ~/.local/share/JetBrains/PyCharm2026.1/<plugin-folder>/
     * The scripts/ folder is bundled inside that directory by buildPlugin.
     */
    private val adapterScriptPath: String by lazy {
        val pluginId = com.intellij.openapi.extensions.PluginId.getId("com.contextcondenser")
        val plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId)
        
        // This is the correct way to find bundled resources in 2026.1
        val path = plugin?.pluginPath?.resolve("resources")?.resolve("scripts")?.resolve("python_adapter.py")
            ?: plugin?.pluginPath?.resolve("scripts")?.resolve("python_adapter.py")

        val finalPath = path?.toAbsolutePath()?.toString()
        
        if (finalPath != null && java.io.File(finalPath).exists()) {
            log.info("Context Condenser: adapter path resolved to $finalPath")
            finalPath
        } else {
            // Fallback for development environments
            val devPath = "${System.getProperty("user.home")}/context-condenser/packages/jetbrains-plugin/src/main/resources/scripts/python_adapter.py"
            log.warn("Context Condenser: Plugin path not found, trying dev path: $devPath")
            devPath
        }
    }

    data class CondensationResult(
        val content: String,
        val rawTokens: Int,
        val condensedTokens: Int,
        val error: String? = null,
    ) {
        val savingsPercent: Int
            get() = if (rawTokens > 0) 100 - (condensedTokens * 100 / rawTokens) else 0
    }

    fun condenseContent(rawCode: String, tier: Int = settings.condensationTier): CondensationResult {
        if (rawCode.isBlank()) return CondensationResult("", 0, 0)
        val pythonExe = project.service<PythonEnvironmentService>().resolveExecutable()
        return try {
            val process = ProcessBuilder(pythonExe, adapterScriptPath, "--tier", tier.toString())
                .redirectErrorStream(false)
                .start()
            process.outputStream.bufferedWriter().use { it.write(rawCode) }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (stderr.isNotBlank()) log.warn("Adapter stderr: $stderr")
            parseAdapterOutput(stdout, rawCode)
        } catch (e: Exception) {
            log.error("Condensation failed", e)
            CondensationResult(
                rawCode, estimateTokens(rawCode), estimateTokens(rawCode),
                error = "Adapter error: ${e.message}"
            )
        }
    }

    fun setupEditorListener() {
        if (!settings.autoCondenseOnSwitch) return
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val file = event.newFile ?: return
                    if (file.extension != "py") return
                    triggerCondensationAsync(file)
                }
            }
        )
    }

    fun triggerCondensationAsync(file: VirtualFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Context Condenser: Analyzing ${file.name}", false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                // FIX: FileDocumentManager.getDocument() requires a read-action
                // when called from a background thread (PyCharm 2026.1 enforces this)
                val text = ReadAction.compute<String?, Throwable> {
                    FileDocumentManager.getInstance().getDocument(file)?.text
                } ?: return

                val result = condenseContent(text)
                toolWindow?.updateUI(result)
            }
        })
    }

    private fun parseAdapterOutput(stdout: String, fallback: String): CondensationResult {
        if (stdout.isBlank()) {
            return CondensationResult(
                fallback, estimateTokens(fallback), estimateTokens(fallback),
                error = "Adapter returned no output — check that python_adapter.py exists"
            )
        }
        return try {
            val json = gson.fromJson(stdout.trim(), Map::class.java)
            CondensationResult(
                content = json["content"] as? String ?: fallback,
                rawTokens = (json["rawTokens"] as? Double)?.toInt() ?: estimateTokens(fallback),
                condensedTokens = (json["condensedTokens"] as? Double)?.toInt()
                    ?: estimateTokens(fallback),
            )
        } catch (e: JsonSyntaxException) {
            log.warn("Adapter returned non-JSON: ${stdout.take(200)}")
            CondensationResult(
                content = stdout,
                rawTokens = estimateTokens(fallback),
                condensedTokens = estimateTokens(stdout),
            )
        }
    }

    private fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
