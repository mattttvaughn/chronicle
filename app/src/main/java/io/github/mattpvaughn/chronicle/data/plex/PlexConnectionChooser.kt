package io.github.mattpvaughn.chronicle.data.plex

import android.util.Log
import io.github.mattpvaughn.chronicle.data.plex.model.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexConnectionChooser @Inject constructor(
    private val plexConfig: PlexConfig,
    private val plexMediaService: PlexMediaService
) {

    private val connectionSet = mutableSetOf<Connection>()

    fun addPotentialConnections(connections: List<Connection>) {
        connectionSet.addAll(connections)
    }

    fun clear() {
        connectionSet.clear()
    }

    sealed class ConnectionResult {
        data class Success(val url: String) : ConnectionResult()
        object Failure : ConnectionResult()
    }

    @InternalCoroutinesApi
    @UseExperimental(ExperimentalCoroutinesApi::class)
    suspend fun chooseViableConnections(scope: CoroutineScope): ConnectionResult {
        Log.i(APP_NAME, "Choosing viable connection from: $connectionSet")
        val connectionResultChannel = Channel<ConnectionResult>(capacity = 3)
        connectionSet.sortedByDescending { it.local }.forEach { conn ->
            scope.launch {
                Log.i(APP_NAME, "Checking uri: ${conn.uri}")
                try {
                    plexMediaService.checkServer(conn.uri)
                    if (!connectionResultChannel.isClosedForSend) {
                        connectionResultChannel.send(ConnectionResult.Success(conn.uri))
                    }
                    Log.i(APP_NAME, "Choosing uri: ${conn.uri}")
                } catch (e: Exception) {
                    Log.e(APP_NAME, "Connection failed for ${conn.uri}, $e")
                } catch (e: SocketTimeoutException) {
                    Log.e(APP_NAME, "Connection timed out for ${conn.uri}, $e")
                    if (!connectionResultChannel.isClosedForSend) {
                        connectionResultChannel.send(ConnectionResult.Failure)
                    }
                }
            }
        }

        val connectionResult = connectionResultChannel.receive()
        return if (connectionResult is ConnectionResult.Success) {
            plexConfig.url = connectionResult.url
            connectionResultChannel.close() // don't want to connect to multiple servers
            connectionResult
        } else {
            connectionResult
        }
    }
}
