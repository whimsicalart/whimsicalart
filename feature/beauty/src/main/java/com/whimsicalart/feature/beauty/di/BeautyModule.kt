package com.whimsicalart.feature.beauty.di

import com.whimsicalart.feature.beauty.detection.FaceDetectorManager
import com.whimsicalart.feature.beauty.domain.BeautyProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object BeautyModule {

    @Provides
    fun provideBeautyProcessor(): BeautyProcessor = BeautyProcessor()

    @Provides
    fun provideFaceDetectorManager(): FaceDetectorManager = FaceDetectorManager()
}