package dev.mockarr.app.ui.theme

/**
 * One dialog verb: the filled pill (confirm) or the outlined one (dismiss).
 * Never red: a destructive verb names what it loses ("Clear route") and
 * carries the trash glyph as [iconRes].
 */
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val iconRes: Int? = null,
)
