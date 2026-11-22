package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ActivityType
import com.example.data.local.RouteWithDetails
import com.example.data.local.WaypointCategory
import com.example.data.preferences.MapLayerChoice
import com.example.data.preferences.TrailPreferences
import com.example.ui.components.DualEngineMapView
import com.example.ui.components.ElevationProfileChart
import com.example.ui.components.GoogleMyMapsImportGuideSheet
import com.example.ui.components.SaveOrEditRouteDialog
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.ElevationAmber
import com.example.ui.theme.TelemetryValueMedium
import com.example.ui.theme.TrailBlazeOrange
import com.example.ui.viewmodel.AiAnalysisUiState
import com.example.util.GeoMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    details: RouteWithDetails?,
    preferences: TrailPreferences,
    isOnline: Boolean,
    aiState: AiAnalysisUiState,
    onBack: () -> Unit,
    onExportKml: (RouteWithDetails) -> Unit,
    onExportGpx: (RouteWithDetails) -> Unit,
    onShareKml: (RouteWithDetails) -> Unit,
    onOpenInGoogleMaps: (RouteWithDetails) -> Unit,
    onOpenGoogleMyMapsWeb: () -> Unit,
    onUpdateMetadata: (routeId: Long, name: String, notes: String, activity: ActivityType) -> Unit,
    onDeleteRoute: (Long) -> Unit,
    onAnalyzeRoute: (RouteWithDetails, String?) -> Unit,
    onToggleTopoCanvas: (Boolean) -> Unit,
    onSelectMapLayer: (MapLayerChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMyMapsGuide by remember { mutableStateOf(false) }
    var customAiQuestion by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = details?.route?.name ?: "Route Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to saved routes"
                        )
                    }
                },
                actions = {
                    if (details != null) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("detail_edit_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit route details")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete route",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (details == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val route = details.route
        val sortedPoints = remember(details.points) { details.points.sortedBy { it.timestampMillis } }
        val sortedWaypoints = remember(details.waypoints) { details.waypoints.sortedBy { it.timestampMillis } }
        val routeColor = parseColorHex(route.colorHex, TrailBlazeOrange)
        val activity = ActivityType.fromName(route.activityType)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interactive Map Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                DualEngineMapView(
                    points = sortedPoints,
                    waypoints = sortedWaypoints,
                    currentLatitude = null,
                    currentLongitude = null,
                    currentAccuracyMeters = null,
                    hasLocationPermission = false,
                    isOnline = isOnline,
                    offlineModeEnabled = preferences.offlineModeEnabled,
                    preferTopoCanvas = preferences.preferTopoCanvas,
                    mapLayer = preferences.mapLayer,
                    useMetric = preferences.useMetricUnits,
                    autoFollowCamera = false,
                    fitBoundsOnPoints = true,
                    routeColor = routeColor,
                    onToggleTopoCanvas = onToggleTopoCanvas,
                    onSelectMapLayer = onSelectMapLayer,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Metadata header chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = activity.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (route.recordedOffline) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = CompassCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Offline Recorded",
                                style = MaterialTheme.typography.labelSmall,
                                color = CompassCyan
                            )
                        }
                    }
                }
                Text(
                    text = GeoMetrics.formatDateTime(route.startedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (route.notes.isNotBlank()) {
                Text(
                    text = route.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Google Maps Import & Export Deck
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_google_maps_import),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showMyMapsGuide = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("open_mymaps_guide_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Guide")
                        }
                    }

                    Button(
                        onClick = { onExportKml(details) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("detail_export_kml_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_export_kml))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onShareKml(details) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_share_kml_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = { onExportGpx(details) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_export_gpx_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GPX", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { onOpenInGoogleMaps(details) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .testTag("detail_open_gmaps_button")
                        ) {
                            Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Maps App", maxLines = 1)
                        }
                    }
                }
            }

            // Telemetry Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.label_distance),
                    value = GeoMetrics.formatDistance(route.distanceMeters, preferences.useMetricUnits),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.label_duration),
                    value = GeoMetrics.formatDuration(route.durationMillis),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.label_elevation_gain),
                    value = GeoMetrics.formatElevation(route.elevationGainMeters, preferences.useMetricUnits),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.label_pace),
                    value = GeoMetrics.formatSpeed(route.avgSpeedMps, preferences.useMetricUnits),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.label_track_points),
                    value = "${sortedPoints.size}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.label_waypoints),
                    value = "${sortedWaypoints.size}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Elevation profile
            ElevationProfileChart(
                points = sortedPoints,
                useMetric = preferences.useMetricUnits
            )

            // Pinned waypoints
            if (sortedWaypoints.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Pinned Waypoints (${sortedWaypoints.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        sortedWaypoints.forEach { wp ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = ElevationAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${wp.label} • ${WaypointCategory.fromName(wp.category).label}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = GeoMetrics.formatCoordinate(wp.latitude, wp.longitude),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (wp.note.isNotBlank()) {
                                        Text(
                                            text = wp.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Route Intelligence Card (Placeholder with explicit Firebase notice)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.label_route_intelligence),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Mandatory in-app notice explaining AI requires Firebase
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.notice_ai_requires_firebase),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customAiQuestion,
                        onValueChange = { customAiQuestion = it },
                        label = { Text("Ask about terrain, pacing, or offline prep (optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_question_input")
                    )

                    FilledTonalButton(
                        onClick = { onAnalyzeRoute(details, customAiQuestion) },
                        enabled = aiState !is AiAnalysisUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("analyze_route_button")
                    ) {
                        if (aiState is AiAnalysisUiState.Loading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing Route…")
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_analyze_route))
                        }
                    }

                    when (aiState) {
                        is AiAnalysisUiState.Success -> {
                            val r = aiState.report
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = r.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = r.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Terrain: ${r.terrainAnalysis}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Pacing: ${r.pacingAndEffort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Google Maps & Offline: ${r.offlineAndMapsTips}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!r.answerToQuestion.isNullOrBlank()) {
                                        Text(
                                            text = r.answerToQuestion,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        is AiAnalysisUiState.Error -> {
                            Text(
                                text = aiState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> Unit
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showEditDialog) {
            SaveOrEditRouteDialog(
                dialogTitle = "Edit Route Details",
                confirmLabel = "Update",
                initialName = route.name,
                initialNotes = route.notes,
                initialActivity = activity,
                showDiscardAction = false,
                onDismiss = { showEditDialog = false },
                onConfirm = { name, notes, act ->
                    showEditDialog = false
                    onUpdateMetadata(route.id, name, notes, act)
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Saved Route?") },
                text = {
                    Text("This permanently removes \"${route.name}\" and all ${sortedPoints.size} GPS fixes from local device storage.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteRoute(route.id)
                        },
                        modifier = Modifier.testTag("confirm_delete_route_button")
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showMyMapsGuide) {
            GoogleMyMapsImportGuideSheet(
                routeName = route.name,
                onDismiss = { showMyMapsGuide = false },
                onExportKml = { onExportKml(details) },
                onShareKml = { onShareKml(details) },
                onOpenInGoogleMaps = { onOpenInGoogleMaps(details) },
                onOpenGoogleMyMapsWeb = onOpenGoogleMyMapsWeb
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = TelemetryValueMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
