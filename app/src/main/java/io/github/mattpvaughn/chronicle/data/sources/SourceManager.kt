package io.github.mattpvaughn.chronicle.data.sources

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import io.github.mattpvaughn.chronicle.data.ConnectionState
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.util.CombinedLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class SourceManager @Inject constructor(
    private val prefsRepo: PrefsRepo,
    private val mediaSourceFactory: MediaSourceFactory
) {

    private val sources = mutableListOf<MediaSource>()
    private val sourcesObservable = MutableLiveData<List<MediaSource>>(sources)

    init {
        // Forget sources which have been fully created
        val savedSources =
            prefsRepo.sources.map { mediaSourceFactory.create(it.first, it.second) }
                .filter {
                    if (it is HttpMediaSource) it.isAuthorized() else true
                }
        sources.addAll(savedSources)
    }

    /** Generates an ID for a new media source which is not used by any [sources] */
    fun generateUniqueId(): Long {
        val existingIds = sources.map { it.id }.toSet()
        // iterate over existingIds.size + 1 elements
        for (id: Long in (0L..existingIds.size.toLong() + 1L)) {
            if (!existingIds.contains(id)) {
                return id
            }
        }
        // Above should always succeed but compiler doesn't know that
        return Random.Default.nextLong()
    }

    fun getSources(): List<MediaSource> {
        return sources.toList()
    }

    fun sourceCount(): Int {
        return sources.size
    }

    fun getSourceById(id: Long): MediaSource? {
        return sources.firstOrNull { it.id == id }
    }

    /** The [MediaSource.id] of all sources which we can access */
    val connectedSourceIds = Transformations.map(sourcesObservable) {
        it.filter { source ->
            if (source is HttpMediaSource) {
                source.connectionState.value == ConnectionState.CONNECTED
            } else {
                true
            }
        }.map { source -> source.id }
    }

    inline fun <reified T> getSourcesOfType(): List<T> {
        return getSources().filterIsInstance<T>()
    }

    /**
     * Returns a [LiveData] wrapping a boolean indicating whether the source with [MediaSource.id]
     * is able to access its source or not
     */
    fun isSourceConnected(sourceId: Long): LiveData<Boolean> {
        val source = getSourceById(sourceId) ?: return MutableLiveData(false)
        return if (source is HttpMediaSource) {
            source.isAuthorizedObservable()
        } else {
            // Consider non-http sources always connected
            MutableLiveData(true)
        }
    }

    /** Adds a [MediaSource] to [sources], persists it to filesystem */
    fun addSource(mediaSource: MediaSource) {
        sourcesObservable.postValue(emptyList())
        sources.add(mediaSource)
        sourcesObservable.postValue(sources)
        prefsRepo.sources = saveSources()
    }

    /** Removes a [MediaSource] from [sources] */
    fun removeSource(mediaSource: MediaSource) {
        sourcesObservable.postValue(emptyList())
        sources.remove(mediaSource)
        sourcesObservable.postValue(sources)
        prefsRepo.sources = saveSources()
    }


    private fun saveSources() = sources.map { Pair(it.id, it.type()) }

    /**
     * For each [MediaSource] in [sources], returns a [Pair] of <[MediaSource.id], [MediaSource.fetchTracks]>
     */
    suspend fun fetchTracks(): List<Pair<Long, Result<List<MediaItemTrack>>>> {
        return withContext(Dispatchers.IO) {
            return@withContext sources.map { source ->
                Pair(source.id, source.fetchTracks())
            }
        }
    }

    /**
     * For each [MediaSource] in [sources], returns a [Pair] of <[MediaSource.id], [MediaSource.fetchBooks]>
     */
    suspend fun fetchBooks(): List<Pair<Long, Result<List<Audiobook>>>> {
        return withContext(Dispatchers.IO) {
            return@withContext sources.map { source ->
                Pair(source.id, source.fetchBooks())
            }
        }
    }

    /** Returns a [Boolean] if there are any sources in http [sources] which are not logged in */
    fun isLoggedIn() = CombinedLiveData(
        sourcesObservable,
        *sources.filterIsInstance<HttpMediaSource>()
            .map { it.isAuthorizedObservable() }.toTypedArray()
    ) {
        // Find all http sources
        val httpSources = it.mapNotNull { source ->
            if (source is HttpMediaSource) source else null
        }
        // Returns true IFF 1) there are http sources and 2) 1+ of the http sources is not logged in
        val httpSourcesNeedingLogin = httpSources.any { source: HttpMediaSource ->
            !source.isAuthorized()
        }
        return@CombinedLiveData !httpSourcesNeedingLogin
    }


    /** Attempt to connect to remote servers for each server */
    suspend fun connectToRemotes() {
        sources.forEach { source ->
            if (source is HttpMediaSource) {
                source.connectToRemote()
            }
        }
    }

    /**
     * Whether the app has access to any source in [sources]. Returns true if one or more have
     * received authorization to access them, otherwise return false
     */
    fun hasAnyAuthorizedSources(): Boolean {
        return sources.any { source ->
            if (source is HttpMediaSource) {
                source.isAuthorized()
            } else {
                true
            }
        }
    }

    /** */
    fun connectionHasBeenLost() {
        sources.forEach { source ->
            if (source is HttpMediaSource) {
                source.connectionHasBeenLost()
            }
        }
    }

}