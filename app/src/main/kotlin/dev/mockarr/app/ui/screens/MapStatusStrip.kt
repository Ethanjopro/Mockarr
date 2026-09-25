package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.mockarr.app.ui.Motion
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.SmallPill
import dev.mockarr.app.ui.theme.Tokens

/**
 * The card's top band: one line of state copy on a tinted colour. A change of state
 * reads as one surface changing: the colour fades while the words and button fade
 * through — the old ones out, then the new ones in, never both at once.
 */
@Composable
fun StatusStrip(
    text: String,
    tone: StripTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    spoken: String? = null,
    customActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val (container, _) = stripColors(tone)
    // Timed with the words below: a default spring left the new words on the old
    // colour for a beat ("Driving" on Ready's green, the idle prompt on amber).
    val background by animateColorAsState(container, tween(Motion.QUICK_MILLIS), label = "stripBackground")
    val action = actionLabel.takeIf { onAction != null }
    val face = StripFace(text, tone, action, spoken, centred = action == null && trailing == null)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = Tokens.inset)
            .heightIn(min = Tokens.touchTarget)
            // The words live on this stable node, not in the animated faces: the band is the
            // app's one state surface, and TalkBack hears Ready → Driving → Arrived once each —
            // in the stable spoken form, so a countdown doesn't re-announce every second.
            .semantics {
                contentDescription = spoken ?: text
                liveRegion = LiveRegionMode.Polite
                if (customActions.isNotEmpty()) this.customActions = customActions
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = face,
            contentKey = { it.key },
            transitionSpec = { stripFadeThrough() },
            modifier = Modifier.weight(1f),
            label = "strip",
        ) { shown ->
            val leaving = transition.targetState == EnterExitState.PostExit
            StripWords(shown, onAction, leaving = leaving)
        }
        // Outside the fade: the speed chip is the same in every drive state and must not blink.
        if (trailing != null) {
            Spacer(Modifier.width(Tokens.space2))
            trailing()
        }
    }
}

/** What the band says in one state. A ticking countdown keeps its [key] (its spoken form is stable). */
private data class StripFace(
    val text: String,
    val tone: StripTone,
    val actionLabel: String?,
    val spoken: String?,
    /** Kept with the words: leaving, the old line re-centred when the speed chip went away. */
    val centred: Boolean,
) {
    val key: Any get() = Triple(tone, actionLabel, spoken ?: text)
}

/** The band's container and content colours for [tone]. */
@Composable
private fun stripColors(tone: StripTone): Pair<Color, Color> {
    val colors = MockarrTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StripTone.Neutral -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        StripTone.Ready -> colors.readyContainer to colors.onReadyContainer
        StripTone.Accent -> scheme.primaryContainer to scheme.onPrimaryContainer
        StripTone.Hold -> colors.holdContainer to colors.onHoldContainer
        StripTone.Error -> scheme.errorContainer to scheme.onErrorContainer
    }
}

/** Old words out, new words in, within the colour's fade; the band's height eases between line counts. */
private fun AnimatedContentTransitionScope<StripFace>.stripFadeThrough(): ContentTransform =
    fadeIn(tween(Motion.QUICK_MILLIS, delayMillis = STRIP_OUT_MILLIS))
        .togetherWith(fadeOut(tween(STRIP_OUT_MILLIS)))
        .using(SizeTransform(clip = false))

/** One state's words and button; [leaving] ones are only a picture of the old state — inert, and silent to TalkBack. */
@Composable
private fun StripWords(face: StripFace, onAction: (() -> Unit)?, leaving: Boolean) {
    val (_, content) = stripColors(face.tone)
    Row(
        modifier = Modifier.fillMaxWidth().then(if (leaving) Modifier.clearAndSetSemantics {} else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = face.text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = content,
            textAlign = if (face.centred) TextAlign.Center else TextAlign.Start,
            // Three lines at large font scales: the error line's consequence must not be cut off.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            // The band's own node speaks these words.
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        if (face.actionLabel != null) {
            Spacer(Modifier.width(Tokens.space2))
            // A leaving band keeps its button as it looked (dropping it because the new
            // state has none flickered), but a tap on it does nothing.
            SmallPill(face.actionLabel, onClick = onAction?.takeUnless { leaving } ?: {}, ink = content)
        }
    }
}

/** How long the old words take to fade before the new ones come in. */
private const val STRIP_OUT_MILLIS = 90
