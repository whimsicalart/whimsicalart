package codes.pepper.whimsicalart.feature.editor.domain

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The share path (docs/effect_stack.md → Share) launches the system chooser from
 * the EDITOR's `@ApplicationContext`, which is NOT an Activity. A Context outside
 * an activity requires `FLAG_ACTIVITY_NEW_TASK` on the chooser or
 * `startActivity()` throws `AndroidRuntimeException`. The original crash
 * (`.tmp/log.11.txt`, ImageSaver.shareImage) fired exactly there.
 */
@RunWith(RobolectricTestRunner::class)
class ImageSaverTest {

    private fun shareWith(context: Context): Intent {
        val saver = ImageSaver(context)
        // shareImage() needs the app module's FileProvider metadata which a
        // library-module Robolectric manifest does not merge; startShareChooser()
        // is the exact path that adds the NEW_TASK flag on the non-Activity
        // context (the fix), so drive it directly with a synthetic content Uri.
        saver.startShareChooser(Uri.parse("content://$PACKAGE.fileprovider/share_image.jpg"), context)
        return when (context) {
            is Activity -> shadowOf(context).nextStartedActivity
            else -> shadowOf(context as Application).nextStartedActivity
        }
    }

    @Test
    fun `sharing from the application context launches the chooser with NEW_TASK`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val chooser = shareWith(context)

        assertNotNull("a chooser intent must be started", chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertTrue(
            "a non-activity context needs FLAG_ACTIVITY_NEW_TASK or startActivity() crashes",
            chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
        )
    }

    @Test
    fun `sharing from an activity context does not force NEW_TASK`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val chooser = shareWith(activity)

        assertNotNull(chooser)
        assertEquals(0, chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun `a picked tree with no backing provider returns null instead of crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saver = ImageSaver(context)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val config = SaveConfig(
            directory = Uri.parse(
                "content://com.android.externalstorage.documents/tree/primary%3ADownloads"
            )
        )

        val saved = saver.saveImage(bitmap, config)

        // DocumentFile.fromTreeUri cannot resolve a tree whose authority has no
        // DocumentsProvider in the Robolectric environment → saveToDirectory bails
        // out cleanly and the caller reports a save failure (never a crash).
        assertEquals("a dead tree must fail closed", null, saved)
    }

    private companion object {
        const val PACKAGE = "codes.pepper.whimsicalart"
    }
}