package com.whimsicalart.core.common

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var saveQuality: Int
        get() = prefs.getInt(KEY_SAVE_QUALITY, 85)
        set(value) = prefs.edit().putInt(KEY_SAVE_QUALITY, value).apply()

    var saveFormat: String
        get() = prefs.getString(KEY_SAVE_FORMAT, "JPEG") ?: "JPEG"
        set(value) = prefs.edit().putString(KEY_SAVE_FORMAT, value).apply()

    var lastEditedImageUri: String?
        get() = prefs.getString(KEY_LAST_IMAGE_URI, null)
        set(value) = prefs.edit().putString(KEY_LAST_IMAGE_URI, value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    var autoSaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SAVE, value).apply()

    companion object {
        private const val PREFS_NAME = "whimsicalart_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_SAVE_QUALITY = "save_quality"
        private const val KEY_SAVE_FORMAT = "save_format"
        private const val KEY_LAST_IMAGE_URI = "last_image_uri"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_AUTO_SAVE = "auto_save"
    }
}
