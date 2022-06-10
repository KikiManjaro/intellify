package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.services.Secret.Companion.clientId
import com.github.kikimanjaro.intellify.services.Secret.Companion.clientSecret
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.remoteServer.util.CloudConfigurationUtil.createCredentialAttributes
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.SpotifyHttpManager
import se.michaelthelin.spotify.enums.AuthorizationScope
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
                println("Expires in: " + authorizationCodeCredentials.expiresIn)
            } else if (spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
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
        try {
            if (code.isNotEmpty()) {
                val authorizationCodeCredentialsFuture = spotifyApi.authorizationCode(code).build().executeAsync()
                val authorizationCodeCredentials = authorizationCodeCredentialsFuture.join()

                spotifyApi.accessToken = authorizationCodeCredentials.accessToken
                saveAccessToken(authorizationCodeCredentials.accessToken)
                spotifyApi.refreshToken = authorizationCodeCredentials.refreshToken
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
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                val currentlyPlayingContextFuture = spotifyApi.usersCurrentlyPlayingTrack.build().executeAsync()

                val currentlyPlayingContext = currentlyPlayingContextFuture.join()
                if (currentlyPlayingContext.item is Track) {
                    isPlaying = currentlyPlayingContext.is_playing
                    val track = currentlyPlayingContext.item as Track
                    title = track.name
                    title += " - " + track.artists[0].name
                }
            } else {
                getTokensFromCode()
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

    fun pauseTrack() {
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.pauseUsersPlayback().build().executeAsync()
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
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.startResumeUsersPlayback().build().executeAsync()
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
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.skipUsersPlaybackToNextTrack().build().executeAsync()
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
        try {
            if (code.isNotEmpty() && spotifyApi.accessToken != null && spotifyApi.accessToken.isNotEmpty()) {
                spotifyApi.skipUsersPlaybackToPreviousTrack().build().executeAsync()
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
        val server = ServerSocket(30498)
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
                        Thread.sleep(10000)
                        socket.close()
                    }
                } catch (e: Exception) {
                    println("Socket error: " + e.message)
                    stop = true
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