package io.github.mattpvaughn.chronicle.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mattpvaughn.chronicle.data.model.Audiobook


private const val BOOK_DATABASE_NAME = "book_db"

private lateinit var INSTANCE: BookDatabase
fun getBookDatabase(context: Context): BookDatabase {
    synchronized(BookDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(
                context.applicationContext,
                BookDatabase::class.java,
                BOOK_DATABASE_NAME
            ).addMigrations(BOOK_MIGRATION_1_2).build()
        }
    }
    return INSTANCE
}

val BOOK_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Do nothing lol
    }

}

@Database(entities = [Audiobook::class], version = 2, exportSchema = false)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookDao: BookDao
}

/**
 * Note: for the weird isCached <= :isOfflineModeActive queries, this ensures that cached items
 * are returned even when offline mode is inactive. A simple equality check would return only
 * cached items during offline mode, but only uncached items when offline mode is inactive. This
 * is a way to implement it at the DB level, avoiding messing code in the repository
 */
@Dao
interface BookDao {
    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY title")
    fun getAllRows(offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook")
    fun getAudiobooks(): List<Audiobook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rows: List<Audiobook>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun update(audiobook: Audiobook)

    @Query("SELECT * FROM Audiobook WHERE id = :id AND isCached >= :isOfflineModeActive LIMIT 1")
    fun getAudiobook(id: Int, isOfflineModeActive: Boolean): LiveData<Audiobook>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY addedAt DESC LIMIT :bookCount")
    fun getRecentlyAdded(bookCount: Int, offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook ORDER BY updatedAt DESC LIMIT 25")
    fun getOnDeck(): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY lastViewedAt DESC LIMIT :bookCount")
    fun getRecentlyListened(bookCount: Int, offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY lastViewedAt DESC LIMIT :bookCount")
    suspend fun getRecentlyListenedAsync(bookCount: Int, offlineModeActive: Boolean): List<Audiobook>

    @Query("UPDATE Audiobook SET lastViewedAt = :currentTime WHERE lastViewedAt < :currentTime AND id = :bookId")
    fun updateLastViewedAt(bookId: Int, currentTime: Long)

    @Query("UPDATE Audiobook SET duration = :duration, leafCount = :trackCount WHERE id = :id")
    suspend fun updateTrackData(id: Int, duration: Long, trackCount: Int)

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive AND (title LIKE :query OR author LIKE :query)")
    fun search(query: String, offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive AND (title LIKE :query OR author LIKE :query)")
    fun searchAsync(query: String, offlineModeActive: Boolean): List<Audiobook>

    @Query("UPDATE Audiobook SET isCached = :cached WHERE id = :bookId")
    fun updateCached(bookId: Int, cached: Boolean)

    @Query("DELETE FROM Audiobook")
    suspend fun clear()

    @Query("SELECT * FROM Audiobook ORDER BY lastViewedAt DESC LIMIT 1")
    suspend fun getMostRecent(): Audiobook

    @Query("SELECT * FROM Audiobook WHERE id = :bookId LIMIT 1")
    suspend fun getAudiobookAsync(bookId: Int): Audiobook

    @Query("SELECT * FROM Audiobook WHERE isCached >= :isCached")
    fun getCachedAudiobooks(isCached : Boolean = true): LiveData<List<Audiobook>>

    @Query("UPDATE Audiobook SET isCached = :isCached")
    suspend fun uncacheAll(isCached: Boolean = false)

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY title ASC")
    fun getAllBooksAsync(offlineModeActive: Boolean): List<Audiobook>
}


