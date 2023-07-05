package com.github.kikimanjaro.intellify.actions

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TogglePlayPauseAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        if (SpotifyService.isPlaying) {
            SpotifyService.pauseTrack()
        } else {
            SpotifyService.startTrack()
        }
    }
}

class PrevTrackAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SpotifyService.prevTrack()
    }
}

class NextTrackAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SpotifyService.nextTrack()
    }
}