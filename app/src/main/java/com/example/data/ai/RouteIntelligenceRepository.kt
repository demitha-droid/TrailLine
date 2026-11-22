package com.example.data.ai

import com.example.data.local.ActivityType
import com.example.data.local.RouteWithDetails
import com.example.util.GeoMetrics
import kotlinx.coroutines.delay

data class RouteIntelligenceReport(
    val isPlaceholder: Boolean,
    val title: String,
    val summary: String,
    val terrainAnalysis: String,
    val pacingAndEffort: String,
    val offlineAndMapsTips: String,
    val answerToQuestion: String? = null
)

/**
 * Single AI abstraction boundary for TrailLine Route Intelligence.
 * Currently backed by PlaceholderRouteIntelligenceRepository because Firebase setup was declined.
 * When Firebase AI Logic is enabled, swap in a Firebase.ai implementation using gemini-4-carbon
 * with ThinkingLevel.HIGH and no maxOutputTokens cap.
 */
interface RouteIntelligenceRepository {
    suspend fun analyzeRoute(
        details: RouteWithDetails,
        useMetric: Boolean,
        customQuestion: String? = null
    ): RouteIntelligenceReport
}

class PlaceholderRouteIntelligenceRepository : RouteIntelligenceRepository {

    override suspend fun analyzeRoute(
        details: RouteWithDetails,
        useMetric: Boolean,
        customQuestion: String?
    ): RouteIntelligenceReport {
        delay(450L)
        val route = details.route
        val activity = ActivityType.fromName(route.activityType)
        val distLabel = GeoMetrics.formatDistance(route.distanceMeters, useMetric)
        val durLabel = GeoMetrics.formatDuration(route.durationMillis)
        val gainLabel = GeoMetrics.formatElevation(route.elevationGainMeters, useMetric)
        val speedLabel = GeoMetrics.formatSpeed(route.avgSpeedMps, useMetric)
        val offlineFixCount = details.points.count { it.isOfflineFix }

        val gradeHint = when {
            route.elevationGainMeters >= 450 -> "Steep alpine climb profile"
            route.elevationGainMeters >= 150 -> "Rolling undulating terrain"
            else -> "Low-grade approachable terrain"
        }

        val questionAnswer = customQuestion?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            "[Sample Placeholder Answer] Regarding \"$q\": Based on this ${activity.label.lowercase()} " +
                "($distLabel, $gainLabel climb, ${details.points.size} GPS fixes, ${details.waypoints.size} waypoints), " +
                "plan extra buffer time on uphill stretches, export the KML layer to Google My Maps before sharing, " +
                "and keep Offline Mode enabled when revisiting sections with $offlineFixCount offline-recorded fixes."
        }

        return RouteIntelligenceReport(
            isPlaceholder = true,
            title = "Sample Route Brief — ${route.name}",
            summary = "[Sample Placeholder Analysis] Recorded a $distLabel ${activity.label.lowercase()} " +
                "over $durLabel averaging $speedLabel with ${details.points.size} locally persisted GPS fixes.",
            terrainAnalysis = "$gradeHint ($gainLabel cumulative ascent" +
                "${route.maxAltitudeMeters?.let { ", peaking at ${GeoMetrics.formatElevation(it, useMetric)}" } ?: ""}). " +
                "${details.waypoints.size} pinned waypoint(s) mark key checkpoints along the corridor.",
            pacingAndEffort = "Average moving tempo of $speedLabel. For repeatable pacing on future runs, " +
                "review the elevation profile chart above to spot steep pitch transitions.",
            offlineAndMapsTips = if (route.recordedOffline || offlineFixCount > 0) {
                "$offlineFixCount GPS fix(es) were captured in Offline Mode with zero data loss. " +
                    "Export as KML to overlay this backcountry track directly inside Google My Maps."
            } else {
                "All trackpoints are archived in the local SQLite vault. Export this path as a KML file " +
                    "to import the full route polyline and waypoints into Google My Maps."
            },
            answerToQuestion = questionAnswer
        )
    }
}
