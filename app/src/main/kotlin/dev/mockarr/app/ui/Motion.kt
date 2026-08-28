package dev.mockarr.app.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The app's motion vocabulary (DESIGN.md): one choreographed moment — Play —
 * built from these few transitions. Compose animations already scale with the
 * system "Remove animations" setting; MapLibre camera moves don't, so
 * [rememberSystemAnimationsEnabled] gates those.
 */
object Motion {
    const val QUICK_MILLIS = 200
    const val STANDARD_MILLIS = 300

    /** Chrome that lives at the top edge leaves upward. */
    val topChromeEnter: EnterTransition =
        slideInVertically(tween(STANDARD_MILLIS)) { -it } + fadeIn(tween(STANDARD_MILLIS))
    val topChromeExit: ExitTransition =
        slideOutVertically(tween(QUICK_MILLIS)) { -it } + fadeOut(tween(QUICK_MILLIS))

    /** Chrome at the bottom edge leaves downward. */
    val bottomChromeEnter: EnterTransition =
        slideInVertically(tween(STANDARD_MILLIS)) { it } + fadeIn(tween(STANDARD_MILLIS))
    val bottomChromeExit: ExitTransition =
        slideOutVertically(tween(QUICK_MILLIS)) { it } + fadeOut(tween(QUICK_MILLIS))

    /** Floating controls pop from their centre. */
    val floatingEnter: EnterTransition =
        scaleIn(tween(STANDARD_MILLIS), initialScale = SCALE_FROM) + fadeIn(tween(STANDARD_MILLIS))
    val floatingExit: ExitTransition =
        scaleOut(tween(QUICK_MILLIS), targetScale = SCALE_FROM) + fadeOut(tween(QUICK_MILLIS))

    /** Material fade-through for content that swaps in place (the sheet peek). */
    fun <T> AnimatedContentTransitionScope<T>.fadeThrough(): ContentTransform {
        val fadeThroughIn = fadeIn(tween(STANDARD_MILLIS, delayMillis = FADE_THROUGH_DELAY)) +
            scaleIn(tween(STANDARD_MILLIS, delayMillis = FADE_THROUGH_DELAY), initialScale = FADE_THROUGH_SCALE)
        return fadeThroughIn.togetherWith(fadeOut(tween(FADE_THROUGH_DELAY)))
    }

    private const val SCALE_FROM = 0.8f
    private const val FADE_THROUGH_SCALE = 0.96f
    private const val FADE_THROUGH_DELAY = 90
}

/** True unless the user turned animations off in system settings; refreshed on resume. */
@Composable
fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(context.systemAnimationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        enabled = context.systemAnimationsEnabled()
        onPauseOrDispose { }
    }
    return enabled
}

private fun Context.systemAnimationsEnabled(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
