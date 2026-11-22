package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.TrackPointEntity
import com.example.data.local.WaypointEntity
import com.example.data.preferences.MapLayerChoice
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.SignalMint
import com.example.ui.theme.TrailBlazeOrange
import com.example.util.GeoMetrics
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun DualEngineMapView(
    points: List<TrackPointEntity>,
    waypoints: List<WaypointEntity>,
    currentLatitude: Double?,
    currentLongitude: Double?,
    currentAccuracyMeters: Float?,
    hasLocationPermission: Boolean,
    isOnline: Boolean,
    offlineModeEnabled: Boolean,
    preferTopoCanvas: Boolean,
    mapLayer: MapLayerChoice,
    useMetric: Boolean,
    autoFollowCamera: Boolean,
    fitBoundsOnPoints: Boolean = false,
    routeColor: Color = TrailBlazeOrange,
    onToggleTopoCanvas: (Boolean) -> Unit,
    onSelectMapLayer: (MapLayerChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapsKeyConfigured = remember { GeoMetrics.isGoogleMapsKeyConfigured() }
    val canRenderGoogleMaps = isOnline && !offlineModeEnabled && mapsKeyConfigured
    val showGoogleMaps = canRenderGoogleMaps && !preferTopoCanvas

    var layerMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (showGoogleMaps) {
            val defaultTarget = remember(points, currentLatitude, currentLongitude) {
                when {
                    currentLatitude != null && currentLongitude != null ->
                        LatLng(currentLatitude, currentLongitude)
                    points.isNotEmpty() ->
                        LatLng(points.last().latitude, points.last().longitude)
                    else -> LatLng(37.7749, -122.4194)
                }
            }

            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(defaultTarget, 15f)
            }

            LaunchedEffect(currentLatitude, currentLongitude, autoFollowCamera) {
                if (autoFollowCamera && !fitBoundsOnPoints && currentLatitude != null && currentLongitude != null) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(currentLatitude, currentLongitude), 16f),
                        durationMs = 500
                    )
                }
            }

            LaunchedEffect(points.size, fitBoundsOnPoints) {
                if (fitBoundsOnPoints && points.size >= 2) {
                    val builder = LatLngBounds.builder()
                    points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                    try {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 96))
                    } catch (_: Exception) {
                        val last = points.last()
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), 15f)
                        )
                    }
                } else if (fitBoundsOnPoints && points.size == 1) {
                    val only = points.first()
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(LatLng(only.latitude, only.longitude), 16f)
                    )
                }
            }

            val googleMapType = when (mapLayer) {
                MapLayerChoice.TERRAIN -> MapType.TERRAIN
                MapLayerChoice.NORMAL -> MapType.NORMAL
                MapLayerChoice.SATELLITE -> MapType.SATELLITE
                MapLayerChoice.HYBRID -> MapType.HYBRID
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("google_map_view"),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    mapType = googleMapType
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = hasLocationPermission,
                    compassEnabled = true,
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false
                )
            ) {
                val latLngs = remember(points) {
                    points.map { LatLng(it.latitude, it.longitude) }
                }

                if (latLngs.size >= 2) {
                    Polyline(
                        points = latLngs,
                        color = routeColor,
                        width = 11f
                    )
                }

                if (latLngs.isNotEmpty()) {
                    Marker(
                        state = MarkerState(position = latLngs.first()),
                        title = "Start",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                    if (latLngs.size > 1) {
                        Marker(
                            state = MarkerState(position = latLngs.last()),
                            title = if (fitBoundsOnPoints) "Finish" else "Latest Fix",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                        )
                    }
                }

                waypoints.forEach { wp ->
                    Marker(
                        state = MarkerState(position = LatLng(wp.latitude, wp.longitude)),
                        title = wp.label,
                        snippet = wp.note.ifBlank { wp.category },
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
            }
        } else {
            OfflineTopoMapCanvas(
                points = points,
                waypoints = waypoints,
                currentLatitude = currentLatitude,
                currentLongitude = currentLongitude,
                currentAccuracyMeters = currentAccuracyMeters,
                useMetric = useMetric,
                routeColor = routeColor,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("offline_topo_map_canvas")
            )
        }

        // Top-right floating map controls (engine toggle + Google Maps layer picker)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            if (canRenderGoogleMaps) {
                Surface(
                    onClick = { onToggleTopoCanvas(!preferTopoCanvas) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDD101F1A),
                    modifier = Modifier.testTag("map_engine_toggle_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (showGoogleMaps) Icons.Rounded.Radar else Icons.Rounded.Map,
                            contentDescription = "Switch map engine",
                            tint = if (showGoogleMaps) CompassCyan else SignalMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showGoogleMaps) "Topo Radar" else "Google Maps",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE7F0EC)
                        )
                    }
                }
            }

            if (showGoogleMaps) {
                Box {
                    FilledTonalIconButton(
                        onClick = { layerMenuExpanded = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xDD101F1A),
                            contentColor = Color(0xFFE7F0EC)
                        ),
                        modifier = Modifier.testTag("map_layer_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = "Select Google Maps layer"
                        )
                    }
                    DropdownMenu(
                        expanded = layerMenuExpanded,
                        onDismissRequest = { layerMenuExpanded = false }
                    ) {
                        MapLayerChoice.entries.forEach { layer ->
                            DropdownMenuItem(
                                text = { Text(layer.label) },
                                onClick = {
                                    onSelectMapLayer(layer)
                                    layerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
