package codes.pepper.whimsicalart.feature.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Standard pull-to-refresh feedback from [GalleryRefreshContainer]: the indicator
 * appears as soon as the user pulls (keyed off the pull progress, not off
 * `isRefreshing`), stays while the re-read runs, and fades out on completion —
 * it is never hard-removed mid-flight, and it never lingers (the container owns
 * the exit fade, so even a single-frame-coalesced `true→false` flip still runs
 * the fade and lands on absent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h600dp-mdpi")
class GalleryRefreshContainerTest {

    @get:Rule
    val composeRule = createComposeRule()

    companion object {
        private const val TAG = "refreshIndicator"
    }

    private fun indicatorAlpha(): Float =
        composeRule.onNodeWithTag(TAG).fetchSemanticsNode().config[IndicatorAlphaKey]

    @Test
    fun shouldShowNoIndicatorWhileIdle() {
        composeRule.setContent {
            MaterialTheme {
                GalleryRefreshContainer(
                    isRefreshing = false,
                    onRefresh = {},
                    indicatorTestTag = TAG
                ) {
                    Box(Modifier.size(1.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertDoesNotExist()
    }

    @Test
    fun shouldShowIndicatorAsThePullStartsBeforeAnyRefresh() {
        val pullState = PullToRefreshState()
        composeRule.setContent {
            MaterialTheme {
                GalleryRefreshContainer(
                    isRefreshing = false,
                    onRefresh = {},
                    pullState = pullState,
                    indicatorTestTag = TAG
                ) {
                    Box(Modifier.size(1.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertDoesNotExist()

        // A pull in progress (distanceFraction > 0, isRefreshing still false)
        // must already show the indicator — it never waits for the refresh flag.
        composeRule.runOnUiThread { runBlocking { pullState.snapTo(0.4f) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()
    }

    @Test
    fun shouldKeepIndicatorWhileRefreshingAndFadeItOutOnCompletion() {
        var refreshing by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                GalleryRefreshContainer(
                    isRefreshing = refreshing,
                    onRefresh = {},
                    indicatorTestTag = TAG
                ) {
                    Box(Modifier.size(1.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()

        // Pause the clock so we can observe the exit animation mid-flight: the
        // indicator must be removed by an animated fade-out, not a same-frame
        // hard removal. 220ms fade — a couple of frames in it must still exist.
        composeRule.mainClock.autoAdvance = false
        refreshing = false
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(TAG).assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun shouldNotLingerWhenRefreshFlipIsCoalescedIntoASingleFrame() {
        var refreshing by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                GalleryRefreshContainer(
                    isRefreshing = refreshing,
                    onRefresh = {},
                    indicatorTestTag = TAG
                ) {
                    Box(Modifier.size(1.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()

        // mutate twice in the same frame so the composition only ever observes
        // `false` — the exact case that let material3 1.3.1's boxed indicator
        // linger. Our own exit fade still runs and lands on absent.
        composeRule.mainClock.autoAdvance = false
        refreshing = true
        refreshing = false
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(TAG).assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun shouldLeaveDistanceFractionAtRestAfterExitAndShowFeedbackOnTheNextPull() {
        val pullState = PullToRefreshState()
        var refreshing by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                GalleryRefreshContainer(
                    isRefreshing = refreshing,
                    onRefresh = {},
                    pullState = pullState,
                    indicatorTestTag = TAG
                ) {
                    Box(Modifier.size(1.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()

        // Refresh completes: the exit fade runs and the icon leaves the
        // composition.
        composeRule.runOnUiThread { refreshing = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertDoesNotExist()

        // The exit pins the pull state to rest (material3 1.3.1's state is a
        // single Animatable whose animateToHidden settles to exactly 0; our exit
        // additionally snapTo(0f)s it) — nothing is left behind to resurrect the
        // icon on its own.
        assertEquals(0f, pullState.distanceFraction)

        // A fresh pull AFTER a completed exit MUST give feedback again. The old
        // persistent "exitCompleted" latch swallowed every pull after the first
        // (regression the user saw as "a second refresh shows no icon") — a
        // pullState.snapTo() is indistinguishable from a real user drag, and the
        // indicator has to follow it.
        composeRule.runOnUiThread { runBlocking { pullState.snapTo(0.4f) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()
    }

    @Test
    fun shouldSupportARepeatedPullRefreshCycleWithoutLeavingTheIconBehind() {
        val pullState = PullToRefreshState()
        var refreshing by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                GalleryRefreshContainer(
                    isRefreshing = refreshing,
                    onRefresh = {},
                    pullState = pullState,
                    indicatorTestTag = TAG
                ) {
                    Box(Modifier.size(1.dp))
                }
            }
        }
        composeRule.waitForIdle()

        // First pull: the icon appears as soon as the grid starts moving.
        composeRule.runOnUiThread { runBlocking { pullState.snapTo(0.4f) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()

        // Refresh runs; on completion the icon fades out (not hard-removed) and
        // lands on absent after the enabled fade budget (see GalleryScreen.kt).
        composeRule.runOnUiThread { refreshing = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()
        assertEquals(1f, indicatorAlpha())

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { refreshing = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists() // mid-fade still composed
        assertTrue(indicatorAlpha() > 0f) // fading, not hard-removed
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(TAG).assertDoesNotExist() // gone after budget
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // Quick re-pull: the icon appears immediately as the pull starts (the
        // old persistent latch broke this — second pulls showed no icon), spins
        // when that refresh begins and leaves again on completion.
        composeRule.runOnUiThread { runBlocking { pullState.snapTo(0.4f) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()

        composeRule.runOnUiThread { refreshing = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertExists()

        composeRule.runOnUiThread { refreshing = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG).assertDoesNotExist()
    }
}