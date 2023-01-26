package com.github.kikimanjaro.intellify.services

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import javax.swing.Icon

class SpotifyStatusUpdater(
    private var statusBar: StatusBar?
) : Runnable {
    private var stop = false
    private val spotifyActiveIcon: Icon = IconLoader.getIcon("/icons/spotify.svg", this::class.java)
    private val spotifyInactiveIcon: Icon = IconLoader.getIcon("/icons/spotify-inactive.svg", this::class.java)
    val playIcon: Icon = IconLoader.getIcon("/icons/play.svg", this::class.java)
    val pauseIcon: Icon = IconLoader.getIcon("/icons/pause.svg", this::class.java)
    val nextIcon: Icon = IconLoader.getIcon("/icons/next.svg", this::class.java)
    val prevIcon: Icon = IconLoader.getIcon("/icons/prev.svg", this::class.java)
    val currentIcon: Icon
        get() = if (SpotifyService.title.isNotEmpty()) spotifyActiveIcon else spotifyInactiveIcon

    override fun run() {
        while (!stop) { //TODO: change with timer
            try {
                SpotifyService.getInformationAboutUsersCurrentPlayingTrack()
                updateUI()
                Thread.sleep(1000L)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateUI() {
        SpotifyService.currentPanel?.update()
        statusBar?.updateWidget("Intellify")
    }

    fun stop() {
        stop = true
    }
}