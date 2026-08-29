package com.whimsicalart.feature.gallery.di

import android.content.ContentResolver
import android.content.Context
import com.whimsicalart.feature.gallery.data.GalleryRepositoryImpl
import com.whimsicalart.feature.gallery.domain.GalleryRepository
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
}
