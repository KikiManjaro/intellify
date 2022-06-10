package com.github.kikimanjaro.intellify.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon

class MyStatusBarWidgetFactory : StatusBarWidgetFactory {
    private var statusUpdaterThread: Thread? = null
    private var spotifyStatusUpdater: SpotifyStatusUpdater? = null

    private val name = "Intellify"

    override fun getId(): String {
        return name
    }

    override fun getDisplayName(): String {
        return name
    }

    override fun isAvailable(project: Project): Boolean {
        return true
    }

    override fun createWidget(project: Project): StatusBarWidget {
        val intellifyWidget = object : StatusBarWidget {

            override fun dispose() {
                spotifyStatusUpdater!!.stop()
                statusUpdaterThread!!.interrupt()
            }

            override fun ID(): String {
                return name
            }

            override fun install(statusBar: StatusBar) {
                spotifyStatusUpdater = SpotifyStatusUpdater(statusBar)
                statusUpdaterThread = Thread(spotifyStatusUpdater)
                statusUpdaterThread!!.start()
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

                    override fun getPopupStep(): ListPopup? {
                        val toolWindowManager = ToolWindowManager.getInstance(project)
                        val toolWindow = toolWindowManager.getToolWindow("Intellify")
                        toolWindow?.show(null)
                        return null
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
        spotifyStatusUpdater!!.stop()
        statusUpdaterThread!!.interrupt()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
}