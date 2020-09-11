package io.github.mattpvaughn.chronicle.data.sources.plex

import android.app.DownloadManager
import android.app.DownloadManager.*
import android.net.Uri
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.io.IOException
import javax.inject.Inject

interface ICachedFileManager {
    enum class CacheStatus {
        CACHED,
        CACHING,
        NOT_CACHED
    }

    fun cancelCaching()
    fun downloadTracks(tracks: List<MediaItemTrack>)
    suspend fun uncacheAllInLibrary(): Int
    suspend fun deleteCachedBook(tracks: List<MediaItemTrack>): Result<Unit>
    suspend fun hasUserCachedTracks(): Boolean
    suspend fun refreshTrackDownloadedStatus()
    suspend fun handleDownloadedTrack(downloadId: Long): Result<Long>
}

class CachedFileManager @Inject constructor(
    private val downloadManager: DownloadManager,
    private val prefsRepo: PrefsRepo,
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

    override suspend fun hasUserCachedTracks(): Boolean {
        return withContext(Dispatchers.IO) {
            trackRepository.getCachedTracks().isNotEmpty()
        }
    }

    override fun downloadTracks(tracks: List<MediaItemTrack>) {
        cacheQueue.clear()
        val cachedFilesDir = prefsRepo.cachedMediaDir
        Timber.i("Caching tracks to: ${cachedFilesDir.path}")
        tracks.sortedBy { it.index }.forEach { track ->
            if (!track.cached) {
                val destFile = File(cachedFilesDir, track.getCachedFileName())
                if (destFile.exists()) {
                    // File exists but is not marked as cached in the database- more likely than not
                    // this means that we failed to download this previously
                    val deleted = destFile.delete()
                    if (!deleted) {
                        Timber.e("Failed to delete previously cached file. Download will fail!")
                    } else {
                        Timber.e("Succeeding in deleting cached file")
                    }
                }
                val dest = Uri.parse("file://${destFile.absolutePath}")
                val downId = downloadManager.enqueue(makeTrackDownloadRequest(track, dest))
                cacheQueue.add(downId)
            }
        }
        prefsRepo.currentDownloadIDs = cacheQueue.toSet()
    }

    override suspend fun uncacheAllInLibrary(): Int {
        Timber.i("Removing books from library")
        val cachedTrackNamesForLibrary = trackRepository.getCachedTracks()
            .map { it.getCachedFileName() }
        val allCachedTrackFiles = externalFileDirs.flatMap { dir ->
            dir.listFiles(FileFilter {
                MediaItemTrack.cachedFilePattern.matches(it.name)
            })?.toList() ?: emptyList()
        }
        allCachedTrackFiles.forEach {
            Timber.i("Cached for library: $cachedTrackNamesForLibrary")
            if (cachedTrackNamesForLibrary.contains(it.name)) {
                Timber.i("Deleting file: ${it.name}")
                it.delete()
            } else {
                Timber.i("Not deleting file: ${it.name}")
            }
        }
        prefsRepo.currentDownloadIDs = emptySet()
        trackRepository.uncacheAll()
        bookRepository.uncacheAll()
        return allCachedTrackFiles.size
    }

    /**
     * Deletes cached tracks from the filesystem corresponding to [tracks]. Assume all tracks have
     * the correct [MediaItemTrack.parentKey] set
     *
     * Return [Result.success] on successful deletion of all files or [Result.failure] if the
     * deletion of any files fail
     */
    override suspend fun deleteCachedBook(tracks: List<MediaItemTrack>): Result<Unit> {
        val cacheLoc = prefsRepo.cachedMediaDir
        val results: List<Result<Unit>> = tracks.map { track ->
            val cachedFile = File(cacheLoc, track.getCachedFileName())
            if (!cachedFile.exists()) {
                // If the cached file is already deleted, that's a success
                trackRepository.updateCachedStatus(track.id, false)
                return@map Result.success(Unit)
            }
            val deleted = cachedFile.delete()
            if (!deleted) {
                return@map Result.failure<Unit>(IOException("Cached file not deleted"))
            } else {
                Timber.i("Removed cached file $cachedFile")
                trackRepository.updateCachedStatus(track.id, false)
                return@map Result.success(Unit)
            }
        }
        val failures = results.filter { it.isFailure }
        return if (failures.isEmpty()) {
            withContext(Dispatchers.IO) {
                val bookId = tracks.firstOrNull()?.parentKey ?: NO_AUDIOBOOK_FOUND_ID
                val book = bookRepository.getAudiobookAsync(bookId)
                if (book != null) {
                    bookRepository.update(book.copy(
                        isCached = false,
                        chapters = book.chapters.map { it.copy(downloaded = false) }
                    ))
                }
            }
            Result.success(Unit)
        } else {
            // Only return the first failure
            failures.first()
        }
    }

    /** Create a [Request] for a track download with the proper metadata */
    private fun makeTrackDownloadRequest(track: MediaItemTrack, dest: Uri): Request {
        return plexConfig.makeDownloadRequest(track.media)
            .setTitle("#${track.index} ${track.album}")
            .setDescription("Downloading")
            .setDestinationUri(dest)
    }

    /** Handle track download finished */
    override suspend fun handleDownloadedTrack(downloadId: Long): Result<Long> {
        val result = getTrackIdForDownload(downloadId)
        if (result.isFailure) {
            Timber.e(result.exceptionOrNull())
            return result
        }

        Timber.i("Download completed for track with id: ${result.getOrNull()}")
        val trackId: Int = result.getOrNull()?.toInt() ?: TRACK_NOT_FOUND
        if (trackId == TRACK_NOT_FOUND) {
            return Result.failure(Exception("Track not found!"))
        }
        withContext(Dispatchers.IO) {
            trackRepository.updateCachedStatus(trackId, true)
            val bookId: Int = trackRepository.getBookIdForTrack(trackId)
            val book: Audiobook? = bookRepository.getAudiobookAsync(bookId)
            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
            // Set the book as cached only when all tracks in it have been cached
            val isBookCached =
                tracks.filter { it.cached }.size == tracks.size && tracks.isNotEmpty()
            if (isBookCached && book != null) {
                Timber.i("Should be caching book with id $bookId")
                bookRepository.update(
                    book.copy(
                        isCached = isBookCached,
                        chapters = book.chapters.map { it.copy(downloaded = isBookCached) })
                )
            }
        }
        return result
    }

    /**
     * Find the [MediaItemTrack.id] for a track where [DownloadManager.enqueue] has already been
     * called and the download has finished.
     *
     * Retrieve info from [DownloadManager] via the [downloadId] returned by [DownloadManager.enqueue]
     */
    private fun getTrackIdForDownload(downloadId: Long): Result<Long> {
        val query = Query().apply { setFilterById(downloadId) }
        val cur = downloadManager.query(query)
        if (!cur.moveToFirst()) {
            return Result.failure(Exception("No download found with id: $downloadId. Perhaps the download was canceled"))
        }
        val statusColumnIndex = cur.getColumnIndex(COLUMN_STATUS)
        if (STATUS_SUCCESSFUL != cur.getInt(statusColumnIndex)) {
            val errorReason = cur.getInt(cur.getColumnIndex(COLUMN_REASON))
            val titleColumn = cur.getString(cur.getColumnIndex(COLUMN_TITLE))
            return Result.failure(
                Exception("Download failed for \"$titleColumn\". Error code: ($errorReason)")
            )
        }
        val downloadedFilePath = cur.getString(cur.getColumnIndex(COLUMN_LOCAL_URI))

        /** Assume that the filename is also the key of the track */
        val downloadedTrack = File(downloadedFilePath.toString())
        val trackName = downloadedTrack.name
        if (!MediaItemTrack.cachedFilePattern.matches(trackName)) {
            // Attempt to delete the previous failed download, then rename this download
            val trackId = MediaItemTrack.getTrackIdFromFileName(trackName).toString()
            val trackFileName = trackId + downloadedTrack.extension
            val newTrackFile = File(downloadedTrack.parentFile, trackFileName)
            downloadedTrack.renameTo(newTrackFile)
            Timber.i("Renamed download to: ${newTrackFile.absolutePath}")
            return if (newTrackFile.exists()) {
                Result.success(trackId.toLong())
            } else {
                Result.failure(
                    IllegalStateException("Downloaded file already exists and could not replace it")
                )
            }
        }
        return try {
            Result.success(MediaItemTrack.getTrackIdFromFileName(trackName).toLong())
        } catch (e: Throwable) {
            Result.failure(Exception("Failed to get track id: ${e.message}"))
        } finally {
            cur.close()
        }
    }

    /** Update [trackRepository] and [bookRepository] to reflect download files */
    override suspend fun refreshTrackDownloadedStatus() {
        val idToFileMap = HashMap<Int, File>()
        val trackIdsFoundOnDisk = prefsRepo.cachedMediaDir.listFiles(FileFilter {
            MediaItemTrack.cachedFilePattern.matches(it.name)
        })?.map {
            val id = MediaItemTrack.getTrackIdFromFileName(it.name)
            idToFileMap[id] = it
            id
        } ?: emptyList()

        val reportedCachedKeys = trackRepository.getCachedTracks().map { it.id }

        val alteredTracks = mutableListOf<Int>()

        // Exists in DB but not in cache- remove from DB!
        reportedCachedKeys.filter {
            !trackIdsFoundOnDisk.contains(it)
        }.forEach {
            Timber.i("Removed track: $it")
            alteredTracks.add(it)
            trackRepository.updateCachedStatus(it, false)
        }

        // Exists in cache but not in DB- add to DB!
        trackIdsFoundOnDisk.filter {
            !reportedCachedKeys.contains(it)
        }.forEach {
            val rowsUpdated = trackRepository.updateCachedStatus(it, true)
            if (rowsUpdated == 0) {
                // TODO: this will be relevant when multiple sources is implemented, but for now
                //       we just have to trust, as they could be from other libraries
//                // File has been orphaned- no longer exists in DB, remove it from file system!
//                idToFileMap[it]?.delete()
            } else {
                alteredTracks.add(it)
            }
        }

        // Update cached status for the books containing any added/removed tracks
        alteredTracks.map {
            trackRepository.getBookIdForTrack(it)
        }.distinct().forEach { bookId: Int ->
            Timber.i("Book: $bookId")
            if (bookId == NO_AUDIOBOOK_FOUND_ID) {
                return@forEach
            }
            val bookTrackCacheCount =
                trackRepository.getCachedTrackCountForBookAsync(bookId)
            val bookTrackCount = trackRepository.getTrackCountForBookAsync(bookId)
            val isBookCached = bookTrackCacheCount == bookTrackCount && bookTrackCount > 0
            val book = bookRepository.getAudiobookAsync(bookId)
            if (book != null) {
                bookRepository.update(book.copy(
                    isCached = isBookCached,
                    chapters = book.chapters.map { it.copy(downloaded = isBookCached) }
                ))
            }
        }
    }
}

