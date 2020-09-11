package io.github.mattpvaughn.chronicle.data.model

import androidx.room.TypeConverter
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND


data class Chapter constructor(
    val title: String = "",
    val id: Long = 0L,
    val index: Long = 0L,
    val discNumber: Int = 1,
    // The number of milliseconds from the start of the containing track and the start of the chapter
    val startTimeOffset: Long = 0L,
    // The number of milliseconds between the start of the containing track and the end of the chapter
    val endTimeOffset: Long = 0L,
    val downloaded: Boolean = false,
    val trackId : Long = TRACK_NOT_FOUND.toLong()
) : Comparable<Chapter> {
    /** A string representing the index but padded to [length] characters with zeroes */
    fun paddedIndex(length: Int): String {
        return index.toString().padStart(length, '0')
    }

    override fun compareTo(other: Chapter): Int {
        val discCompare = discNumber.compareTo(other.discNumber)
        if (discCompare != 0) {
            return discCompare
        }
        return index.compareTo(other.index)
    }
}

val EMPTY_CHAPTER = Chapter("")

/**
 * Returns the chapter which contains the [timeStamp] (the playback progress of the track containing
 * this chapter), or [EMPTY_TRACK] if there is no chapter
 */
fun List<Chapter>.getChapterAt(timeStamp: Long): Chapter {
    for (chapter in this) {
        if (timeStamp >= chapter.startTimeOffset && timeStamp <= chapter.endTimeOffset) {
            return chapter
        }
    }
    return EMPTY_CHAPTER
}

class ChapterListConverter {

    @TypeConverter
    fun toChapterList(s: String): List<Chapter> {
        if (s.isEmpty()) {
            return emptyList()
        }
        return s.split("®").map {
            val split = it.split("©")
            val discNumber = if (split.size >= 6) split[5].toInt() else 1
            val downloaded = if (split.size >= 7) split[6].toBoolean() else false
            val parentId = if (split.size >= 8) split[7].toLong() else TRACK_NOT_FOUND.toLong()
            Chapter(
                title = split[0],
                id = split[1].toLong(),
                index = split[2].toLong(),
                startTimeOffset = split[3].toLong(),
                endTimeOffset = split[4].toLong(),
                discNumber = discNumber,
                downloaded = downloaded,
                trackId = parentId
            )
        }
    }

    // A little yikes but funny
    @TypeConverter
    fun toString(chapters: List<Chapter>): String {
        return chapters.joinToString("®") { "${it.title}©${it.id}©${it.index}©${it.startTimeOffset}©${it.endTimeOffset}©${it.discNumber}©${it.downloaded}©${it.trackId}" }
    }
}

