package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexMediaService
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.model.asAudiobooks
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.data.sources.plex.model.toChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A repository abstracting all [Audiobook]s from all [MediaSource]s */
interface IBookRepository {
    /** Return all [Audiobook]s in the DB, sorted by [Audiobook.titleSort] */
    fun getAllBooks(): LiveData<List<Audiobook>>
    suspend fun getAllBooksAsync(): List<Audiobook>

    suspend fun getRandomBookAsync(): Audiobook

    /** Refreshes the data in the local database with elements from the network */
    suspend fun refreshData()

    /** Returns the number of books in the repository */
    suspend fun getBookCount(): Int

    /** Removes all books from the local database */
    suspend fun clear()

    /** Updates the book with information regarding the tracks contained within the book */
    suspend fun updateTrackData(
        bookId: Int,
        bookProgress: Long,
        bookDuration: Long,
        trackCount: Int
    )

    /**
     * Returns a [LiveData<Audiobook>] corresponding to an [Audiobook] with the [Audiobook.id]
     * equal to [id]
     */
    fun getAudiobook(id: Int): LiveData<Audiobook?>
    suspend fun getAudiobookAsync(bookId: Int): Audiobook?

    /**
     * Returns the [getBookCount] most recently added books in the local database, ordered by most
     * recently added to added the longest time ago
     */
    fun getRecentlyAdded(): LiveData<List<Audiobook>>
    suspend fun getRecentlyAddedAsync(): List<Audiobook>

    /**
     * Returns the [getBookCount] most recently added listened to books in the local database,
     * ordered from most recently listened to last listened book
     */
    fun getRecentlyListened(): LiveData<List<Audiobook>>

    /**
     * Returns the [getBookCount] most recently added listened to books in the local database,
     * ordered from most recently listened to last listened book
     */
    suspend fun getRecentlyListenedAsync(): List<Audiobook>

    /**
     * Update the [Audiobook.lastViewedAt] and [Audiobook.progress] fields to [currentTime] and
     * [progress], respectively for a book with id [bookId]
     */
    suspend fun updateProgress(bookId: Int, currentTime: Long, progress: Long)

    /**
     * Return a [LiveData<List<Audiobook>>] of all audiobooks containing [query] within their
     * [Audiobook.author] or [Audiobook.title] fields
     */
    fun search(query: String): LiveData<List<Audiobook>>

    /**
     * Return a [List<Audiobook>] of all audiobooks containing [query] within their
     * [Audiobook.author] or [Audiobook.title] fields
     */
    suspend fun searchAsync(query: String): List<Audiobook>

    /**
     * Return the [Audiobook] which has been listened to the most recently. Specifically, look for
     * the [Audiobook.lastViewedAt] field which is largest among all [Audiobook]s in the local DB
     */
    suspend fun getMostRecentlyPlayed(): Audiobook

    /**
     * Returns a [LiveData<List<Audiobook>>] with all [Audiobook]s in the local DB where
     * [Audiobook.isCached] == true.
     */
    fun getCachedAudiobooks(): LiveData<List<Audiobook>>

    /**
     * Returns a [List<Audiobook>] with all [Audiobook]s in the local DB where [Audiobook.isCached] == true.
     */
    suspend fun getCachedAudiobooksAsync(): List<Audiobook>

    /** Sets the [Audiobook.isCached] for all [Audiobook]s in the DB to false */
    suspend fun uncacheAll()

    /**
     * Loads m4b chapter data and any other audiobook details which are not loaded in by default
     * from the network and saves it to the DB if there are chapters found. Pass [forceNetwork]
     * to determine if network data (progress, metadata) ought to be preferred
     *
     * @return true if chapter data was found and added to db, otherwise false
     */
    suspend fun syncAudiobook(
        audiobook: Audiobook,
        tracks: List<MediaItemTrack>,
        forceNetwork: Boolean = false
    ): Boolean

    /** Updates the column uniquely identified by [Audiobook.id] to the new [Audiobook] */
    suspend fun update(audiobook: Audiobook)

    /**
     * Updates the [Audiobook.isCached] column to be true for the [Audiobook] uniquely identified
     * by [Audiobook.id] == [bookId]
     */
    suspend fun updateCachedStatus(bookId: Int, isCached: Boolean)

    /** Sets the book's [Audiobook.progress] to 0 in the DB and the server */
    suspend fun setWatched(bookId: Int)

    suspend fun setUnwatched(bookId: Int)

    /** Loads an [Audiobook] in from the network */
    suspend fun fetchBookAsync(bookId: Int): Audiobook?

    suspend fun refreshDataPaginated()
}

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val prefsRepo: PrefsRepo,
    private val plexPrefsRepo: PlexPrefsRepo,
    private val plexMediaService: PlexMediaService
) : IBookRepository {

    /** TODO: observe prefsRepo.offlineMode? */

    /**
     * Limits the number of elements returned in cases where it doesn't make sense to return all
     * elements in the database
     */
    private val limitReturnCount = 25

    override fun getAllBooks(): LiveData<List<Audiobook>> {
        return bookDao.getAllRows(prefsRepo.offlineMode)
    }

    override suspend fun getBookCount(): Int {
        return withContext(Dispatchers.IO) {
            bookDao.getBookCount()
        }
    }

    @Throws(Throwable::class)
    override suspend fun refreshData() {
        if (prefsRepo.offlineMode) {
            return
        }
        prefsRepo.lastRefreshTimeStamp = System.currentTimeMillis()
        val networkBooks: List<Audiobook> = withContext(Dispatchers.IO) {
            try {
                plexMediaService.retrieveAllAlbums(plexPrefsRepo.library!!.id).plexMediaContainer.asAudiobooks()
            } catch (t: Throwable) {
                Timber.i("Failed to retrieve books: $t")
                null
            }
        } ?: return
        //    ^^^ quit on network failure- nothing below matters without new books from server

        val localBooks = withContext(Dispatchers.IO) { bookDao.getAudiobooks() }

        val mergedBooks = networkBooks.map { networkBook ->
            val localBook = localBooks.find { it.id == networkBook.id }
            if (localBook != null) {
                // [Audiobook.merge] chooses fields depending on [Audiobook.lastViewedAt]
                return@map Audiobook.merge(network = networkBook, local = localBook)
            } else {
                return@map networkBook
            }
        }

        // remove books which have been deleted from server
        val networkIds = networkBooks.map { it.id }
        val removedFromNetwork = localBooks.filter { localBook ->
            !networkIds.contains(localBook.id)
        }

        Timber.i("Removed from network: ${removedFromNetwork.map { it.title }}")
        withContext(Dispatchers.IO) {
            val removed = bookDao.removeAll(removedFromNetwork.map { it.id.toString() })
            Timber.i("Removed $removed items from DB")

            Timber.i("Loaded books: $mergedBooks")
            bookDao.insertAll(mergedBooks)
        }
    }

    @Throws(Throwable::class)
    override suspend fun refreshDataPaginated() {
        if (prefsRepo.offlineMode) {
            return
        }

        prefsRepo.lastRefreshTimeStamp = System.currentTimeMillis()
        val networkBooks: MutableList<Audiobook> = mutableListOf()
        withContext(Dispatchers.IO) {
            try {
                val libraryId = plexPrefsRepo.library?.id ?: return@withContext
                var booksLeft = 1L
                // Maximum number of pages of data we fetch. Failsafe in case of bad data from the
                // server since we don't want infinite loops. This limits us to a maximum 1,000,000
                // tracks for now
                val maxIterations = 5000
                var i = 0
                while (booksLeft > 0 && i < maxIterations) {
                    val response = plexMediaService
                        .retrieveAlbumPage(libraryId, i * 100)
                        .plexMediaContainer
                    booksLeft = response.totalSize - (response.offset + response.size)
                    networkBooks.addAll(response.asAudiobooks())
                    i++
                }
            } catch (t: Throwable) {
                Timber.i("Failed to retrieve books: $t")
            }
        }
        //    ^^^ quit on network failure- nothing below matters without new books from server

        val localBooks = withContext(Dispatchers.IO) { bookDao.getAudiobooks() }

        val mergedBooks = networkBooks.map { networkBook ->
            val localBook = localBooks.find { it.id == networkBook.id }
            if (localBook != null) {
                // [Audiobook.merge] chooses fields depending on [Audiobook.lastViewedAt]
                return@map Audiobook.merge(network = networkBook, local = localBook)
            } else {
                return@map networkBook
            }
        }

        // remove books which have been deleted from server
        val networkIds = networkBooks.map { it.id }
        val removedFromNetwork = localBooks.filter { localBook ->
            !networkIds.contains(localBook.id)
        }

        Timber.i("Removed from network: ${removedFromNetwork.map { it.title }}")
        withContext(Dispatchers.IO) {
            val removed = bookDao.removeAll(removedFromNetwork.map { it.id.toString() })
            Timber.i("Removed $removed items from DB")

            Timber.i("Loaded books: $mergedBooks")
            bookDao.insertAll(mergedBooks)
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            bookDao.clear()
        }
    }

    override suspend fun updateTrackData(
        bookId: Int,
        bookProgress: Long,
        bookDuration: Long,
        trackCount: Int
    ) {
        withContext(Dispatchers.IO) {
            bookDao.updateTrackData(bookId, bookProgress, bookDuration, trackCount)
        }
    }

    override fun getAudiobook(id: Int): LiveData<Audiobook?> {
        return bookDao.getAudiobook(id, prefsRepo.offlineMode)
    }

    override fun getRecentlyAdded(): LiveData<List<Audiobook>> {
        return bookDao.getRecentlyAdded(limitReturnCount, prefsRepo.offlineMode)
    }

    override suspend fun getRecentlyAddedAsync(): List<Audiobook> {
        return withContext(Dispatchers.IO) {
            bookDao.getRecentlyAddedAsync(limitReturnCount, prefsRepo.offlineMode)
        }
    }

    override fun getRecentlyListened(): LiveData<List<Audiobook>> {
        return bookDao.getRecentlyListened(limitReturnCount, prefsRepo.offlineMode)
    }

    override suspend fun getRecentlyListenedAsync(): List<Audiobook> {
        return withContext(Dispatchers.IO) {
            bookDao.getRecentlyListenedAsync(limitReturnCount, prefsRepo.offlineMode)
        }
    }

    override suspend fun updateProgress(bookId: Int, lastViewedAt: Long, progress: Long) {
        withContext(Dispatchers.IO) {
            bookDao.updateProgress(bookId, lastViewedAt, progress)
        }
    }

    override suspend fun searchAsync(query: String): List<Audiobook> {
        return withContext(Dispatchers.IO) {
            bookDao.searchAsync("%$query%", prefsRepo.offlineMode)
        }
    }

    override fun search(query: String): LiveData<List<Audiobook>> {
        return bookDao.search("%$query%", prefsRepo.offlineMode)
    }

    override suspend fun update(audiobook: Audiobook) {
        withContext(Dispatchers.IO) {
            // set the chapters stored in the db to also be cached
            bookDao.update(audiobook)
        }
    }

    override suspend fun updateCachedStatus(bookId: Int, isCached: Boolean) {
        withContext(Dispatchers.IO) {
            // set the chapters stored in the db to also be cached
            bookDao.updateCachedStatus(bookId, isCached)
            val audiobook = bookDao.getAudiobookAsync(bookId)
            audiobook?.let { book ->
                bookDao.update(book.copy(chapters = book.chapters.map { it.copy(downloaded = isCached) }))
            }
        }
    }

    override suspend fun setWatched(bookId: Int) {
        withContext(Dispatchers.IO) {
            try {
                plexMediaService.watched(bookId.toString())
                bookDao.setWatched(bookId)
                bookDao.resetBookProgress(bookId)
            } catch (t: Throwable) {
                Timber.e("Failed to update watched status: $t")
            }
        }
    }

    override suspend fun setUnwatched(bookId: Int) {
        withContext(Dispatchers.IO) {
            try {
                plexMediaService.unwatched(bookId.toString())
                bookDao.setUnwatched(bookId)
            } catch (t: Throwable) {
                Timber.e("Failed to update watched status: $t")
            }
        }
    }

    override suspend fun getMostRecentlyPlayed(): Audiobook {
        return bookDao.getMostRecent() ?: EMPTY_AUDIOBOOK
    }

    override suspend fun getAudiobookAsync(bookId: Int): Audiobook? {
        return withContext(Dispatchers.IO) {
            bookDao.getAudiobookAsync(bookId)
        }
    }

    override fun getCachedAudiobooks(): LiveData<List<Audiobook>> {
        return bookDao.getCachedAudiobooks()
    }

    override suspend fun getCachedAudiobooksAsync(): List<Audiobook> {
        return withContext(Dispatchers.IO) {
            bookDao.getCachedAudiobooksAsync()
        }
    }

    override suspend fun uncacheAll() {
        withContext(Dispatchers.IO) {
            bookDao.uncacheAll()
        }
    }

    override suspend fun getAllBooksAsync(): List<Audiobook> {
        return withContext(Dispatchers.IO) {
            bookDao.getAllBooksAsync(prefsRepo.offlineMode)
        }
    }

    override suspend fun getRandomBookAsync(): Audiobook {
        return withContext(Dispatchers.IO) {
            bookDao.getRandomBookAsync() ?: EMPTY_AUDIOBOOK
        }
    }

    override suspend fun syncAudiobook(
        audiobook: Audiobook,
        tracks: List<MediaItemTrack>,
        forceNetwork: Boolean,
    ): Boolean {
        Timber.i(
            "Loading chapter data. Book ID is ${audiobook.id}, it is ${
                if (audiobook.isCached) "cached" else "uncached"
            }, tracks are $tracks"
        )
        withContext(Dispatchers.IO) {
            val chapters: List<Chapter> = try {
                tracks.flatMap { track ->
                    val networkChapters = plexMediaService.retrieveChapterInfo(track.id)
                        .plexMediaContainer.metadata.firstOrNull()?.plexChapters
                    if (BuildConfig.DEBUG) {
                        // prevent networkChapters from toString()ing and being slow even if timber
                        // tree isn't attached in the release build
                        Timber.i("Network chapters: $networkChapters")
                    }
                    // If no chapters for this track, make a chapter from the current track
                    networkChapters?.map { plexChapter ->
                        plexChapter.toChapter(
                            track.id.toLong(),
                            track.discNumber,
                            audiobook.isCached
                        )
                    }.takeIf { !it.isNullOrEmpty() } ?: listOf(track.asChapter(0L))
                }.sorted()
            } catch (t: Throwable) {
                Timber.e("Failed to load chapters: $t")
                return@withContext false
            }

            val networkBook = try {
                val retrievedBook = fetchBookAsync(audiobook.id)
                retrievedBook ?: return@withContext false
            } catch (t: Throwable) {
                Timber.e("Failed to load audiobook update")
                return@withContext false
            }

            Timber.i("Loaded chapters: ${chapters.map { "[${it.index}/${it.discNumber}]" }}")

            val merged = Audiobook.merge(
                network = networkBook,
                local = audiobook,
                forceNetwork = forceNetwork,
            ).copy(
                progress = tracks.getProgress(),
                duration = tracks.getDuration(),
                chapters = chapters
            )
            bookDao.update(merged)

        }
        return true
    }

    override suspend fun fetchBookAsync(bookId: Int): Audiobook? = withContext(Dispatchers.IO) {
        plexMediaService.retrieveAlbum(bookId)
            .plexMediaContainer
            .asAudiobooks()
            .firstOrNull()
    }
}
