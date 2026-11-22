package com.example.data.repository

import com.example.data.export.ImportedRouteData
import com.example.data.local.ActivityType
import com.example.data.local.RouteEntity
import com.example.data.local.RouteWithDetails
import com.example.data.local.TrackPointEntity
import com.example.data.local.TrailDao
import com.example.data.local.WaypointCategory
import com.example.data.local.WaypointEntity
import com.example.util.GeoMetrics
import kotlinx.coroutines.flow.Flow

class TrailRepository(private val dao: TrailDao) {

    val completedRoutes: Flow<List<RouteWithDetails>> = dao.observeCompletedRoutesWithDetails()
    val savedRouteCount: Flow<Int> = dao.observeSavedRouteCount()
    val totalTrackPointCount: Flow<Int> = dao.observeTotalTrackPointCount()
    val offlineTrackPointCount: Flow<Int> = dao.observeOfflineTrackPointCount()

    fun observeRoute(routeId: Long): Flow<RouteWithDetails?> = dao.observeRouteWithDetails(routeId)

    suspend fun getRoute(routeId: Long): RouteWithDetails? = dao.getRouteWithDetails(routeId)

    suspend fun getActiveRecordingRoute(): RouteEntity? = dao.getActiveRecordingRoute()

    fun observePointsForRoute(routeId: Long): Flow<List<TrackPointEntity>> =
        dao.observePointsForRoute(routeId)

    fun observeWaypointsForRoute(routeId: Long): Flow<List<WaypointEntity>> =
        dao.observeWaypointsForRoute(routeId)

    suspend fun getPointsForRoute(routeId: Long): List<TrackPointEntity> =
        dao.getPointsForRoute(routeId)

    suspend fun startNewRoute(
        name: String,
        activityType: ActivityType,
        startedOffline: Boolean
    ): RouteEntity {
        val route = RouteEntity(
            name = name,
            activityType = activityType.name,
            startedAtMillis = System.currentTimeMillis(),
            isRecording = true,
            recordedOffline = startedOffline,
            colorHex = activityType.defaultColorHex,
            sourceFormat = "LIVE_GPS"
        )
        val id = dao.insertRoute(route)
        return route.copy(id = id)
    }

    suspend fun appendTrackPoint(
        routeId: Long,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?,
        accuracyMeters: Float,
        speedMps: Float,
        bearingDegrees: Float,
        isOfflineFix: Boolean
    ): TrackPointEntity {
        val point = TrackPointEntity(
            routeId = routeId,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            accuracyMeters = accuracyMeters,
            speedMps = speedMps,
            bearingDegrees = bearingDegrees,
            timestampMillis = System.currentTimeMillis(),
            isOfflineFix = isOfflineFix
        )
        val id = dao.insertTrackPoint(point)
        return point.copy(id = id)
    }

    suspend fun updateLiveRouteMetrics(
        route: RouteEntity,
        distanceMeters: Double,
        durationMillis: Long,
        elevationGainMeters: Double,
        maxAltitudeMeters: Double?,
        minAltitudeMeters: Double?,
        pointCount: Int,
        recordedOffline: Boolean
    ): RouteEntity {
        val avgSpeed = if (durationMillis > 0) distanceMeters / (durationMillis / 1000.0) else 0.0
        val updated = route.copy(
            distanceMeters = distanceMeters,
            durationMillis = durationMillis,
            elevationGainMeters = elevationGainMeters,
            maxAltitudeMeters = maxAltitudeMeters,
            minAltitudeMeters = minAltitudeMeters,
            avgSpeedMps = avgSpeed,
            pointCount = pointCount,
            recordedOffline = route.recordedOffline || recordedOffline
        )
        dao.updateRoute(updated)
        return updated
    }

    suspend fun finalizeRoute(
        routeId: Long,
        customName: String?,
        notes: String?,
        durationMillis: Long
    ): RouteEntity? {
        val details = dao.getRouteWithDetails(routeId) ?: return null
        val points = details.points.sortedBy { it.timestampMillis }
        val stats = GeoMetrics.computeStats(points, durationMillis)
        val updated = details.route.copy(
            name = customName?.trim().takeIf { !it.isNullOrBlank() } ?: details.route.name,
            notes = notes?.trim() ?: details.route.notes,
            endedAtMillis = System.currentTimeMillis(),
            durationMillis = durationMillis,
            distanceMeters = stats.distanceMeters,
            elevationGainMeters = stats.elevationGainMeters,
            maxAltitudeMeters = stats.maxAltitudeMeters,
            minAltitudeMeters = stats.minAltitudeMeters,
            avgSpeedMps = stats.avgSpeedMps,
            pointCount = points.size,
            isRecording = false
        )
        dao.updateRoute(updated)
        return updated
    }

    suspend fun addWaypoint(
        routeId: Long,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?,
        label: String,
        category: WaypointCategory,
        note: String
    ): WaypointEntity {
        val wp = WaypointEntity(
            routeId = routeId,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            label = label.trim().ifBlank { category.label },
            category = category.name,
            note = note.trim(),
            timestampMillis = System.currentTimeMillis()
        )
        val id = dao.insertWaypoint(wp)
        return wp.copy(id = id)
    }

    suspend fun updateRouteMetadata(
        routeId: Long,
        name: String,
        notes: String,
        activityType: ActivityType
    ) {
        val existing = dao.getRouteWithDetails(routeId)?.route ?: return
        dao.updateRoute(
            existing.copy(
                name = name.trim().ifBlank { existing.name },
                notes = notes.trim(),
                activityType = activityType.name,
                colorHex = activityType.defaultColorHex
            )
        )
    }

    suspend fun saveImportedRoute(imported: ImportedRouteData): Long {
        val points = imported.points.sortedBy { it.timestampMillis }
        val start = points.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val end = points.lastOrNull()?.timestampMillis ?: start
        val duration = (end - start).coerceAtLeast(0L)
        val stats = GeoMetrics.computeStats(points, duration)

        val routeId = dao.insertRoute(
            RouteEntity(
                name = imported.name,
                activityType = ActivityType.HIKE.name,
                startedAtMillis = start,
                endedAtMillis = end,
                distanceMeters = stats.distanceMeters,
                durationMillis = duration,
                elevationGainMeters = stats.elevationGainMeters,
                maxAltitudeMeters = stats.maxAltitudeMeters,
                minAltitudeMeters = stats.minAltitudeMeters,
                avgSpeedMps = stats.avgSpeedMps,
                isRecording = false,
                recordedOffline = false,
                pointCount = points.size,
                notes = imported.notes,
                colorHex = ActivityType.HIKE.defaultColorHex,
                sourceFormat = imported.sourceFormat
            )
        )

        if (points.isNotEmpty()) {
            dao.insertTrackPoints(points.map { it.copy(id = 0L, routeId = routeId) })
        }
        if (imported.waypoints.isNotEmpty()) {
            dao.insertWaypoints(imported.waypoints.map { it.copy(id = 0L, routeId = routeId) })
        }
        return routeId
    }

    suspend fun deleteRoute(routeId: Long) {
        dao.deleteRouteById(routeId)
    }

    suspend fun deleteWaypoint(waypointId: Long) {
        dao.deleteWaypointById(waypointId)
    }
}
