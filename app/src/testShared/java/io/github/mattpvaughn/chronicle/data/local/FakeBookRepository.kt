package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.MediaSource.Companion.NO_SOURCE_FOUND
import javax.inject.Inject

class FakeBookRepository @Inject constructor() : IBookRepository {

    companion object {
        val books = makeBooks()

        private fun makeBooks(): MutableList<Audiobook> {
            return (0..20).map { makeAudiobook(it) }.toMutableList()
        }

        private fun makeAudiobook(id: Int): Audiobook {
            return Audiobook(id = id, source = NO_SOURCE_FOUND)
        }
    }

    override fun getAllBooks(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }


    override suspend fun clear() {
        books.clear()
    }

    override suspend fun updateTrackData(
        bookId: Int,
        bookProgress: Long,
        bookDuration: Long,
        trackCount: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun getAudiobook(bookId: Int): LiveData<Audiobook?> {
        TODO("Not yet implemented")
    }


    override suspend fun getAudiobookAsync(bookId: Int): Audiobook? {
        return books.find { it.id == bookId }
    }

    override fun getRecentlyAdded(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun getRecentlyAddedAsync(): List<Audiobook> {
        TODO("Not yet implemented")
    }

    override fun getRecentlyListened(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun getRecentlyListenedAsync(): List<Audiobook> {
        TODO("Not yet implemented")
    }

    override suspend fun updateProgress(bookId: Int, currentTime: Long, progress: Long) {
        TODO("Not yet implemented")
    }

    override fun search(query: String): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun searchAsync(query: String): List<Audiobook> {
        return books
    }

    override suspend fun getMostRecentlyPlayed(): Audiobook {
        return books.first()
    }

    override fun getCachedAudiobooks(): LiveData<List<Audiobook>> {
        return MutableLiveData(books)
    }

    override suspend fun getCachedAudiobooksAsync(): List<Audiobook> {
        TODO("Not yet implemented")
    }

    override suspend fun uncacheAll() {}

    override suspend fun getAllBooksAsync(): List<Audiobook> {
        return books
    }

    override suspend fun getRandomBookAsync(): Audiobook {
        TODO("Not yet implemented")
    }

    override suspend fun upsert() {
        TODO("Not yet implemented")
    }

    override suspend fun getBookCount(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun loadChapterData(
        audiobook: Audiobook,
        tracks: List<MediaItemTrack>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun update(audiobook: Audiobook) {
        TODO("Not yet implemented")
    }


}