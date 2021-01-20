package io.github.mattpvaughn.chronicle.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack


private const val TRACK_DATABASE_NAME = "track_db"

private lateinit var INSTANCE: TrackDatabase
fun getTrackDatabase(context: Context): TrackDatabase {
    synchronized(TrackDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(
                context.applicationContext,
                TrackDatabase::class.java,
                TRACK_DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        }
    }
    return INSTANCE
}

@Database(entities = [MediaItemTrack::class], version = 5, exportSchema = false)
abstract class TrackDatabase : RoomDatabase() {
    abstract val trackDao: TrackDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE MediaItemTrack ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE MediaItemTrack ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE MediaItemTrack ADD COLUMN discNumber INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE MediaItemTrack ADD COLUMN source INTEGER NOT NULL DEFAULT -1")
    }
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM MediaItemTrack")
    fun getAllTracks(): LiveData<List<MediaItemTrack>>

    @Query("SELECT * FROM MediaItemTrack")
    suspend fun getAllTracksAsync(): List<MediaItemTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rows: List<MediaItemTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun update(track: MediaItemTrack)

    @Query("SELECT * FROM MediaItemTrack WHERE id = :id LIMIT 1")
    suspend fun getTrackAsync(id: Int): MediaItemTrack?

    @Query("SELECT * FROM MediaItemTrack WHERE source = :sourceId")
    suspend fun getAllTracksInSource(sourceId: Long): List<MediaItemTrack>

    @Query("SELECT * FROM MediaItemTrack WHERE parentKey = :bookId AND cached >= :isOfflineMode ORDER BY `index` ASC")
    fun getTracksForAudiobook(bookId: Int, isOfflineMode: Boolean): LiveData<List<MediaItemTrack>>

    @Query("SELECT * FROM MediaItemTrack WHERE parentKey = :id AND cached >= :offlineModeActive ORDER BY `index` ASC")
    suspend fun getTracksForAudiobookAsync(
        id: Int,
        offlineModeActive: Boolean
    ): List<MediaItemTrack>

    @Query("SELECT COUNT(*) FROM MediaItemTrack WHERE parentKey = :bookId")
    suspend fun getTrackCountForAudiobookAsync(bookId: Int): Int

    @Query("UPDATE MediaItemTrack SET progress = :trackProgress, lastViewedAt = :lastViewedAt WHERE id = :trackId")
    fun updateProgress(trackProgress: Long, trackId: Int, lastViewedAt: Long)

    @Query("DELETE FROM MediaItemTrack")
    fun clear()

    @Query("UPDATE MediaItemTrack SET cached = :isCached WHERE id = :trackId")
    fun updateCachedStatus(trackId: Int, isCached: Boolean) : Int

    @Query("SELECT * FROM MediaItemTrack WHERE cached = :isCached")
    fun getCachedTracksAsync(isCached: Boolean = true): List<MediaItemTrack>

    @Query("SELECT COUNT(*) FROM MediaItemTrack WHERE cached = :isCached AND parentKey = :bookId")
    suspend fun getCachedTrackCountForBookAsync(bookId: Int, isCached: Boolean = true): Int

    @Query("UPDATE MediaItemTrack SET cached = :isCached")
    suspend fun uncacheAll(isCached: Boolean = false)

    @Query("SELECT * FROM MediaItemTrack WHERE title LIKE :title")
    suspend fun findTrackByTitle(title: String): MediaItemTrack?

    @Query("DELETE FROM MediaItemTrack WHERE source = :sourceId")
    suspend fun removeWithSource(sourceId: Long)


}


