package codes.pepper.whimsicalart.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The app's primary-coloured top chrome row (gallery, settings, collage — the
 * editor carries its own equivalent row in the editor module). Pure
 * presentation: [title], an optional [navigationIcon] and [actions].
 *
 * A compact custom row (NOT the fixed-height M3
 * [androidx.compose.material3.TopAppBar]) so its HEIGHT and touch targets
 * shrink with [LocalUiScale] in multi-window / split-screen. At uiScale == 1f
 * the geometry matches the Material 3 64dp app bar: 64dp row (below the status
 * bar), 48dp IconButtons, 24dp icons.
 */
@Composable
fun ScaledTopBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = LocalUiScale.current
    val barHeight = (64.dp * s).coerceAtLeast(48.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(barHeight)
                .padding(horizontal = (4.dp * s).coerceAtLeast(2.dp)),
            horizontalArrangement = Arrangement.spacedBy((4.dp * s).coerceAtLeast(2.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon?.invoke()
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * s
                ),
                maxLines = 1,
                modifier = Modifier.padding(start = (8.dp * s).coerceAtLeast(4.dp))
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End
            ) {
                actions()
            }
        }
    }
}

/** An on-primary IconButton the size a [ScaledTopBar] expects ([LocalUiScale]-aware). */
@Composable
fun ScaledTopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String?,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color? = null
) {
    val s = LocalUiScale.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size((48.dp * s).coerceAtLeast(40.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp * s)
        )
    }
}