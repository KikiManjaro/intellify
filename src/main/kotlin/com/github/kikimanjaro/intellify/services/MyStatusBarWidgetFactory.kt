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
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
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
                statusUpdaterThread = Thread(spotifyStatusUpdater, "Intellify-SpotifyStatusUpdater").apply {
                    isDaemon = true
                    start()
                }
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
                        // Returning null avoids the default ListPopup handling which uses unstable coordinates in New UI.
                        return null
                    }

                    /**
                     * Shows the Spotify popup anchored to the status bar widget instead of absolute mouse coordinates.
                     * Fixes #1: Window Position out of bounds when New UI (Beta) is enabled.
                     * Uses a static anchor (status bar component) + screen bounds clamping + JBUI scaling for HiDPI.
                     */
                    private fun showPopup(event: MouseEvent?, statusBar: StatusBar?) {
                        kotlin.runCatching {
                            val updater = spotifyStatusUpdater ?: return@runCatching
                            if (SpotifyService.code.isEmpty()) {
                                SpotifyService.getCodeFromBrowser()
                                return@runCatching
                            }
                            val spotifyPanel = SpotifyPanel(updater)
                            SpotifyService.currentPanel = spotifyPanel

                            // HiDPI-aware preferred size via JBUI.scale
                            val scaledWidth = JBUI.scale(spotifyPanel.customWidth.coerceAtLeast(200))
                            val scaledHeight = JBUI.scale(spotifyPanel.customHeight.coerceAtLeast(200))
                            spotifyPanel.preferredSize = Dimension(scaledWidth, scaledHeight + JBUI.scale(80))

                            val popup = JBPopupFactory.getInstance()
                                .createComponentPopupBuilder(spotifyPanel, spotifyPanel)
                                .setRequestFocus(true)
                                .setCancelOnClickOutside(true)
                                .setMovable(true)
                                .setResizable(false)
                                .setTitle("Intellify")
                                .createPopup()

                            // Static anchoring: prefer statusBar component (stable in New UI), fallback to mouse event
                            val anchorPoint: RelativePoint = when {
                                statusBar?.component != null -> {
                                    val comp = statusBar.component
                                    val compLocation = comp.locationOnScreen
                                    val compSize = comp.size
                                    val gap = JBUI.scale(8)
                                    val x = compLocation.x + compSize.width / 2
                                    val y = compLocation.y - gap
                                    val popupSize = spotifyPanel.preferredSize
                                    val clamped = clampToScreen(Point(x - popupSize.width / 2, y - popupSize.height), popupSize)
                                    RelativePoint(clamped)
                                }
                                event != null -> {
                                    val popupSize = spotifyPanel.preferredSize
                                    val raw = Point(event.locationOnScreen.x - popupSize.width / 2, event.locationOnScreen.y - popupSize.height - JBUI.scale(8))
                                    RelativePoint(clampToScreen(raw, popupSize))
                                }
                                else -> RelativePoint.getCenterOf(spotifyPanel)
                            }

                            popup.show(anchorPoint)
                        }.onFailure { e ->
                            e.printStackTrace()
                        }
                    }

                    private fun clampToScreen(point: Point, popupSize: Dimension): Point {
                        val screenBounds = getVisibleScreenBounds(point)
                        val margin = JBUI.scale(4)
                        var x = point.x.coerceIn(screenBounds.x + margin, screenBounds.x + screenBounds.width - popupSize.width - margin)
                        var y = point.y.coerceIn(screenBounds.y + margin, screenBounds.y + screenBounds.height - popupSize.height - margin)
                        if (x < screenBounds.x) x = screenBounds.x + margin
                        if (y < screenBounds.y) y = screenBounds.y + margin
                        return Point(x, y)
                    }

                    private fun getVisibleScreenBounds(point: Point): Rectangle {
                        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        for (device in env.screenDevices) {
                            for (config in device.configurations) {
                                val bounds = config.bounds
                                if (bounds.contains(point)) {
                                    return bounds
                                }
                            }
                        }
                        return env.maximumWindowBounds ?: Rectangle(0, 0, 1920, 1080)
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
