package com.github.kikimanjaro.intellify.settings

import com.github.kikimanjaro.intellify.provider.MusicProvider
import com.github.kikimanjaro.intellify.provider.MusicProviderRegistry
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * Settings > Tools > Intellify.
 *
 * Lets the user pick the active [MusicProvider] and, for Spotify, store the client credentials of
 * their own Spotify application — the values that used to live in the untracked `Secret.kt`.
 */
class IntellifyConfigurable : Configurable {

    private var rootPanel: JPanel? = null
    private val providerCombo = JComboBox<MusicProvider>()
    private val playerctlPlayerField = JBTextField()
    private val clientIdField = JBTextField()
    private val clientSecretField = JBPasswordField()
    private val statusLabel = JBLabel()

    override fun getDisplayName(): String = "Intellify"

    override fun getPreferredFocusedComponent(): JComponent = providerCombo

    override fun createComponent(): JComponent {
        var panel = rootPanel
        if (panel == null) {
            providerCombo.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean,
                ): Component =
                    super.getListCellRendererComponent(
                        list,
                        (value as? MusicProvider)?.displayName ?: "",
                        index,
                        isSelected,
                        cellHasFocus
                    )
            }
            providerCombo.addActionListener { updateStatus() }

            panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Music provider:", providerCombo)
                .addComponent(statusLabel)
                .addSeparator()
                .addLabeledComponent("playerctl player:", playerctlPlayerField)
                .addComponent(
                    JBLabel(
                        "<html>Only used by the playerctl provider on Linux; leave empty to use the " +
                            "first player reported by <b>playerctl --list-all</b>.</html>"
                    )
                )
                .addSeparator()
                .addLabeledComponent("Spotify client ID:", clientIdField)
                .addLabeledComponent("Spotify client secret:", clientSecretField)
                .addComponent(
                    JBLabel(
                        "<html>Only needed for the Spotify provider. Create an application on " +
                            "developer.spotify.com/dashboard, add " +
                            "<b>http://localhost:30498/callback</b> as a redirect URI and paste its " +
                            "client ID and secret here — or leave both empty and set " +
                            "INTELLIFY_SPOTIFY_CLIENT_ID / INTELLIFY_SPOTIFY_CLIENT_SECRET instead.</html>"
                    )
                )
                .addComponentFillVertically(JPanel(), 0)
                .panel
            rootPanel = panel
            reset()
        }
        return panel
    }

    override fun isModified(): Boolean {
        val state = IntellifySettings.getInstance()?.state ?: return false
        return (providerCombo.selectedItem as? MusicProvider)?.id.orEmpty() != state.providerId ||
            playerctlPlayerField.text.trim() != state.playerctlPlayer ||
            clientIdField.text.trim() != state.spotifyClientId ||
            String(clientSecretField.password) != state.spotifyClientSecret
    }

    override fun apply() {
        val settings = IntellifySettings.getInstance() ?: return
        settings.state.providerId = (providerCombo.selectedItem as? MusicProvider)?.id.orEmpty()
        settings.state.playerctlPlayer = playerctlPlayerField.text.trim()
        settings.state.spotifyClientId = clientIdField.text.trim()
        settings.state.spotifyClientSecret = String(clientSecretField.password)
        updateStatus()
    }

    override fun reset() {
        providerCombo.removeAllItems()
        MusicProviderRegistry.all().forEach { providerCombo.addItem(it) }
        val state = IntellifySettings.getInstance()?.state
        providerCombo.selectedItem = MusicProviderRegistry.byId(state?.providerId) ?: MusicProviderRegistry.active()
        playerctlPlayerField.text = state?.playerctlPlayer.orEmpty()
        clientIdField.text = state?.spotifyClientId.orEmpty()
        clientSecretField.text = state?.spotifyClientSecret.orEmpty()
        updateStatus()
    }

    override fun disposeUIResources() {
        rootPanel = null
    }

    private fun updateStatus() {
        val provider = providerCombo.selectedItem as? MusicProvider ?: MusicProviderRegistry.active()
        statusLabel.text = "<html>Active provider: <b>${provider.displayName}</b> — ${provider.describeStatus()}</html>"
    }
}
