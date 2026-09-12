package codes.pepper.whimsicalart.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale

/**
 * First-launch welcome screen. Shows the app's value props and a single
 * "Get Started" action that persists the completed flag and hands off to the
 * gallery. If onboarding was already completed, the flow is skipped and
 * [onFinish] is invoked immediately.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val s = LocalUiScale.current
    LaunchedEffect(Unit) {
        if (!viewModel.isFirstLaunch) {
            onFinish()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding((24.dp * s).coerceAtLeast(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WhimsicalArt",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * s),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp * s))
        Text(
            text = "Edit your photos beautifully",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * s),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp * s))

        FeatureRow(label = "Filters, stickers, text and beauty tools", scale = s)
        Spacer(modifier = Modifier.height(12.dp * s))
        FeatureRow(label = "Background blur and object removal", scale = s)
        Spacer(modifier = Modifier.height(12.dp * s))
        FeatureRow(label = "Collage maker and in-app camera", scale = s)
        Spacer(modifier = Modifier.height(40.dp * s))

        Button(
            onClick = {
                viewModel.complete()
                onFinish()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Get Started",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize * s)
            )
        }
    }
}

@Composable
private fun FeatureRow(label: String, scale: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale),
            textAlign = TextAlign.Center
        )
    }
}
