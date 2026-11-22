package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.export.RouteExportImportManager
import com.example.data.local.ActivityType
import com.example.data.local.RouteWithDetails
import com.example.ui.screens.OfflineToolsScreen
import com.example.ui.screens.RouteDetailScreen
import com.example.ui.screens.RoutesScreen
import com.example.ui.screens.TrackScreen
import com.example.ui.theme.TrailLineTheme
import com.example.ui.viewmodel.TrailViewModel
import com.example.util.GeoMetrics

const val ROUTE_TRACK = "track"
const val ROUTE_ROUTES = "routes"
const val ROUTE_OFFLINE = "offline"
const val ROUTE_DETAIL_PATTERN = "route_detail/{routeId}"

private data class TopLevelTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topLevelTabs = listOf(
    TopLevelTab(
        route = ROUTE_TRACK,
        labelRes = R.string.nav_track,
        selectedIcon = Icons.Rounded.Explore,
        unselectedIcon = Icons.Outlined.Explore
    ),
    TopLevelTab(
        route = ROUTE_ROUTES,
        labelRes = R.string.nav_routes,
        selectedIcon = Icons.Rounded.Route,
        unselectedIcon = Icons.Outlined.Route
    ),
    TopLevelTab(
        route = ROUTE_OFFLINE,
        labelRes = R.string.nav_offline,
        selectedIcon = Icons.Rounded.CloudOff,
        unselectedIcon = Icons.Outlined.CloudOff
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrailLineTheme {
                TrailLineApp()
            }
        }
    }
}

@Composable
fun TrailLineApp(viewModel: TrailViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val completedRoutes by viewModel.completedRoutes.collectAsStateWithLifecycle()
    val vaultStats by viewModel.vaultStats.collectAsStateWithLifecycle()
    val selectedRouteDetails by viewModel.selectedRouteDetails.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val selectedActivity by viewModel.selectedActivity.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    // Pending export state for zero-permission SAF CreateDocument launchers
    var pendingExportDetails by remember { mutableStateOf<RouteWithDetails?>(null) }

    val createKmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
    ) { uri ->
        val details = pendingExportDetails
        pendingExportDetails = null
        if (uri != null && details != null) {
            viewModel.exportRouteToUri(details, uri, "kml")
        }
    }

    val createGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val details = pendingExportDetails
        pendingExportDetails = null
        if (uri != null && details != null) {
            viewModel.exportRouteToUri(details, uri, "gpx")
        }
    }

    val importRouteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRouteFromUri(uri) { importedId ->
                viewModel.selectRouteForDetail(importedId)
                navController.navigate("route_detail/$importedId")
            }
        }
    }

    fun launchKmlExport(details: RouteWithDetails) {
        pendingExportDetails = details
        createKmlLauncher.launch(GeoMetrics.sanitizeFilename(details.route.name, "kml"))
    }

    fun launchGpxExport(details: RouteWithDetails) {
        pendingExportDetails = details
        createGpxLauncher.launch(GeoMetrics.sanitizeFilename(details.route.name, "gpx"))
    }

    fun shareKml(details: RouteWithDetails) {
        try {
            val (uri, mime) = RouteExportImportManager.createShareableFileUri(context, details, "kml")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${details.route.name} — Google Maps KML Route")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "TrailLine KML route file for \"${details.route.name}\". Import into Google My Maps (mymaps.google.com) or Google Earth to visualize the full path."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share KML Route"))
        } catch (e: Exception) {
            viewModel.postMessage("Unable to share KML: ${e.localizedMessage ?: "Error"}")
        }
    }

    fun openInGoogleMaps(details: RouteWithDetails) {
        val uri = RouteExportImportManager.buildGoogleMapsDirectionsUri(
            details.points,
            ActivityType.fromName(details.route.activityType)
        )
        if (uri == null) {
            viewModel.postMessage("This route has no GPS points to open in Google Maps.")
            return
        }
        try {
            val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsIntent)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        } catch (e: Exception) {
            viewModel.postMessage("Could not launch Google Maps: ${e.localizedMessage ?: "No browser available"}")
        }
    }

    fun openGoogleMyMapsWeb() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/d/"))
            )
        } catch (e: Exception) {
            viewModel.postMessage("Could not open browser for Google My Maps.")
        }
    }

    fun launchImportPicker() {
        importRouteLauncher.launch(
            arrayOf(
                "application/vnd.google-earth.kml+xml",
                "application/gpx+xml",
                "application/xml",
                "text/xml",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelNav = currentRoute in setOf(ROUTE_TRACK, ROUTE_ROUTES, ROUTE_OFFLINE)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 640.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TRACK
                    ) {
                        composable(ROUTE_TRACK) {
                            TrackScreen(
                                liveState = liveState,
                                preferences = preferences,
                                isOnline = isOnline,
                                selectedActivity = selectedActivity,
                                onSelectActivity = viewModel::selectActivityType,
                                onEnsurePassiveLocation = viewModel::ensurePassiveLocationUpdates,
                                onStartTracking = { viewModel.startTracking() },
                                onPauseTracking = viewModel::pauseTracking,
                                onResumeTracking = viewModel::resumeTracking,
                                onFinishAndSave = { name, notes, activity ->
                                    viewModel.selectActivityType(activity)
                                    viewModel.finishAndSaveRoute(name, notes) { savedId ->
                                        viewModel.updateRouteMetadata(savedId, name, notes, activity)
                                        viewModel.selectRouteForDetail(savedId)
                                        navController.navigate("route_detail/$savedId")
                                    }
                                },
                                onDiscardActive = viewModel::discardActiveRoute,
                                onDismissRecoveredNotice = viewModel::dismissRecoveredNotice,
                                onDropWaypoint = viewModel::dropWaypoint,
                                onToggleOfflineMode = viewModel::setOfflineMode,
                                onToggleTopoCanvas = viewModel::setPreferTopoCanvas,
                                onSelectMapLayer = viewModel::setMapLayer
                            )
                        }

                        composable(ROUTE_ROUTES) {
                            RoutesScreen(
                                routes = completedRoutes,
                                useMetric = preferences.useMetricUnits,
                                onOpenRoute = { routeId ->
                                    viewModel.selectRouteForDetail(routeId)
                                    navController.navigate("route_detail/$routeId")
                                },
                                onExportKml = { launchKmlExport(it) },
                                onShareKml = { shareKml(it) },
                                onImportFile = { launchImportPicker() },
                                onJumpToTrack = {
                                    navController.navigate(ROUTE_TRACK) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(ROUTE_OFFLINE) {
                            OfflineToolsScreen(
                                preferences = preferences,
                                isOnline = isOnline,
                                vaultStats = vaultStats,
                                onSetOfflineMode = viewModel::setOfflineMode,
                                onSetUseMetric = viewModel::setUseMetricUnits,
                                onSetGpsInterval = viewModel::setGpsIntervalSeconds,
                                onSetMapLayer = viewModel::setMapLayer,
                                onSetAutoFollow = viewModel::setAutoFollowCamera,
                                onImportRouteFile = { launchImportPicker() },
                                onOpenGoogleMyMapsWeb = { openGoogleMyMapsWeb() }
                            )
                        }

                        composable(
                            route = ROUTE_DETAIL_PATTERN,
                            arguments = listOf(navArgument("routeId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val routeId = backStackEntry.arguments?.getLong("routeId")
                            LaunchedEffect(routeId) {
                                if (routeId != null) {
                                    viewModel.selectRouteForDetail(routeId)
                                }
                            }
                            RouteDetailScreen(
                                details = selectedRouteDetails,
                                preferences = preferences,
                                isOnline = isOnline,
                                aiState = aiState,
                                onBack = { navController.popBackStack() },
                                onExportKml = { launchKmlExport(it) },
                                onExportGpx = { launchGpxExport(it) },
                                onShareKml = { shareKml(it) },
                                onOpenInGoogleMaps = { openInGoogleMaps(it) },
                                onOpenGoogleMyMapsWeb = { openGoogleMyMapsWeb() },
                                onUpdateMetadata = viewModel::updateRouteMetadata,
                                onDeleteRoute = { id ->
                                    viewModel.deleteRoute(id) {
                                        navController.popBackStack()
                                    }
                                },
                                onAnalyzeRoute = viewModel::analyzeRouteWithIntelligence,
                                onToggleTopoCanvas = viewModel::setPreferTopoCanvas,
                                onSelectMapLayer = viewModel::setMapLayer
                            )
                        }
                    }
                }
            }
        }
    }
}
