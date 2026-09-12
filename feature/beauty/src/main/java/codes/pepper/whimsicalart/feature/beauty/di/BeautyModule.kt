package codes.pepper.whimsicalart.feature.beauty.di

import android.content.Context
import codes.pepper.whimsicalart.feature.beauty.detection.FaceMeshDetector
import codes.pepper.whimsicalart.feature.beauty.detection.HairSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.MediaPipeFaceMeshDetector
import codes.pepper.whimsicalart.feature.beauty.detection.MediaPipeHairSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.MediaPipeSkinSegmenter
import codes.pepper.whimsicalart.feature.beauty.detection.SkinSegmenter
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryContext
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryGenerator
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryResolver
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
    @Singleton
    fun provideFaceMeshDetector(@ApplicationContext context: Context): FaceMeshDetector =
        MediaPipeFaceMeshDetector(context)

    @Provides
    @Singleton
    fun provideHairSegmenter(@ApplicationContext context: Context): HairSegmenter =
        MediaPipeHairSegmenter(context)

    @Provides
    @Singleton
    fun provideSkinSegmenter(@ApplicationContext context: Context): SkinSegmenter =
        MediaPipeSkinSegmenter(context)

    /**
     * The editor's shared beauty ML pipeline, hoisted app-scoped so every beauty
     * geometry generation (the shared context below, the editor's fold context)
     * reuses the SAME detector instances. The detector instances are built
     * **once** (lazy singletons — each class already wraps its native runtime in
     * a `runCatching`-backed, synchronised `lazy`) and reused by every
     * generation; the previous per-generation construction re-parsed and
     * re-compiled the models on every resolve. The resolver they feed is equally
     * stateless between resolves. As an app-scoped singleton it lives for the
     * process (a re-entered editor must keep working after the previous editor
     * closed), so the OS releases its native memory at teardown.
     */
    @Provides
    @Singleton
    fun provideBeautyGeometryResolver(
        faceMeshDetector: FaceMeshDetector,
        hairSegmenter: HairSegmenter,
        skinSegmenter: SkinSegmenter
    ): BeautyGeometryResolver = DefaultBeautyGeometryResolver(
        faceMeshDetector,
        hairSegmenter,
        skinSegmenter
    )

    /**
     * The app's shared beauty geometry context. The editor folds its beauty
     * layers through its OWN context (docs/effect_stack.md → Shared geometry
     * context) whose generator is this same [BeautyGeometryResolver] PLUS the
     * background tool's SUBJECT_MASK via the editor's segmenter.
     */
    @Provides
    @Singleton
    fun provideBeautyGeometryContext(beautyGeometryResolver: BeautyGeometryResolver): BeautyGeometryContext =
        DefaultBeautyGeometryContext(
            generator = BeautyGeometryGenerator { image, maxPixelCountHint, neededFields ->
                beautyGeometryResolver.resolve(image, maxPixelCountHint, neededFields)
            }
        )
}