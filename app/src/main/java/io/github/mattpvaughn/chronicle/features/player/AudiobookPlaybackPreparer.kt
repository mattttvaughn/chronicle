package io.github.mattpvaughn.chronicle.features.player

import android.support.v4.media.MediaMetadataCompat
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.toMediaMetadata
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig

fun buildPlaylist(
    tracks: List<MediaItemTrack>,
    plexConfig: PlexConfig,
): List<MediaMetadataCompat> {
    return tracks.map { track -> track.toMediaMetadata(plexConfig) }
}
