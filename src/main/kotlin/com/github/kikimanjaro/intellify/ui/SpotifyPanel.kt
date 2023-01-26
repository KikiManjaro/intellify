package com.github.kikimanjaro.intellify.ui

import com.github.kikimanjaro.intellify.services.SpotifyService
import com.github.kikimanjaro.intellify.services.SpotifyStatusUpdater
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Image
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel


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

    init {
        val image: BufferedImage = ImageIO.read(URL(SpotifyService.imageUrl))
        val scaledImage = image.getScaledInstance(customWidth, customHeight, Image.SCALE_SMOOTH)

        imageIcon = ImageIcon(scaledImage)
        imageLabel = JLabel(imageIcon)

        artistNameLabel = JLabel(SpotifyService.artist, JLabel.CENTER)
        artistNameLabel.setFont(artistNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        songNameLabel = JLabel(SpotifyService.song, JLabel.CENTER)

        titlePanel = JPanel(BorderLayout())
        titlePanel.add(artistNameLabel, BorderLayout.NORTH)
        titlePanel.add(songNameLabel, BorderLayout.SOUTH)

        val buttonPanel = JPanel()
        buttonPanel.layout = BorderLayout()
        buttonPanel.isOpaque = false

        playPauseButton = JButton()
        if (SpotifyService.isPlaying) {
            playPauseButton.icon = spotifyStatusUpdater.pauseIcon
        } else {
            playPauseButton.icon = spotifyStatusUpdater.playIcon
        }
        playPauseButton.addActionListener {
            if (SpotifyService.isPlaying) {
                SpotifyService.pauseTrack()
            } else {
                SpotifyService.startTrack()
            }
        }
        prevButton = JButton(spotifyStatusUpdater.prevIcon)
        prevButton.addActionListener {
            SpotifyService.prevTrack()
        }
        nextButton = JButton(spotifyStatusUpdater.nextIcon)
        nextButton.addActionListener {
            SpotifyService.nextTrack()
        }

        // Add the buttons to the button panel
        buttonPanel.add(prevButton, BorderLayout.WEST)
        buttonPanel.add(playPauseButton, BorderLayout.CENTER)
        buttonPanel.add(nextButton, BorderLayout.EAST)

        add(titlePanel, BorderLayout.NORTH)
        add(imageLabel, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    fun update() {
        artistNameLabel.text = SpotifyService.artist
        songNameLabel.text = SpotifyService.song
        titlePanel.repaint()

        val image: BufferedImage = ImageIO.read(URL(SpotifyService.imageUrl))
        val scaledImage = image.getScaledInstance(customWidth, customHeight, Image.SCALE_SMOOTH)

        imageIcon.image = scaledImage
        imageLabel.repaint()

        if (SpotifyService.isPlaying) {
            playPauseButton.icon = spotifyStatusUpdater.pauseIcon
        } else {
            playPauseButton.icon = spotifyStatusUpdater.playIcon
        }
    }
}