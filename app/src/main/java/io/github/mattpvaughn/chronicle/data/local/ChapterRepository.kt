package io.github.mattpvaughn.chronicle.data.local

import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.asChapter
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexMediaService
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.model.toChapter
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
}

@Singleton
class ChapterRepository @Inject constructor(
    private val chapterDao: ChapterDao,
    private val prefsRepo: PrefsRepo,
    private val plexPrefsRepo: PlexPrefsRepo,
    private val plexMediaService: PlexMediaService
) : IChapterRepository {

    override suspend fun loadChapterData(
        isAudiobookCached: Boolean,
        tracks: List<MediaItemTrack>
    ) = withContext(Dispatchers.IO) {
        Timber.i("Loading chapter data for tracks: $tracks")
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
                        isAudiobookCached
                    )
                }.takeIf { !it.isNullOrEmpty() } ?: listOf(track.asChapter(0L))
            }.sorted()
        } catch (t: Throwable) {
            Timber.e("Failed to load chapters: $t")
            emptyList()
        }
        chapterDao.insertAll(chapters)
    }
}
