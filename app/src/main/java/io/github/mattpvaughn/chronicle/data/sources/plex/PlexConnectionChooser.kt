package io.github.mattpvaughn.chronicle.data.sources.plex

import io.github.mattpvaughn.chronicle.data.sources.plex.model.Connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexConnectionChooser @Inject constructor(
    private val plexConfig: PlexConfig,
    private val plexMediaService: PlexMediaService
) {

    private val connectionSet = mutableSetOf<Connection>()

    fun setPotentialConnections(connections: List<Connection>) {
        connectionSet.clear()
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
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun chooseViableConnections(): ConnectionResult {
        Timber.i("Choosing viable connection from: $connectionSet")
        val connectionResultChannel = Channel<ConnectionResult>(capacity = 3)
        connectionSet.sortedByDescending { it.local }.forEach { conn ->
            coroutineScope {
                Timber.i("Checking uri: ${conn.uri}")
                try {
                    plexMediaService.checkServer(conn.uri)
                    if (!connectionResultChannel.isClosedForSend) {
                        connectionResultChannel.send(ConnectionResult.Success(conn.uri))
                    }
                    Timber.i("Choosing uri: ${conn.uri}")
                } catch (e: SocketTimeoutException) {
                    Timber.e("Connection timed out for ${conn.uri}, $e")
                    if (!connectionResultChannel.isClosedForSend) {
                        connectionResultChannel.send(ConnectionResult.Failure)
                    }
                } catch (e: Throwable) {
                    Timber.e("Connection failed for ${conn.uri}, $e")
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
