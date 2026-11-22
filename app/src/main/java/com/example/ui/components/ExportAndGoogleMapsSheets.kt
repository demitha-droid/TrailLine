package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ActivityType
import com.example.data.local.WaypointCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleMyMapsImportGuideSheet(
    routeName: String?,
    onDismiss: () -> Unit,
    onExportKml: (() -> Unit)? = null,
    onShareKml: (() -> Unit)? = null,
    onOpenInGoogleMaps: (() -> Unit)? = null,
    onOpenGoogleMyMapsWeb: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Import into Google Maps",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (routeName != null) {
                    "Visualize \"$routeName\" with its full GPS polyline, start/finish pins, and custom waypoints inside Google My Maps."
                } else {
                    "TrailLine exports standards-compliant KML 2.2 files designed for direct import into Google My Maps and Google Earth."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GuideStepRow(
                stepNumber = "1",
                title = "Export your route as KML",
                description = "Tap Export KML below to save the .kml file to your device Downloads or Google Drive."
            )
            GuideStepRow(
                stepNumber = "2",
                title = "Open Google My Maps",
                description = "Visit mymaps.google.com, create or open a map, and tap \"Import\" inside a map layer."
            )
            GuideStepRow(
                stepNumber = "3",
                title = "Select your .kml file",
                description = "Google Maps immediately plots your full saved path, trail color, and every pinned waypoint. It then syncs to the Google Maps app under You → Maps."
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (onExportKml != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onExportKml()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guide_export_kml_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save .KML for Google My Maps")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onShareKml != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onShareKml()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("guide_share_kml_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share KML")
                    }
                }
                if (onOpenInGoogleMaps != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenInGoogleMaps()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("guide_open_gmaps_button")
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Maps")
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenGoogleMyMapsWeb()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("guide_open_my_maps_web_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Google My Maps (mymaps.google.com)")
            }
        }
    }
}

@Composable
private fun GuideStepRow(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DropWaypointDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, category: WaypointCategory, note: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(WaypointCategory.VIEWPOINT) }
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin Offline Waypoint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Saved directly to your route and included as a Google Maps pin when exported to KML.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WaypointCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Ridge Overlook)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("waypoint_label_input")
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("waypoint_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        label.ifBlank { selectedCategory.label },
                        selectedCategory,
                        note
                    )
                },
                modifier = Modifier.testTag("confirm_waypoint_button")
            ) {
                Text("Save Pin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveOrEditRouteDialog(
    dialogTitle: String,
    confirmLabel: String,
    initialName: String,
    initialNotes: String,
    initialActivity: ActivityType,
    showDiscardAction: Boolean = false,
    onDismiss: () -> Unit,
    onDiscard: (() -> Unit)? = null,
    onConfirm: (name: String, notes: String, activity: ActivityType) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }
    var activity by remember(initialActivity) { mutableStateOf(initialActivity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Route Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_name_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ActivityType.entries.forEach { item ->
                        FilterChip(
                            selected = activity == item,
                            onClick = { activity = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Trail Notes (conditions, gear, landmarks)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_notes_input")
                )
                if (showDiscardAction && onDiscard != null) {
                    TextButton(
                        onClick = onDiscard,
                        modifier = Modifier.testTag("discard_recording_button")
                    ) {
                        Text(
                            text = "Discard Recording",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, notes, activity) },
                modifier = Modifier.testTag("confirm_save_route_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
