package com.github.kikimanjaro.intellify

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import javax.swing.Icon

/**
 * Created by rozsenichb on 11/02/2016.
 */
class SpotifyStatusUpdater(
    private var statusBar: StatusBar?
) : Runnable {
    private var stop = false
    private val spotifyActiveIcon: Icon = IconLoader.getIcon("/icons/spotify.png", this::class.java)
    private val spotifyInactiveIcon: Icon = IconLoader.getIcon("/icons/spotify-inactive.png", this::class.java)
    val currentIcon: Icon
        get() = if (SpotifyService.title.isNotEmpty()) spotifyActiveIcon else spotifyInactiveIcon

    private var playPauseAction: PlayPause? =
        ActionManager.getInstance().getAction("SpotifyPlugin.playpause") as PlayPause
    private var prevAction: Prev? = ActionManager.getInstance().getAction("SpotifyPlugin.prev") as Prev
    private var nextAction: Next? = ActionManager.getInstance().getAction("SpotifyPlugin.next") as Next

    override fun run() {
        while (!stop) {
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
        playPauseAction?.setActive(SpotifyService.title.isNotEmpty())
        prevAction?.setActive(SpotifyService.title.isNotEmpty())
        nextAction?.setActive(SpotifyService.title.isNotEmpty())
        statusBar?.updateWidget("Intellify")
    }

    fun stop() {
        stop = true
    }
}