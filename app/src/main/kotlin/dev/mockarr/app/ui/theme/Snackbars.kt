package dev.mockarr.app.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.mockarr.app.R

/**
 * M3's snackbar with its action as a [SmallPill] in the snackbar's own action ink, not
 * M3's bare text button (DESIGN.md → Buttons). Every screen's `SnackbarHost` is this one.
 */
@Composable
fun MockarrSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        val visuals = data.visuals
        val label = visuals.actionLabel
        val action: (@Composable () -> Unit)? = if (label != null) {
            {
                SmallPill(label, data::performAction, ink = SnackbarDefaults.actionColor)
            }
        } else {
            null
        }
        val dismiss: (@Composable () -> Unit)? = if (visuals.withDismissAction) {
            {
                IconButton(onClick = data::dismiss) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.snack_dismiss))
                }
            }
        } else {
            null
        }
        // M3's own margin around a host-drawn snackbar.
        Snackbar(modifier = Modifier.padding(Tokens.space3), action = action, dismissAction = dismiss) {
            Text(visuals.message)
        }
    }
}
