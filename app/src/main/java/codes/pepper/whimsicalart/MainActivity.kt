package codes.pepper.whimsicalart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import codes.pepper.whimsicalart.core.common.PreferencesManager
import codes.pepper.whimsicalart.navigation.AppNavGraph
import codes.pepper.whimsicalart.navigation.Routes
import codes.pepper.whimsicalart.ui.theme.WhimsicalArtTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    /** The image an ACTION_SEND intent wants opened in the editor, if any. */
    private var pendingShareUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShareUri = sharedImageUri(intent)
        setContent {
            WhimsicalArtTheme {
                val navController = rememberNavController()
                val startDestination = if (pendingShareUri == null && preferencesManager.isFirstLaunch) {
                    Routes.ONBOARDING
                } else {
                    Routes.GALLERY
                }
                AppNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
                LaunchedEffect(pendingShareUri) {
                    val uri = pendingShareUri ?: return@LaunchedEffect
                    pendingShareUri = null
                    navController.navigate(Routes.editor(uri)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShareUri = sharedImageUri(intent)
    }

    /**
     * Extracts the shared image from an ACTION_SEND intent (reads ClipData
     * first, falling back to EXTRA_STREAM) or null when the intent is not a
     * single-image share.
     */
    @Suppress("DEPRECATION")
    private fun sharedImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val clipUri = intent.clipData?.getItemAt(0)?.uri
        val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        return (clipUri ?: streamUri)?.takeIf { it.scheme != null }
    }
}