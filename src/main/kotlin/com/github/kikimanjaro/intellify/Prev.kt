package com.github.kikimanjaro.intellify

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.actionSystem.AnActionEvent

class Prev : AbstractSpotifyCommandAction() {

    override fun actionPerformed(e: AnActionEvent) {
        SpotifyService.prevTrack()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isActionActive
    }
}