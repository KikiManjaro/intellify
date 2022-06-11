package com.github.kikimanjaro.intellify

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon
import javax.swing.JLabel

/**
 * Created by rozsenichb on 11/02/2016.
 */
class SpotifyStatusUpdater(
    private val label: JLabel?,
    private val playPauseAction: PlayPause?,
    private val prevAction: Prev?,
    private val nextAction: Next?
) : Runnable {
    private var stop = false
    private val spotifyActiveIcon: Icon = IconLoader.getIcon("/icons/spotify.png", this::class.java)
    private val spotifyInactiveIcon: Icon = IconLoader.getIcon("/icons/spotify-inactive.png", this::class.java)

    override fun run() {
        while (!stop) {
            SpotifyService.authorizationCodeRefresh()
            SpotifyService.getInformationAboutUsersCurrentPlayingTrack()
            updateUI()
            try {
                Thread.sleep(1000L)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun updateUI() {
        label?.text = SpotifyService.title
        label?.icon = if (SpotifyService.isPlaying) spotifyActiveIcon else spotifyInactiveIcon
        playPauseAction?.setActive(SpotifyService.title.isNotEmpty())
        prevAction?.setActive(SpotifyService.title.isNotEmpty())
        nextAction?.setActive(SpotifyService.title.isNotEmpty())
    }

    fun stop() {
        stop = true
    }
}