package com.contextcondenser.python

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager

/**
 * Resolves the Python interpreter for the current project.
 *
 * PyCharm 2026.1 (build 261) changed the PythonSdkUtil API signatures.
 * This implementation uses the stable ProjectRootManager + ProjectJdkTable
 * APIs which work across all IntelliJ platform versions.
 */
@Service(Service.Level.PROJECT)
class PythonEnvironmentService(private val project: Project) {

    private val log = thisLogger()

    data class PythonEnv(
        val executable: String,
        val version: String,
        val hasTiktoken: Boolean,
    )

    /**
     * Resolves Python executable using stable SDK APIs.
     * Priority: project SDK → any registered Python SDK → system python3
     */
    fun resolveExecutable(): String {
        return try {
            // 1. Try the project SDK first
            val projectSdk: Sdk? = ProjectRootManager.getInstance(project).projectSdk
            if (projectSdk != null && isPythonSdk(projectSdk)) {
                val path = projectSdk.homePath
                if (!path.isNullOrBlank()) {
                    log.info("Context Condenser: using project SDK at $path")
                    return path
                }
            }

            // 2. Fall back to any registered Python SDK in the IDE SDK table
            val pythonSdk: Sdk? = ProjectJdkTable.getInstance().allJdks
                .firstOrNull { isPythonSdk(it) }
            if (pythonSdk != null) {
                val path = pythonSdk.homePath
                if (!path.isNullOrBlank()) {
                    log.info("Context Condenser: using SDK table entry at $path")
                    return path
                }
            }

            // 3. Fall back to system python3
            log.warn("Context Condenser: no Python SDK found, falling back to python3")
            "python3"
        } catch (e: Exception) {
            log.warn("Context Condenser: SDK resolution error: ${e.message}")
            "python3"
        }
    }

    /**
     * Checks if an SDK is a Python interpreter by looking at the SDK type name.
     * Avoids importing PythonSdkType directly to stay compatible across versions.
     */
    private fun isPythonSdk(sdk: Sdk): Boolean {
        val typeName = sdk.sdkType.name.lowercase()
        return typeName.contains("python")
    }

    /**
     * Tests whether tiktoken is importable in the resolved interpreter.
     */
    fun hasTiktoken(executable: String): Boolean {
        return try {
            ProcessBuilder(executable, "-c", "import tiktoken")
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Installs tiktoken in the background using pip.
     * Shows a balloon notification on success or failure.
     */
    fun installTiktokenAsync() {
        val executable = resolveExecutable()
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Context Condenser: Installing tiktoken...", false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val process = ProcessBuilder(
                        executable, "-m", "pip", "install", "tiktoken", "--quiet"
                    ).redirectErrorStream(true).start()

                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()

                    val (type, msg) = if (exitCode == 0) {
                        NotificationType.INFORMATION to
                            "tiktoken installed. Context Condenser token counts are now exact."
                    } else {
                        NotificationType.ERROR to
                            "tiktoken install failed:\n$output"
                    }

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Context Condenser")
                        .createNotification(msg, type)
                        .notify(project)

                } catch (e: Exception) {
                    log.error("tiktoken install error", e)
                }
            }
        })
    }

    fun probe(): PythonEnv {
        val exe = resolveExecutable()
        val version = runCatching {
            ProcessBuilder(exe, "--version")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().readText().trim()
        }.getOrDefault("unknown")
        return PythonEnv(exe, version, hasTiktoken(exe))
    }
}
