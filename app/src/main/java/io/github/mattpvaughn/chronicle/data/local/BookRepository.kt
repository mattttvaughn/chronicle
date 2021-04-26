package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.HttpMediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A repository abstracting all [Audiobook]s from all [MediaSource]s */
@Singleton
interface IBookRepository {
    /** Return all [Audiobook]s in the DB, sorted by [Audiobook.titleSort] */
    fun getAllBooks(): LiveData<List<Audiobook>>
    suspend fun getAllBooksAsync(): List<Audiobook>

    suspend fun getRandomBookAsync(): Audiobook

    /** Refreshes the data in the local database with elements from the network */
    suspend fun upsert(
        sourceId: Long,
        updateBooks: List<Audiobook>,
        isLocal: Boolean = false
    )

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
     * equal to [bookId]
     */
    fun getAudiobook(bookId: Int): LiveData<Audiobook?>
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
        source: HttpMediaSource,
        audiobook: Audiobook,
        tracks: List<MediaItemTrack>,
        forceNetwork: Boolean = false,
    ): Boolean

    suspend fun update(audiobook: Audiobook)

    /**
     * Updates the [Audiobook.isCached] column to be true for the [Audiobook] uniquely identified
     * by [Audiobook.id] == [bookId]
     */
    suspend fun updateCachedStatus(bookId: Int, isCached: Boolean)

    /** Sets the book's [Audiobook.progress] to 0 in the DB and the server */
    suspend fun setWatched(bookId: Int, source: MediaSource?)

    /** Loads an [Audiobook] in from the network */
    suspend fun fetchBookAsync(bookId: Int, source: HttpMediaSource): Audiobook?

    /** Removes all [Audiobook]s where [Audiobook.source] == [sourceId] */
    suspend fun removeWithSource(sourceId: Long)

    /** Returns all audiobooks where [Audiobook.source] == [sourceId] */
    suspend fun getAudiobooksForSourceAsync(
        sourceId: Long,
        isOfflineModeActive: Boolean
    ): List<Audiobook>
}

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val prefsRepo: PrefsRepo
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
    override suspend fun upsert(sourceId: Long, updateBooks: List<Audiobook>, isLocal: Boolean) {

        val localBooks = withContext(Dispatchers.IO) {
            bookDao.getAudiobooksForSourceAsync(sourceId, false)
        }.associateBy { it.serverId }

        val mergedBooks = updateBooks.map { book ->
            val dbBook = if (isLocal) {
                // Match book on filesystem to book in DB by title
                localBooks.values.find { it.title == book.title }
            } else {
                localBooks[book.serverId]
            }
            val updateBook = if (isLocal && dbBook != null) {
                book.copy(id = dbBook.id)
            } else {
                book
            }
            if (dbBook != null) {
                // [Audiobook.merge] chooses fields depending on [Audiobook.lastViewedAt]
                Audiobook.merge(network = updateBook, local = dbBook)
            } else {
                updateBook
            }
        }

        Timber.i("Merged books: ${mergedBooks.map { it.title }}")

        // Remove books which have been deleted from the source
        val networkIds = updateBooks.map { networkBook -> networkBook.id }.toSet()
        val removedFromSource = localBooks.filterValues { localBook ->
            localBook.id !in networkIds
        }.values

        withContext(Dispatchers.IO) {
            val removedCount = bookDao.removeAll(removedFromSource.map { it.id.toString() })
            Timber.i("Removed $removedCount items from DB")
            bookDao.insertAll(mergedBooks)
            Timber.i("Loaded books: $mergedBooks")
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

    override fun getAudiobook(bookId: Int): LiveData<Audiobook?> {
        return bookDao.getAudiobook(bookId, prefsRepo.offlineMode)
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

    override suspend fun removeWithSource(sourceId: Long) {
        withContext(Dispatchers.IO) {
            // set the chapters stored in the db to also be cached
            bookDao.removeWithSource(sourceId)
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

    override suspend fun setWatched(bookId: Int, source: MediaSource?) {
        withContext(Dispatchers.IO) {
            try {
                bookDao.setWatched(bookId)
                bookDao.resetBookProgress(bookId)
                if (source is HttpMediaSource) {
                    source.watched(bookId)
                }
            } catch (t: Throwable) {
                Timber.e("Failed to update watched status: $t")
            }
        }
    }

    override suspend fun getAudiobooksForSourceAsync(
        sourceId: Long,
        isOfflineModeActive: Boolean
    ): List<Audiobook> {
        return bookDao.getAudiobooksForSourceAsync(sourceId, isOfflineModeActive)
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
        source: HttpMediaSource,
        audiobook: Audiobook,
        tracks: List<MediaItemTrack>,
        forceNetwork: Boolean,
    ): Boolean {
        Timber.i("Loading chapter data. Bookid is ${audiobook.id}, tracks are $tracks")
        return withContext(Dispatchers.IO) {
            val chapters = try {
                source.fetchChapterInfo(audiobook.isCached, tracks)
            } catch (t: Throwable) {
                Timber.e("Failed to load chapters: $t")
                return@withContext false
            }

            val networkBook = try {
                val retrievedBook = fetchBookAsync(audiobook.id, source)
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
            )
            bookDao.update(merged)
            return@withContext true
        }
    }


    override suspend fun fetchBookAsync(bookId: Int, source: HttpMediaSource): Audiobook? =
        withContext(Dispatchers.IO) {
            return@withContext source.fetchBook(bookId)
        }
}
