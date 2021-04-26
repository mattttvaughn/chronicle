package io.github.mattpvaughn.chronicle.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import io.github.mattpvaughn.chronicle.data.model.Chapter

private const val CHAPTER_DATABASE_NAME = "chapter_db"

private lateinit var INSTANCE: ChapterDatabase
fun getChapterDatabase(context: Context): ChapterDatabase {
    synchronized(ChapterDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(
                context.applicationContext,
                ChapterDatabase::class.java,
                CHAPTER_DATABASE_NAME
            ).addMigrations().build()
        }
    }
    return INSTANCE
}

@Database(entities = [Chapter::class], version = 1, exportSchema = false)
abstract class ChapterDatabase : RoomDatabase() {
    abstract val chapterDao: ChapterDao
}

/**
 * Note: for the weird isCached <= :isOfflineModeActive queries, this ensures that cached items
 * are returned even when offline mode is inactive. A simple equality check would return only
 * cached items during offline mode, but only uncached items when offline mode is inactive. This
 * is an easy way to implement it at the DB level, avoiding messing code in the repository
 */
@Dao
interface ChapterDao {
    @Query("SELECT * FROM Chapter ORDER BY discNumber, `index`")
    fun getAllRows(): LiveData<List<Chapter>>

    @Query("SELECT * FROM Chapter WHERE bookId == :bookId ORDER BY discNumber, `index`")
    fun getChaptersInBook(bookId: Int): LiveData<List<Chapter>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rows: List<Chapter>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun update(chapter: Chapter)

    @Query("UPDATE Chapter SET downloaded = :cached WHERE id = :chapterId")
    fun updateCachedStatus(chapterId: Int, cached: Boolean)

    @Query("DELETE FROM Chapter WHERE id IN (:chaptersToRemove)")
    fun removeAll(chaptersToRemove: List<Long>): Int
}


