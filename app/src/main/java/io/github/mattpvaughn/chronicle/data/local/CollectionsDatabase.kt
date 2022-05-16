package io.github.mattpvaughn.chronicle.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import io.github.mattpvaughn.chronicle.data.model.Collection

private const val COLLECTIONS_DATABASE_NAME = "collections_db"

private lateinit var INSTANCE: CollectionsDatabase
fun getCollectionsDatabase(context: Context): CollectionsDatabase {
    synchronized(CollectionsDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(
                context.applicationContext,
                CollectionsDatabase::class.java,
                COLLECTIONS_DATABASE_NAME
            ).addMigrations().build()
        }
    }
    return INSTANCE
}

@Database(entities = [Collection::class], version = 1, exportSchema = false)
abstract class CollectionsDatabase : RoomDatabase() {
    abstract val collectionsDao: CollectionsDao
}

@Dao
interface CollectionsDao {
    @Query("SELECT * FROM Collection ORDER BY title")
    fun getAllRows(): LiveData<List<Collection>>

    @Query("SELECT * FROM Collection WHERE id = :id LIMIT 1")
    fun getCollection(id: Int): LiveData<Collection?>

    @Query("SELECT * FROM Collection WHERE :collectionId = id")
    suspend fun getCollectionAsync(collectionId: Int): Collection

    @Query("SELECT * FROM Collection")
    fun getCollections(): List<Collection>

    @Query("SELECT Count(id) FROM Collection")
    fun countCollections(): LiveData<Long>

    @Query("DELETE FROM Collection")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rows: List<Collection>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun update(collection: Collection)

    @Query("DELETE FROM Collection WHERE id IN (:collectionsToRemove)")
    fun removeAll(collectionsToRemove: List<Long>): Int
}
