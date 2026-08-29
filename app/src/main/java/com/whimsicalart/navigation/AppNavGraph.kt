package com.whimsicalart.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.whimsicalart.feature.beauty.ui.BeautyScreen
import com.whimsicalart.feature.collage.ui.CollageScreen
import com.whimsicalart.feature.editor.ui.EditorScreen
import com.whimsicalart.feature.gallery.ui.GalleryScreen

object Routes {
    const val GALLERY = "gallery"
    const val EDITOR = "editor/{imageUri}"
    const val BEAUTY = "beauty/{imageUri}"
    const val COLLAGE = "collage/{imageUris}"

    fun editor(imageUri: Uri): String {
        return "editor/${Uri.encode(imageUri.toString())}"
    }

    fun beauty(imageUri: Uri): String {
        return "beauty/${Uri.encode(imageUri.toString())}"
    }

    fun collage(imageUris: List<Uri>): String {
        val joined = imageUris.joinToString(",") { it.toString() }
        return "collage/${Uri.encode(joined)}"
    }
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.GALLERY
    ) {
        composable(Routes.GALLERY) {
            GalleryScreen(
                onPhotoSelected = { uri ->
                    navController.navigate(Routes.editor(uri))
                },
                onPhotosSelected = { uris ->
                    navController.navigate(Routes.collage(uris))
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
                },
                onOpenBeauty = {
                    navController.navigate(Routes.beauty(imageUri))
                }
            )
        }

        composable(
            route = Routes.BEAUTY,
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: ""
            val imageUri = Uri.parse(imageUriString)

            BeautyScreen(
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
