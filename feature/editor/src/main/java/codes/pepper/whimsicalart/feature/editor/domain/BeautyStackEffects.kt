package codes.pepper.whimsicalart.feature.editor.domain

import android.content.Context
import android.graphics.Bitmap
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometry
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyLayerSpec
import codes.pepper.whimsicalart.feature.beauty.domain.BeautyProcessor
import codes.pepper.whimsicalart.feature.beauty.domain.BrushStroke
import codes.pepper.whimsicalart.feature.editor.ui.viewmodel.EditTool
import java.util.UUID

/**
 * A beauty sub-tool expressed as its own [StackEffect] layer. Each instance
 * carries the precise parameters of the operation plus the [geometry] it used —
 * the [BeautyGeometry] it lazily resolved (via the editor's shared
 * [codes.pepper.whimsicalart.feature.beauty.domain.BeautyGeometryContext]) for
 * the image state it folds over.
 *
 * The fold only calls [render]; the effect itself is fully responsible for
 * knowing which geometry to apply (it holds it as a parameter), so the fold and
 * the editor never decide/pick geometry.
 */
sealed interface BeautyStackEffect : StackEffect {
    /** The [BeautyGeometry] this effect used (its render parameter). null = none resolved yet. */
    val geometry: BeautyGeometry?

    override val tool: EditTool get() = EditTool.BEAUTY

    /** Returns this effect with [geometry] bound, used when lazily resolving at fold time. */
    fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect

    /**
     * Returns this effect with [BeautyProcessor] bound. The fold HANDS a single
     * injected (Hilt-provided) instance to every beauty effect before [render],
     * so effects never construct their own processor per call.
     */
    fun withProcessor(processor: BeautyProcessor): BeautyStackEffect
}

/** Auto beautify — the one-tap composite. */
data class BeautyAutoEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:auto"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val g = geometry
        val fr = g?.faceResult ?: return current
        return processor.applyAutoBeauty(
            current, fr, intensity, g.skinPath
        )
    }
}

/** Skin smoothing. */
data class BeautySmoothingEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:smoothing"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val g = geometry
        val fr = g?.faceResult ?: return current
        return processor.applySkinSmoothing(
            current, fr, 20f, intensity, g.skinPath
        )
    }
}

/** Teeth whitening. */
data class BeautyTeethEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:teeth"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyTeethWhitening(current, fr, intensity)
    }
}

/** Eye brightening. */
data class BeautyEyeBrightenEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:eye_brighten"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyEyeBrightening(current, fr, intensity)
    }
}

/** Dark-circle removal. */
data class BeautyDarkCircleEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:dark_circles"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyDarkCircleRemoval(current, fr, intensity)
    }
}

/** Spot removal. */
data class BeautySpotEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:spots"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val g = geometry
        val fr = g?.faceResult ?: return current
        return processor.applySpotRemoval(current, fr, intensity, g.skinPath)
    }
}

/** Wrinkle removal. */
data class BeautyWrinkleEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:wrinkles"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyWrinkleRemoval(current, fr, intensity)
    }
}

/** Skin-tone adjustment. */
data class BeautySkinToneEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:skin_tone"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity == 0f) return current
        val g = geometry
        val fr = g?.faceResult ?: return current
        return processor.applySkinTone(current, fr, intensity, g.skinPath)
    }
}

/**
 * Face re-shape — slim. Each of the four reshape dimensions ("slim", "eye
 * enlarge", "nose", "jaw") is its OWN single-purpose, single-parameter effect
 * and own stack layer per the one-layered-per-effect rule, so each dimension is
 * independently editable / reorderable / removable. Slim is a DEFORMATION, so it
 * invalidates geometry for any later beauty layer (the editor marks the shared
 * geometry context stale when it is applied).
 */
/** Face re-shape — slim (value range -1..1: positive slims the face, negative widens). */
data class BeautySlimEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:slim"
    override val changesGeometry: Boolean get() = true
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity == 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applySlim(current, fr, intensity)
    }
}

/** Face re-shape — eye enlarge (value range -1..1: positive enlarges, negative shrinks). */
data class BeautyEyeEnlargeEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:eye_enlarge"
    override val changesGeometry: Boolean get() = true
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity == 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyEyeEnlarge(current, fr, intensity)
    }
}

/** Face re-shape — nose (value range -1..1). See [BeautySlimEffect]. */
data class BeautyNoseEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:nose"
    override val changesGeometry: Boolean get() = true
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity == 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyNose(current, fr, intensity)
    }
}

/** Face re-shape — jaw (value range -1..1). See [BeautySlimEffect]. */
data class BeautyJawEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:jaw"
    override val changesGeometry: Boolean get() = true
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity == 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyJaw(current, fr, intensity)
    }
}

/** Lipstick. */
data class BeautyLipstickEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val color: Int = 0,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:lipstick"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyLipstick(current, fr, color, intensity)
    }
}

/** Blush. */
data class BeautyBlushEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val color: Int = 0,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:blush"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyBlush(current, fr, color, intensity)
    }
}

/** Eye shadow. */
data class BeautyEyeShadowEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val color: Int = 0,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:eye_shadow"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyEyeShadow(current, fr, color, intensity)
    }
}

/** Eyeliner. */
data class BeautyEyelinerEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val color: Int = 0,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:eyeliner"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val fr = geometry?.faceResult ?: return current
        return processor.applyEyeliner(current, fr, color, intensity)
    }
}

/** Foundation / base. */
data class BeautyFoundationEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val color: Int = 0,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:foundation"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val g = geometry
        val fr = g?.faceResult ?: return current
        return processor.applyFoundation(current, fr, color, intensity, g.skinPath)
    }
}

/** Hair color. */
data class BeautyHairEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val color: Int = 0,
    val intensity: Float = 0f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:hair"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (intensity <= 0f) return current
        val g = geometry
        val fr = g?.faceResult ?: return current
        return processor.applyHairColor(current, fr, color, intensity, g.hairPath)
    }
}

/** Brightness pen (manual brush). */
data class BeautyPenEffect(
    override val id: String = UUID.randomUUID().toString(),
    override val geometry: BeautyGeometry? = null,
    val strokes: List<BrushStroke> = emptyList(),
    val opacity: Float = 1f,
    private val processor: BeautyProcessor = BeautyProcessor()
) : BeautyStackEffect {
    override fun withGeometry(geometry: BeautyGeometry?): BeautyStackEffect = copy(geometry = geometry)
    override fun withProcessor(processor: BeautyProcessor): BeautyStackEffect = copy(processor = processor)
    override val layerKey: String get() = "beauty:pen"
    override fun render(current: Bitmap, context: Context): Bitmap {
        if (strokes.isEmpty()) return current
        return processor.applyBrightnessPen(current, strokes, opacity)
    }
}

/**
 * Maps an ordered list of beauty sub-tool [BeautyLayerSpec]s (reported by the
 * live beauty UI) into the corresponding [BeautyStackEffect] layers. Each spec's
 * [BeautyLayerSpec.toolKey] discriminates the concrete effect and its params.
 * Geometry is left unresolved here (`geometry = null`); the editor's geometry
 * track resolves each effect's geometry over the original geometry image (a
 * fold of only the geometry-changing effects up to the layer) via
 * [bindBeautyGeometries].
 */
fun List<BeautyLayerSpec>.toBeautyEffects(): List<BeautyStackEffect> = mapNotNull { spec ->
    when (spec.toolKey) {
        "beauty:auto" -> BeautyAutoEffect(intensity = spec.intensity)
        "beauty:smoothing" -> BeautySmoothingEffect(intensity = spec.intensity)
        "beauty:teeth" -> BeautyTeethEffect(intensity = spec.intensity)
        "beauty:eye_brighten" -> BeautyEyeBrightenEffect(intensity = spec.intensity)
        "beauty:dark_circles" -> BeautyDarkCircleEffect(intensity = spec.intensity)
        "beauty:spots" -> BeautySpotEffect(intensity = spec.intensity)
        "beauty:wrinkles" -> BeautyWrinkleEffect(intensity = spec.intensity)
        "beauty:skin_tone" -> BeautySkinToneEffect(intensity = spec.intensity)
        "beauty:slim" -> BeautySlimEffect(intensity = spec.intensity)
        "beauty:eye_enlarge" -> BeautyEyeEnlargeEffect(intensity = spec.intensity)
        "beauty:nose" -> BeautyNoseEffect(intensity = spec.intensity)
        "beauty:jaw" -> BeautyJawEffect(intensity = spec.intensity)
        "beauty:lipstick" -> BeautyLipstickEffect(color = spec.color, intensity = spec.intensity)
        "beauty:blush" -> BeautyBlushEffect(color = spec.color, intensity = spec.intensity)
        "beauty:eye_shadow" -> BeautyEyeShadowEffect(color = spec.color, intensity = spec.intensity)
        "beauty:eyeliner" -> BeautyEyelinerEffect(color = spec.color, intensity = spec.intensity)
        "beauty:foundation" -> BeautyFoundationEffect(color = spec.color, intensity = spec.intensity)
        "beauty:hair" -> BeautyHairEffect(color = spec.color, intensity = spec.intensity)
        "beauty:pen" -> BeautyPenEffect(strokes = spec.strokes, opacity = spec.opacity)
        else -> null
    }
}
