package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.data.model.Audiobook

class FakeBookRepository : IBookRepository {

    companion object {
        val books = makeBooks()

        private fun makeBooks(): MutableList<Audiobook> {
            return (0..20).map { makeAudiobook(it) }.toMutableList()
        }

        private fun makeAudiobook(id: Int): Audiobook {
            return Audiobook(id = id)
        }
    }

    override fun getAllBooks(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun refreshData(trackRepository: ITrackRepository?) {}

    override suspend fun clear() {
        books.clear()
    }

    override suspend fun updateTrackData(id: Int, duration: Long, trackCount: Int) {}

    override fun getAudiobook(id: Int): LiveData<Audiobook> {
        require(books.isNotEmpty())
        return MutableLiveData<Audiobook>(books[0])
    }

    override suspend fun getAudiobookAsync(bookId: Int): Audiobook? {
        return books.find { it.id == bookId }
    }

    override fun getRecentlyAdded(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override fun getRecentlyListened(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun getRecentlyListenedAsync(): List<Audiobook>? {
        return books
    }

    override suspend fun updateLastViewedAt(bookId: Int, currentTime: Long) {}

    override fun search(query: String): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun searchAsync(query: String): List<Audiobook> {
        return books
    }

    override suspend fun updateCached(bookId: Int, isCached: Boolean) {}

    override suspend fun getMostRecentlyPlayed(): Audiobook {
        return books.first()
    }

    override fun getCachedAudiobooks(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun uncacheAll() {}

    override suspend fun getAllBooksAsync(): List<Audiobook> {
        return books
    }


}