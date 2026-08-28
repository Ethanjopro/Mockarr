package dev.mockarr.app.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.ui.map.effectiveStyleUrl
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.data.SavedRouteEntity
import kotlinx.coroutines.launch

/**
 * The Routes tab, after Strava's Saved Routes: centred title with a sort
 * action, a keyword field, and thumbnail cards. Tapping a
 * card hands the route to the Map tab; ⋯ renames or deletes (with undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedRoutesScreen(
    onBack: () -> Unit,
    onRouteLoaded: () -> Unit,
    onPlanDrive: () -> Unit,
    viewModel: SavedRoutesViewModel = hiltViewModel(),
) {
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val hasAnyRoutes by viewModel.hasAnyRoutes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val mapStyleUrl by viewModel.mapStyleUrl.collectAsStateWithLifecycle()
    val thumbStyleUrl = effectiveStyleUrl(mapStyleUrl, isSystemInDarkTheme())
    val palette = MockarrTheme.colors.map
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // One "now" per composition of the list keeps every card's "Created today" consistent.
    val now = remember(routes) { System.currentTimeMillis() }
    var renaming by remember { mutableStateOf<SavedRouteEntity?>(null) }
    val deletedTemplate = stringResource(R.string.routes_deleted)
    val undoLabel = stringResource(R.string.routes_undo)

    renaming?.let { entity ->
        RenameRouteDialog(
            initialName = entity.name,
            onConfirm = { name ->
                viewModel.rename(entity, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.routes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
                actions = { SortAction(sort = sort, onSort = viewModel::setSort) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasAnyRoutes) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.routes_search_hint)) },
                    singleLine = true,
                    shape = Tokens.controlShape,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.space3, vertical = Tokens.space2),
                )
            }
            if (routes.isEmpty()) {
                RoutesEmptyState(hasAnyRoutes = hasAnyRoutes, onPlanDrive = onPlanDrive)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Tokens.space3, vertical = Tokens.space2),
                    verticalArrangement = Arrangement.spacedBy(Tokens.space2),
                ) {
                    items(routes, key = { it.id }) { entity ->
                        SavedRouteCard(
                            entity = entity,
                            units = units,
                            thumbStyleUrl = thumbStyleUrl,
                            palette = palette,
                            nowEpochMillis = now,
                            loadThumbnail = viewModel::thumbnail,
                            onClick = {
                                viewModel.load(entity)
                                onRouteLoaded()
                            },
                            onRename = { renaming = entity },
                            onDelete = {
                                viewModel.delete(entity)
                                scope.launch {
                                    val result = snackbarHost.showSnackbar(
                                        message = deletedTemplate.format(splitRouteName(entity.name).title),
                                        actionLabel = undoLabel,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(entity)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Sort icon in the app bar (Strava's pencil slot) with a menu of orderings. */
@Composable
private fun SortAction(sort: SavedRoutesViewModel.Sort, onSort: (SavedRoutesViewModel.Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.routes_sort_cd))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        SavedRoutesViewModel.Sort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                trailingIcon = if (option == sort) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    open = false
                    onSort(option)
                },
            )
        }
    }
}

private fun SavedRoutesViewModel.Sort.labelRes(): Int = when (this) {
    SavedRoutesViewModel.Sort.RECENT -> R.string.routes_sort_recent
    SavedRoutesViewModel.Sort.LONGEST -> R.string.routes_sort_longest
    SavedRoutesViewModel.Sort.NAME -> R.string.routes_sort_name
}
