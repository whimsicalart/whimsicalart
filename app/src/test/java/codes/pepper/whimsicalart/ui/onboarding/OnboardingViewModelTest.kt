package codes.pepper.whimsicalart.ui.onboarding

import androidx.test.core.app.ApplicationProvider
import codes.pepper.whimsicalart.core.common.PreferencesManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class OnboardingViewModelTest {

    private fun viewModel() = OnboardingViewModel(
        PreferencesManager(ApplicationProvider.getApplicationContext())
    )

    @Test
    fun should_start_as_first_launch() {
        assertTrue(viewModel().isFirstLaunch)
    }

    @Test
    fun should_persist_completion_across_instances() {
        val vm = viewModel()
        vm.complete()
        assertFalse(vm.isFirstLaunch)
        assertFalse("completion must survive a fresh instance", viewModel().isFirstLaunch)
    }
}
