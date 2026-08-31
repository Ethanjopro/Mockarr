package dev.mockarr.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Layout constants shared by every screen. Spacing stays on the 4-dp grid;
 * radii follow one family (sheet > card > control) so no screen invents a
 * fourth corner.
 */
object Tokens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space6 = 24.dp
    val space8 = 32.dp

    /** Horizontal inset for content inside sheets and cards. */
    val inset = 20.dp

    /** Gap between the screen edge and floating map controls. */
    val mapEdge = 12.dp

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val cardShape = RoundedCornerShape(16.dp)
    val controlShape = RoundedCornerShape(12.dp)

    val touchTarget = 48.dp

    /** Popover rows sit denser than sheet rows (DESIGN.md → Popover: 44dp min). */
    val popoverRowHeight = 44.dp

    /** Soft lift for the floating stat card and the sheet (DESIGN.md → Soft Lift). */
    val cardElevation = 4.dp
    val sheetElevation = 8.dp

    /** Strava's white map pills and their popovers float too (DESIGN.md → Soft Lift). */
    val floatingElevation = 6.dp
    val popoverElevation = 8.dp
    val pillSize = 48.dp

    /** Full-width pill actions (Pause / Resume / Finish). */
    val pillHeight = 56.dp

    /** The numbered stop disc, in the sheet and the popover (the map marker is 11dp radius + ring). */
    val discSize = 28.dp

    /** Material centres the sheet at this width (landscape/tablet); floating overlays match it. */
    val sheetMaxWidth = 640.dp
}
