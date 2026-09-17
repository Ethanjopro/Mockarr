package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.mockarr.app.ui.Motion.roll

/**
 * A stat value that ticks over like an instrument wheel: only the glyphs that
 * changed roll, up when the number grew and down when it shrank, each out of
 * its own mask. When the length changes the whole value rolls once instead.
 * Sized to fit [maxWidth] like an auto-sizing Text would ("1 h 12 min" shrinks,
 * never clips). TalkBack reads the value as one string.
 */
@Composable
internal fun RollingText(
    text: String,
    magnitude: Double?,
    style: TextStyle,
    color: Color,
    minFontSize: TextUnit,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val tracker = remember { RollTracker() }
    val up = remember(text, magnitude) { tracker.advance(text, magnitude) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        val fitted = remember(text, style, maxWidth, minFontSize, maxFontSize) {
            val spRange = minFontSize.value..maxFontSize.value
            style.copy(fontSize = fitFontSize(measurer, text, style, constraints.maxWidth, spRange))
        }
        AnimatedContent(
            targetState = text,
            contentKey = { it.length },
            transitionSpec = { roll(up) },
            contentAlignment = Alignment.Center,
            label = "rollingValue",
        ) { value ->
            Row {
                value.forEachIndexed { index, glyph ->
                    AnimatedContent(
                        targetState = glyph,
                        transitionSpec = { roll(up) },
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.clipToBounds(),
                        label = "glyph$index",
                    ) { shown ->
                        Text(
                            text = shown.toString(),
                            style = fitted,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Remembers the last value shown so a change knows which way to roll. */
internal class RollTracker {
    private var lastText: String? = null
    private var lastMagnitude: Double? = null
    private var up: Boolean = true

    /** Records [text] and returns true when it should roll up (grew, or no basis to say otherwise). */
    fun advance(text: String, magnitude: Double?): Boolean {
        val previous = lastMagnitude
        val changed = text != lastText && magnitude != previous
        if (changed && magnitude != null && previous != null) up = magnitude > previous
        lastText = text
        lastMagnitude = magnitude
        return up
    }
}

/** The largest size in [spRange] (2 sp steps, largest first) at which [text] fits [maxWidthPx]. */
private fun fitFontSize(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    spRange: ClosedFloatingPointRange<Float>,
): TextUnit {
    var size = spRange.endInclusive
    while (size > spRange.start) {
        val width = measurer.measure(text, style.copy(fontSize = size.sp), softWrap = false).size.width
        if (width <= maxWidthPx) return size.sp
        size -= FIT_STEP_SP
    }
    return spRange.start.sp
}

private const val FIT_STEP_SP = 2f
