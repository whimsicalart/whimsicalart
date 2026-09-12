package codes.pepper.whimsicalart

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.designsystem.computeUiScale

/**
 * Provides [LocalUiScale] ONCE for the whole app, derived from the window-
 * manager-reported configuration.
 *
 * Previous implementation used `BoxWithConstraints`, which reads the **view's
 * measured size**. On devices where `enableEdgeToEdge` makes the ComposeView
 * fill the physical screen, BoxWithConstraints always returns the full-screen
 * dimensions even when the activity is letterboxed inside a split pane.
 *
 * This version reads [LocalConfiguration.current], which reflects the
 * window-manager-reported configuration (`mLastReportedConfiguration` on the
 * window record). In split-screen the WM correctly reports the pane dimensions
 * (e.g. `w360dp h330dp land`), so [computeUiScale] receives the right values
 * regardless of the view's physical size. The configuration also updates
 * automatically when the window resizes (multi-window toggle, device rotation,
 * fold/unfold).
 *
 * Non-chrome scopes (canvas layout, gesture/preview px math, the preview
 * resolution ceiling) must NOT read [LocalUiScale]; the editor receives it as
 * an explicit parameter where it needs canvas accounting.
 */
@Composable
fun AppUiScale(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val scale = remember(config.screenWidthDp, config.screenHeightDp) {
        computeUiScale(config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat())
    }
    CompositionLocalProvider(
        LocalUiScale provides scale,
        content = content
    )
}
