package io.github.mattpvaughn.chronicle.data.sources.demo

import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.navigation.Navigator
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


class DemoMediaSource(
    override val id: Long,
    application: ChronicleApplication
) : MediaSource(application) {

    override val name: String
        get() = "Demo Library"

    override val icon: Int
        get() = R.drawable.ic_library_music

    override val dataSourceFactory: DefaultDataSourceFactory
        get() = TODO("Not yet implemented")

    @ExperimentalTime
    private val audiobooks = listOf(
        Audiobook(
            id = 1,
            source = id,
            title = "Demo book 1",
            titleSort = "Demo book 1",
            author = "Author",
            duration = 1000.seconds.toLongMilliseconds(),
            isCached = true
        ),
        Audiobook(
            id = 2,
            source = id,
            title = "Demo book 2",
            titleSort = "Demo book 2",
            author = "Author",
            duration = 2000.seconds.toLongMilliseconds(),
            isCached = true
        )
    )

    @ExperimentalTime
    private val tracks = listOf(
        // sole track for demo book 1
        MediaItemTrack(
            id = 3,
            parentKey = 1,
            title = "Track 1",
            playQueueItemID = 1,
            index = 1,
            duration = 1000.seconds.toLongMilliseconds(),
            album = "Demo book 1",
            artist = "Author",
            media = "demo_book_1",
            cached = true
        ),
        // sole track for demo book
        MediaItemTrack(
            id = 4,
            parentKey = 2,
            title = "Track 1",
            playQueueItemID = 1,
            index = 1,
            duration = 2000.seconds.toLongMilliseconds(),
            album = "Demo book 2",
            artist = "Author",
            media = "demo_book_2",
            cached = true
        )
    )

    @ExperimentalTime
    override suspend fun fetchBooks(): Result<List<Audiobook>> {
        return Result.success(audiobooks)
    }

    @ExperimentalTime
    override suspend fun fetchTracks(): Result<List<MediaItemTrack>> {
        return Result.success(tracks)
    }


    /** No setup to do, just brings the user home */
    override fun setup(navigator: Navigator) {
        navigator.showHome()
    }

    override fun type(): String {
        return TAG
    }

    // false as it's already on device
    override val isDownloadable: Boolean
        get() = false

    companion object {
        const val TAG = "DemoMediaSource"
    }
}