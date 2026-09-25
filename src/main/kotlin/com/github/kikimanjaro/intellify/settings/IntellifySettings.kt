package com.github.kikimanjaro.intellify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-wide Intellify settings, persisted in `intellify.xml` inside the IDE config directory.
 *
 * The Spotify client credentials used to be hardcoded in a `Secret.kt` that was listed in
 * `.gitignore`, which meant a fresh clone could never compile. They now live here (or in the
 * environment, see [com.github.kikimanjaro.intellify.services.SpotifyCredentials]).
 *
 * No user password is ever stored here: OAuth tokens keep using [com.intellij.ide.passwordSafe.PasswordSafe].
 */
@State(name = "IntellifySettings", storages = [Storage("intellify.xml")])
class IntellifySettings : PersistentStateComponent<IntellifySettings.State> {

    class State {
        /**
         * Id of the active [com.github.kikimanjaro.intellify.provider.MusicProvider].
         * Empty means "the registry default", i.e. Spotify.
         */
        var providerId: String = ""

        /** Spotify Web API client id of the Spotify application the user created. */
        var spotifyClientId: String = ""

        /** Spotify Web API client secret of the same application. */
        var spotifyClientSecret: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        /**
         * The application service, or `null` when there is no running IDE application
         * (unit tests, tooling). Callers must handle `null` gracefully.
         */
        fun getInstance(): IntellifySettings? =
            runCatching { ApplicationManager.getApplication()?.getService(IntellifySettings::class.java) }
                .getOrNull()
    }
}
