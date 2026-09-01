package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.ui.SpotifyPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon

class MyStatusBarWidgetFactory : StatusBarWidgetFactory {
    private var statusUpdaterThread: Thread? = null
    private var spotifyStatusUpdater: SpotifyStatusUpdater? = null
    private lateinit var intellifyWidget: StatusBarWidget
    private val name = "Intellify"

    override fun getId(): String = name

    override fun getDisplayName(): String = name

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        intellifyWidget = object : StatusBarWidget {

            override fun dispose() {
                spotifyStatusUpdater?.stop()
                statusUpdaterThread?.interrupt()
            }

            override fun ID(): String = name

            override fun install(statusBar: StatusBar) {
                spotifyStatusUpdater = SpotifyStatusUpdater(statusBar)
                statusUpdaterThread = Thread(spotifyStatusUpdater, "Intellify-SpotifyStatusUpdater")
                statusUpdaterThread!!.isDaemon = true
                statusUpdaterThread!!.start()
            }

            override fun getPresentation(): StatusBarWidget.WidgetPresentation {
                return object : StatusBarWidget.MultipleTextValuesPresentation {
                    override fun getTooltipText(): String = "Intellify - Click to open Spotify controls"

                    override fun getClickConsumer(): Consumer<MouseEvent>? {
                        return Consumer { event ->
                            showPopup(event, statusBar)
                        }
                    }

                    override fun getPopupStep(): ListPopup? {
                        // Popup is shown via clickConsumer to get MouseEvent for correct anchoring.
                        // Returning null here avoids the default ListPopup handling.
                        return null
                    }

                    private fun showPopup(event: MouseEvent?, statusBar: StatusBar?) {
                        kotlin.runCatching {
                            val updater = spotifyStatusUpdater ?: return@runCatching
                            // If not authenticated, trigger OAuth flow instead of empty popup
                            if (SpotifyService.code.isEmpty()) {
                                SpotifyService.getCodeFromBrowser()
                                return@runCatching
                            }
                            val spotifyPanel = SpotifyPanel(updater)
                            SpotifyService.currentPanel = spotifyPanel
                            val popup = JBPopupFactory.getInstance()
                                .createComponentPopupBuilder(spotifyPanel, spotifyPanel)
                                .setRequestFocus(true)
                                .setCancelOnClickOutside(true)
                                .setMovable(true)
                                .setResizable(false)
                                .setTitle("Intellify")
                                .createPopup()

                            // Anchor to the status bar widget / mouse event — fixes #1 (New UI out-of-bounds)
                            val relativePoint = when {
                                event != null -> RelativePoint(event)
                                statusBar?.component != null -> RelativePoint.getCenterOf(statusBar.component)
                                else -> RelativePoint.getCenterOf(spotifyPanel)
                            }
                            popup.show(relativePoint)
                        }.onFailure { e ->
                            e.printStackTrace()
                        }
                    }

                    override fun getSelectedValue(): String? {
                        return if (SpotifyService.title.isNotEmpty()) {
                            " " + SpotifyService.title
                        } else {
                            " No song playing"
                        }
                    }

                    override fun getIcon(): Icon {
                        return spotifyStatusUpdater?.currentIcon ?: IconLoader.getIcon(
                            "/icons/spotify-inactive.svg",
                            this::class.java
                        )
                    }
                }
            }
        }
        return intellifyWidget
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        spotifyStatusUpdater?.stop()
        statusUpdaterThread?.interrupt()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
