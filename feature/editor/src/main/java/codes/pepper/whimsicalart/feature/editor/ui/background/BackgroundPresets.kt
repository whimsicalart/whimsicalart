package codes.pepper.whimsicalart.feature.editor.ui.background

import androidx.annotation.DrawableRes
import codes.pepper.whimsicalart.feature.editor.R

data class BackgroundPreset(
    val id: String,
    @DrawableRes val drawableRes: Int,
    val label: String
)

/**
 * Built-in replacement backgrounds. All images are free stock photos released
 * under the Pexels License (free for commercial and non-commercial use, no
 * attribution required). See NOTICE for credits.
 */
object BackgroundPresets {

    val presets = listOf(
        BackgroundPreset("landscape", R.drawable.bg_landscape, "Scenic"),
        BackgroundPreset("office", R.drawable.bg_office, "Office"),
        BackgroundPreset("park", R.drawable.bg_park, "Park"),
        BackgroundPreset("library", R.drawable.bg_library, "Library"),
        BackgroundPreset("coffee", R.drawable.bg_coffee, "Café"),
        BackgroundPreset("mountains", R.drawable.bg_mountains, "Mountains"),
        BackgroundPreset("room", R.drawable.bg_room, "Interior"),
        BackgroundPreset("city", R.drawable.bg_city, "City")
    )

    fun getPresetByRes(@DrawableRes res: Int): BackgroundPreset? =
        presets.firstOrNull { it.drawableRes == res }
}
