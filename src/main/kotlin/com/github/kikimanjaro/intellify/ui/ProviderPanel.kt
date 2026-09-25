package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.provider.MusicProviderRegistry
import com.github.kikimanjaro.intellify.provider.ProviderCapability
import com.github.kikimanjaro.intellify.services.ProviderStatusUpdater
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.plaf.basic.BasicSliderUI

/**
 * Popup panel: artist/song labels, album cover, seekable progress bar and Prev / Play-Pause / Next.
 *
 * It is provider-agnostic: everything comes from the active provider through [MusicProviderRegistry],
 * and the controls a provider does not support (see [ProviderCapability]) are hidden instead of being
 * displayed but dead.
 */
class ProviderPanel(private val statusUpdater: ProviderStatusUpdater) : JPanel(BorderLayout()) {
    val customWidth = 200
    val customHeight = 200

    private val playPauseButton: JButton
    private val prevButton: JButton
    private val nextButton: JButton

    private val artistNameLabel: JLabel
    private val songNameLabel: JLabel
    private val imageIcon: ImageIcon
    private val imageLabel: JLabel
    private val titlePanel: JPanel

    private val slider: JSlider

    init {
        val initialTrack = MusicProviderRegistry.currentTrack
        imageIcon = ImageIcon(loadAndScaleImage(initialTrack?.artworkUrl))
        imageLabel = JLabel(imageIcon)

        artistNameLabel = JLabel(initialTrack?.artist?.ifBlank { null } ?: "Unknown Artist", JLabel.CENTER)
        artistNameLabel.font = artistNameLabel.font.deriveFont(Font.BOLD, 14f)
        songNameLabel = JLabel(initialTrack?.title?.ifBlank { null } ?: "No track", JLabel.CENTER)

        titlePanel = JPanel(BorderLayout())
        titlePanel.add(artistNameLabel, BorderLayout.NORTH)
        titlePanel.add(songNameLabel, BorderLayout.SOUTH)

        val buttonPanel = JPanel()
        buttonPanel.layout = BorderLayout()
        buttonPanel.isOpaque = false

        playPauseButton = JButton()
        playPauseButton.icon = if (initialTrack?.isPlaying == true) statusUpdater.pauseIcon else statusUpdater.playIcon
        playPauseButton.addActionListener {
            MusicProviderRegistry.active().playPause()
            update()
        }
        prevButton = JButton(statusUpdater.prevIcon)
        prevButton.addActionListener {
            MusicProviderRegistry.active().previous()
            update()
        }
        nextButton = JButton(statusUpdater.nextIcon)
        nextButton.addActionListener {
            MusicProviderRegistry.active().next()
            update()
        }

        val max = sliderMaximum(initialTrack?.durationMs)
        slider = object : JSlider(0, max) {
            override fun updateUI() {
                setUI(CustomSliderUI(this))
            }
        }
        slider.border = BorderFactory.createEmptyBorder(6, 0, 4, 0)
        slider.value = sliderValue(initialTrack?.positionMs, max)
        slider.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                val newVal = slider.value
                MusicProviderRegistry.active().seek(newVal.toLong())
                update()
                slider.value = newVal
            }
        })

        buttonPanel.add(prevButton, BorderLayout.WEST)
        buttonPanel.add(playPauseButton, BorderLayout.CENTER)
        buttonPanel.add(nextButton, BorderLayout.EAST)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(slider, BorderLayout.NORTH)
        bottomPanel.add(buttonPanel, BorderLayout.CENTER)

        add(titlePanel, BorderLayout.NORTH)
        add(imageLabel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        // Hide what the active provider cannot do. Spotify supports everything, so its panel is
        // exactly the one users already know.
        val capabilities = MusicProviderRegistry.active().capabilities
        buttonPanel.isVisible = ProviderCapability.CONTROL in capabilities
        slider.isVisible = ProviderCapability.SEEK in capabilities && ProviderCapability.POSITION in capabilities
        imageLabel.isVisible = ProviderCapability.ARTWORK in capabilities
    }

    fun update() {
        val track = MusicProviderRegistry.currentTrack
        artistNameLabel.text = track?.artist?.ifBlank { null } ?: "Unknown Artist"
        songNameLabel.text = track?.title?.ifBlank { null } ?: "No track"
        titlePanel.repaint()

        val scaled = loadAndScaleImage(track?.artworkUrl)
        if (scaled != null) {
            imageIcon.image = scaled
            imageLabel.repaint()
        }

        playPauseButton.icon = if (track?.isPlaying == true) statusUpdater.pauseIcon else statusUpdater.playIcon

        val max = sliderMaximum(track?.durationMs)
        // Keep slider max in sync with track duration
        if (slider.maximum != max) slider.maximum = max
        slider.value = sliderValue(track?.positionMs, max)
    }

    /** JSlider works on `Int`: clamp the (nullable) duration to something usable. */
    private fun sliderMaximum(durationMs: Long?): Int =
        (durationMs ?: 0L).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun sliderValue(positionMs: Long?, max: Int): Int =
        (positionMs ?: 0L).coerceIn(0L, max.toLong()).toInt()

    private fun loadAndScaleImage(url: String?): Image {
        if (url.isNullOrBlank()) return createPlaceholderImage()
        return try {
            val image: BufferedImage = ImageIO.read(URL(url)) ?: return createPlaceholderImage()
            image.getScaledInstance(customWidth, customHeight, Image.SCALE_SMOOTH)
        } catch (e: Exception) {
            createPlaceholderImage()
        }
    }

    private fun createPlaceholderImage(): Image {
        val img = BufferedImage(customWidth, customHeight, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(40, 40, 40)
        g.fillRect(0, 0, customWidth, customHeight)
        g.color = Color(29, 184, 84)
        g.font = g.font.deriveFont(Font.BOLD, 48f)
        val text = "\u266a"
        val fm = g.fontMetrics
        val x = (customWidth - fm.stringWidth(text)) / 2
        val y = (customHeight + fm.ascent) / 2 - 10
        g.drawString(text, x, y)
        g.dispose()
        return img
    }
}

private class CustomSliderUI(b: JSlider?) : BasicSliderUI(b) {
    private val trackShape = RoundRectangle2D.Float()
    override fun calculateTrackRect() {
        super.calculateTrackRect()
        if (isHorizontal) {
            trackRect.y = trackRect.y + (trackRect.height - TRACK_HEIGHT) / 2
            trackRect.height = TRACK_HEIGHT
        } else {
            trackRect.x = trackRect.x + (trackRect.width - TRACK_WIDTH) / 2
            trackRect.width = TRACK_WIDTH
        }
        trackShape.setRoundRect(
            trackRect.x.toFloat(),
            trackRect.y.toFloat(),
            trackRect.width.toFloat(),
            trackRect.height.toFloat(),
            TRACK_ARC.toFloat(),
            TRACK_ARC.toFloat()
        )
    }

    override fun calculateThumbLocation() {
        super.calculateThumbLocation()
        if (isHorizontal) {
            thumbRect.y = trackRect.y + (trackRect.height - thumbRect.height) / 2
        } else {
            thumbRect.x = trackRect.x + (trackRect.width - thumbRect.width) / 2
        }
    }

    override fun getThumbSize(): Dimension = THUMB_SIZE

    private val isHorizontal: Boolean
        get() = slider.orientation == JSlider.HORIZONTAL

    override fun paint(g: Graphics, c: JComponent) {
        (g as Graphics2D).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        super.paint(g, c)
    }

    override fun paintTrack(g: Graphics) {
        val g2 = g as Graphics2D
        val clip: Shape = g2.clip
        val horizontal = isHorizontal
        var inverted = slider.inverted

        g2.color = Color(170, 170, 170)
        g2.fill(trackShape)

        g2.color = Color(200, 200, 200)
        g2.clip = trackShape
        trackShape.y += 1f
        g2.fill(trackShape)
        trackShape.y = trackRect.y.toFloat()
        g2.clip = clip

        if (horizontal) {
            val ltr = slider.componentOrientation.isLeftToRight
            if (ltr) inverted = !inverted
            val thumbPos = thumbRect.x + thumbRect.width / 2
            if (inverted) g2.clipRect(0, 0, thumbPos, slider.height)
            else g2.clipRect(thumbPos, 0, slider.width - thumbPos, slider.height)
        } else {
            val thumbPos = thumbRect.y + thumbRect.height / 2
            if (inverted) g2.clipRect(0, 0, slider.height, thumbPos)
            else g2.clipRect(0, thumbPos, slider.width, slider.height - thumbPos)
        }
        g2.color = Color(29, 184, 84)
        g2.fill(trackShape)
        g2.clip = clip
    }

    override fun paintThumb(g: Graphics) {
        g.color = Color.WHITE
        g.fillOval(thumbRect.x + thumbRect.width / 4, thumbRect.y + thumbRect.height / 4, thumbRect.width / 2, thumbRect.height / 2)
    }

    override fun paintFocus(g: Graphics) {}

    companion object {
        private const val TRACK_HEIGHT = 8
        private const val TRACK_WIDTH = 8
        private const val TRACK_ARC = 10
        private val THUMB_SIZE: Dimension = Dimension(20, 20)
    }
}
