package com.contextcondenser.services

import com.intellij.openapi.components.*

/**
 * Persisted settings for the Context Condenser plugin.
 * Stored in: <IDE config>/options/CondenserSettings.xml
 */
@State(
    name = "CondenserSettings",
    storages = [Storage("CondenserSettings.xml")]
)
@Service(Service.Level.APP)
class CondenserSettings : PersistentStateComponent<CondenserSettings.State> {

    data class State(
        var tokenModel: String = "cl100k_base",        // tiktoken encoding
        var condensationTier: Int = 2,                  // 1=comments, 2=structural, 3=AI-driven
        var includeDocstringSummary: Boolean = true,    // Keep first docstring line
        var autoCondenseOnSwitch: Boolean = true,       // Trigger on file switch
        var scopeMode: String = "file",                 // "file" or "project"
        var excludePatterns: String = "venv,__pycache__,.git,dist,build",
        var maxProjectTokens: Int = 8000,               // Hard cap for project-wide scan
        var showTokenBadge: Boolean = true,
        var enableSmartNavigation: Boolean = true,
        var preferredLLM: String = "claude",            // for prompt header formatting
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    companion object {
        fun getInstance(): CondenserSettings =
            service<CondenserSettings>()
    }
}
