package codes.pepper.whimsicalart.feature.gallery.di

import android.content.ContentResolver
import android.content.Context
import codes.pepper.whimsicalart.feature.gallery.data.GalleryRepositoryImpl
import codes.pepper.whimsicalart.feature.gallery.data.tag.GalleryDatabase
import codes.pepper.whimsicalart.feature.gallery.data.tag.MlKitImageLabeler
import codes.pepper.whimsicalart.feature.gallery.data.tag.PhotoTagDao
import codes.pepper.whimsicalart.feature.gallery.data.tag.RoomTagRepository
import codes.pepper.whimsicalart.feature.gallery.domain.GalleryRepository
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneClassifier
import codes.pepper.whimsicalart.feature.gallery.domain.tag.TagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GalleryModule {

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: Context
    ): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideGalleryRepository(
        contentResolver: ContentResolver
    ): GalleryRepository {
        return GalleryRepositoryImpl(contentResolver)
    }

    @Provides
    @Singleton
    fun provideGalleryDatabase(
        @ApplicationContext context: Context
    ): GalleryDatabase = GalleryDatabase.create(context)

    @Provides
    @Singleton
    fun providePhotoTagDao(database: GalleryDatabase): PhotoTagDao =
        database.photoTagDao()

    @Provides
    @Singleton
    fun provideTagRepository(dao: PhotoTagDao): TagRepository =
        RoomTagRepository(dao)

    @Provides
    @Singleton
    fun provideSceneClassifier(): SceneClassifier = MlKitImageLabeler()
}
