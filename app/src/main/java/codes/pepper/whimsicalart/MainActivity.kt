package codes.pepper.whimsicalart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import codes.pepper.whimsicalart.core.common.PreferencesManager
import codes.pepper.whimsicalart.navigation.AppNavGraph
import codes.pepper.whimsicalart.navigation.Routes
import codes.pepper.whimsicalart.navigation.ShareEntry
import codes.pepper.whimsicalart.ui.theme.WhimsicalArtTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    /** The image an ACTION_SEND intent wants opened in the editor, if any. */
    private var pendingShareUri by mutableStateOf<Uri?>(null)

    /** The share uri from the COLD intent, fixed at onCreate for the start destination. */
    private var coldShareUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        coldShareUri = ShareEntry.sharedImageUri(intent)
        pendingShareUri = coldShareUri
        // A stable, per-activity composition key: the share session and the
        // normal session are kept in DIFFERENT saveable slots, so a recreated
        // share activity never restores a previous (gallery) session's nav
        // state over the freshly computed share start destination. As a
        // session-neutral string this key never changes mid-composition (it is
        // captured here, once), so it cannot reset the running UI.
        val sessionKey = ShareEntry.sessionKey(coldShareUri)
        setContent {
            key(sessionKey) {
                WhimsicalArtTheme {
                    // The window-anchored chrome scale is provided at the app root
                    // ONCE (AppUiScale), so every destination/dialog shares it.
                    AppUiScale {
                        val navController = rememberNavController()
                        val startDestination = ShareEntry.startDestination(
                            coldShareUri,
                            preferencesManager.isFirstLaunch
                        )
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination
                        )
                        LaunchedEffect(pendingShareUri) {
                            val uri = pendingShareUri ?: return@LaunchedEffect
                            pendingShareUri = null
                            if (coldShareUri == uri) {
                                // COLD share: the editor already is the NavHost start
                                // destination. Guard against a restored state that
                                // slipped past the key()d slot (activity recreation
                                // restoring a previous session's gallery back stack
                                // over the share root): make the share editor the
                                // SINGLE destination again, so the shared image opens
                                // and STAYS in the editor and only a deliberate back
                                // exits to Gallery.
                                if (navController.currentDestination?.route != Routes.EDITOR) {
                                    navController.navigate(Routes.editor(uri)) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            } else if (uri != null) {
                                // A share arriving later (onNewIntent) — push the
                                // editor over the current destination; back returns
                                // there as before.
                                navController.navigate(Routes.editor(uri)) {
                                    launchSingleTop = true
                                }
                            }
                            coldShareUri = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShareUri = ShareEntry.sharedImageUri(intent)
    }
}