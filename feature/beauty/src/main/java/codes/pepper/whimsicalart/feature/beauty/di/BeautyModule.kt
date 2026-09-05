package codes.pepper.whimsicalart.feature.beauty.di

import android.content.Context
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectorManager
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshDetector
import codes.pepper.whimsicalart.feature.beauty.detection.HairSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.MediaPipeFaceMeshDetector
import codes.pepper.whimsicalart.feature.beauty.detection.MediaPipeHairSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.MediaPipeSkinSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.SkinSegmenter
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryContext
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryGenerator
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyProcessor
import codes.pepper.whimsicalart.feature.beauty.domain.DefaultBeautyGeometryContext
import codes.pepper.whimsicalart.feature.beauty.domain.DefaultBeautyGeometryResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BeautyModule {

    @Provides
    fun provideBeautyProcessor(): BeautyProcessor = BeautyProcessor()

    @Provides
    fun provideFaceDetectorManager(): FaceDetectorManager = FaceDetectorManager()

    @Provides
    fun provideFaceMeshDetector(@ApplicationContext context: Context): FaceMeshDetector =
        MediaPipeFaceMeshDetector(context)

    @Provides
    fun provideHairSegmenter(@ApplicationContext context: Context): HairSegmenter =
        MediaPipeHairSegmenter(context)

    @Provides
    fun provideSkinSegmenter(@ApplicationContext context: Context): SkinSegmenter =
        MediaPipeSkinSegmenter(context)

    /**
     * The editor's shared beauty geometry context. The context lazily generates
     * geometry via the ML resolver (which owns FRESH detector instances, not
     * cached ones, so their lifecycle — closed by the editor — never races other
     * consumers of detectors).
     */
    @Provides
    @Singleton
    fun provideBeautyGeometryContext(
        @ApplicationContext context: Context
    ): BeautyGeometryContext = DefaultBeautyGeometryContext(
        generator = BeautyGeometryGenerator { image ->
            DefaultBeautyGeometryResolver(
                FaceDetectorManager(),
                MediaPipeFaceMeshDetector(context),
                MediaPipeHairSegmenter(context),
                MediaPipeSkinSegmenter(context)
            ).resolve(image)
        }
    )
}