package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
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
    suspend fun refreshData(): List<Result<Unit>>

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
     * from the network and saves it to the DB if there are chapters found.
     *
     * @return true if chapter data was found and added to db, otherwise false
     */
    suspend fun loadChapterData(audiobook: Audiobook, tracks: List<MediaItemTrack>): Boolean
    suspend fun update(audiobook: Audiobook)
}

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val prefsRepo: PrefsRepo,
    private val plexLibrarySource: PlexLibrarySource,
    private val sourceManager: SourceManager
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
    override suspend fun refreshData(): List<Result<Unit>> {
        if (prefsRepo.offlineMode) {
            return listOf(Result.success(Unit))
        }
        prefsRepo.lastRefreshTimeStamp = System.currentTimeMillis()
        val queryResults: List<Pair<Long, Result<List<Audiobook>>>> = sourceManager.fetchBooks()

        return queryResults.map { queryResult ->
            val sourceId = queryResult.first
            if (queryResult.second.isFailure) {
                return@map Result.failure<Unit>(queryResult.second.exceptionOrNull() ?: Exception())
            }
            val localBooks = withContext(Dispatchers.IO) {
                bookDao.getAudiobooksForSourceAsync(sourceId, false)
            }
            val networkBooks = queryResult.second.getOrNull() ?: emptyList()

            val mergedBooks = networkBooks.map { networkBook ->
                val localBook = localBooks.find { it.id == networkBook.id }
                if (localBook != null) {
                    // [Audiobook.merge] chooses fields depending on [Audiobook.lastViewedAt]
                    Audiobook.merge(network = networkBook, local = localBook)
                } else {
                    networkBook
                }
            }

            // Remove books which have been deleted from the server
            val networkIds = networkBooks.map { networkBook -> networkBook.id }
            val removedFromNetwork = localBooks.filter { localBook ->
                !networkIds.contains(localBook.id)
            }

            withContext(Dispatchers.IO) {
                val removed = bookDao.removeAll(removedFromNetwork.map { it.id.toString() })
                Timber.i("Removed $removed items from DB")
                bookDao.insertAll(mergedBooks)
                Timber.i("Loaded books: $mergedBooks")
            }
            return@map Result.success(Unit)
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

    override suspend fun updateProgress(bookId: Int, currentTime: Long, progress: Long) {
        withContext(Dispatchers.IO) {
            bookDao.updateProgress(bookId, currentTime, progress)
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

    override suspend fun loadChapterData(
        audiobook: Audiobook,
        tracks: List<MediaItemTrack>
    ): Boolean {
        val chapters: List<Chapter> = withContext(Dispatchers.IO) {
            try {
                plexLibrarySource.fetchChapterInfo(audiobook.isCached, tracks)
            } catch (t: Throwable) {
                Timber.e("Failed to load chapters: $t")
                emptyList<Chapter>()
            }
        }
        return if (chapters.isNotEmpty()) {
            // Update [Audiobook.chapters] in the local db
            val replacementBook = audiobook.copy(chapters = chapters)
            withContext(Dispatchers.IO) {
                bookDao.update(replacementBook)
            }
            true
        } else {
            // no good chapter data found- use the track data instead
            val replacementBook = audiobook.copy(chapters = tracks.asChapterList())
            withContext(Dispatchers.IO) {
                bookDao.update(replacementBook)
            }
            false
        }
    }
}
