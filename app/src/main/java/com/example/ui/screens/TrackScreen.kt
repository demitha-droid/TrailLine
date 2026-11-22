package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.ActivityType
import com.example.data.local.WaypointCategory
import com.example.data.preferences.MapLayerChoice
import com.example.data.preferences.TrailPreferences
import com.example.tracking.LiveTrackingState
import com.example.tracking.TrackingStatus
import com.example.ui.components.DropWaypointDialog
import com.example.ui.components.DualEngineMapView
import com.example.ui.components.SaveOrEditRouteDialog
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.ElevationAmber
import com.example.ui.theme.SignalMint
import com.example.ui.theme.TelemetryValueLarge
import com.example.ui.theme.TelemetryValueMedium
import com.example.ui.theme.TrailBlazeOrange
import com.example.util.GeoMetrics

@Composable
fun TrackScreen(
    liveState: LiveTrackingState,
    preferences: TrailPreferences,
    isOnline: Boolean,
    selectedActivity: ActivityType,
    onSelectActivity: (ActivityType) -> Unit,
    onEnsurePassiveLocation: () -> Unit,
    onStartTracking: () -> Unit,
    onPauseTracking: () -> Unit,
    onResumeTracking: () -> Unit,
    onFinishAndSave: (name: String, notes: String, activity: ActivityType) -> Unit,
    onDiscardActive: () -> Unit,
    onDismissRecoveredNotice: () -> Unit,
    onDropWaypoint: (label: String, category: WaypointCategory, note: String) -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onToggleTopoCanvas: (Boolean) -> Unit,
    onSelectMapLayer: (MapLayerChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(checkLocationPermission(context))
    }
    var showWaypointDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            onEnsurePassiveLocation()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            onEnsurePassiveLocation()
        }
    }

    val effectiveOffline = preferences.offlineModeEnabled || !isOnline
    val activeRouteColor = remember(liveState.activityType, selectedActivity, liveState.status) {
        val type = if (liveState.status == TrackingStatus.IDLE) selectedActivity else liveState.activityType
        parseColorHex(type.defaultColorHex, TrailBlazeOrange)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Upper live map stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            DualEngineMapView(
                points = liveState.livePoints,
                waypoints = liveState.liveWaypoints,
                currentLatitude = liveState.currentLatitude,
                currentLongitude = liveState.currentLongitude,
                currentAccuracyMeters = liveState.currentAccuracyMeters,
                hasLocationPermission = hasLocationPermission,
                isOnline = isOnline,
                offlineModeEnabled = preferences.offlineModeEnabled,
                preferTopoCanvas = preferences.preferTopoCanvas,
                mapLayer = preferences.mapLayer,
                useMetric = preferences.useMetricUnits,
                autoFollowCamera = preferences.autoFollowCamera,
                fitBoundsOnPoints = false,
                routeColor = activeRouteColor,
                onToggleTopoCanvas = onToggleTopoCanvas,
                onSelectMapLayer = onSelectMapLayer,
                modifier = Modifier.fillMaxSize()
            )

            // Top-center floating status pill strip (respecting status bar)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 54.dp, start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Offline Mode toggle chip
                    Surface(
                        onClick = { onToggleOfflineMode(!preferences.offlineModeEnabled) },
                        shape = RoundedCornerShape(50),
                        color = when {
                            preferences.offlineModeEnabled -> Color(0xEE0C3144)
                            !isOnline -> Color(0xEE471B09)
                            else -> Color(0xDD101F1A)
                        },
                        modifier = Modifier.testTag("quick_offline_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    preferences.offlineModeEnabled -> Icons.Rounded.CloudOff
                                    !isOnline -> Icons.Rounded.WifiOff
                                    else -> Icons.Rounded.Wifi
                                },
                                contentDescription = "Connectivity mode",
                                tint = when {
                                    preferences.offlineModeEnabled -> CompassCyan
                                    !isOnline -> ElevationAmber
                                    else -> SignalMint
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    preferences.offlineModeEnabled -> stringResource(R.string.status_offline_manual)
                                    !isOnline -> stringResource(R.string.status_offline_auto)
                                    else -> stringResource(R.string.status_online)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEAF4F0)
                            )
                        }
                    }

                    // GPS satellite lock chip
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xDD101F1A)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = if (liveState.isGpsLocked) {
                                    Icons.Rounded.GpsFixed
                                } else {
                                    Icons.Rounded.GpsNotFixed
                                },
                                contentDescription = "GPS fix status",
                                tint = if (liveState.isGpsLocked) SignalMint else ElevationAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    !hasLocationPermission -> "GPS Off"
                                    liveState.isGpsLocked -> "GPS Lock"
                                    else -> "Searching"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEAF4F0)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = liveState.recoveredRouteNotice != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = liveState.recoveredRouteNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissRecoveredNotice) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }

        // Bottom Telemetry & Controls Cockpit
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasLocationPermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SatelliteAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.status_permission_needed),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Grant GPS permission to record routes online or completely offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { permissionLauncher.launch(permissionsToRequest) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("grant_location_button")
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }

                // Telemetry HUD Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryCell(
                        label = stringResource(R.string.label_distance),
                        value = GeoMetrics.formatDistance(
                            liveState.distanceMeters,
                            preferences.useMetricUnits
                        ),
                        large = true,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCell(
                        label = stringResource(R.string.label_duration),
                        value = GeoMetrics.formatDuration(liveState.elapsedMillis),
                        large = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryCell(
                        label = stringResource(R.string.label_elevation_gain),
                        value = GeoMetrics.formatElevation(
                            liveState.elevationGainMeters,
                            preferences.useMetricUnits
                        ),
                        large = false,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCell(
                        label = stringResource(R.string.label_altitude),
                        value = GeoMetrics.formatElevation(
                            liveState.currentAltitudeMeters,
                            preferences.useMetricUnits
                        ),
                        large = false,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCell(
                        label = stringResource(R.string.label_track_points),
                        value = "${liveState.livePoints.size}",
                        large = false,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                if (liveState.status == TrackingStatus.IDLE) {
                    // Activity type selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedActivity == type,
                                onClick = { onSelectActivity(type) },
                                label = { Text(type.label) },
                                modifier = Modifier.testTag("activity_chip_${type.name.lowercase()}")
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!hasLocationPermission) {
                                permissionLauncher.launch(permissionsToRequest)
                            } else {
                                onStartTracking()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_tracking_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (effectiveOffline) {
                                "Start Offline Track (${selectedActivity.label})"
                            } else {
                                "Start Track (${selectedActivity.label})"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                } else {
                    // Active recording banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = if (liveState.status == TrackingStatus.RECORDING) {
                                    SignalMint
                                } else {
                                    ElevationAmber
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(9.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = liveState.routeName.ifBlank { "Active Route" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (effectiveOffline) "Offline Vault • Safe" else "Local Vault • Live",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (effectiveOffline) CompassCyan else SignalMint
                        )
                    }

                    // Active control buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showWaypointDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("drop_waypoint_button")
                        ) {
                            Icon(
                                Icons.Rounded.AddLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pin", maxLines = 1)
                        }

                        if (liveState.status == TrackingStatus.RECORDING) {
                            FilledTonalButton(
                                onClick = onPauseTracking,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pause_tracking_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause", maxLines = 1)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onResumeTracking,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("resume_tracking_button")
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume", maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("finish_route_button")
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showWaypointDialog) {
        DropWaypointDialog(
            onDismiss = { showWaypointDialog = false },
            onConfirm = { label, category, note ->
                onDropWaypoint(label, category, note)
                showWaypointDialog = false
            }
        )
    }

    if (showSaveDialog) {
        SaveOrEditRouteDialog(
            dialogTitle = "Finish & Save Route",
            confirmLabel = "Save Route",
            initialName = liveState.routeName,
            initialNotes = "",
            initialActivity = liveState.activityType,
            showDiscardAction = true,
            onDismiss = { showSaveDialog = false },
            onDiscard = {
                showSaveDialog = false
                onDiscardActive()
            },
            onConfirm = { name, notes, activity ->
                showSaveDialog = false
                onFinishAndSave(name, notes, activity)
            }
        )
    }
}

@Composable
private fun TelemetryCell(
    label: String,
    value: String,
    large: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (large) TelemetryValueLarge else TelemetryValueMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun checkLocationPermission(context: android.content.Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

fun parseColorHex(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
