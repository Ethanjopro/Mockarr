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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.PlaceKind

/**
 * The Map tab's search field and its drop-down: recents on focus, structured
 * rows (glyph · name with the typed prefix bold · where · distance) as you
 * type, the previous list staying put while a lookup runs (DESIGN.md →
 * Search).
 */
@Composable
fun MapSearchBar(
    state: MapSearchViewModel.SearchState,
    units: DistanceUnits,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onResultSelected: (GeocodingResult) -> Unit,
    onClearRecents: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.map_search_hint)) },
            singleLine = true,
            shape = Tokens.controlShape,
            trailingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(modifier = Modifier.size(Tokens.space6), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.map_search_cd))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) },
        )
        val recentsOnly = state.query.isBlank() && state.results.isNotEmpty() && state.results.all { it.recent }
        if (state.results.isNotEmpty() || state.status != MapSearchViewModel.Status.IDLE) {
            Spacer(Modifier.height(Tokens.space1))
            // The popover family: lowest surface, floating (DESIGN.md → Search).
            Card(
                shape = Tokens.cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = Tokens.popoverElevation),
            ) {
                Column {
                    if (recentsOnly) RecentsHeader(onClearRecents)
                    SearchNotice(state.status)
                    // A long list ends in a fade, so the cut reads as "more below", not a clipped row.
                    val overflows = state.results.size > RESULTS_ROWS_BEFORE_FADE
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = RESULTS_MAX_HEIGHT)
                            .bottomFade(visible = overflows, height = Tokens.touchTarget / 2),
                    ) {
                        items(state.results) { suggestion ->
                            SearchResultRow(suggestion, state.query, units) { onResultSelected(suggestion.result) }
                            HorizontalDivider()
                        }
                    }
                    Row(modifier = Modifier.align(Alignment.End)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_search_close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentsHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Tokens.inset, end = Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.map_search_recent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.map_search_clear_recent)) }
    }
}

/** No-match / offline line above the list (recents stay usable underneath). */
@Composable
private fun SearchNotice(status: MapSearchViewModel.Status) {
    val text = when (status) {
        MapSearchViewModel.Status.IDLE -> return
        MapSearchViewModel.Status.NO_MATCH -> stringResource(R.string.map_search_empty)
        MapSearchViewModel.Status.FAILED -> stringResource(R.string.map_search_failed)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space3),
    )
}

@Composable
private fun SearchResultRow(
    suggestion: SearchSuggestion,
    query: String,
    units: DistanceUnits,
    onClick: () -> Unit,
) {
    val result = suggestion.result
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = Tokens.touchTarget)
            .padding(horizontal = Tokens.inset, vertical = Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(glyphFor(result.kind, suggestion.recent)),
            contentDescription = if (suggestion.recent) stringResource(R.string.map_search_recent_cd) else null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Tokens.space6),
        )
        Spacer(Modifier.width(Tokens.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightMatch(result.name, query),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            result.secondary?.let { where ->
                Text(
                    text = where,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        suggestion.distanceMeters?.let { meters ->
            Spacer(Modifier.width(Tokens.space2))
            Text(
                text = rememberFormatter().distance(meters, units),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun glyphFor(kind: PlaceKind, recent: Boolean): Int = when {
    recent -> R.drawable.ic_history
    kind == PlaceKind.STREET -> R.drawable.ic_road
    kind == PlaceKind.ADDRESS -> R.drawable.ic_home
    kind == PlaceKind.CITY || kind == PlaceKind.REGION -> R.drawable.ic_city
    else -> R.drawable.ic_place
}

/** The typed text bold inside [name] — the first match, case-insensitive. */
internal fun highlightMatch(name: String, query: String): AnnotatedString {
    val needle = query.trim()
    val start = if (needle.isEmpty()) -1 else name.indexOf(needle, ignoreCase = true)
    if (start < 0) return AnnotatedString(name)
    val end = start + needle.length
    return buildAnnotatedString {
        append(name.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.substring(start, end)) }
        append(name.substring(end))
    }
}

private val RESULTS_MAX_HEIGHT = 280.dp
private const val RESULTS_ROWS_BEFORE_FADE = 4
