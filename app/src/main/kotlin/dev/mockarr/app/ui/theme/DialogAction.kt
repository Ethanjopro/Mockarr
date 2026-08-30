package dev.mockarr.app.ui.theme

/** One dialog verb: a pill primary or an outlined secondary; [destructive] paints the primary in error. */
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)
