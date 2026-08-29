package com.whimsicalart.core.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesManagerTest {

    private lateinit var prefs: PreferencesManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("whimsicalart_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = PreferencesManager(context)
    }

    @Test
    fun isFirstLaunchDefaultsToTrue() {
        assertTrue(prefs.isFirstLaunch)
    }

    @Test
    fun isFirstLaunchRoundTrips() {
        prefs.isFirstLaunch = false
        assertFalse(prefs.isFirstLaunch)
        prefs.isFirstLaunch = true
        assertTrue(prefs.isFirstLaunch)
    }

    @Test
    fun isDarkModeDefaultsToFalse() {
        assertFalse(prefs.isDarkMode)
    }

    @Test
    fun isDarkModeRoundTrips() {
        prefs.isDarkMode = true
        assertTrue(prefs.isDarkMode)
        prefs.isDarkMode = false
        assertFalse(prefs.isDarkMode)
    }

    @Test
    fun saveQualityDefaultsTo85() {
        assertEquals(85, prefs.saveQuality)
    }

    @Test
    fun saveQualityRoundTrips() {
        prefs.saveQuality = 70
        assertEquals(70, prefs.saveQuality)
    }

    @Test
    fun saveFormatDefaultsToJpeg() {
        assertEquals("JPEG", prefs.saveFormat)
    }

    @Test
    fun saveFormatRoundTrips() {
        prefs.saveFormat = "PNG"
        assertEquals("PNG", prefs.saveFormat)
    }

    @Test
    fun lastEditedImageUriDefaultsToNull() {
        assertNull(prefs.lastEditedImageUri)
    }

    @Test
    fun lastEditedImageUriRoundTrips() {
        prefs.lastEditedImageUri = "content://media/123"
        assertEquals("content://media/123", prefs.lastEditedImageUri)
        prefs.lastEditedImageUri = null
        assertNull(prefs.lastEditedImageUri)
    }

    @Test
    fun hapticFeedbackDefaultsToTrue() {
        assertTrue(prefs.hapticFeedbackEnabled)
    }

    @Test
    fun hapticFeedbackRoundTrips() {
        prefs.hapticFeedbackEnabled = false
        assertFalse(prefs.hapticFeedbackEnabled)
    }

    @Test
    fun autoSaveDefaultsToTrue() {
        assertTrue(prefs.autoSaveEnabled)
    }

    @Test
    fun autoSaveRoundTrips() {
        prefs.autoSaveEnabled = false
        assertFalse(prefs.autoSaveEnabled)
    }

    @Test
    fun valuesSurviveNewInstance() {
        prefs.isDarkMode = true
        prefs.saveQuality = 60
        prefs.saveFormat = "WEBP"
        prefs.hapticFeedbackEnabled = false

        val context = ApplicationProvider.getApplicationContext<Context>()
        val reloaded = PreferencesManager(context)

        assertTrue(reloaded.isDarkMode)
        assertEquals(60, reloaded.saveQuality)
        assertEquals("WEBP", reloaded.saveFormat)
        assertFalse(reloaded.hapticFeedbackEnabled)
    }
}
