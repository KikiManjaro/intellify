package com.github.kikimanjaro.intellify

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.IconLoader

/**
 * Created by rozsenichb on 10/02/2016.
 */
class PlayPause : AbstractSpotifyCommandAction() {
    var playIcon = IconLoader.getIcon("/icons/play.png", this::class.java)
    var pauseIcon = IconLoader.getIcon("/icons/pause.png", this::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        if (SpotifyService.isPlaying) {
            SpotifyService.pauseTrack()
        } else {
            SpotifyService.startTrack()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = active
        e.presentation.icon = if (SpotifyService.isPlaying) pauseIcon else playIcon
    }
}