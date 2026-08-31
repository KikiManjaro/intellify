# Intellify

[![Version](https://img.shields.io/jetbrains/plugin/v/20623)](https://plugins.jetbrains.com/plugin/20623-intellify)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/20623)](https://plugins.jetbrains.com/plugin/20623-intellify)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/20623)](https://plugins.jetbrains.com/plugin/20623-intellify)

![PopupScreenshot](https://user-images.githubusercontent.com/59285425/214960689-08ba6172-68f0-4408-8ed1-045e0830be0f.png)

<!-- Plugin description -->

Introducing the [Intellify plugin](https://plugins.jetbrains.com/plugin/20623-intellify) for [JetBrains IDEs](https://www.jetbrains.com/idea/)! This minimalistic Spotify integration allows you to seamlessly listen to your favorite tunes while coding. With the ability to display the currently playing track in the status bar and Prev, Play/Pause, Next buttons plus album cover in a popup, you'll never miss a beat. Say goodbye to switching tabs to change your music and hello to an integrated, uninterrupted workflow.

<!-- Plugin description end -->

## Features

- **Status bar widget** — shows the currently playing track (title + artist) with Spotify icon; indicates inactive state when nothing is playing.
- **Popup panel** — album cover, artist/song labels, playback progress bar (seekable), and Prev / Play-Pause / Next controls.
- **Keymap actions** — `Intellify Toggle Play`, `Intellify Previous Track`, `Intellify Next Track` (bind them in *Settings > Keymap*).
- **OAuth via Spotify Web API** — automatic token refresh, credentials stored securely via IntelliJ PasswordSafe.

## Requirements

- JetBrains IDE 2021.1+ (IC, IU, PyCharm, WebStorm, etc. — any `com.intellij.modules.platform` IDE).
- Spotify account (Free or Premium). Note: Spotify requires an active device for playback control.

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Intellify"</kbd> > <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/KikiManjaro/intellify/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

After installing, click the Spotify icon in the bottom status bar to authenticate via your browser (one-time OAuth flow on `http://localhost:30498/callback`).

## Usage

1. Click the **Intellify** widget in the status bar (bottom bar) to open the popup.
2. Use **Prev / Play-Pause / Next** or drag the progress bar to seek.
3. Optional: assign keyboard shortcuts via <kbd>Settings</kbd> > <kbd>Keymap</kbd> — search for *Intellify*.
4. To switch Spotify accounts, use *Help > Intellify > Change Account* (or clear stored credentials in *Settings > Appearance & Behavior > System Settings > Passwords*).

## Troubleshooting

- **No song showing / inactive icon**: ensure Spotify is playing on *any* device (desktop/mobile). The Web API only returns currently-playing state when a device is active.
- **Auth loop**: if the browser callback fails, ensure port `30498` is not blocked by a firewall.
- **Popup off-screen (New UI)**: fixed — popup now anchors to the status bar widget instead of the mouse cursor.
- **Do I need the Spotify app open?** No, but an active Spotify session on any device is required for playback state/control.

## Contributing

Contributions are very welcome! Please see our [contributing guidelines](CONTRIBUTING.md) and [code of conduct](CODE_OF_CONDUCT.md) to get started.

## Acknowledgements

### Icons

- All icons came from [Flaticon](https://www.flaticon.com/)
- Spotify icon belongs to [Spotify](https://www.spotify.com/)

### Code

- The code was inspired partially by [bearlysophisticated/spotify-idea-plugin](https://github.com/bearlysophisticated/spotify-idea-plugin).
- Uses [spotify-web-api-java](https://github.com/spotify-web-api-java/spotify-web-api-java)
- Heavily dependent on the JetBrains IntelliJ Platform SDK
- Based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

[![Buy Me a Coffee](https://img.buymeacoffee.com/api/?url=aHR0cHM6Ly9pbWcuYnV5bWVhY29mZmVlLmNvbS9hcGkvP3VybD1hSFIwY0hNNkx5OWpaRzR1WW5WNWJXVmhZMjltWm1WbExtTnZiUzkxY0d4dllXUnpMM0J5YjJacGJHVmZjR2xqZEhWeVpYTXZNakF5TVM4d015ODBZekkwT0RnNE1XWmxOVE5pWmprM1lUa3pOV1EyTWk1d2JtYz0mc2l6ZT0zMDAmbmFtZT1raWtpbWFuamFybw==&creator=kikimanjaro&is_creating=creating%20mobile%20apps%20and%20plugins&design_code=1&design_color=%23ff813f&slug=kikimanjaro)](https://www.buymeacoffee.com/kikimanjaro)
