package com.github.kikimanjaro.intellify.actions

import com.github.kikimanjaro.intellify.provider.MusicProviderRegistry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Keymap actions. They always talk to the *active* provider (see [MusicProviderRegistry]), never to a
 * concrete one, so they keep working whatever the platform.
 */
class TogglePlayPauseAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        MusicProviderRegistry.active().playPause()
    }
}

class PrevTrackAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        MusicProviderRegistry.active().previous()
    }
}

class NextTrackAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        MusicProviderRegistry.active().next()
    }
}

/** Signs out of the active provider (clears its stored credentials) — addresses issue #4 (Change Account). */
class ChangeAccountAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        MusicProviderRegistry.active().signOut()
    }
}
