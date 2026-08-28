package dev.mockarr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens

/**
 * Strava's settings icon grid, one tile per headline setting: icon, name, and
 * the current value as the subtitle. Tapping opens or toggles it.
 */
@Composable
fun SettingTile(
    iconRes: Int,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueTone: TileTone = TileTone.Neutral,
) {
    val valueColor = when (valueTone) {
        TileTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        TileTone.Ready -> MockarrTheme.colors.ready
        TileTone.Error -> MaterialTheme.colorScheme.error
    }
    Card(
        onClick = onClick,
        shape = Tokens.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.semantics { contentDescription = "$title, $value" },
    ) {
        Column(modifier = Modifier.padding(Tokens.space4).heightIn(min = TILE_MIN_HEIGHT)) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(TILE_ICON),
            )
            Spacer(Modifier.height(Tokens.space3))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class TileTone { Neutral, Ready, Error }

/** Small-caps section label, Strava's list grouping. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Tokens.inset,
            end = Tokens.inset,
            top = Tokens.space6,
            bottom = Tokens.space1,
        ),
    )
}

/** A slider setting: icon, title + description, the value trailing, the slider underneath. */
@Composable
fun SliderRow(
    iconRes: Int,
    title: String,
    description: String,
    valueText: String,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    contentDescription: String,
) {
    Column(modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Tokens.space4))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            modifier = Modifier
                .padding(start = Tokens.space6 + Tokens.space4)
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = valueText
                },
        )
    }
}

/** A navigating row with the current value trailing ("Default travel mode  Drive ›"). */
@Composable
fun ValueRow(iconRes: Int, title: String, description: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.space8 + Tokens.space6)
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.inset, vertical = Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Tokens.space4))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Tokens.space1))
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Custom OSRM server: URL field, a live Test, and a way back to the public server. */
@Composable
fun ServerDialog(
    currentUrl: String,
    isCustom: Boolean,
    testState: SettingsViewModel.TestState,
    onTest: (String) -> Unit,
    onSave: (String) -> Unit,
    onUsePublic: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(if (isCustom) currentUrl else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_server_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.space2)) {
                Text(
                    text = stringResource(R.string.settings_server_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text(stringResource(R.string.settings_server_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        if (testState is SettingsViewModel.TestState.Testing) {
                            CircularProgressIndicator(modifier = Modifier.size(Tokens.space6), strokeWidth = 2.dp)
                        } else {
                            TextButton(enabled = url.isNotBlank(), onClick = { onTest(url) }) {
                                Text(stringResource(R.string.settings_server_test))
                            }
                        }
                    },
                )
                when (testState) {
                    SettingsViewModel.TestState.Success -> Text(
                        text = stringResource(R.string.settings_server_ok),
                        style = MaterialTheme.typography.bodySmall,
                        color = MockarrTheme.colors.ready,
                    )
                    is SettingsViewModel.TestState.Failure -> Text(
                        text = testState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                if (isCustom) {
                    TextButton(onClick = onUsePublic) { Text(stringResource(R.string.settings_server_use_public)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = { onSave(url) }) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private val TILE_MIN_HEIGHT = 96.dp
private val TILE_ICON = 28.dp
