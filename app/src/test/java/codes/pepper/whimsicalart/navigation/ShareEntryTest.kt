package codes.pepper.whimsicalart.navigation

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cold/warm share-into-editor entry decisions ([ShareEntry], the logic [MainActivity]
 * applies): a shared image must open THE EDITOR and stay there — the flicker
 * regression ("Edit Photo" flashes, then Gallery swaps in) was the saved-state
 * restore racing the share root, so the session slot and start destination are
 * pinned here without needing a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ShareEntryTest {

    private val contentUri = Uri.parse("content://com.example.sender/photo/42")
    private val fileUri = Uri.parse("file:///data/share.jpg")

    private fun sendIntent(extraStream: Uri? = null, clip: Uri? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            if (extraStream != null) putExtra(Intent.EXTRA_STREAM, extraStream)
            if (clip != null) clipData = ClipData.newRawUri("shared image", clip)
        }

    @Test
    fun `sharedImageUri reads the clip data uri for a send intent`() {
        assertEquals(contentUri, ShareEntry.sharedImageUri(sendIntent(clip = contentUri)))
    }

    @Test
    fun `sharedImageUri falls back to extra_stream when no clip data`() {
        assertEquals(fileUri, ShareEntry.sharedImageUri(sendIntent(extraStream = fileUri)))
    }

    @Test
    fun `sharedImageUri prefers the clip data uri over extra_stream`() {
        assertEquals(
            contentUri,
            ShareEntry.sharedImageUri(sendIntent(extraStream = fileUri, clip = contentUri))
        )
    }

    @Test
    fun `sharedImageUri is null for non-send intents and uri-less sends`() {
        assertNull(ShareEntry.sharedImageUri(Intent(Intent.ACTION_VIEW)))
        assertNull(ShareEntry.sharedImageUri(Intent(Intent.ACTION_SEND)))
    }

    @Test
    fun `a cold share lands on the editor as the start destination`() {
        assertEquals(
            Routes.editor(contentUri),
            ShareEntry.startDestination(coldShareUri = contentUri, isFirstLaunch = true)
        )
        assertEquals(
            Routes.editor(contentUri),
            ShareEntry.startDestination(coldShareUri = contentUri, isFirstLaunch = false)
        )
    }

    @Test
    fun `the normal session keeps onboarding or gallery as its destination`() {
        assertEquals(Routes.ONBOARDING, ShareEntry.startDestination(null, isFirstLaunch = true))
        assertEquals(Routes.GALLERY, ShareEntry.startDestination(null, isFirstLaunch = false))
    }

    @Test
    fun `a share launch lives in its own saved-state slot`() {
        assertEquals("share-$contentUri", ShareEntry.sessionKey(contentUri))
        assertEquals("session", ShareEntry.sessionKey(null))
    }
}