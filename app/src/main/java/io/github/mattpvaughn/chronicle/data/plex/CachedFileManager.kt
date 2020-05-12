package io.github.mattpvaughn.chronicle.data.plex

import android.app.DownloadManager
import android.net.Uri
import android.util.Log
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileFilter
import javax.inject.Inject

interface ICachedFileManager {
    enum class CacheStatus {
        CACHED,
        CACHING,
        NOT_CACHED
    }

    fun cancelCaching()
    fun downloadTracks(tracks: List<MediaItemTrack>)
    fun uncacheAll(): Int
    fun uncacheTracks(bookId: Int, tracks: List<MediaItemTrack>)
}

class CachedFileManager @Inject constructor(
    private val downloadManager: DownloadManager,
    private val prefsRepo: PrefsRepo,
    private val coroutineScope: CoroutineScope,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val plexConfig: PlexConfig
) : ICachedFileManager {

    private val externalFileDirs = Injector.get().externalDeviceDirs()

    // Keep a list of tracks which we are actively caching so they can be canceled if needed
    private var cacheQueue = ArrayList<Long>()

    override fun cancelCaching() {
        cacheQueue.forEach { downloadManager.remove(it) }
    }

    override fun downloadTracks(tracks: List<MediaItemTrack>) {
        cacheQueue.clear()
        val cachedFilesDir = prefsRepo.cachedMediaDir
        Log.i(APP_NAME, "Caching tracks to: ${cachedFilesDir.path}")
        tracks.sortedBy { it.index }.forEach { track ->
            if (!track.cached) {
                val destFile = File(cachedFilesDir, track.getCachedFileName())
                if (destFile.exists()) {
                    // File exists but is not marked as cached in the database- more likely than not
                    // this means that we failed to download this previously
                    destFile.delete()
                }
                val dest = Uri.parse("file://${destFile.absolutePath}")
                val downId = downloadManager.enqueue(makeRequest(track, dest))
                cacheQueue.add(downId)
            }
        }
    }

    override fun uncacheAll(): Int {
        Log.i(APP_NAME, "Removing books from: ${externalFileDirs.map { it.path }}")
        val allCachedFiles = externalFileDirs.flatMap { dir ->
            dir.listFiles(FileFilter {
                MediaItemTrack.cachedFilePattern.matches(it.name)
            })?.toList() ?: emptyList()
        }
        allCachedFiles.forEach {
            Log.i(APP_NAME, "Deleting file: ${it.name}")
            it.delete()
        }
        coroutineScope.launch {
            trackRepository.uncacheAll()
            bookRepository.uncacheAll()
        }
        return allCachedFiles.size
    }

    override fun uncacheTracks(bookId: Int, tracks: List<MediaItemTrack>) {
        val cacheLoc = prefsRepo.cachedMediaDir
        tracks.forEach { track ->
            val cachedFile = File(cacheLoc, track.getCachedFileName())
            if (cachedFile.exists()) {
                val deleted = cachedFile.delete()
                if (!deleted) {
                    Log.e(APP_NAME, "Failed to remove cached file $cachedFile")
                } else {
                    Log.i(APP_NAME, "Removed cached file $cachedFile")
                    coroutineScope.launch {
                        trackRepository.updateCachedStatus(track.id, false)
                    }
                }
            }
        }
        coroutineScope.launch {
            bookRepository.updateCached(bookId, false)
        }
    }

    private fun makeRequest(track: MediaItemTrack, dest: Uri): DownloadManager.Request {
        return plexConfig.makeDownloadRequest(track.media)
            .setTitle("#${track.index} ${track.album}")
            .setDescription("Downloading")
            .setDestinationUri(dest)
    }

}