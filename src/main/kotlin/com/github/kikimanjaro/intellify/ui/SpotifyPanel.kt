package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyStatusUpdater
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.plaf.basic.BasicSliderUI

class SpotifyPanel(val spotifyStatusUpdater: SpotifyStatusUpdater) : JPanel(BorderLayout()) {
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
        val initialImage = loadAndScaleImage(SpotifyService.imageUrl)
        imageIcon = ImageIcon(initialImage)
        imageLabel = JLabel(imageIcon)

        artistNameLabel = JLabel(SpotifyService.artist.ifEmpty { "Unknown Artist" }, JLabel.CENTER)
        artistNameLabel.font = artistNameLabel.font.deriveFont(Font.BOLD, 14f)
        songNameLabel = JLabel(SpotifyService.song.ifEmpty { "No track" }, JLabel.CENTER)

        titlePanel = JPanel(BorderLayout())
        titlePanel.add(artistNameLabel, BorderLayout.NORTH)
        titlePanel.add(songNameLabel, BorderLayout.SOUTH)

        val buttonPanel = JPanel()
        buttonPanel.layout = BorderLayout()
        buttonPanel.isOpaque = false

        playPauseButton = JButton()
        playPauseButton.icon = if (SpotifyService.isPlaying) spotifyStatusUpdater.pauseIcon else spotifyStatusUpdater.playIcon
        playPauseButton.addActionListener {
            if (SpotifyService.isPlaying) SpotifyService.pauseTrack() else SpotifyService.startTrack()
            update()
        }
        prevButton = JButton(spotifyStatusUpdater.prevIcon)
        prevButton.addActionListener {
            SpotifyService.prevTrack()
            update()
        }
        nextButton = JButton(spotifyStatusUpdater.nextIcon)
        nextButton.addActionListener {
            SpotifyService.nextTrack()
            update()
        }

        val max = if (SpotifyService.durationMs > 0) SpotifyService.durationMs else 1
        slider = object : JSlider(0, max) {
            override fun updateUI() {
                setUI(CustomSliderUI(this))
            }
        }
        slider.border = BorderFactory.createEmptyBorder(6, 0, 4, 0)
        slider.value = SpotifyService.progressInMs.coerceIn(0, max)
        slider.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                val newVal = slider.value
                SpotifyService.setProgress(newVal)
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
    }

    fun update() {
        artistNameLabel.text = SpotifyService.artist.ifEmpty { "Unknown Artist" }
        songNameLabel.text = SpotifyService.song.ifEmpty { "No track" }
        titlePanel.repaint()

        val scaled = loadAndScaleImage(SpotifyService.imageUrl)
        if (scaled != null) {
            imageIcon.image = scaled
            imageLabel.repaint()
        }

        playPauseButton.icon = if (SpotifyService.isPlaying) spotifyStatusUpdater.pauseIcon else spotifyStatusUpdater.playIcon

        val max = if (SpotifyService.durationMs > 0) SpotifyService.durationMs else 1
        // Keep slider max in sync with track duration
        if (slider.maximum != max) slider.maximum = max
        slider.value = SpotifyService.progressInMs.coerceIn(0, max)
    }

    private fun loadAndScaleImage(url: String): Image? {
        if (url.isBlank()) return createPlaceholderImage()
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
        val text = "♪"
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
