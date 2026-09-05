package codes.pepper.whimsicalart.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import codes.pepper.whimsicalart.feature.camera.ui.CameraScreen
import codes.pepper.whimsicalart.feature.collage.ui.CollageScreen
import codes.pepper.whimsicalart.feature.editor.ui.EditorScreen
import codes.pepper.whimsicalart.feature.gallery.ui.GalleryScreen
import codes.pepper.whimsicalart.feature.settings.ui.SettingsScreen
import codes.pepper.whimsicalart.ui.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val GALLERY = "gallery"
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{imageUri}"
    const val COLLAGE = "collage/{imageUris}"

    fun editor(imageUri: Uri): String {
        return "editor/${Uri.encode(imageUri.toString())}"
    }

    fun collage(imageUris: List<Uri>): String {
        val joined = imageUris.joinToString(",") { it.toString() }
        return "collage/${Uri.encode(joined)}"
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.GALLERY
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Routes.GALLERY) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CAMERA) {
            CameraScreen(
                onPhotoCaptured = { uri ->
                    navController.navigate(Routes.editor(uri))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                onPhotoSelected = { uri ->
                    navController.navigate(Routes.editor(uri))
                },
                onPhotosSelected = { uris ->
                    navController.navigate(Routes.collage(uris))
                },
                onOpenCamera = {
                    navController.navigate(Routes.CAMERA)
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: ""
            val imageUri = Uri.parse(imageUriString)
            
            EditorScreen(
                imageUri = imageUri,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.COLLAGE,
            arguments = listOf(
                navArgument("imageUris") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val imageUrisString = backStackEntry.arguments?.getString("imageUris") ?: ""
            val imageUris = imageUrisString
                .takeIf { it.isNotBlank() }
                ?.split(",")
                ?.map { Uri.parse(it) }
                .orEmpty()

            CollageScreen(
                initialPhotos = imageUris,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
