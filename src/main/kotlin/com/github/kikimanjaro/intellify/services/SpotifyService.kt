package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.services.Secret.Companion.clientId
import com.github.kikimanjaro.intellify.services.Secret.Companion.clientSecret
import com.github.kikimanjaro.intellify.ui.SpotifyPanel
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.remoteServer.util.CloudConfigurationUtil.createCredentialAttributes
import com.intellij.openapi.diagnostic.Logger
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
    private val logger = Logger.getInstance(SpotifyService::class.java)
    var currentPanel: SpotifyPanel? = null
    private const val codeServiceName = "Intellify-code"
    private const val accesServiceName = "Intellify-acces"
    private const val refreshServiceName = "Intellify-refresh"
    private val redirectUri =
        SpotifyHttpManager.makeUri("http://localhost:30498/callback")
    private val spotifyApi = SpotifyApi.Builder()
        .setClientId(clientId)
        .setClientSecret(clientSecret)
        .setRedirectUri(redirectUri)
        .setAccessToken(retrieveAccessToken())
        .setRefreshToken(retrieveRefreshToken())
        .build()

    private val authorizationCodeUriRqst = AuthorizationCodeUriRequest.Builder().client_id(clientId)
        .redirect_uri(SpotifyHttpManager.makeUri("http://localhost:30498/callback")).show_dialog(true)
        .response_type("code").scope(
            AuthorizationScope.USER_LIBRARY_READ,
            AuthorizationScope.APP_REMOTE_CONTROL,
            AuthorizationScope.USER_READ_CURRENTLY_PLAYING,
            AuthorizationScope.USER_MODIFY_PLAYBACK_STATE,
            AuthorizationScope.USER_TOP_READ
        ).build()
    var code = retrieveCode()
    var title = ""
    var artist = ""
    var song = ""
    var imageUrl = ""

    var durationMs = 0
    var progressInMs = 0

    var isPlaying = false

    fun refreshAccessTokenWithRefreshToken() {
        try {
            if (spotifyApi.refreshToken != null && spotifyApi.refreshToken.isNotEmpty()) {
                val authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh().build()
                val authorizationCodeCredentialsFuture = authorizationCodeRefreshRequest.executeAsync()

                // Thread free to do other tasks...

                // Example Only. Never block in production code.
                val authorizationCodeCredentials = authorizationCodeCredentialsFuture.join()

                // Set access token for further "spotifyApi" object usage
                spotifyApi.accessToken = authorizationCodeCredentials.accessToken
                saveAccessToken(authorizationCodeCredentials.accessToken)
                logger.info("Expires in: ${authorizationCodeCredentials.expiresIn}")
            } else if (spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                getTokensFromCode()
            } else {
                getCodeFromBrowser()
            }
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
            getCodeFromBrowser()
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun getTokensFromCode() {
        try {
            if (code.isNotEmpty()) {
                val authorizationCodeCredentialsFuture = spotifyApi.authorizationCode(code).build().executeAsync()
                val authorizationCodeCredentials = authorizationCodeCredentialsFuture.join()

                spotifyApi.accessToken = authorizationCodeCredentials.accessToken
                saveAccessToken(authorizationCodeCredentials.accessToken)
                spotifyApi.refreshToken = authorizationCodeCredentials.refreshToken
                saveRefreshToken(authorizationCodeCredentials.refreshToken)
//                logger.info("Expires in: ${authorizationCodeCredentials.expiresIn}")
            } else {
                getCodeFromBrowser()
            }
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
            refreshAccessTokenWithRefreshToken()
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun getCodeFromBrowser() {
        try {
            val uriFuture = authorizationCodeUriRqst.executeAsync()

            val uri = uriFuture.join()
//            println("URI: $uri")
            openServer()
            BrowserUtil.browse(uri) //TODO: use embeded browser
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun getInformationAboutUsersCurrentPlayingTrack() {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                val currentlyPlayingContext = spotifyApi.usersCurrentlyPlayingTrack.build().execute()
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
            logger.warn("Spotify API error", e)
            refreshAccessTokenWithRefreshToken()
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: UnauthorizedException) {
            logger.warn("Spotify unauthorized")
            refreshAccessTokenWithRefreshToken()
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun pauseTrack() {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.pauseUsersPlayback().build().execute()
            }
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun startTrack() {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.startResumeUsersPlayback().build().execute()
            }
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun nextTrack() {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.skipUsersPlaybackToNextTrack().build().execute()
            }
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun prevTrack() {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.skipUsersPlaybackToPreviousTrack().build().execute()
            }
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun setProgress(progressInMsToGoTo: Int) {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.seekToPositionInCurrentlyPlayingTrack(progressInMsToGoTo).build().execute()
            }
        } catch (e: CompletionException) {
            logger.warn("Spotify API error", e)
        } catch (e: CancellationException) {
            logger.warn("Async operation cancelled", e)
        } catch (e: Exception) {
            logger.warn("Spotify API error", e)
        }
    }

    fun openServer() {
        val server = try {
            ServerSocket(30498)
        } catch (e: Exception) {
            logger.warn("Failed to bind OAuth callback on port 30498", e)
            return
        }

        var stop = false
        thread(isDaemon = true, name = "Intellify-OAuth-Callback") {
            try {
                while (!stop) {
                    val socket = try {
                        server.accept()
                    } catch (e: Exception) {
                        if (!stop) logger.warn("OAuth accept failed", e)
                        break
                    }
                    socket.use { s ->
                        try {
                            logger.debug("OAuth client connected")
                            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                            val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
                            val line = reader.readLine() ?: continue
                            writer.write("HTTP/1.1 200 OK\r\n")
                            writer.write("Content-Type: text/html; charset=UTF-8\r\n\r\n")
                            writer.write(
                                "<!DOCTYPE html>\n" +
                                        "<html lang=\"en\">\n" +
                                        "<head><meta charset=\"UTF-8\"><title>Intellify</title></head>\n" +
                                        "<body><p>Thank you for using Intellify.</p><p>You can close this tab.</p><p>KikiManjaro</p></body>\n" +
                                        "</html>"
                            )
                            writer.flush()
                            // Robustly extract code param: ?code=xxx& or ?code=xxx HTTP
                            val codeParam = line.substringAfter("code=", "").substringBefore("&").substringBefore(" ")
                            if (codeParam.isNotEmpty()) {
                                code = codeParam
                                saveCode(code)
                                getTokensFromCode()
                                stop = true
                            }
                        } catch (e: Exception) {
                            logger.warn("OAuth callback handling failed", e)
                        }
                    }
                    if (stop) break
                }
            } finally {
                runCatching { server.close() }
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
            createCredentialAttributes(accesServiceName, "user") // see previous sample
        val credentials = Credentials(accesServiceName, token)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun retrieveAccessToken(): String? {
        val credentialAttributes = createCredentialAttributes(accesServiceName, "user")
        return PasswordSafe.instance.getPassword(credentialAttributes!!)
    }

    private fun saveRefreshToken(token: String) {
        val credentialAttributes: CredentialAttributes? =
            createCredentialAttributes(refreshServiceName, "user") // see previous sample
        val credentials = Credentials(refreshServiceName, token)
        PasswordSafe.instance.set(credentialAttributes!!, credentials)
    }

    private fun retrieveRefreshToken(): String? {
        val credentialAttributes = createCredentialAttributes(refreshServiceName, "user")
        return PasswordSafe.instance.getPassword(credentialAttributes!!)
    }
}