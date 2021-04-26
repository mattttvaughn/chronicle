package io.github.mattpvaughn.chronicle.data.sources.local

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.util.extractColumnNullable
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaParser @Inject constructor(
    private val application: ChronicleApplication
) {
    fun parseMedia(sourceId: Long, root: DocumentFile): List<MediaItemTrack> {
        Timber.i("Root files: ${root.listFiles().map { it.name }}")

        // Map parent directory to contained mediaFiles
        val tracksByDirectory = mutableMapOf<DocumentFile, MutableSet<TrackFileDetails>>()
        val imagesByDirectory = mutableMapOf<DocumentFile, Uri>()
        val maxDepth = 5
        val mediaDirectories = mutableListOf(root)
        listMediaDirectories(root, maxDepth, mediaDirectories)
        Timber.i("Media directories: ${mediaDirectories.map { it.name }}")
        var minUnusedTrackId = 0
        mediaDirectories.forEach { mediaDir ->
            mediaDir.listFiles().forEach mediaFile@{ mediaFile ->
                if (mediaFile.isDirectory) {
                    return@mediaFile
                }
                if (mediaFile.type in IMAGE_MIME_TYPES) {
                    imagesByDirectory[mediaDir] = mediaFile.uri
                }
                application.contentResolver.openFileDescriptor(mediaFile.uri, "r").use { desc ->
                    val mmr = MediaMetadataRetriever()
                    mmr.use {
                        try {
                            mmr.setDataSource(desc?.fileDescriptor)
                            val tracksForDir =
                                tracksByDirectory.getOrPut(mediaDir) { mutableSetOf() }
                            tracksForDir.add(
                                makeTrackFromMetadata(
                                    it,
                                    mediaFile,
                                    minUnusedTrackId++,
                                    parseMediaFileName(mediaFile.name ?: "")
                                )
                            )
                        } catch (e: Throwable) {
                            Timber.e("Failed to get data source for: ${mediaFile.name}")
                        }
                    }
                }
            }
        }
        val booksToTracks = tracksByDirectory.map { (mediaDir, tracksInDir) ->
            val image = imagesByDirectory[mediaDir]
            mediaDir to (if (image != null) {
                tracksInDir.map { it.copy(thumb = image.toString()) }
            } else {
                tracksInDir
            })
        }.flatMap { (mediaDir, tracksInDir) ->
            partitionByBook(mediaDir.name ?: "", tracksInDir)
        }.filter {
            it.second.isNotEmpty()
        }

        var minUnusedBookId = 0
        return booksToTracks.map { (bookName, tracks) ->
            val tempBookId = minUnusedBookId++
            bookName to orderTracks(tracks).map {
                it.toMediaItemTrack(sourceId, tempBookId)
            }
        }.flatMap {
            it.second
        }
    }

    /**
     * Given a list of track files from the same directory, partition them by book.
     *
     * Books will be determined as follows:
     *  - Tracks sharing a [TrackFileDetails.album] will be partitioned into
     *    a book of that name.
     *  - Remaining tracks in the folder will be partitioned together, with
     *    name determined by containing folder
     */
    private fun partitionByBook(
        containingDirectoryName: String,
        tracksInDirectory: Collection<TrackFileDetails>
    ): List<Pair<String, List<TrackFileDetails>>> {
        val partitionedByTrack = tracksInDirectory
            .filter { it.album.isNotBlank() }
            .groupBy { it.album }
            .toList()

        if (partitionedByTrack.size == tracksInDirectory.size) {
            return partitionedByTrack
        }

        val remaining = tracksInDirectory
            .filter { it.album.isBlank() }
            .map { it.copy(album = containingDirectoryName) }

        return partitionedByTrack.plus(containingDirectoryName to remaining)
    }

    /**
     * Given a list of tracks, order them. If possible, order the tracks by
     * [TrackFileDetails.trackNumber]. If [TrackFileDetails.trackNumber]s are
     * missing, try to fill in the gaps via [TrackFileDetails.possibleIndices].
     *
     * Update the [TrackFileDetails.trackNumber]s to match the ordering
     *
     * TODO: handle discNumber + trackNumber
     *
     * Otherwise, default to sorting by [TrackFileDetails.fileName]
     */
    private fun orderTracks(trackList: List<TrackFileDetails>): List<TrackFileDetails> {
        // Preserve disc numbers, sort within groups
        return trackList.groupBy { it.discNumber }
            .mapValues { (_, tracksInDisc) ->
                if (tracksInDisc.hasAllTrackNumbers()) {
                    trackList
                } else {
                    tryToContiguouify(trackList)
                }
            }.mapValues { (_, tracksInDisc) ->
                val anyMissingNumbering = tracksInDisc.any {
                    it.trackNumber == NO_TRACK_NUMBER_FOUND
                }
                if (anyMissingNumbering) {
                    tracksInDisc
                        .sortedBy { it.fileName }
                        .mapIndexed { idx, item -> item.copy(trackNumber = idx) }
                } else {
                    tracksInDisc
                }
            }.values.flatten()
    }

    /**
     * Given a [trackList] which does not have a contiguous set of
     * [TrackFileDetails.trackNumber]s, attempt to find a contiguous list of
     * numbers from a given column in [TrackFileDetails.possibleIndices]
     */
    private fun tryToContiguouify(trackList: List<TrackFileDetails>): List<TrackFileDetails> {
        if (trackList.size < 2) {
            return trackList
        }
        val discs = trackList.groupBy { it.discNumber }.toMutableMap()

        for ((discNumber, discList) in discs) {
            val maxIndices = discList.maxByOrNull { it.possibleIndices.size }!!.possibleIndices.size
            for (index in 0 until maxIndices) {
                val attempt = discList.map { it.possibleIndices[index] }
                if (isContiguous(attempt)) {
                    discs[discNumber] =
                        discList.map { it.copy(trackNumber = it.possibleIndices[index]) }
                }
            }
        }
        return trackList
    }

    /**
     * Checks whether there are any [TrackFileDetails] where either
     * [TrackFileDetails.trackNumber] == [NO_TRACK_NUMBER_FOUND] or the
     * track numbers are not contiguous within [TrackFileDetails.discNumber]
     * groups
     */
    private fun List<TrackFileDetails>.hasAllTrackNumbers(): Boolean {
        return groupBy { it.discNumber }
            .all { (_, tracksInDisk) ->
                isContiguous(tracksInDisk.map { it.trackNumber })
            }
    }

    /**
     * Check whether [Int]s in [intList] contain all [Int]s between
     * the max and min value.
     */
    private fun isContiguous(intList: List<Int>): Boolean {
        if (intList.size < 2) {
            return true
        }
        val indices = intList.toSet()
        val range = indices.minOrNull()!!..indices.maxOrNull()!!
        return range.all { it in indices }
    }

    private fun makeTrackFromMetadata(
        it: MediaMetadataRetriever,
        trackFile: DocumentFile,
        tempTrackId: Int,
        nameMetadata: TrackFileNameMetadata
    ): TrackFileDetails {

        val title = it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: nameMetadata.name
        val album = it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_ALBUM)

        val author = it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            ?: it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_WRITER)
            ?: it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            ?: it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)

        val genre = it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_GENRE)
        val duration = it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val trackNumber =
            it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        val discNumber = it.extractColumnNullable(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)

        return TrackFileDetails(
            id = tempTrackId,
            fileName = trackFile.name ?: "",
            title = title,
            genre = genre ?: "",
            duration = (duration?.toLongOrNull() ?: 0L),
            possibleIndices = nameMetadata.possibleIndices,
            trackNumber = trackNumber?.toIntOrNull() ?: NO_TRACK_NUMBER_FOUND,
            artist = author ?: "",
            discNumber = discNumber?.toIntOrNull() ?: 1,
            media = trackFile.uri.toString(),
            album = album ?: "",
            thumb = trackFile.uri.toString(),
        )
    }

    private data class TrackFileDetails(
        val id: Int,
        val fileName: String,
        val title: String,
        val genre: String,
        val duration: Long,
        val possibleIndices: List<Int>,
        val artist: String,
        val discNumber: Int,
        val media: String,
        val album: String,
        val thumb: String,
        val trackNumber: Int = NO_TRACK_NUMBER_FOUND,
    ) {
        fun toMediaItemTrack(sourceId: Long, tempBookId: Int) = MediaItemTrack(
            id = id,
            parentServerId = tempBookId,
            duration = duration,
            title = title,
            index = trackNumber,
            discNumber = discNumber,
            media = media,
            album = album,
            artist = artist,
            thumb = thumb,
            artwork = thumb,
            genre = genre,
            cached = true,
            source = sourceId,
        )
    }

    companion object {
        const val NO_TRACK_NUMBER_FOUND = Int.MIN_VALUE
        const val ID_NOT_YET_SET = Int.MIN_VALUE
        val IMAGE_MIME_TYPES = listOf(
            "image/jpg",
            "image/jpeg",
            "image/gif",
            "image/png",
            "image/webp"
        )
    }

    /**
     * Adds subdirectories of [file] which contain any number of non-directory
     * files to [mediaDirs], recursively down to a maximum depth of [maxDepth]
     */
    private fun listMediaDirectories(
        file: DocumentFile,
        maxDepth: Int,
        mediaDirs: MutableList<DocumentFile>
    ) {
        if (!file.isDirectory || maxDepth < 0) {
            return
        }
        val children = file.listFiles()
        val hasNonDirectoryChildren = children.any { !it.isDirectory }
        if (hasNonDirectoryChildren) {
            mediaDirs.add(file)
        }
        file.listFiles().forEach {
            listMediaDirectories(it, maxDepth - 1, mediaDirs)
        }
    }

    /**
     * Parses a media file's name to extract a title as well as an ordered list
     * of numbers, to be used to attempt to find a track number. Ambiguous whether
     * the title is the book or track title.
     *
     *  Example files (any file extension possible):
     *      "The Brothers Karamazov.mp3" -> Metadata(name = The Brothers Karamazov, matches = [])
     *      "01 - The Brothers Karamazov.mp3" -> Metadata(name = The Brothers Karamazov, matches = [1])
     *      "The Brothers Karamazov 05.mp3" -> Metadata(name = The Brothers Karamazov, matches = [5])
     *      "The Brothers Karamazov 01 of 22.mp3" -> Metadata(name = The Brothers Karamazov, matches = [1, 22])
     *      "The Brothers Karamazov 01-22.mp3" -> Metadata(name = The Brothers Karamazov, matches = [1, 22])
     */
    private fun parseMediaFileName(fileName: String): TrackFileNameMetadata {
        val contiguousNumberRegex = "[0-9]+".toRegex()

        val numberMatches = contiguousNumberRegex.findAll(fileName)
            .flatMap { it.groupValues }
            .mapNotNull { it.toIntOrNull() }
            .toList()

        return TrackFileNameMetadata(numberMatches, File(fileName).nameWithoutExtension)
    }

    private data class TrackFileNameMetadata(
        val possibleIndices: List<Int>,
        val name: String = ""
    )

}