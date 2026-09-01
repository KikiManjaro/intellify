package com.github.kikimanjaro.intellify.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.*


class MyStatusBarWidgetFactory : StatusBarWidgetFactory {
    private val logger = Logger.getInstance(MyStatusBarWidgetFactory::class.java)
    private var statusUpdaterThread: Thread? = null
    private var spotifyStatusUpdater: SpotifyStatusUpdater? = null
    private lateinit var intellifyWidget: StatusBarWidget
    private val name = "Intellify"

    override fun getId(): String {
        return name
    }

    override fun getDisplayName(): String {
        return name
    }

    override fun isAvailable(project: com.intellij.openapi.project.Project): Boolean {
        return true
    }

    override fun createWidget(project: com.intellij.openapi.project.Project): StatusBarWidget {
        intellifyWidget = object : StatusBarWidget {

            override fun dispose() {
                spotifyStatusUpdater?.stop()
                statusUpdaterThread?.interrupt()
            }

            override fun ID(): String {
                return name
            }

            override fun install(statusBar: StatusBar) {
                spotifyStatusUpdater = SpotifyStatusUpdater(statusBar)
                statusUpdaterThread = Thread(spotifyStatusUpdater).apply {
                    isDaemon = true
                    name = "Intellify-Spotify-Updater"
                    start()
                }
            }

            override fun getPresentation(): StatusBarWidget.WidgetPresentation? {
                return object : StatusBarWidget.MultipleTextValuesPresentation {
                    override fun getTooltipText(): String? {
                        return "Intellify"
                    }

                    override fun getClickConsumer(): Consumer<MouseEvent>? {
                        return Consumer {
                            SpotifyService.getCodeFromBrowser()
                        }
                    }

                    override fun getPopupStep(): com.intellij.openapi.ui.popup.ListPopup? {
                        return runCatching {
                            val spotifyPanel = SpotifyPanel(spotifyStatusUpdater ?: return@runCatching null)
                                ?: return@runCatching null
                            SpotifyService.currentPanel = spotifyPanel
                            val popup =
                                com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().createComponentPopupBuilder(spotifyPanel, spotifyPanel)
                                    .setRequestFocus(true)
                                    .setCancelOnClickOutside(true)
                                    .createPopup()
                            val mouseX = MouseInfo.getPointerInfo().location.getX()
                            val mouseY = MouseInfo.getPointerInfo().location.getY()
                            popup.show(RelativePoint(Point(mouseX.toInt(), mouseY.toInt())))
                            popup.setLocation(
                                Point(
                                    (mouseX - spotifyPanel.width / 2).toInt(),
                                    (mouseY - spotifyPanel.height * 1.05).toInt()
                                )
                            )
                        }.onFailure { e ->
                            logger.warn("Failed to show Intellify popup", e)
                        }.getOrNull()
                    }

                    override fun getSelectedValue(): String? {
                        return SpotifyService.title.isNotEmpty().let {
                            if (it) {
                                " " + SpotifyService.title
                            } else {
                                " No song playing"
                            }
                        }
                    }

                    override fun getIcon(): Icon? {
                        return spotifyStatusUpdater?.currentIcon ?: com.intellij.openapi.util.IconLoader.getIcon(
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
        spotifyStatusUpdater = null
        statusUpdaterThread = null
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
}
