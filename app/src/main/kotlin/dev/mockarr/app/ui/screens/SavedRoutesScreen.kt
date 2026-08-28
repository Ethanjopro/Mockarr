package dev.mockarr.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.launch

/**
 * The Routes tab, after Strava's Saved Routes: centred title with a sort
 * action, a keyword field, a filter-chip row, and thumbnail cards. Tapping a
 * card hands the route to the Map tab; ⋯ renames or deletes (with undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedRoutesScreen(
    onRouteLoaded: () -> Unit,
    onPlanDrive: () -> Unit,
    viewModel: SavedRoutesViewModel = hiltViewModel(),
) {
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val hasAnyRoutes by viewModel.hasAnyRoutes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val profileFilter by viewModel.profileFilter.collectAsStateWithLifecycle()
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
                FilterRow(
                    sort = sort,
                    profileFilter = profileFilter,
                    onSort = viewModel::setSort,
                    onProfileFilter = viewModel::setProfileFilter,
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

/** "All ▾" mode filter, then the two orderings as chips — Strava's filter strip. */
@Composable
private fun FilterRow(
    sort: SavedRoutesViewModel.Sort,
    profileFilter: RoutingProfile?,
    onSort: (SavedRoutesViewModel.Sort) -> Unit,
    onProfileFilter: (RoutingProfile?) -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Tokens.space3, vertical = Tokens.space1),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
    ) {
        Box {
            FilterChip(
                selected = profileFilter != null,
                onClick = { modeMenu = true },
                label = {
                    Text(
                        profileFilter?.let { stringResource(it.shortLabelRes()) }
                            ?: stringResource(R.string.routes_filter_all),
                    )
                },
                leadingIcon = profileFilter?.let { filter ->
                    {
                        Icon(painterResource(filter.iconRes()), contentDescription = null)
                    }
                },
                trailingIcon = {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.routes_filter_mode_cd),
                    )
                },
            )
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.routes_filter_all)) },
                    onClick = {
                        modeMenu = false
                        onProfileFilter(null)
                    },
                )
                RoutingProfile.entries.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(stringResource(profile.shortLabelRes())) },
                        leadingIcon = { Icon(painterResource(profile.iconRes()), contentDescription = null) },
                        onClick = {
                            modeMenu = false
                            onProfileFilter(profile)
                        },
                    )
                }
            }
        }
        listOf(SavedRoutesViewModel.Sort.RECENT, SavedRoutesViewModel.Sort.LONGEST).forEach { option ->
            FilterChip(
                selected = sort == option,
                onClick = { onSort(option) },
                label = { Text(stringResource(option.labelRes())) },
            )
        }
    }
}

private fun SavedRoutesViewModel.Sort.labelRes(): Int = when (this) {
    SavedRoutesViewModel.Sort.RECENT -> R.string.routes_sort_recent
    SavedRoutesViewModel.Sort.LONGEST -> R.string.routes_sort_longest
    SavedRoutesViewModel.Sort.NAME -> R.string.routes_sort_name
}
