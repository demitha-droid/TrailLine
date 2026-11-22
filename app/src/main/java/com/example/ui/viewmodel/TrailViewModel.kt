package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.PlaceholderRouteIntelligenceRepository
import com.example.data.ai.RouteIntelligenceReport
import com.example.data.ai.RouteIntelligenceRepository
import com.example.data.export.RouteExportImportManager
import com.example.data.local.ActivityType
import com.example.data.local.RouteWithDetails
import com.example.data.local.WaypointCategory
import com.example.data.preferences.MapLayerChoice
import com.example.data.preferences.TrailPreferences
import com.example.tracking.LiveTrackingState
import com.example.tracking.TrackingSessionManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OfflineVaultStats(
    val savedRouteCount: Int = 0,
    val totalTrackPoints: Int = 0,
    val offlineTrackPoints: Int = 0,
    val databaseSizeKb: Long = 0L
)

sealed interface AiAnalysisUiState {
    data object Idle : AiAnalysisUiState
    data object Loading : AiAnalysisUiState
    data class Success(val report: RouteIntelligenceReport) : AiAnalysisUiState
    data class Error(val message: String) : AiAnalysisUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrailViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val sessionManager = TrackingSessionManager.getInstance(appContext)
    private val repository = sessionManager.repository
    private val prefsRepo = sessionManager.preferencesRepository
    private val aiRepository: RouteIntelligenceRepository = PlaceholderRouteIntelligenceRepository()

    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private var passiveUpdatesActive = false

    private val passiveCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { sessionManager.onPassiveLocationUpdate(it) }
        }
    }

    val liveState: StateFlow<LiveTrackingState> = sessionManager.state

    val preferences: StateFlow<TrailPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrailPreferences()
    )

    val isOnline: StateFlow<Boolean> = sessionManager.connectivityMonitor.isNetworkAvailable

    val completedRoutes: StateFlow<List<RouteWithDetails>> = repository.completedRoutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val vaultStats: StateFlow<OfflineVaultStats> = combine(
        repository.savedRouteCount,
        repository.totalTrackPointCount,
        repository.offlineTrackPointCount
    ) { routeCount, totalPoints, offlinePoints ->
        val dbFile = appContext.getDatabasePath("trailline_offline_vault.db")
        val sizeKb = if (dbFile.exists()) (dbFile.length() / 1024L).coerceAtLeast(1L) else 0L
        OfflineVaultStats(
            savedRouteCount = routeCount,
            totalTrackPoints = totalPoints,
            offlineTrackPoints = offlinePoints,
            databaseSizeKb = sizeKb
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OfflineVaultStats()
    )

    private val _selectedRouteId = MutableStateFlow<Long?>(null)
    val selectedRouteId: StateFlow<Long?> = _selectedRouteId.asStateFlow()

    val selectedRouteDetails: StateFlow<RouteWithDetails?> = _selectedRouteId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeRoute(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _aiState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val aiState: StateFlow<AiAnalysisUiState> = _aiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _selectedActivity = MutableStateFlow(ActivityType.HIKE)
    val selectedActivity: StateFlow<ActivityType> = _selectedActivity.asStateFlow()

    fun selectActivityType(type: ActivityType) {
        _selectedActivity.value = type
    }

    fun selectRouteForDetail(routeId: Long?) {
        _selectedRouteId.value = routeId
        _aiState.value = AiAnalysisUiState.Idle
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun postMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun ensurePassiveLocationUpdates() {
        if (passiveUpdatesActive) return
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) sessionManager.onPassiveLocationUpdate(loc)
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(2_500L)
                .build()
            fusedClient.requestLocationUpdates(request, passiveCallback, Looper.getMainLooper())
            passiveUpdatesActive = true
        } catch (_: SecurityException) {
            // Ignored when permission is revoked
        }
    }

    fun startTracking(customName: String? = null) {
        sessionManager.startTracking(_selectedActivity.value, customName)
    }

    fun pauseTracking() = sessionManager.pauseTracking()

    fun resumeTracking() = sessionManager.resumeTracking()

    fun finishAndSaveRoute(
        name: String?,
        notes: String?,
        onSavedRouteId: (Long) -> Unit = {}
    ) {
        sessionManager.finishAndSaveRoute(name, notes) { savedId ->
            if (savedId != null) {
                _snackbarMessage.value = "Route saved locally to Offline Vault."
                onSavedRouteId(savedId)
            }
        }
    }

    fun discardActiveRoute() {
        sessionManager.discardActiveRoute()
        _snackbarMessage.value = "Active recording discarded."
    }

    fun dismissRecoveredNotice() = sessionManager.dismissRecoveredNotice()

    fun dropWaypoint(label: String, category: WaypointCategory, note: String) {
        sessionManager.dropWaypoint(label, category, note)
        _snackbarMessage.value = "Waypoint \"$label\" pinned to route."
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setOfflineMode(enabled)
            _snackbarMessage.value = if (enabled) {
                "Offline Mode ON — using local GPS & Topographic Vector Map."
            } else {
                "Offline Mode OFF — live Google Maps enabled when online."
            }
        }
    }

    fun setUseMetricUnits(metric: Boolean) {
        viewModelScope.launch { prefsRepo.setUseMetricUnits(metric) }
    }

    fun setGpsIntervalSeconds(seconds: Int) {
        viewModelScope.launch { prefsRepo.setGpsIntervalSeconds(seconds) }
    }

    fun setMapLayer(layer: MapLayerChoice) {
        viewModelScope.launch { prefsRepo.setMapLayer(layer) }
    }

    fun setAutoFollowCamera(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoFollowCamera(enabled) }
    }

    fun setPreferTopoCanvas(prefer: Boolean) {
        viewModelScope.launch { prefsRepo.setPreferTopoCanvas(prefer) }
    }

    fun updateRouteMetadata(routeId: Long, name: String, notes: String, activityType: ActivityType) {
        viewModelScope.launch {
            repository.updateRouteMetadata(routeId, name, notes, activityType)
            _snackbarMessage.value = "Route details updated."
        }
    }

    fun deleteRoute(routeId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteRoute(routeId)
            if (_selectedRouteId.value == routeId) {
                _selectedRouteId.value = null
            }
            _snackbarMessage.value = "Route deleted from device."
            onDeleted()
        }
    }

    fun exportRouteToUri(details: RouteWithDetails, uri: Uri, format: String) {
        viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.Default) {
                    if (format.equals("kml", ignoreCase = true)) {
                        RouteExportImportManager.generateKml(details)
                    } else {
                        RouteExportImportManager.generateGpx(details)
                    }
                }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: throw IllegalStateException("Could not open target document for writing.")
                }
                val fmtUpper = format.uppercase()
                _snackbarMessage.value = "$fmtUpper exported! Import it into Google My Maps to visualize your full path."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = "Export failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun importRouteFromUri(uri: Uri, onImported: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val fallbackName = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.')
                    ?.ifBlank { "Imported Route" } ?: "Imported Route"

                val imported = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        RouteExportImportManager.parseRouteFile(stream, fallbackName)
                    } ?: throw IllegalStateException("Unable to read selected route file.")
                }

                val newId = repository.saveImportedRoute(imported)
                _snackbarMessage.value = "Imported \"${imported.name}\" (${imported.points.size} points) for offline viewing."
                onImported(newId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = "Import failed: ${e.localizedMessage ?: "Invalid KML/GPX file"}"
            }
        }
    }

    fun analyzeRouteWithIntelligence(details: RouteWithDetails, customQuestion: String? = null) {
        viewModelScope.launch {
            _aiState.value = AiAnalysisUiState.Loading
            try {
                val report = aiRepository.analyzeRoute(
                    details = details,
                    useMetric = preferences.value.useMetricUnits,
                    customQuestion = customQuestion
                )
                _aiState.value = AiAnalysisUiState.Success(report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiAnalysisUiState.Error(
                    e.localizedMessage ?: "Unable to analyze route right now."
                )
            }
        }
    }

    override fun onCleared() {
        if (passiveUpdatesActive) {
            try {
                fusedClient.removeLocationUpdates(passiveCallback)
            } catch (_: Exception) {
                // Ignore
            }
            passiveUpdatesActive = false
        }
        super.onCleared()
    }
}
