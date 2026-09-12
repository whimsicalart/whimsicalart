package codes.pepper.whimsicalart.feature.editor.ui.mosaic

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class MosaicStroke(
    val id: String,
    val points: List<Offset>,
    val brushSize: Float = 20f,
    val opacity: Float = 1f
)

data class MosaicEditorState(
    val strokes: List<MosaicStroke> = emptyList(),
    val brushSize: Float = 20f,
    val opacity: Float = 1f,
    val isErasing: Boolean = false,
    val currentStroke: MosaicStroke? = null,
    val suggestedRegions: List<RectF> = emptyList(),
    val isSuggesting: Boolean = false,
    /**
     * True while the on-screen preview shows the REAL pixelation/privacy effect
     * for the committed strokes (so the editor hides the patterned stand-in and
     * folds the strokes in). False while the user is mid-stroke or the 500 ms
     * debounce is still counting down (docs/effects_reference.md → Live-applied
     * brush effects).
     */
    val liveMosaicApplied: Boolean = false,
    /**
     * Monotonically increasing version of the committed [strokes] list, bumped
     * whenever a stroke is settled / erased / cleared. The editor keeps drawing
     * the committed strokes while the published composite preview still predates
     * this version (no dead gap), hiding them only once a composite that folds
     * these strokes has been published.
     */
    val strokesVersion: Int = 0
)
