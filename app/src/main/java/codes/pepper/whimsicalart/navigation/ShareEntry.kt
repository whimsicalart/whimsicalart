package codes.pepper.whimsicalart.navigation

import android.content.Intent
import android.net.Uri

/**
 * The share-into-editor entry decisions (docs/user_guide.md → sharing an image
 * into the app): extracting the shared image from an [Intent.ACTION_SEND] intent
 * and choosing the nav-graph start destination / session slot for the launch.
 * Pure so the cold- and warm-share behaviour is unit-testable without a device;
 * [MainActivity] applies these decisions in its nav wiring.
 */
internal object ShareEntry {

    /**
     * The shared image uri from a single-image [Intent.ACTION_SEND] intent —
     * reads ClipData first (the system chooser places the uri there), falling
     * back to [Intent.EXTRA_STREAM] (the classic field). Returns null when the
     * intent is not an ACTION_SEND, or the uri has no scheme.
     */
    @Suppress("DEPRECATION")
    fun sharedImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val clipUri = intent.clipData?.getItemAt(0)?.uri
        val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        return (clipUri ?: streamUri)?.takeIf { it.scheme != null }
    }

    /**
     * The session-stable composition slot for the launch. A share launch lives
     * in its OWN saved-state slot ("share-<uri>") so activity recreation can
     * never restore a previous session's back stack (e.g. Gallery) over the
     * freshly computed share start destination; the normal session uses a fixed
     * key.
     */
    fun sessionKey(coldShareUri: Uri?): String =
        if (coldShareUri != null) "share-$coldShareUri" else "session"

    /**
     * The nav-graph start destination for this launch: a shared image opens THE
     * EDITOR directly (the gallery is never composed in front of it, so its read
     * permission prompt does not fire over a cold share launch); otherwise first
     * launch → onboarding, then gallery.
     */
    fun startDestination(coldShareUri: Uri?, isFirstLaunch: Boolean): String =
        when {
            coldShareUri != null -> Routes.editor(coldShareUri)
            isFirstLaunch -> Routes.ONBOARDING
            else -> Routes.GALLERY
        }
}