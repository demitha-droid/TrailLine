package com.example.data.local

import kotlinx.coroutines.flow.Flow

interface TrailDao {
    fun observeCompletedRoutesWithDetails(): Flow<List<RouteWithDetails>>

    fun observeRouteWithDetails(routeId: Long): Flow<RouteWithDetails?>

    suspend fun getRouteWithDetails(routeId: Long): RouteWithDetails?

    suspend fun getActiveRecordingRoute(): RouteEntity?

    fun observePointsForRoute(routeId: Long): Flow<List<TrackPointEntity>>

    suspend fun getPointsForRoute(routeId: Long): List<TrackPointEntity>

    fun observeWaypointsForRoute(routeId: Long): Flow<List<WaypointEntity>>

    fun observeSavedRouteCount(): Flow<Int>

    fun observeTotalTrackPointCount(): Flow<Int>

    fun observeOfflineTrackPointCount(): Flow<Int>

    suspend fun insertRoute(route: RouteEntity): Long

    suspend fun updateRoute(route: RouteEntity)

    suspend fun insertTrackPoint(point: TrackPointEntity): Long

    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    suspend fun insertWaypoints(waypoints: List<WaypointEntity>)

    suspend fun deleteWaypointById(waypointId: Long)

    suspend fun deleteRouteById(routeId: Long)
}
