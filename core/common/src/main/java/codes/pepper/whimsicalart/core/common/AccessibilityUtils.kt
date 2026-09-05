package codes.pepper.whimsicalart.core.common

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

object AccessibilityUtils {

    @Composable
    fun isAccessibilityEnabled(): Boolean {
        val context = LocalContext.current
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return accessibilityManager.isEnabled
    }

    @Composable
    fun isPreviewMode(): Boolean {
        return LocalInspectionMode.current
    }

    fun getContentDescription(toolName: String, state: String? = null): String {
        return if (state != null) {
            "$toolName: $state"
        } else {
            toolName
        }
    }

    fun getActionDescription(action: String, target: String? = null): String {
        return if (target != null) {
            "$action $target"
        } else {
            action
        }
    }
}
