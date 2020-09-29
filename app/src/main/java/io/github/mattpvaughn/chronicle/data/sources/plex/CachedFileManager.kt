package io.github.mattpvaughn.chronicle.data.sources.plex

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchGroup
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import io.github.mattpvaughn.chronicle.features.download.DownloadWorker
import io.github.mattpvaughn.chronicle.features.download.FetchGroupStartFinishListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * TODO: migration from DownloadManager to Fetch.
 */
interface ICachedFileManager {
    enum class CacheStatus {
        CACHED,
        CACHING,
        NOT_CACHED
    }

    val activeBookDownloads: LiveData<Set<Int>>

    fun cancelCaching()
    fun cancelGroup(id: Int)
    fun downloadTracks(bookId: Int, bookTitle: String)
    suspend fun uncacheAllInLibrary(): Int
    suspend fun deleteCachedBook(bookId: Int)
    suspend fun hasUserCachedTracks(): Boolean
    suspend fun refreshTrackDownloadedStatus()

}

class CachedFileManager @Inject constructor(
    private val fetch: Fetch,
    private val prefsRepo: PrefsRepo,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val workManager: WorkManager
) : ICachedFileManager {

    private val externalFileDirs = Injector.get().externalDeviceDirs()

    override fun cancelGroup(id: Int) {
        fetch.cancelGroup(id)
    }

    override fun cancelCaching() {
        fetch.cancelAll()
    }

    override suspend fun hasUserCachedTracks(): Boolean {
        return withContext(Dispatchers.IO) {
            trackRepository.getCachedTracks().isNotEmpty()
        }
    }

    override fun downloadTracks(bookId: Int, bookTitle: String) {
        val syncWorkerConstraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val inputData = DownloadWorker.makeWorkerData(bookId, bookTitle)
        val worker = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .setConstraints(syncWorkerConstraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()

        workManager.beginUniqueWork(bookId.toString(), ExistingWorkPolicy.REPLACE, worker).enqueue()
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
    override suspend fun deleteCachedBook(bookId: Int) {
        // Attempt to delete group via fetch.
        fetch.deleteGroup(bookId, { success ->
            GlobalScope.launch(Dispatchers.IO) {
                Timber.i("Deleting tracks: $success")
                success.forEach { deleted ->
                    val trackId = MediaItemTrack.getTrackIdFromFileName(File(deleted.file).name)
                    Timber.i("Deleted track with id: $trackId")
                    trackRepository.updateCachedStatus(trackId, false)
                }
                Timber.i("Deleted book with id: $bookId")
                bookRepository.updateCachedStatus(bookId, false)
            }
        }, { error ->
            // If Fetch fails to delete the files, attempt to manually delete the files, as this was
            // how it was handled when [DownloadManager] was used
            Timber.i("Failed to delete tracks: $error")
            // TODO: we could maybe get into a bad state if app is killed while this runs
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
                    tracks.forEach {
                        val trackFile = File(prefsRepo.cachedMediaDir, it.getCachedFileName())
                        trackFile.delete()
                        // run again just in case
                        fetch.deleteGroup(bookId)
                        // now count it as deleted
                        trackRepository.updateCachedStatus(it.id, false)
                    }
                    bookRepository.updateCachedStatus(bookId, false)
                }
            }

        })
    }

    /** Set of pairs <[MediaItemTrack.id], [Audiobook.id]> representing a track download */
    private var activeDownloads: Set<Int> = mutableSetOf()
        set(value) {
            _activeBookDownloads.postValue(value)
            field = value
        }

    private val _activeBookDownloads = MutableLiveData(activeDownloads)
    override val activeBookDownloads: LiveData<Set<Int>>
        get() = _activeBookDownloads

    init {
        // singleton so we can observe forever
        fetch.addListener(object : FetchGroupStartFinishListener() {
            override fun onStarted(groupId: Int, fetchGroup: FetchGroup) {
                if (groupId !in activeDownloads) {
                    Timber.i("Starting downloading book with id: $groupId")
                }
                activeDownloads = activeDownloads + groupId
            }

            override fun onFinished(groupId: Int, fetchGroup: FetchGroup) {
                if (fetchGroup.completedDownloads.size == fetchGroup.downloads.size) {
                    Timber.i("Finished downloading book with id: $groupId")

                    GlobalScope.launch {
                        withContext(Dispatchers.IO) {
                            Timber.i("Book downloaded ($groupId): cache status updated")
                            bookRepository.updateCachedStatus(groupId, true)
                        }
                    }
                    activeDownloads = activeDownloads - groupId
                }
            }
        })
    }

    /** Update [trackRepository] and [bookRepository] to reflect downloaded files */
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

