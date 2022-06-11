package com.github.kikimanjaro.intellify

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.WindowManager
import javax.swing.JLabel
import javax.swing.SwingConstants

/**
 * Created by rozsenichb on 11/02/2016.
 */
class SpotifyPluginStatusBarComponent protected constructor(project: Project?) : AbstractProjectComponent(project),
    ProjectComponent {
    private val label = JLabel("", IconLoader.getIcon("/icons/spotify-inactive.png"), SwingConstants.LEADING)
    private var statusUpdaterThread: Thread? = null
    private var spotifyStatusUpdater: SpotifyStatusUpdater? = null
    private var playPauseAction: PlayPause? = null
    private var prevAction: Prev? = null
    private var nextAction: Next? = null

    override fun projectOpened() {
        WindowManager.getInstance().getStatusBar(myProject).addCustomIndicationComponent(label)
        statusUpdaterThread!!.start()
        SpotifyService.authorizationCodeUri_Async();
    }

    override fun projectClosed() {}
    override fun initComponent() {
        playPauseAction = ActionManager.getInstance().getAction("SpotifyPlugin.playpause") as PlayPause
        prevAction = ActionManager.getInstance().getAction("SpotifyPlugin.prev") as Prev
        nextAction = ActionManager.getInstance().getAction("SpotifyPlugin.next") as Next
        spotifyStatusUpdater = SpotifyStatusUpdater(label, playPauseAction, prevAction, nextAction)
        statusUpdaterThread = Thread(spotifyStatusUpdater)
    }

    override fun disposeComponent() {
        spotifyStatusUpdater!!.stop()
        statusUpdaterThread!!.interrupt()
    }

    override fun getComponentName(): String {
        return "SpotifyPlugin.StatusBarComponent"
    }
}