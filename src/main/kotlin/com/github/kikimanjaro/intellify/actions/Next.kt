package com.github.kikimanjaro.intellify.actions

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.actionSystem.AnActionEvent

class Next : AbstractSpotifyAction() {

    override fun actionPerformed(e: AnActionEvent) {
        SpotifyService.nextTrack()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isActionActive
    }
}