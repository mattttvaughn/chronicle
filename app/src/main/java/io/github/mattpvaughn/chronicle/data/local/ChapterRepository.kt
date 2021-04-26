package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A repository abstracting all [Chapter]s
 *
 * TODO: this is a work in progress- usage is not recommended. Eventually we will silently move
 *       all chapters from being an attribute of [Audiobook] into this repo
 *
 * */
interface IChapterRepository {

    /**
     * Loads m4b chapter data and any other audiobook details which are not loaded in by default
     * by [BookRepository] or [TrackRepository] and saves results to the DB
     */
    suspend fun loadChapterData(isAudiobookCached: Boolean, tracks: List<MediaItemTrack>)

    /** Fetch all chapters */
    fun getChaptersForBook(bookId: Int): LiveData<List<Chapter>>
}

@Singleton
class ChapterRepository @Inject constructor(
    private val chapterDao: ChapterDao,
    private val sourceManager: SourceManager,
) : IChapterRepository {

    override suspend fun loadChapterData(
        isAudiobookCached: Boolean,
        tracks: List<MediaItemTrack>
    ) = withContext(Dispatchers.IO) {
        Timber.i("Loading chapter data for tracks: $tracks")
        chapterDao.insertAll(chapters)
    }

    override fun getChaptersForBook(bookId: Int): LiveData<List<Chapter>> {
        return chapterDao.getChaptersInBook(bookId)
    }
}
