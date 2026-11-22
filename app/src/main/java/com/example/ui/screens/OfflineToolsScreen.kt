package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.MapLayerChoice
import com.example.data.preferences.TrailPreferences
import com.example.ui.components.GoogleMyMapsImportGuideSheet
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.ElevationAmber
import com.example.ui.theme.SignalMint
import com.example.ui.theme.TelemetryValueMedium
import com.example.ui.viewmodel.OfflineVaultStats
import com.example.util.GeoMetrics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfflineToolsScreen(
    preferences: TrailPreferences,
    isOnline: Boolean,
    vaultStats: OfflineVaultStats,
    onSetOfflineMode: (Boolean) -> Unit,
    onSetUseMetric: (Boolean) -> Unit,
    onSetGpsInterval: (Int) -> Unit,
    onSetMapLayer: (MapLayerChoice) -> Unit,
    onSetAutoFollow: (Boolean) -> Unit,
    onImportRouteFile: () -> Unit,
    onOpenGoogleMyMapsWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapsKeyReady = remember { GeoMetrics.isGoogleMapsKeyConfigured() }
    var showGuideSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.label_offline_vault),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Hero Banner with Live Offline Mode Switch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_topo_banner_1795322285963),
                contentDescription = "Offline backcountry topographic ridge",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                color = Color(0xBB0A1410),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = if (isOnline) SignalMint else ElevationAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isOnline) "Network Connected" else "No Signal — Offline Protection Engaged",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE7F0EC)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dedicated Offline Mode",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Force pure GPS + Offline Topographic Vector Map to save battery and prevent signal-drop glitches.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC2D6CD)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = preferences.offlineModeEnabled,
                            onCheckedChange = onSetOfflineMode,
                            modifier = Modifier.testTag("offline_mode_master_switch")
                        )
                    }
                }
            }
        }

        // Local Offline Vault Diagnostics
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
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = SignalMint
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "On-Device SQLite Route Vault",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.notice_offline_guaranteed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VaultMetric(
                        label = "Routes",
                        value = "${vaultStats.savedRouteCount}",
                        modifier = Modifier.weight(1f)
                    )
                    VaultMetric(
                        label = "GPS Fixes",
                        value = "${vaultStats.totalTrackPoints}",
                        modifier = Modifier.weight(1f)
                    )
                    VaultMetric(
                        label = "Offline Fixes",
                        value = "${vaultStats.offlineTrackPoints}",
                        modifier = Modifier.weight(1f)
                    )
                    VaultMetric(
                        label = "Vault Size",
                        value = "${vaultStats.databaseSizeKb} KB",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // GPS Sampling & Tracking Preferences
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "GPS & Map Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GPS Fix Interval (higher saves battery in backcountry)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            1 to "1s High",
                            3 to "3s Balanced",
                            10 to "10s Saver",
                            20 to "20s Ultra"
                        ).forEach { (sec, label) ->
                            FilterChip(
                                selected = preferences.gpsIntervalSeconds == sec,
                                onClick = { onSetGpsInterval(sec) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("gps_interval_$sec")
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Distance & Elevation Units",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.useMetricUnits,
                            onClick = { onSetUseMetric(true) },
                            label = { Text("Metric (km / m)") },
                            modifier = Modifier.testTag("units_metric_chip")
                        )
                        FilterChip(
                            selected = !preferences.useMetricUnits,
                            onClick = { onSetUseMetric(false) },
                            label = { Text("Imperial (mi / ft)") },
                            modifier = Modifier.testTag("units_imperial_chip")
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Online Google Maps Layer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapLayerChoice.entries.forEach { layer ->
                            FilterChip(
                                selected = preferences.mapLayer == layer,
                                onClick = { onSetMapLayer(layer) },
                                label = { Text(layer.label) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Follow Live GPS Camera",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Keep Google Maps centered on your moving position",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.autoFollowCamera,
                        onCheckedChange = onSetAutoFollow
                    )
                }
            }
        }

        // Google Maps API & KML Import Hub
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
                        imageVector = if (mapsKeyReady) Icons.Rounded.Shield else Icons.Rounded.Key,
                        contentDescription = null,
                        tint = if (mapsKeyReady) SignalMint else ElevationAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Maps & KML Interop",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (mapsKeyReady) {
                        "Google Maps Platform API key is active. Live satellite, terrain, and standard map tiles render automatically when online."
                    } else {
                        stringResource(R.string.notice_maps_key_placeholder)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onImportRouteFile,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tools_import_file_button")
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import .KML or .GPX Route File")
                }

                OutlinedButton(
                    onClick = { showGuideSheet = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tools_mymaps_guide_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_google_my_maps_guide))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showGuideSheet) {
        GoogleMyMapsImportGuideSheet(
            routeName = null,
            onDismiss = { showGuideSheet = false },
            onOpenGoogleMyMapsWeb = onOpenGoogleMyMapsWeb
        )
    }
}

@Composable
private fun VaultMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = TelemetryValueMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
