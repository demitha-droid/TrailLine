package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ActivityType
import com.example.data.local.RouteWithDetails
import com.example.ui.components.RouteMiniMapPreview
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.TelemetryValueMedium
import com.example.ui.theme.TrailBlazeOrange
import com.example.util.GeoMetrics

@Composable
fun RoutesScreen(
    routes: List<RouteWithDetails>,
    useMetric: Boolean,
    onOpenRoute: (Long) -> Unit,
    onExportKml: (RouteWithDetails) -> Unit,
    onShareKml: (RouteWithDetails) -> Unit,
    onImportFile: () -> Unit,
    onJumpToTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") }

    val filteredRoutes = remember(routes, searchQuery, filterMode) {
        routes.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.route.name.contains(searchQuery, ignoreCase = true) ||
                item.route.notes.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                "ALL" -> true
                "OFFLINE" -> item.route.recordedOffline
                else -> item.route.activityType.equals(filterMode, ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_saved_routes),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${routes.size} locally archived • Ready for Google Maps KML",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = onImportFile,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.testTag("import_route_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (routes.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search saved paths or notes…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routes_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode == "ALL",
                    onClick = { filterMode = "ALL" },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filterMode == "OFFLINE",
                    onClick = { filterMode = "OFFLINE" },
                    label = { Text("Offline Recorded") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                ActivityType.entries.forEach { act ->
                    FilterChip(
                        selected = filterMode == act.name,
                        onClick = { filterMode = act.name },
                        label = { Text(act.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        if (routes.isEmpty()) {
            EmptyRoutesState(
                onStartTracking = onJumpToTrack,
                onImportFile = onImportFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (filteredRoutes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching routes found.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredRoutes, key = { it.route.id }) { details ->
                    SavedRouteCard(
                        details = details,
                        useMetric = useMetric,
                        onClick = { onOpenRoute(details.route.id) },
                        onExportKml = { onExportKml(details) },
                        onShareKml = { onShareKml(details) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedRouteCard(
    details: RouteWithDetails,
    useMetric: Boolean,
    onClick: () -> Unit,
    onExportKml: () -> Unit,
    onShareKml: () -> Unit
) {
    val route = details.route
    val routeColor = parseColorHex(route.colorHex, TrailBlazeOrange)
    val activity = ActivityType.fromName(route.activityType)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("route_card_${route.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RouteMiniMapPreview(
                    points = details.points.sortedBy { it.timestampMillis },
                    routeColor = routeColor,
                    modifier = Modifier.size(84.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = activity.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (route.recordedOffline) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "OFFLINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CompassCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (route.sourceFormat != "LIVE_GPS") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (route.sourceFormat.contains("KML")) "KML" else "GPX",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = GeoMetrics.formatDateTime(route.startedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = GeoMetrics.formatDistance(route.distanceMeters, useMetric),
                                style = TelemetryValueMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Distance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text(
                                text = GeoMetrics.formatDuration(route.durationMillis),
                                style = TelemetryValueMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text(
                                text = GeoMetrics.formatElevation(route.elevationGainMeters, useMetric),
                                style = TelemetryValueMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gain",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onExportKml,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_export_kml_${route.id}")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export KML (Google Maps)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                IconButton(
                    onClick = onShareKml,
                    modifier = Modifier.testTag("card_share_kml_${route.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share KML route",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRoutesState(
    onStartTracking: () -> Unit,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_topo_banner_1795322285963),
            contentDescription = "Topographic alpine trail illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Text(
            text = stringResource(R.string.empty_routes_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = stringResource(R.string.empty_routes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onStartTracking,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("empty_start_track_button")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Live GPS Recording")
        }

        OutlinedButton(
            onClick = onImportFile,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("empty_import_route_button")
        ) {
            Icon(Icons.Rounded.FileUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_import_route))
        }
    }
}
