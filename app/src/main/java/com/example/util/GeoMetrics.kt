package com.example.util

import com.example.BuildConfig
import com.example.data.local.TrackPointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMetrics {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun isGoogleMapsKeyConfigured(): Boolean {
        val key = BuildConfig.MAPS_API_KEY.trim()
        return key.isNotEmpty() &&
            !key.equals("YOUR_GOOGLE_MAPS_API_KEY", ignoreCase = true) &&
            !key.startsWith("MY_", ignoreCase = true) &&
            key.length >= 20
    }

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_METERS * c
    }

    data class ComputedRouteStats(
        val distanceMeters: Double,
        val elevationGainMeters: Double,
        val maxAltitudeMeters: Double?,
        val minAltitudeMeters: Double?,
        val avgSpeedMps: Double
    )

    fun computeStats(points: List<TrackPointEntity>, durationMillis: Long): ComputedRouteStats {
        if (points.isEmpty()) {
            return ComputedRouteStats(0.0, 0.0, null, null, 0.0)
        }
        var totalDistance = 0.0
        var elevationGain = 0.0
        var maxAlt: Double? = null
        var minAlt: Double? = null
        var lastValidAlt: Double? = null

        for (i in points.indices) {
            val pt = points[i]
            if (i > 0) {
                val prev = points[i - 1]
                val segDist = haversineMeters(prev.latitude, prev.longitude, pt.latitude, pt.longitude)
                if (segDist in 0.5..5000.0) {
                    totalDistance += segDist
                }
            }
            val alt = pt.altitudeMeters
            if (alt != null && alt > -500.0 && alt < 10_000.0) {
                if (maxAlt == null || alt > maxAlt) maxAlt = alt
                if (minAlt == null || alt < minAlt) minAlt = alt
                if (lastValidAlt != null) {
                    val delta = alt - lastValidAlt
                    // Filter small GPS vertical jitter (< 2.5m)
                    if (delta >= 2.5) {
                        elevationGain += delta
                        lastValidAlt = alt
                    } else if (delta <= -2.5) {
                        lastValidAlt = alt
                    }
                } else {
                    lastValidAlt = alt
                }
            }
        }
        val seconds = (durationMillis / 1000.0).coerceAtLeast(1.0)
        val avgSpeed = if (durationMillis > 0) totalDistance / seconds else 0.0
        return ComputedRouteStats(
            distanceMeters = totalDistance,
            elevationGainMeters = elevationGain,
            maxAltitudeMeters = maxAlt,
            minAltitudeMeters = minAlt,
            avgSpeedMps = avgSpeed
        )
    }

    fun formatDistance(meters: Double, useMetric: Boolean): String {
        return if (useMetric) {
            if (meters < 1000.0) {
                String.format(Locale.US, "%.0f m", meters)
            } else {
                String.format(Locale.US, "%.2f km", meters / 1000.0)
            }
        } else {
            val miles = meters / 1609.344
            if (miles < 0.1) {
                val feet = meters * 3.28084
                String.format(Locale.US, "%.0f ft", feet)
            } else {
                String.format(Locale.US, "%.2f mi", miles)
            }
        }
    }

    fun formatElevation(meters: Double?, useMetric: Boolean): String {
        if (meters == null) return "—"
        return if (useMetric) {
            String.format(Locale.US, "%.0f m", meters)
        } else {
            String.format(Locale.US, "%.0f ft", meters * 3.28084)
        }
    }

    fun formatSpeed(speedMps: Double, useMetric: Boolean): String {
        return if (useMetric) {
            String.format(Locale.US, "%.1f km/h", speedMps * 3.6)
        } else {
            String.format(Locale.US, "%.1f mph", speedMps * 2.23694)
        }
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatCoordinate(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.5f°%s, %.5f°%s",
            kotlin.math.abs(lat),
            latDir,
            kotlin.math.abs(lon),
            lonDir
        )
    }

    fun formatDateTime(millis: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
        return formatter.format(Date(millis))
    }

    fun sanitizeFilename(name: String, extension: String): String {
        val clean = name
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "trailline_route" }
        return "$clean.$extension"
    }
}
