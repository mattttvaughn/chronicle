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
            ).addMigrations(
                BOOK_MIGRATION_1_2,
                BOOK_MIGRATION_2_3,
                BOOK_MIGRATION_3_4,
                BOOK_MIGRATION_4_5,
                BOOK_MIGRATION_5_6,
                BOOK_MIGRATION_6_7
            ).build()
        }
    }
    return INSTANCE
}

val BOOK_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Do nothing lol
    }
}

val BOOK_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Audiobook ADD COLUMN chapters TEXT NOT NULL DEFAULT ''")
    }
}

val BOOK_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Audiobook ADD COLUMN source BIGINT NOT NULL DEFAULT -1")
    }
}

val BOOK_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Audiobook ADD COLUMN progress BIGINT NOT NULL DEFAULT 0")
    }
}

val BOOK_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Audiobook ADD COLUMN titleSort TEXT NOT NULL DEFAULT ''")
    }
}

val BOOK_MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // STOPSHIP: this has to be reworked!
        database.execSQL(
            "CREATE TABLE `new_Audiobook`" +
                    "(`id` INTEGER NOT NULL," +
                    "`source` INTEGER NOT NULL," +
                    "`title` TEXT NOT NULL," +
                    "`titleSort` TEXT NOT NULL," +
                    "`author` TEXT NOT NULL," +
                    "`thumb` TEXT NOT NULL," +
                    "`parentId` INTEGER NOT NULL," +
                    "`genre` TEXT NOT NULL," +
                    "`summary` TEXT NOT NULL," +
                    "`addedAt` INTEGER NOT NULL," +
                    "`updatedAt` INTEGER NOT NULL," +
                    "`lastViewedAt` INTEGER NOT NULL," +
                    "`duration` INTEGER NOT NULL," +
                    "`isCached` INTEGER NOT NULL," +
                    "`progress` INTEGER NOT NULL," +
                    "`favorited` INTEGER NOT NULL," +
                    "`viewedLeafCount` INTEGER NOT NULL," +
                    "`leafCount` INTEGER NOT NULL," +
                    "`chapters` TEXT NOT NULL," +
                    "PRIMARY KEY(`id`) )"
        )

        //insert data from old table into new table
        database.execSQL(
            "INSERT INTO new_Audiobook" +
                    "(`id`," +
                    "`source`," +
                    "`title`," +
                    "`titleSort`," +
                    "`author`," +
                    "`thumb`," +
                    "`parentId`," +
                    "`genre`," +
                    "`summary`," +
                    "`addedAt`," +
                    "`updatedAt`," +
                    "`lastViewedAt`," +
                    "`duration`," +
                    "`isCached`," +
                    "`progress`," +
                    "`favorited`," +
                    "`viewedLeafCount`," +
                    "`leafCount`," +
                    "`chapters`)" +
                    " SELECT " +
                    "`id`," +
                    "`source`," +
                    "`title`," +
                    "`titleSort`," +
                    "`author`," +
                    "`thumb`," +
                    "`parentId`," +
                    "`genre`," +
                    "`summary`," +
                    "`addedAt`," +
                    "`updatedAt`," +
                    "`lastViewedAt`," +
                    "`duration`," +
                    "`isCached`," +
                    "`progress`," +
                    "`favorited`," +
                    "`viewedLeafCount`," +
                    "`leafCount`," +
                    "`chapters`" +
                    " FROM Audiobook"
        )

        //drop old table
        database.execSQL("DROP TABLE Audiobook")

        //rename new table to the old table name
        database.execSQL("ALTER TABLE new_Audiobook RENAME TO Audiobook")

        database.execSQL("ALTER TABLE Audiobook ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0")
    }

}

@Database(
    entities = [Audiobook::class],
    version = 7,
    exportSchema = true,
)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookDao: BookDao
}

/**
 * Note: for the weird isCached <= :isOfflineModeActive queries, this ensures that cached items
 * are returned even when offline mode is inactive. A simple equality check would return only
 * cached items during offline mode, but only uncached items when offline mode is inactive. This
 * is an easy way to implement it at the DB level, avoiding messing code in the repository
 */
@Dao
interface BookDao {
    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY titleSort")
    fun getAllRows(offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook")
    fun getAudiobooks(): List<Audiobook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rows: List<Audiobook>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun update(audiobook: Audiobook)

    @Query("UPDATE Audiobook SET isCached = :cached WHERE id = :bookId")
    fun updateCachedStatus(bookId: Int, cached: Boolean)

    @Query("SELECT * FROM Audiobook WHERE id = :id AND isCached >= :isOfflineModeActive LIMIT 1")
    fun getAudiobook(
        id: Int,
        isOfflineModeActive: Boolean
    ): LiveData<Audiobook?>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY addedAt DESC LIMIT :bookCount")
    fun getRecentlyAdded(bookCount: Int, offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY addedAt DESC LIMIT :bookCount")
    suspend fun getRecentlyAddedAsync(bookCount: Int, offlineModeActive: Boolean): List<Audiobook>

    @Query("SELECT * FROM Audiobook ORDER BY updatedAt DESC LIMIT 25")
    fun getOnDeck(): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive AND lastViewedAt != 0 AND progress > 10000 AND progress < duration - 120000 ORDER BY lastViewedAt DESC LIMIT :bookCount")
    fun getRecentlyListened(bookCount: Int, offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive AND lastViewedAt != 0 AND progress > 10000 AND progress < duration - 120000 ORDER BY lastViewedAt DESC LIMIT :bookCount")
    suspend fun getRecentlyListenedAsync(
        bookCount: Int,
        offlineModeActive: Boolean
    ): List<Audiobook>

    @Query("UPDATE Audiobook SET lastViewedAt = :currentTime, progress = :progress WHERE lastViewedAt < :currentTime AND id = :bookId")
    fun updateProgress(bookId: Int, currentTime: Long, progress: Long)

    @Query("UPDATE Audiobook SET duration = :duration, leafCount = :trackCount, progress = :progress WHERE id = :bookId")
    suspend fun updateTrackData(bookId: Int, progress: Long, duration: Long, trackCount: Int)

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive AND (title LIKE :query OR author LIKE :query)")
    fun search(query: String, offlineModeActive: Boolean): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive AND (title LIKE :query OR author LIKE :query)")
    fun searchAsync(query: String, offlineModeActive: Boolean): List<Audiobook>

    @Query("DELETE FROM Audiobook")
    suspend fun clear()

    @Query("SELECT * FROM Audiobook ORDER BY lastViewedAt DESC LIMIT 1")
    suspend fun getMostRecent(): Audiobook?

    @Query("SELECT * FROM Audiobook WHERE id = :bookId LIMIT 1")
    suspend fun getAudiobookAsync(bookId: Int): Audiobook?

    @Query("SELECT * FROM Audiobook WHERE isCached >= :isCached")
    fun getCachedAudiobooks(isCached: Boolean = true): LiveData<List<Audiobook>>

    @Query("SELECT * FROM Audiobook WHERE isCached >= :isCached")
    fun getCachedAudiobooksAsync(isCached: Boolean = true): List<Audiobook>

    @Query("UPDATE Audiobook SET isCached = :isCached")
    suspend fun uncacheAll(isCached: Boolean = false)

    @Query("SELECT * FROM Audiobook WHERE isCached >= :offlineModeActive ORDER BY titleSort ASC")
    fun getAllBooksAsync(offlineModeActive: Boolean): List<Audiobook>

    @Query("SELECT COUNT(*) FROM Audiobook")
    suspend fun getBookCount(): Int

    @Query("DELETE FROM Audiobook WHERE id IN (:booksToRemove)")
    fun removeAll(booksToRemove: List<String>): Int

    @Query("SELECT * FROM Audiobook ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomBookAsync(): Audiobook?

    @Query("SELECT * FROM Audiobook WHERE source= :sourceId AND isCached >= :isOfflineModeActive LIMIT 1")
    fun getAudiobooksForSourceAsync(sourceId: Long, isOfflineModeActive: Boolean): List<Audiobook>

    @Query("DELETE FROM Audiobook WHERE source = :sourceId")
    suspend fun removeWithSource(sourceId: Long)

    @Query("UPDATE Audiobook SET progress = 0 WHERE id = :bookId")
    suspend fun resetBookProgress(bookId: Int)

    @Query("UPDATE Audiobook SET viewCount = viewCount + 1 WHERE id = :bookId")
    suspend fun setWatched(bookId: Int)
}


