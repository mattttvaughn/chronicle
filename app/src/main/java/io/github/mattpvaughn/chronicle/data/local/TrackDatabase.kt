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
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
    return INSTANCE
}


@Database(entities = [MediaItemTrack::class], version = 2, exportSchema = false)
abstract class TrackDatabase : RoomDatabase() {
    abstract val trackDao: TrackDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE MediaItemTrack ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
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

    @Query("SELECT * FROM MediaItemTrack WHERE parentKey = :bookId AND cached >= :isOfflineMode ORDER BY `index` ASC")
    fun getTracksForAudiobook(bookId: Int, isOfflineMode: Boolean): LiveData<List<MediaItemTrack>>

    @Query("SELECT * FROM MediaItemTrack WHERE parentKey = :id AND cached >= :offlineModeActive ORDER BY `index` ASC")
    suspend fun getTracksForAudiobookAsync(id: Int, offlineModeActive: Boolean): List<MediaItemTrack>

    @Query("SELECT COUNT(*) FROM MediaItemTrack WHERE parentKey = :bookId")
    suspend fun getTrackCountForAudiobookAsync(bookId: Int): Int

    @Query("UPDATE MediaItemTrack SET progress = :trackProgress, lastViewedAt = :lastViewedAt WHERE id = :trackId")
    fun updateProgress(trackProgress: Long, trackId: Int, lastViewedAt: Long)

    @Query("DELETE FROM MediaItemTrack")
    fun clear()

    @Query("UPDATE MediaItemTrack SET cached = :isCached WHERE id = :trackId")
    fun updateCachedStatus(trackId: Int, isCached: Boolean)

    @Query("SELECT * FROM MediaItemTrack WHERE cached = :isCached")
    fun getCachedTracksAsync(isCached: Boolean = true): List<MediaItemTrack>

    @Query("SELECT COUNT(*) FROM MediaItemTrack WHERE cached = :isCached AND parentKey = :bookId")
    suspend fun getCachedTrackCountForBookAsync(bookId: Int, isCached: Boolean = true): Int

    @Query("UPDATE MediaItemTrack SET cached = :isCached")
    suspend fun uncacheAll(isCached: Boolean = false)


}


