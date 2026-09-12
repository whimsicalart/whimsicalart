package codes.pepper.whimsicalart.feature.camera.ui

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.camera.R
import java.io.File

/**
 * In-app camera capture that hands the resulting image to the editor.
 *
 * Uses the system camera app via [ActivityResultContracts.TakePicture] (no ML
 * Kit / CameraX dependency): the captured photo is written to a fresh
 * [MediaStore] image entry (or, pre-Q, a [FileProvider]-exposed cache file) and
 * the resulting [Uri] is returned through [onPhotoCaptured] so the app can open
 * it in the editor.
 *
 * The launch is guarded so a missing camera handler can never crash the app:
 * the [android.content.pm.PackageManager.resolveActivity] pre-check plus a
 * [runCatching] around the launch turn "no camera / camera can't write the
 * destination" into `onBack()` with a toast. Before launching, the runtime
 * [Manifest.permission.CAMERA] grant is requested when missing, because some
 * system camera handlers put a start permission on their capture activity and
 * would otherwise refuse the launch. `captureUri` and the `launched` flag are
 * `rememberSaveable`, so if the system kills the process while the camera is
 * foreground, recreation does NOT re-insert a second destination and re-launch
 * the camera — instead it resumes with the restored [Uri] and lets the pending
 * result open the photo.
 */
@Composable
fun CameraScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalUiScale.current
    val unavailableMessage = stringResource(R.string.camera_unavailable)
    val storageUnavailableMessage = stringResource(R.string.camera_storage_unavailable)
    val launchFailedMessage = stringResource(R.string.camera_launch_failed)
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var launched by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = captureUri
        when {
            success && uri != null -> onPhotoCaptured(uri)
            else -> onBack()
        }
    }

    fun startCapture() {
        val uri = createCaptureUri(context)
        captureUri = uri
        when {
            uri == null -> {
                Toast.makeText(context, storageUnavailableMessage, Toast.LENGTH_SHORT).show()
                onBack()
            }
            else -> runCatching { launcher.launch(uri) }
                .onFailure {
                    Toast.makeText(context, launchFailedMessage, Toast.LENGTH_SHORT).show()
                    onBack()
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        when {
            granted -> startCapture()
            else -> {
                Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
                onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            when {
                !isCameraAvailable(context) -> {
                    Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
                    onBack()
                }
                requiresCameraPermission(context) -> {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
                else -> startCapture()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp * s))
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp * s)
        ) {
            Text(
            "Cancel",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
            )
        )
        }
    }
}

/**
 * Returns true when the device has a system camera handler that can service
 * [MediaStore.ACTION_IMAGE_CAPTURE]. On Android 11+ the manifest's `<queries>`
 * declaration is what makes the app eligible to see such a handler.
 */
internal fun isCameraAvailable(context: Context): Boolean {
    return context.packageManager.resolveActivity(
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        PackageManager.MATCH_DEFAULT_ONLY
    ) != null
}

/**
 * True when the runtime [Manifest.permission.CAMERA] grant is missing. Some
 * system camera handlers (eg. the Motorola one on the reference device) put a
 * start permission on their [MediaStore.ACTION_IMAGE_CAPTURE] activity, so the
 * launching app must be granted CAMERA or the start throws a
 * SecurityException ("Permission Denial ... with revoked permission
 * android.permission.CAMERA"). No-op on API < 23 where permissions are granted
 * at install time.
 */
internal fun requiresCameraPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    return context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
}

/**
 * Creates the destination that receives the captured photo and returns its
 * content [Uri]. Returns null only if both the MediaStore path (API >= 29) and
 * the FileProvider fallback fail.
 *
 * - API >= 29: a fresh [MediaStore] image entry, falling back to a cache file
 *   exposed through [FileProvider] if the MediaStore insert fails.
 * - API < 29: a cache file exposed through the app's [FileProvider] authority
 *   (`${packageName}.fileprovider`), because a pre-Q [MediaStore] insert needs a
 *   runtime `WRITE_EXTERNAL_STORAGE` grant that this screen does not request.
 */
internal fun createCaptureUri(context: Context): Uri? {
    // API >= 29: try MediaStore first (avoids needing WRITE_EXTERNAL_STORAGE at
    // runtime). If the insert fails (disk full, storage unavailable, etc.) fall
    // back to the FileProvider cache path so the launch is not silently killed.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        createMediaStoreCaptureUri(context.contentResolver)?.let { return it }
    }
    // Fallback / pre-Q: a cache file exposed through FileProvider.
    return runCatching {
        val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()
}

private fun createMediaStoreCaptureUri(contentResolver: ContentResolver): Uri? {
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
