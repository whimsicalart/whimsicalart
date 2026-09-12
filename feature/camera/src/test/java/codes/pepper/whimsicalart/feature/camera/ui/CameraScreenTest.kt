package codes.pepper.whimsicalart.feature.camera.ui

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowContentResolver.registerProviderInternal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CameraScreenTest {

    @Test
    fun should_create_a_writable_media_store_capture_uri() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uri = createCaptureUri(app)
        assertNotNull("capture uri must be created", uri)
    }

    @Test
    @Config(sdk = [28])
    fun should_not_throw_when_creating_a_capture_uri_on_pre_q() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Pre-Q we fall back to a cache FileProvider uri; under the library-only
        // test manifest that authority is absent, so we must gracefully produce
        // null (or a uri) rather than throw — the screen then just backs out.
        createCaptureUri(app)
    }

    @Test
    fun should_fall_back_to_the_file_provider_cache_when_the_media_store_insert_fails() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Force the Q+ MediaStore insert to fail by installing a media provider
        // that returns null. createCaptureUri must then degrade via the
        // FileProvider fallback (null here because the library-only test manifest
        // has no fileprovider authority) instead of silently swallowing the
        // failure — the "nothing happens" gap this guard replaces.
        registerProviderInternal(
            "media",
            object : ContentProvider() {
                override fun onCreate(): Boolean = true
                override fun insert(uri: Uri, values: ContentValues?): Uri? = null
                override fun query(
                    uri: Uri, projection: Array<out String>?,
                    selection: String?, selectionArgs: Array<out String>?, sortOrder: String?
                ): Cursor? = null
                override fun update(
                    uri: Uri, values: ContentValues?,
                    selection: String?, selectionArgs: Array<out String>?
                ): Int = 0
                override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
                override fun getType(uri: Uri): String = "image/jpeg"
            }
        )
        assertNull(
            "a failed MediaStore insert falls back without throwing (FileProvider absent in tests -> null)",
            createCaptureUri(app)
        )
    }

    @Test
    fun should_report_no_camera_when_no_handler_resolves() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(
            "no camera handler resolves on the test emulator",
            isCameraAvailable(app)
        )
    }

    @Test
    fun should_require_camera_permission_when_not_granted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(
            "CAMERA is not granted by default, so the launch guard must request it",
            requiresCameraPermission(app)
        )
    }

    @Test
    fun should_not_require_camera_permission_when_already_granted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA)
        assertFalse(
            "CAMERA granted means the launch can proceed without another request",
            requiresCameraPermission(app)
        )
    }
}
