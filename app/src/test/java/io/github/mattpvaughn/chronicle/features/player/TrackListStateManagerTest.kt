package io.github.mattpvaughn.chronicle.features.player

import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Test

class TrackListStateManagerTest {

    val exampleTrackList = listOf(
        MediaItemTrack(1, progress = 0, duration = 50, lastViewedAt = 1),
        MediaItemTrack(2, progress = 25, duration = 50, lastViewedAt = 3),
        MediaItemTrack(3, progress = 0, duration = 50, lastViewedAt = 0)
    )

    val manager = TrackListStateManager()

    @Before
    fun setupManager() {
        manager.trackList = exampleTrackList
    }

    @Test
    fun updatePosition() {
    }

    @Test
    fun seekToActiveTrack() {
        manager.seekToActiveTrack()

        // assert track index correct
        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )
    }

    @Test
    fun `test seeking forwards within track`() {
        manager.updatePosition(1, 25)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )

        manager.seekByRelative(20)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (45L)))
        )

    }

    @Test
    fun `test seeking forwards into to next track`() {
        manager.updatePosition(1, 25)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )

        manager.seekByRelative(40)

        assertThat(
            Pair(manager.currentTrackProgress, manager.currentTrackIndex),
            `is`(Pair(15L, 2))
        )
    }

    @Test
    fun `test seeking forwards beyond end of track list`() {
        manager.updatePosition(1, 25)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )

        manager.seekByRelative(1000)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((2), (50L)))
        )
    }

    @Test
    fun `test seeking forwards beyond end of track list, starting with finished track`() {
        manager.updatePosition(2, 50)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((2), (50L)))
        )

        manager.seekByRelative(1000)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((2), (50L)))
        )
    }

    @Test
    fun `test seeking backwards within track`() {
        manager.updatePosition(1, 25)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )

        manager.seekByRelative(-15)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair(1, 10L))
        )
    }

    @Test
    fun `test seeking backwards into previous track`() {
        manager.updatePosition(1, 25)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )

        manager.seekByRelative(-40)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((0), (35L)))
        )
    }

    @Test
    fun `test seeking backwards starting at index == 0, offset == 0`() {
        manager.updatePosition(0, 0)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((0), (0L)))
        )

        manager.seekByRelative(-20)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((0), (0L)))
        )
    }

    @Test
    fun `test seeking backwards beyond start of track list`() {
        manager.updatePosition(1, 25)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )

        manager.seekByRelative(-1000)

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((0), (0L)))
        )
    }

    @Test
    fun seekToTrack() {
        manager.seekToActiveTrack()

        assertThat(
            Pair(manager.currentTrackIndex, manager.currentTrackProgress),
            `is`(Pair((1), (25L)))
        )

    }
}