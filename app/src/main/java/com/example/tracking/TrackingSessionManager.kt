package com.example.tracking

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import com.example.data.connectivity.ConnectivityMonitor
import com.example.data.local.ActivityType
import com.example.data.local.RouteEntity
import com.example.data.local.TrackPointEntity
import com.example.data.local.TrailDatabase
import com.example.data.local.WaypointCategory
import com.example.data.local.WaypointEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.TrailRepository
import com.example.util.GeoMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TrackingStatus {
    IDLE,
    RECORDING,
    PAUSED
}

data class LiveTrackingState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val activeRouteId: Long? = null,
    val routeName: String = "",
    val activityType: ActivityType = ActivityType.HIKE,
    val elapsedMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val currentAltitudeMeters: Double? = null,
    val currentSpeedMps: Float = 0f,
    val currentAccuracyMeters: Float? = null,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val isGpsLocked: Boolean = false,
    val isOfflineRecording: Boolean = false,
    val livePoints: List<TrackPointEntity> = emptyList(),
    val liveWaypoints: List<WaypointEntity> = emptyList(),
    val recoveredRouteNotice: String? = null
)

class TrackingSessionManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = TrailRepository(TrailDatabase.getInstance(appContext).trailDao())
    val preferencesRepository = UserPreferencesRepository(appContext)
    val connectivityMonitor = ConnectivityMonitor(appContext)

    private val _state = MutableStateFlow(LiveTrackingState())
    val state: StateFlow<LiveTrackingState> = _state.asStateFlow()

    private var activeRoute: RouteEntity? = null
    private var lastFixPoint: TrackPointEntity? = null
    private var lastElevationRef: Double? = null
    private var maxAltitude: Double? = null
    private var minAltitude: Double? = null
    private var timerJob: Job? = null
    private var accumulatedElapsedMillis: Long = 0L
    private var segmentStartElapsedAt: Long = 0L

    init {
        scope.launch {
            recoverInterruptedSessionIfAny()
        }
    }

    private suspend fun recoverInterruptedSessionIfAny() {
        val unfinished = repository.getActiveRecordingRoute() ?: return
        val existingPoints = repository.getPointsForRoute(unfinished.id)
        val stats = GeoMetrics.computeStats(existingPoints, unfinished.durationMillis)
        activeRoute = unfinished
        lastFixPoint = existingPoints.lastOrNull()
        lastElevationRef = existingPoints.lastOrNull { it.altitudeMeters != null }?.altitudeMeters
        maxAltitude = stats.maxAltitudeMeters
        minAltitude = stats.minAltitudeMeters
        accumulatedElapsedMillis = unfinished.durationMillis

        _state.update {
            it.copy(
                status = TrackingStatus.PAUSED,
                activeRouteId = unfinished.id,
                routeName = unfinished.name,
                activityType = ActivityType.fromName(unfinished.activityType),
                elapsedMillis = unfinished.durationMillis,
                distanceMeters = stats.distanceMeters,
                elevationGainMeters = stats.elevationGainMeters,
                currentLatitude = lastFixPoint?.latitude,
                currentLongitude = lastFixPoint?.longitude,
                currentAltitudeMeters = lastFixPoint?.altitudeMeters,
                isOfflineRecording = unfinished.recordedOffline,
                livePoints = existingPoints,
                recoveredRouteNotice = "Recovered unfinished offline session \"${unfinished.name}\" (${existingPoints.size} GPS fixes preserved)."
            )
        }
    }

    fun dismissRecoveredNotice() {
        _state.update { it.copy(recoveredRouteNotice = null) }
    }

    fun startTracking(activityType: ActivityType, customName: String? = null) {
        if (_state.value.status == TrackingStatus.RECORDING) return
        scope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            val offlineNow = prefs.offlineModeEnabled || !connectivityMonitor.isNetworkAvailable.value
            val defaultName = customName?.trim().takeIf { !it.isNullOrBlank() } ?: run {
                val stamp = SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date())
                "${activityType.label} — $stamp"
            }

            val created = repository.startNewRoute(
                name = defaultName,
                activityType = activityType,
                startedOffline = offlineNow
            )
            activeRoute = created
            lastFixPoint = null
            lastElevationRef = null
            maxAltitude = null
            minAltitude = null
            accumulatedElapsedMillis = 0L
            segmentStartElapsedAt = System.currentTimeMillis()

            _state.update {
                it.copy(
                    status = TrackingStatus.RECORDING,
                    activeRouteId = created.id,
                    routeName = created.name,
                    activityType = activityType,
                    elapsedMillis = 0L,
                    distanceMeters = 0.0,
                    elevationGainMeters = 0.0,
                    isOfflineRecording = offlineNow,
                    livePoints = emptyList(),
                    liveWaypoints = emptyList(),
                    recoveredRouteNotice = null
                )
            }

            startTimer()
            startForegroundLocationService()
        }
    }

    fun pauseTracking() {
        if (_state.value.status != TrackingStatus.RECORDING) return
        accumulatedElapsedMillis = currentElapsedMillis()
        timerJob?.cancel()
        _state.update {
            it.copy(
                status = TrackingStatus.PAUSED,
                elapsedMillis = accumulatedElapsedMillis
            )
        }
        stopForegroundLocationService()
        persistCurrentMetrics()
    }

    fun resumeTracking() {
        if (_state.value.status != TrackingStatus.PAUSED || activeRoute == null) return
        segmentStartElapsedAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                status = TrackingStatus.RECORDING,
                recoveredRouteNotice = null
            )
        }
        startTimer()
        startForegroundLocationService()
    }

    fun finishAndSaveRoute(
        finalName: String? = null,
        notes: String? = null,
        onSaved: (Long?) -> Unit = {}
    ) {
        val currentRoute = activeRoute
        val finalElapsed = if (_state.value.status == TrackingStatus.RECORDING) {
            currentElapsedMillis()
        } else {
            accumulatedElapsedMillis
        }

        timerJob?.cancel()
        stopForegroundLocationService()

        scope.launch {
            val savedId = if (currentRoute != null) {
                repository.finalizeRoute(
                    routeId = currentRoute.id,
                    customName = finalName,
                    notes = notes,
                    durationMillis = finalElapsed
                )?.id
            } else {
                null
            }

            activeRoute = null
            lastFixPoint = null
            lastElevationRef = null
            maxAltitude = null
            minAltitude = null
            accumulatedElapsedMillis = 0L

            _state.update {
                it.copy(
                    status = TrackingStatus.IDLE,
                    activeRouteId = null,
                    routeName = "",
                    elapsedMillis = 0L,
                    distanceMeters = 0.0,
                    elevationGainMeters = 0.0,
                    livePoints = emptyList(),
                    liveWaypoints = emptyList(),
                    recoveredRouteNotice = null
                )
            }
            launch(Dispatchers.Main) {
                onSaved(savedId)
            }
        }
    }

    fun discardActiveRoute() {
        val currentRoute = activeRoute
        timerJob?.cancel()
        stopForegroundLocationService()
        scope.launch {
            if (currentRoute != null) {
                repository.deleteRoute(currentRoute.id)
            }
            activeRoute = null
            lastFixPoint = null
            lastElevationRef = null
            accumulatedElapsedMillis = 0L
            _state.update {
                it.copy(
                    status = TrackingStatus.IDLE,
                    activeRouteId = null,
                    routeName = "",
                    elapsedMillis = 0L,
                    distanceMeters = 0.0,
                    elevationGainMeters = 0.0,
                    livePoints = emptyList(),
                    liveWaypoints = emptyList(),
                    recoveredRouteNotice = null
                )
            }
        }
    }

    fun dropWaypoint(label: String, category: WaypointCategory, note: String) {
        val routeId = activeRoute?.id ?: return
        val lat = _state.value.currentLatitude ?: _state.value.livePoints.lastOrNull()?.latitude ?: return
        val lon = _state.value.currentLongitude ?: _state.value.livePoints.lastOrNull()?.longitude ?: return
        val alt = _state.value.currentAltitudeMeters ?: _state.value.livePoints.lastOrNull()?.altitudeMeters

        scope.launch {
            val savedWp = repository.addWaypoint(
                routeId = routeId,
                latitude = lat,
                longitude = lon,
                altitudeMeters = alt,
                label = label,
                category = category,
                note = note
            )
            _state.update { current ->
                current.copy(liveWaypoints = current.liveWaypoints + savedWp)
            }
        }
    }

    fun onPassiveLocationUpdate(location: Location) {
        _state.update {
            it.copy(
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                currentAltitudeMeters = if (location.hasAltitude()) location.altitude else it.currentAltitudeMeters,
                currentAccuracyMeters = if (location.hasAccuracy()) location.accuracy else it.currentAccuracyMeters,
                currentSpeedMps = if (location.hasSpeed()) location.speed else it.currentSpeedMps,
                isGpsLocked = !location.hasAccuracy() || location.accuracy <= 35f
            )
        }
    }

    fun onTrackingLocationFix(location: Location) {
        onPassiveLocationUpdate(location)
        if (_state.value.status != TrackingStatus.RECORDING) return
        val route = activeRoute ?: return

        // Reject wildly inaccurate fixes when we already have points
        if (location.hasAccuracy() && location.accuracy > 55f && _state.value.livePoints.isNotEmpty()) {
            return
        }

        val prev = lastFixPoint
        val segDistance = if (prev != null) {
            GeoMetrics.haversineMeters(prev.latitude, prev.longitude, location.latitude, location.longitude)
        } else {
            0.0
        }

        // De-duplicate stationary GPS jitter (< 2.5m) unless it's the first fix
        if (prev != null && segDistance < 2.5) {
            return
        }

        val alt = if (location.hasAltitude()) location.altitude else null
        var addedGain = 0.0
        if (alt != null && alt > -500.0 && alt < 10_000.0) {
            if (maxAltitude == null || alt > maxAltitude!!) maxAltitude = alt
            if (minAltitude == null || alt < minAltitude!!) minAltitude = alt
            val ref = lastElevationRef
            if (ref == null) {
                lastElevationRef = alt
            } else {
                val delta = alt - ref
                if (delta >= 2.5) {
                    addedGain = delta
                    lastElevationRef = alt
                } else if (delta <= -2.5) {
                    lastElevationRef = alt
                }
            }
        }

        scope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            val offlineFix = prefs.offlineModeEnabled || !connectivityMonitor.isNetworkAvailable.value

            val savedPoint = repository.appendTrackPoint(
                routeId = route.id,
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = alt,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                bearingDegrees = if (location.hasBearing()) location.bearing else 0f,
                isOfflineFix = offlineFix
            )
            lastFixPoint = savedPoint

            val newDistance = _state.value.distanceMeters + segDistance
            val newGain = _state.value.elevationGainMeters + addedGain
            val updatedPoints = _state.value.livePoints + savedPoint
            val elapsed = currentElapsedMillis()

            activeRoute = repository.updateLiveRouteMetrics(
                route = route,
                distanceMeters = newDistance,
                durationMillis = elapsed,
                elevationGainMeters = newGain,
                maxAltitudeMeters = maxAltitude,
                minAltitudeMeters = minAltitude,
                pointCount = updatedPoints.size,
                recordedOffline = offlineFix
            )

            _state.update {
                it.copy(
                    distanceMeters = newDistance,
                    elevationGainMeters = newGain,
                    elapsedMillis = elapsed,
                    isOfflineRecording = it.isOfflineRecording || offlineFix,
                    livePoints = updatedPoints
                )
            }
        }
    }

    private fun persistCurrentMetrics() {
        val route = activeRoute ?: return
        val s = _state.value
        scope.launch {
            activeRoute = repository.updateLiveRouteMetrics(
                route = route,
                distanceMeters = s.distanceMeters,
                durationMillis = accumulatedElapsedMillis,
                elevationGainMeters = s.elevationGainMeters,
                maxAltitudeMeters = maxAltitude,
                minAltitudeMeters = minAltitude,
                pointCount = s.livePoints.size,
                recordedOffline = s.isOfflineRecording
            )
        }
    }

    private fun currentElapsedMillis(): Long {
        return accumulatedElapsedMillis + (System.currentTimeMillis() - segmentStartElapsedAt).coerceAtLeast(0L)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val elapsed = currentElapsedMillis()
                _state.update { it.copy(elapsedMillis = elapsed) }
                delay(1000L)
            }
        }
    }

    private fun startForegroundLocationService() {
        val intent = Intent(appContext, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
            // Fall back gracefully if OS restricts background start
        }
    }

    private fun stopForegroundLocationService() {
        val intent = Intent(appContext, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        try {
            appContext.startService(intent)
        } catch (_: Exception) {
            // Service already stopped
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TrackingSessionManager? = null

        fun getInstance(context: Context): TrackingSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrackingSessionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
