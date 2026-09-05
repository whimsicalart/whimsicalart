package codes.pepper.whimsicalart.feature.camera.ui

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * In-app camera capture that hands the resulting image to the editor.
 *
 * Uses the system camera app via [ActivityResultContracts.TakePicture] (no ML
 * Kit / CameraX dependency): the captured photo is written to a fresh
 * [MediaStore] image entry and the resulting [Uri] is returned through
 * [onPhotoCaptured] so the app can open it in the editor.
 */
@Composable
fun CameraScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var launched by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = captureUri
        when {
            success && uri != null -> onPhotoCaptured(uri)
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            val uri = createCaptureUri(context.contentResolver)
            captureUri = uri
            if (uri != null) {
                launcher.launch(uri)
            } else {
                onBack()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Creates a new [MediaStore] image entry to receive the captured photo and
 * returns its content [Uri]. Returns null if the insert fails.
 */
internal fun createCaptureUri(contentResolver: ContentResolver): Uri? {
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val collection =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        contentResolver.insert(collection, values)
    }.getOrNull()
}
