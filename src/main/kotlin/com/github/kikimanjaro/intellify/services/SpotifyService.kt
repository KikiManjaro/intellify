package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.ui.SpotifyPanel
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.remoteServer.util.CloudConfigurationUtil.createCredentialAttributes
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.SpotifyHttpManager
import se.michaelthelin.spotify.enums.AuthorizationScope
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException
import se.michaelthelin.spotify.model_objects.specification.Track
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import kotlin.concurrent.thread


object SpotifyService {
    var currentPanel: SpotifyPanel? = null
    private const val codeServiceName = "Intellify-code"
    private const val accessServiceName = "Intellify-access"
    @Deprecated("Typo alias, kept for migration")
    private const val accesServiceName = "Intellify-acces"
    private const val refreshServiceName = "Intellify-refresh"
    private val redirectUri =
        SpotifyHttpManager.makeUri("http://localhost:30498/callback")

    /** Whether both Spotify client credentials are available (settings or environment variables). */
    val isConfigured: Boolean
        get() = SpotifyCredentials.isConfigured

    /** Human readable explanation of what is missing while [isConfigured] is false, `null` otherwise. */
    val configurationProblem: String?
        get() = SpotifyCredentials.configurationProblem

    /**
     * The Web API client, built on first use. It is `null` while no client id/secret is configured:
     * every method below then degrades to a no-op instead of throwing, so an unconfigured plugin
     * shows a clear message instead of breaking the IDE.
     */
    private val spotifyApi: SpotifyApi? by lazy {
        val id = SpotifyCredentials.clientId
        val secret = SpotifyCredentials.clientSecret
        if (id == null || secret == null) {
            null
        } else {
            runCatching {
                SpotifyApi.Builder()
                    .setClientId(id)
                    .setClientSecret(secret)
                    .setRedirectUri(redirectUri)
                    .setAccessToken(retrieveAccessToken())
                    .setRefreshToken(retrieveRefreshToken())
                    .build()
            }.onFailure {
                println("Intellify: could not initialise the Spotify client: " + it.message)
            }.getOrNull()
        }
    }

    private val authorizationCodeUriRqst by lazy {
        AuthorizationCodeUriRequest.Builder().client_id(SpotifyCredentials.clientId ?: "")
            .redirect_uri(redirectUri).show_dialog(true)
            .response_type("code").scope(
                AuthorizationScope.USER_LIBRARY_READ,
                AuthorizationScope.APP_REMOTE_CONTROL,
                AuthorizationScope.USER_READ_CURRENTLY_PLAYING,
                AuthorizationScope.USER_MODIFY_PLAYBACK_STATE,
                AuthorizationScope.USER_TOP_READ
            ).build()
    }
    var code = retrieveCode()
    var title = ""
    var artist = ""
    var song = ""
    var imageUrl = ""

    var durationMs = 0
    var progressInMs = 0

    var isPlaying = false

    fun refreshAccessTokenWithRefreshToken() {
        val api = spotifyApi ?: return
        try {
            if (api.refreshToken != null && api.refreshToken.isNotEmpty()) {
                val authorizationCodeRefreshRequest = api.authorizationCodeRefresh().build()
                val authorizationCodeCredentialsFuture = authorizationCodeRefreshRequest.executeAsync()

                // Thread free to do other tasks...

                // Example Only. Never block in production code.
                val authorizationCodeCredentials = authorizationCodeCredentialsFuture.join()

                // Set access token for further "spotifyApi" object usage
                api.accessToken = authorizationCodeCredentials.accessToken
                saveAccessToken(authorizationCodeCredentials.accessToken)
                println("Expires in: " + authorizationCodeCredentials.expiresIn)
            } else if (api.accessToken != null && api.accessToken.isNotEmpty()) {
                getTokensFromCode()
            } else {
                getCodeFromBrowser()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
            getCodeFromBrowser()
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun getTokensFromCode() {
        val api = spotifyApi ?: return
        try {
            if (code.isNotEmpty()) {
                val authorizationCodeCredentialsFuture = api.authorizationCode(code).build().executeAsync()
                val authorizationCodeCredentials = authorizationCodeCredentialsFuture.join()

                api.accessToken = authorizationCodeCredentials.accessToken
                saveAccessToken(authorizationCodeCredentials.accessToken)
                api.refreshToken = authorizationCodeCredentials.refreshToken
                saveRefreshToken(authorizationCodeCredentials.refreshToken)
//                println("Expires in: " + authorizationCodeCredentials.expiresIn)
            } else {
                getCodeFromBrowser()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
            refreshAccessTokenWithRefreshToken()
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun getCodeFromBrowser() {
        if (!isConfigured) return
        try {
            val uriFuture = authorizationCodeUriRqst.executeAsync()

            val uri = uriFuture.join()
//            println("URI: $uri")
            openServer()
            BrowserUtil.browse(uri) //TODO: use embeded browser
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun getInformationAboutUsersCurrentPlayingTrack() {
        val api = spotifyApi ?: return
        try {
            if (code.isNotEmpty() && api.accessToken != null && api.accessToken.isNotEmpty()) {
                val currentlyPlayingContext = api.usersCurrentlyPlayingTrack.build().execute()
                if (currentlyPlayingContext.item is Track) {
                    isPlaying = currentlyPlayingContext.is_playing
                    val track = currentlyPlayingContext.item as Track
                    song = track.name
                    artist = track.artists[0].name
                    title = track.name
                    title += " - " + track.artists[0].name
                    durationMs = track.durationMs
                    progressInMs = currentlyPlayingContext.progress_ms
                    if (track.album != null && track.album.images.isNotEmpty()) {
                        imageUrl = track.album.images[0].url
                    } else {
                        imageUrl = ""
                    }
                }
            } else {
                getTokensFromCode()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
            refreshAccessTokenWithRefreshToken()
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: UnauthorizedException) {
            println("Unauthorized.")
            refreshAccessTokenWithRefreshToken()
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun pauseTrack() {
        val api = spotifyApi ?: return
        try {
            if (code.isNotEmpty() && api.accessToken != null && api.accessToken.isNotEmpty()) {
                api.pauseUsersPlayback().build().execute()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun startTrack() {
        val api = spotifyApi ?: return
        try {
            if (code.isNotEmpty() && api.accessToken != null && api.accessToken.isNotEmpty()) {
                api.startResumeUsersPlayback().build().execute()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun nextTrack() {
        val api = spotifyApi ?: return
        try {
            if (code.isNotEmpty() && api.accessToken != null && api.accessToken.isNotEmpty()) {
                api.skipUsersPlaybackToNextTrack().build().execute()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun prevTrack() {
        val api = spotifyApi ?: return
        try {
            if (code.isNotEmpty() && api.accessToken != null && api.accessToken.isNotEmpty()) {
                api.skipUsersPlaybackToPreviousTrack().build().execute()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun setProgress(progressInMsToGoTo: Int) {
        val api = spotifyApi ?: return
        try {
            if (code.isNotEmpty() && api.accessToken != null && api.accessToken.isNotEmpty()) {
                api.seekToPositionInCurrentlyPlayingTrack(progressInMsToGoTo).build().execute()
            }
        } catch (e: CompletionException) {
            println("Error: " + e.cause!!.message)
        } catch (e: CancellationException) {
            println("Async operation cancelled.")
        } catch (e: Exception) {
            println("Error: " + e.message)
        }
    }

    fun openServer() {
        val server = ServerSocket(30498).apply { soTimeout = 300000 } // 5 min timeout for OAuth callback
//        println("Server is running on port ${server.localPort}")

        var stop = false;
        thread {
            while (!stop) {
                try {
                    val socket = server.accept()
                    println("Client connected")

                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    val reader = BufferedReader(InputStreamReader(input))
                    val writer = BufferedWriter(OutputStreamWriter(output))
                    val line = reader.readLine()
                    writer.write("HTTP/1.1 200 OK\r\n") //TODO: make this beautiful, maybe with an image
                    writer.write(
                        "<!DOCTYPE html>\n" +
                                "<html lang=\"en\">\n" +
                                "<head>\n" +
                                "    <meta charset=\"UTF-8\">\n" +
                                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                                "    <title>My html page</title>\n" +
                                "</head>\n" +
                                "<body>\n" +
                                "\n" +
                                "    <p>\n" +
                                "        Thank you for using Intellify.\n" +
                                "    </p>\n" +
                                "    \n" +
                                "    <p>\n" +
                                "         You can close this, it's useless now :p\n" +
                                "    </p>\n" +
                                "    \n" +
                                "    <p>\n" +
                                "         KikiManjaro\n" +
                                "    </p>\n" +
                                "    \n" +
                                "</body>\n" +
                                "</html>"
                    )
                    writer.flush()
                    code = line.split("=")[1].split(" ")[0]
                    if (code.isNotEmpty()) {
                        saveCode(code)
                        getTokensFromCode()
                        stop = true
                        // Give browser time to render success page then close
                        Thread.sleep(2000)
                        try { socket.close() } catch (_: Exception) {}
                        try { server.close() } catch (_: Exception) {}
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    println("OAuth callback timed out")
                    stop = true
                    try { server.close() } catch (_: Exception) {}
                } catch (e: Exception) {
                    println("Socket error: " + e.message)
                    stop = true
                    try { server.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun saveCode(newCode: String) {
        val credentialAttributes: CredentialAttributes? =
            createCredentialAttributes(codeServiceName, "user") // see previous sample
        val credentials = Credentials(codeServiceName, newCode)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun retrieveCode(): String {
        val credentialAttributes = createCredentialAttributes(codeServiceName, "user")
        return PasswordSafe.instance.getPassword(credentialAttributes!!) ?: ""
    }

    private fun saveAccessToken(token: String) {
        val credentialAttributes: CredentialAttributes? =
            createCredentialAttributes(accessServiceName, "user") // see previous sample
        val credentials = Credentials(accessServiceName, token)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun saveRefreshToken(token: String) {
        val credentialAttributes: CredentialAttributes? =
            createCredentialAttributes(refreshServiceName, "user") // see previous sample
        val credentials = Credentials(refreshServiceName, token)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun retrieveRefreshToken(): String? {
        val credentialAttributes = createCredentialAttributes(refreshServiceName, "user")
        // Try new name first, fall back to legacy typo name for migration
        val token = PasswordSafe.instance.getPassword(credentialAttributes!!)
        if (token != null) return token
        val legacyAttributes = createCredentialAttributes(accesServiceName, "user")
        return PasswordSafe.instance.getPassword(legacyAttributes!!)
    }

    /** Clears all stored Spotify credentials — used for switching accounts (issue #4). */
    fun clearCredentials() {
        for (serviceName in listOf(codeServiceName, accessServiceName, refreshServiceName, accesServiceName)) {
            try {
                val attrs = createCredentialAttributes(serviceName, "user")
                PasswordSafe.instance.set(attrs!!, null)
            } catch (_: Exception) { }
        }
        code = ""
        spotifyApi?.accessToken = null
        spotifyApi?.refreshToken = null
        title = ""; artist = ""; song = ""; imageUrl = ""
        durationMs = 0; progressInMs = 0; isPlaying = false
    }

    fun changeAccount() {
        clearCredentials()
        getCodeFromBrowser()
    }

    private fun retrieveAccessToken(): String? {
        val credentialAttributes = createCredentialAttributes(accessServiceName, "user")
        val token = PasswordSafe.instance.getPassword(credentialAttributes!!)
        if (token != null) return token
        // Fall back to legacy typo key
        val legacyAttributes = createCredentialAttributes(accesServiceName, "user")
        return PasswordSafe.instance.getPassword(legacyAttributes!!)
    }

    private fun saveAccessTokenCompat(token: String) {
        saveAccessToken(token)
        // Also clear legacy entry to avoid confusion
        try {
            val legacy = createCredentialAttributes(accesServiceName, "user")
            if (PasswordSafe.instance.getPassword(legacy!!) != null) {
                PasswordSafe.instance.set(legacy, Credentials(accessServiceName, token))
            }
        } catch (_: Exception) { }
    }
}