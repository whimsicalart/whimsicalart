package codes.pepper.whimsicalart.feature.settings.ui

import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.core.common.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsViewModelTest {

    @Test
    fun should_default_to_85_quality_and_jpeg() {
        val vm = SettingsViewModel(PreferencesManager(ApplicationProvider.getApplicationContext()))
        assertEquals(85, vm.uiState.value.saveQuality)
        assertEquals("JPEG", vm.uiState.value.saveFormat)
    }

    @Test
    fun should_persist_save_quality_across_instances() {
        val vm = SettingsViewModel(PreferencesManager(ApplicationProvider.getApplicationContext()))
        vm.updateSaveQuality(70)
        assertEquals(70, vm.uiState.value.saveQuality)
        val reloaded = SettingsViewModel(PreferencesManager(ApplicationProvider.getApplicationContext()))
        assertEquals(70, reloaded.uiState.value.saveQuality)
    }

    @Test
    fun should_persist_dark_mode_and_auto_save_flags() {
        val vm = SettingsViewModel(PreferencesManager(ApplicationProvider.getApplicationContext()))
        vm.updateDarkMode(true)
        vm.updateAutoSave(false)
        assertTrue(vm.uiState.value.darkMode)
        assertFalse(vm.uiState.value.autoSave)
        val reloaded = SettingsViewModel(PreferencesManager(ApplicationProvider.getApplicationContext()))
        assertTrue(reloaded.uiState.value.darkMode)
        assertFalse(reloaded.uiState.value.autoSave)
    }
}
