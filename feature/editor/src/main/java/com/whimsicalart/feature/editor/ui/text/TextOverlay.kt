package com.whimsicalart.feature.editor.ui.text

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class TextOverlay(
    val id: String,
    val text: String,
    val position: Offset,
    val fontSize: Float = 24f,
    val color: Color = Color.White,
    val typeface: Typeface = Typeface.DEFAULT,
    val fontStyle: FontStyle = FontStyle.NORMAL,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val hasShadow: Boolean = false,
    val shadowColor: Color = Color.Black,
    val shadowRadius: Float = 4f,
    val hasStroke: Boolean = false,
    val strokeColor: Color = Color.Black,
    val strokeWidth: Float = 2f,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val backgroundShape: TextBackgroundShape = TextBackgroundShape.NONE,
    val backgroundColor: Color = Color.Black
)

enum class TextBackgroundShape {
    NONE,
    ROUNDED,
    OVAL,
    PILL
}

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class TextFont(val displayName: String) {
    DEFAULT("Default"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    SANS_SERIF("Sans Serif"),
    HANDWRITING("Handwriting")
}

enum class FontStyle(val value: Int) {
    NORMAL(Typeface.NORMAL),
    BOLD(Typeface.BOLD),
    ITALIC(Typeface.ITALIC),
    BOLD_ITALIC(Typeface.BOLD_ITALIC)
}
