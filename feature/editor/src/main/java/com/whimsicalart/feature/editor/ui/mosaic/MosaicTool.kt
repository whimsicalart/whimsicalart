package com.whimsicalart.feature.editor.ui.mosaic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class MosaicStroke(
    val id: String,
    val points: List<Offset>,
    val brushType: MosaicBrushType,
    val brushSize: Float = 20f,
    val opacity: Float = 1f
)

enum class MosaicBrushType {
    PIXEL,
    BLUR,
    CUSTOM
}

data class MosaicEditorState(
    val strokes: List<MosaicStroke> = emptyList(),
    val selectedBrushType: MosaicBrushType = MosaicBrushType.PIXEL,
    val brushSize: Float = 20f,
    val opacity: Float = 1f,
    val isErasing: Boolean = false,
    val currentStroke: MosaicStroke? = null
)
