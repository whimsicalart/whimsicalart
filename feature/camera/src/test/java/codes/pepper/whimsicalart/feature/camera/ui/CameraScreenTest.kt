package codes.pepper.whimsicalart.feature.camera.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CameraScreenTest {

    @Test
    fun should_create_a_writable_media_store_capture_uri() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uri = createCaptureUri(app.contentResolver)
        assertNotNull("capture uri must be created", uri)
    }
}
