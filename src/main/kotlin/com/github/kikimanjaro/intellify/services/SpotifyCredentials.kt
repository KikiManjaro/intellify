package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.settings.IntellifySettings

/**
 * Source of the Spotify Web API client credentials.
 *
 * Resolution order, first non-blank value wins:
 *  1. the plugin settings ([com.github.kikimanjaro.intellify.settings.IntellifySettings], stored in `intellify.xml`),
 *  2. the environment variables [CLIENT_ID_ENV] / [CLIENT_SECRET_ENV].
 *
 * Nothing is hardcoded and nothing credential-like is committed: a fresh clone builds
 * and runs with no local file. When neither source provides a value, [isConfigured] is
 * `false` and the UI degrades gracefully instead of throwing.
 */
object SpotifyCredentials {
    /** Environment variable holding the Spotify client id. */
    const val CLIENT_ID_ENV: String = "INTELLIFY_SPOTIFY_CLIENT_ID"

    /** Environment variable holding the Spotify client secret. */
    const val CLIENT_SECRET_ENV: String = "INTELLIFY_SPOTIFY_CLIENT_SECRET"

    val clientId: String?
        get() = resolveCredential(
            IntellifySettings.getInstance()?.state?.spotifyClientId,
            System.getenv(CLIENT_ID_ENV)
        )

    val clientSecret: String?
        get() = resolveCredential(
            IntellifySettings.getInstance()?.state?.spotifyClientSecret,
            System.getenv(CLIENT_SECRET_ENV)
        )

    val isConfigured: Boolean
        get() = missingConfigurationReason(clientId, clientSecret) == null

    /**
     * `null` when the plugin is configured, otherwise a human readable explanation of what is
     * missing, ready to be shown in a notification or as a tooltip.
     */
    val configurationProblem: String?
        get() {
            val reason = missingConfigurationReason(clientId, clientSecret) ?: return null
            return "Intellify: $reason. Set $CLIENT_ID_ENV and $CLIENT_SECRET_ENV " +
                "and restart the IDE — see the \"Building from source\" section of the README."
        }
}

/** First non-blank value, or `null` when neither source provides one. */
internal fun resolveCredential(settingsValue: String?, environmentValue: String?): String? =
    settingsValue?.takeIf { it.isNotBlank() } ?: environmentValue?.takeIf { it.isNotBlank() }

/** `null` when both credentials are present, otherwise what is missing. */
internal fun missingConfigurationReason(clientId: String?, clientSecret: String?): String? = when {
    clientId != null && clientSecret != null -> null
    clientId == null && clientSecret == null -> "no Spotify client ID and no Spotify client secret are configured"
    clientId == null -> "no Spotify client ID is configured"
    else -> "no Spotify client secret is configured"
}
