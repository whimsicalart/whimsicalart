package codes.pepper.whimsicalart.ui.onboarding

import androidx.lifecycle.ViewModel
import codes.pepper.whimsicalart.core.common.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Backs the first-launch onboarding gate. Completion is persisted through
 * [PreferencesManager.isFirstLaunch] so the flow only shows once per install.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val isFirstLaunch: Boolean
        get() = preferencesManager.isFirstLaunch

    /** Marks onboarding as completed so future launches start in the gallery. */
    fun complete() {
        preferencesManager.isFirstLaunch = false
    }
}
