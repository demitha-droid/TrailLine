package com.example.data.local

enum class ActivityType(val label: String, val defaultColorHex: String) {
    HIKE("Hike", "#FF6B2C"),
    RUN("Run", "#2EE59D"),
    CYCLE("Cycle", "#38BDF8"),
    DRIVE("Drive", "#FFB547"),
    BACKCOUNTRY("Backcountry", "#E879F9");

    companion object {
        fun fromName(name: String): ActivityType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HIKE
    }
}

enum class WaypointCategory(val label: String) {
    TRAILHEAD("Trailhead"),
    VIEWPOINT("Viewpoint"),
    WATER("Water"),
    CAMP("Camp"),
    HAZARD("Hazard"),
    CUSTOM("Pin");

    companion object {
        fun fromName(name: String): WaypointCategory =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CUSTOM
    }
}

data class RouteEntity(
    val id: Long = 0L,
    val name: String,
    val activityType: String = ActivityType.HIKE.name,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val endedAtMillis: Long? = null,
    val distanceMeters: Double = 0.0,
    val durationMillis: Long = 0L,
    val elevationGainMeters: Double = 0.0,
    val maxAltitudeMeters: Double? = null,
    val minAltitudeMeters: Double? = null,
    val avgSpeedMps: Double = 0.0,
    val isRecording: Boolean = false,
    val recordedOffline: Boolean = false,
    val pointCount: Int = 0,
    val notes: String = "",
    val colorHex: String = "#FF6B2C",
    val sourceFormat: String = "LIVE_GPS"
)

data class TrackPointEntity(
    val id: Long = 0L,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float = 0f,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isOfflineFix: Boolean = false
)

data class WaypointEntity(
    val id: Long = 0L,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val label: String,
    val category: String = WaypointCategory.CUSTOM.name,
    val note: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

data class RouteWithDetails(
    val route: RouteEntity,
    val points: List<TrackPointEntity>,
    val waypoints: List<WaypointEntity>
)
