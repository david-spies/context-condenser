package com.contextcondenser.util

import com.contextcondenser.services.CondenserSettings
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JComboBox

/**
 * Settings panel: Tools > Context Condenser
 */
class CondenserConfigurable : Configurable {

    private lateinit var panel: JPanel
    private lateinit var condensationTierSlider: JSlider
    private lateinit var autoCondenseCheckbox: JBCheckBox
    private lateinit var showTokenBadgeCheckbox: JBCheckBox
    private lateinit var smartNavCheckbox: JBCheckBox
    private lateinit var preferredLLMCombo: JComboBox<String>
    private lateinit var excludePatternsField: JBTextField
    private lateinit var maxProjectTokensField: JBTextField
    private lateinit var includeDocstringCheckbox: JBCheckBox

    override fun getDisplayName() = "Context Condenser"

    override fun createComponent(): JComponent {
        condensationTierSlider = JSlider(1, 3, 2).apply {
            majorTickSpacing = 1
            paintTicks = true
            paintLabels = true
            snapToTicks = true
            labelTable = java.util.Hashtable<Int, JBLabel>().apply {
                put(1, JBLabel("Comments"))
                put(2, JBLabel("Structural"))
                put(3, JBLabel("AI-Driven"))
            }
        }

        autoCondenseCheckbox = JBCheckBox("Auto-condense on file switch")
        showTokenBadgeCheckbox = JBCheckBox("Show token budget badge")
        smartNavCheckbox = JBCheckBox("Enable Smart Navigation (double-click to source)")
        includeDocstringCheckbox = JBCheckBox("Preserve first docstring line in skeletons")
        preferredLLMCombo = JComboBox(arrayOf("claude", "gpt4", "gemini"))
        excludePatternsField = JBTextField()
        maxProjectTokensField = JBTextField()

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Condensation tier:", condensationTierSlider)
            .addSeparator()
            .addComponent(autoCondenseCheckbox)
            .addComponent(showTokenBadgeCheckbox)
            .addComponent(smartNavCheckbox)
            .addComponent(includeDocstringCheckbox)
            .addSeparator()
            .addLabeledComponent("Preferred LLM:", preferredLLMCombo)
            .addLabeledComponent(
                "Exclude patterns (comma-separated):", excludePatternsField
            )
            .addLabeledComponent(
                "Max project tokens (project-wide mode):", maxProjectTokensField
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = CondenserSettings.getInstance().state
        return condensationTierSlider.value != s.condensationTier ||
            autoCondenseCheckbox.isSelected != s.autoCondenseOnSwitch ||
            showTokenBadgeCheckbox.isSelected != s.showTokenBadge ||
            smartNavCheckbox.isSelected != s.enableSmartNavigation ||
            includeDocstringCheckbox.isSelected != s.includeDocstringSummary ||
            preferredLLMCombo.selectedItem != s.preferredLLM ||
            excludePatternsField.text != s.excludePatterns ||
            maxProjectTokensField.text != s.maxProjectTokens.toString()
    }

    override fun apply() {
        val s = CondenserSettings.getInstance().state
        s.condensationTier = condensationTierSlider.value
        s.autoCondenseOnSwitch = autoCondenseCheckbox.isSelected
        s.showTokenBadge = showTokenBadgeCheckbox.isSelected
        s.enableSmartNavigation = smartNavCheckbox.isSelected
        s.includeDocstringSummary = includeDocstringCheckbox.isSelected
        s.preferredLLM = preferredLLMCombo.selectedItem as String
        s.excludePatterns = excludePatternsField.text
        s.maxProjectTokens = maxProjectTokensField.text.toIntOrNull() ?: 8000
    }

    override fun reset() {
        val s = CondenserSettings.getInstance().state
        condensationTierSlider.value = s.condensationTier
        autoCondenseCheckbox.isSelected = s.autoCondenseOnSwitch
        showTokenBadgeCheckbox.isSelected = s.showTokenBadge
        smartNavCheckbox.isSelected = s.enableSmartNavigation
        includeDocstringCheckbox.isSelected = s.includeDocstringSummary
        preferredLLMCombo.selectedItem = s.preferredLLM
        excludePatternsField.text = s.excludePatterns
        maxProjectTokensField.text = s.maxProjectTokens.toString()
    }
}
