package codes.pepper.whimsicalart.feature.gallery.data.tag

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoTagEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GalleryDatabase : RoomDatabase() {

    abstract fun photoTagDao(): PhotoTagDao

    companion object {
        private const val NAME = "whimsicalart-gallery.db"

        fun create(context: Context): GalleryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                GalleryDatabase::class.java,
                NAME
            ).build()
    }
}