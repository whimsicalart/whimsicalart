package codes.pepper.whimsicalart.feature.gallery.data.tag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoTagEntity)

    @Query("DELETE FROM photo_tags WHERE uri = :uri AND tag = :tag")
    suspend fun delete(uri: String, tag: String)

    @Query("DELETE FROM photo_tags WHERE uri = :uri")
    suspend fun deleteAllForUri(uri: String)

    @Query("SELECT DISTINCT tag FROM photo_tags")
    fun observeDistinctTags(): Flow<List<String>>

    @Query("SELECT * FROM photo_tags WHERE uri = :uri")
    fun observeForUri(uri: String): Flow<List<PhotoTagEntity>>

    @Query("SELECT uri FROM photo_tags WHERE tag = :tag")
    suspend fun urisForTag(tag: String): List<String>

    @Query("SELECT uri FROM photo_tags")
    suspend fun allUris(): List<String>

    @Query("SELECT COUNT(*) FROM photo_tags")
    fun observeCount(): Flow<Int>
}