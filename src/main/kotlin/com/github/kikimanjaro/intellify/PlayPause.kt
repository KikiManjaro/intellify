package com.github.kikimanjaro.intellify

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.IconLoader

class PlayPause : AbstractSpotifyCommandAction() {
    var playIcon = IconLoader.getIcon("/icons/play.svg", this::class.java)
    var pauseIcon = IconLoader.getIcon("/icons/pause.svg", this::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        if (SpotifyService.isPlaying) {
            SpotifyService.pauseTrack()
        } else {
            SpotifyService.startTrack()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isActionActive
        e.presentation.icon = if (SpotifyService.isPlaying) pauseIcon else playIcon
    }
}