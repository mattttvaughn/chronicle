package io.github.mattpvaughn.chronicle.data.local

import android.util.Log
import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexMediaApi
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton.libraryId
import io.github.mattpvaughn.chronicle.data.plex.model.asAudiobooks
import io.github.mattpvaughn.chronicle.data.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


interface IBookRepository {
    fun getAllBooks(): LiveData<List<Audiobook>>

    /**
     * Refreshes the data in the local database with elements from the network
     */
    suspend fun refreshData(trackRepository: ITrackRepository? = null)

    /** Removes all books from the local database */
    suspend fun clear()

    /** Updates the book with information regarding the tracks contained within the book */
    suspend fun updateTrackData(id: Int, duration: Long, trackCount: Int)

    /**
     * Returns a [LiveData<Audiobook>] corresponding to an [Audiobook] with the [Audiobook.id]
     * equal to [id]
     */
    fun getAudiobook(id: Int): LiveData<Audiobook>

    /**
     * Return an [Audiobook] with the [Audiobook.id] field equal to [bookId], or null if no such
     * audiobook exists
     */
    suspend fun getAudiobookAsync(bookId: Int): Audiobook?

    /**
     * Returns the [bookCount] most recently added books in the local database, ordered by most
     * newly added to oldest
     */
    fun getRecentlyAdded(): LiveData<List<Audiobook>>

    /**
     * Returns the [bookCount] most recently added listened to books in the local database, ordered
     * from most recently listened to last listened book
     */
    fun getRecentlyListened(): LiveData<List<Audiobook>>

    /**
     * Returns the [bookCount] most recently added listened to books in the local database, ordered
     * from most recently listened to last listened book
     */
    suspend fun getRecentlyListenedAsync(): List<Audiobook>?

    /** Update the [Audiobook.lastViewedAt] field to [currentTime] for a book with id [bookId] */
    suspend fun updateLastViewedAt(bookId: Int, currentTime: Long)

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

    /** Update the [Audiobook.isCached] field to [isCached] for an audiobook with id [bookId] */
    suspend fun updateCached(bookId: Int, isCached: Boolean)

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

    /** Sets the [Audiobook.isCached] for all [Audiobook]s in the DB to false */
    suspend fun uncacheAll()

    /** Return all [Audiobook]s in the DB asynchronously */
    suspend fun getAllBooksAsync(): List<Audiobook>
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

    override suspend fun refreshData(trackRepository: ITrackRepository?) {
        if (prefsRepo.offlineMode) {
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val networkBooks =
                    PlexMediaApi.retrofitService.retrieveAllAlbums(libraryId).asAudiobooks()
                val localBooks = bookDao.getAudiobooks()
                /**
                 * Keep the network or local copy with the most recent [Audiobook.lastViewedAt]
                 * field
                 */
                val mergedBooks = networkBooks.map { networkBook ->
                    val localBook = localBooks.find { it.id == networkBook.id }
                    if (localBook != null) {
                        if (trackRepository != null && networkBook.lastViewedAt > localBook.lastViewedAt) {
                            val tracks = trackRepository.loadTracksForAudiobook(networkBook.id)
                            updateTrackData(networkBook.id, tracks.getDuration(), tracks.size)
                        }
                        return@map Audiobook.merge(networkBook = networkBook, localBook = localBook)
                    } else {
                        return@map networkBook
                    }
                }
                bookDao.insertAll(mergedBooks)
            } catch (e: Error) {
                Log.e(APP_NAME, "Failed to refresh audiobook library: $e")
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            bookDao.clear()
        }
    }

    override suspend fun updateTrackData(id: Int, duration: Long, trackCount: Int) {
        withContext(Dispatchers.IO) {
            bookDao.updateTrackData(id, duration, trackCount)
        }
    }

    override fun getAudiobook(id: Int): LiveData<Audiobook> {
        return bookDao.getAudiobook(id, prefsRepo.offlineMode)
    }

    override fun getRecentlyAdded(): LiveData<List<Audiobook>> {
        return bookDao.getRecentlyAdded(limitReturnCount, prefsRepo.offlineMode)
    }

    override fun getRecentlyListened(): LiveData<List<Audiobook>> {
        return bookDao.getRecentlyListened(limitReturnCount, prefsRepo.offlineMode)
    }

    override suspend fun getRecentlyListenedAsync(): List<Audiobook>? {
        return withContext(Dispatchers.IO) {
            bookDao.getRecentlyListenedAsync(limitReturnCount, prefsRepo.offlineMode)
        }
    }

    override suspend fun updateLastViewedAt(bookId: Int, currentTime: Long) {
        withContext(Dispatchers.IO) {
            bookDao.updateLastViewedAt(bookId, currentTime)
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

    override suspend fun updateCached(bookId: Int, isCached: Boolean) {
        withContext(Dispatchers.IO) {
            bookDao.updateCached(bookId, isCached)
        }
    }

    override suspend fun getMostRecentlyPlayed(): Audiobook {
        return bookDao.getMostRecent()
    }

    override suspend fun getAudiobookAsync(bookId: Int): Audiobook? {
        return withContext(Dispatchers.IO) {
            bookDao.getAudiobookAsync(bookId)
        }
    }

    override fun getCachedAudiobooks(): LiveData<List<Audiobook>> {
        return bookDao.getCachedAudiobooks()
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
}