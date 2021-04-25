package io.github.mattpvaughn.chronicle.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import io.github.mattpvaughn.chronicle.data.sources.HttpMediaSource
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.features.download.DownloadNotificationWorker
import io.github.mattpvaughn.chronicle.features.download.FetchGroupStartFinishListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import javax.inject.Inject
import javax.inject.Singleton

interface ICachedFileManager {
    enum class CacheStatus { CACHED, CACHING, NOT_CACHED }

    val activeBookDownloads: LiveData<Set<Int>>

    fun cancelCaching()
    fun cancelGroup(id: Int)
    fun downloadTracks(bookId: Int, bookTitle: String)
    suspend fun uncacheAllInLibrary(): Int
    suspend fun deleteCachedBook(bookId: Int, sourceId: Long)
    suspend fun hasUserCachedTracks(): Boolean
    suspend fun refreshTrackDownloadedStatus()
}

interface SimpleSet<T> {
    fun add(elem: T): Boolean
    fun remove(elem: T): Boolean
    operator fun contains(elem: T): Boolean
    val size: Int
}

@Singleton
class CachedFileManager @Inject constructor(
    private val applicationContext: Context,
    private val fetch: Fetch,
    private val prefsRepo: PrefsRepo,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val sourceManager: SourceManager,
) : ICachedFileManager {

    private val externalFileDirs = Injector.get().externalDeviceDirs()

    private val downloadListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadNotificationWorker.ACTION_CANCEL_ALL_DOWNLOADS -> Injector.get().fetch()
                    .cancelAll()
                DownloadNotificationWorker.ACTION_CANCEL_BOOK_DOWNLOAD -> {
                    val bookId = intent.getIntExtra(DownloadNotificationWorker.KEY_BOOK_ID, -1)
                    if (bookId != -1) {
                        Timber.i("Cancelling book: $bookId")
                        Injector.get().fetch().cancelGroup(bookId)
                    }
                }
            }
        }
    }

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
        // Add downloads to Fetch
        GlobalScope.launch {
            fetch.enqueue(makeRequests(bookId, bookTitle)) {
                val errors = it.mapNotNull { (_, error) ->
                    if (error == Error.NONE) null else error
                }
                if (BuildConfig.DEBUG && errors.isNotEmpty()) {
                    Toast.makeText(
                        applicationContext,
                        "Error enqueuing download: $errors",
                        LENGTH_SHORT
                    ).show()
                }
                if (errors.isEmpty()) {
                    DownloadNotificationWorker.start()
                }
            }
        }

    }

    /**
     * Creates [Request]s for all missing files associated with [bookId]
     *
     * @return the number of files to be downloaded
     */
    private suspend fun makeRequests(bookId: Int, bookTitle: String): List<Request> {
        // Gets all tracks for album id
        val tracks = trackRepository.getTracksForAudiobookAsync(bookId)

        val cachedFilesDir = prefsRepo.cachedMediaDir
        Timber.i("Caching tracks to: ${cachedFilesDir.path}")
        Timber.i("Tracks to cache: $tracks")

        val requests = tracks.mapNotNull { track ->
            // File exists but is not marked as cached in the database- more likely than not
            // this means that it has failed to fully download
            val destFile = File(cachedFilesDir, track.getCachedFileName())
            val trackCached = track.cached
            val destFileExists = destFile.exists()

            // No need to make a new request, the file is already downloaded
            if (trackCached && destFileExists) {
                return@mapNotNull null
            }

            val dest = Uri.parse("file://${destFile.absolutePath}")
            val source = sourceManager.getSourceById(track.source)

            if (source !is HttpMediaSource) {
                return@mapNotNull null
            }

            // File exists but is not marked as cached in the database- probably means a download
            // has failed. Delete it and try again
            if (!trackCached && destFileExists) {
                val deleted = destFile.delete()
                if (!deleted) {
                    Timber.e("Failed to delete previously cached file. Download will fail!")
                } else {
                    Timber.e("Succeeding in deleting cached file")
                }
            }

            return@mapNotNull source.makeDownloadRequest(
                trackUrl = track.media,
                dest = dest,
                bookTitle = "#${track.index} ${track.album}",
            )

        }
        return requests
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
     * the correct [MediaItemTrack.parentServerId] set
     *
     * Return [Result.success] on successful deletion of all files or [Result.failure] if the
     * deletion of any files fail
     */
    override suspend fun deleteCachedBook(bookId: Int, sourceId: Long) {
        Timber.i("Deleting downloaded book: $bookId")
        fetch.deleteGroup(bookId)
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
                tracks.forEach {
                    val trackFile = File(prefsRepo.cachedMediaDir, it.getCachedFileName())
                    trackFile.delete()
                    // now count it as deleted
                    trackRepository.updateCachedStatus(it.id, false)
                }
                bookRepository.updateCachedStatus(bookId, false)
            }
        }
    }

    /** Set of [Audiobook.id] representing all books being actively downloaded */
    private var activeDownloads = object : SimpleSet<Int> {
        private val internalSet = mutableSetOf<Int>()
        override val size: Int
            get() = internalSet.size

        override fun add(elem: Int): Boolean {
            _activeBookDownloads.postValue(internalSet)
            return internalSet.add(elem)
        }

        override fun remove(elem: Int): Boolean {
            _activeBookDownloads.postValue(internalSet)
            return internalSet.remove(elem)
        }

        override fun toString() = internalSet.toString()
        override operator fun contains(elem: Int) = internalSet.contains(elem)
    }

    private val _activeBookDownloads = MutableLiveData<Set<Int>>()
    override val activeBookDownloads: LiveData<Set<Int>>
        get() = _activeBookDownloads

    init {
        applicationContext.registerReceiver(downloadListener, IntentFilter().apply {
            addAction(DownloadNotificationWorker.ACTION_CANCEL_BOOK_DOWNLOAD)
            addAction(DownloadNotificationWorker.ACTION_CANCEL_ALL_DOWNLOADS)
        })

        // singleton so we can observe downloads always
        fetch.addListener(object : FetchGroupStartFinishListener() {
            override fun onStarted(groupId: Int, fetchGroup: FetchGroup) {
                if (groupId !in activeDownloads) {
                    Timber.i("Starting downloading book with id: $groupId")
                }
                activeDownloads.add(groupId)
            }

            override fun onStarted(
                download: Download,
                downloadBlocks: List<DownloadBlock>,
                totalBlocks: Int
            ) {
                Timber.i("Starting download!")
                DownloadNotificationWorker.start()
                super.onResumed(download)
            }

            override fun onFinished(groupId: Int, fetchGroup: FetchGroup) {
                // Handle the various downloaded statuses
                Timber.i("Group change for book with id $groupId: ${fetchGroup.downloads.size} tracks downloaded")
                val downloads = fetchGroup.downloads
                Timber.i(downloads.joinToString { it.status.toString() })
                activeDownloads.remove(groupId)
                val downloadSuccess =
                    downloads.all { it.error == Error.NONE } && downloads.isNotEmpty()
                if (downloadSuccess) {
                    GlobalScope.launch {
                        withContext(Dispatchers.IO) {
                            Timber.i("Book download success for ($groupId)")
                            bookRepository.updateCachedStatus(groupId, true)
                        }
                    }
                }
            }
        })
    }

    /**
     * Update [trackRepository] and [bookRepository] to reflect downloaded files
     *
     * Deletes files for [Audiobook]s no longer in the database and updates [Audiobook.isCached]
     * for downloaded files which no longer exist on the file system
     */
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
                //       we just have to trust as we allow users to retain downloads across libraries
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

