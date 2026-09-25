package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.provider.MusicProviderRegistry
import com.github.kikimanjaro.intellify.ui.ProviderPanel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import javax.swing.Icon

/**
 * Periodically asks the active provider for its state and refreshes the status bar and the popup
 * panel. Provider-agnostic: it only knows [MusicProviderRegistry].
 */
class ProviderStatusUpdater(
    private var statusBar: StatusBar?
) : Runnable {
    private val logger = Logger.getInstance(ProviderStatusUpdater::class.java)
    @Volatile private var stop = false

    /** Popup panel currently displayed, if any; refreshed on every tick. */
    @Volatile var panel: ProviderPanel? = null

    private val activeIcon: Icon = IconLoader.getIcon("/icons/spotify.svg", this::class.java)
    private val inactiveIcon: Icon = IconLoader.getIcon("/icons/spotify-inactive.svg", this::class.java)
    val playIcon: Icon = IconLoader.getIcon("/icons/play.svg", this::class.java)
    val pauseIcon: Icon = IconLoader.getIcon("/icons/pause.svg", this::class.java)
    val nextIcon: Icon = IconLoader.getIcon("/icons/next.svg", this::class.java)
    val prevIcon: Icon = IconLoader.getIcon("/icons/prev.svg", this::class.java)
    val currentIcon: Icon
        get() = if (MusicProviderRegistry.currentTrack != null) activeIcon else inactiveIcon

    override fun run() {
        while (!stop) {
            try {
                MusicProviderRegistry.refresh()
                updateUI()
                Thread.sleep(1000L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                logger.warn("Intellify status update failed", e)
            }
        }
    }

    private fun updateUI() {
        panel?.update()
        statusBar?.updateWidget("Intellify")
    }

    fun stop() {
        stop = true
    }
}
