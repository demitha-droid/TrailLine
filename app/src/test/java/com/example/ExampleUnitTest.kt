package com.example

import com.example.data.export.RouteExportImportManager
import com.example.data.local.ActivityType
import com.example.data.local.RouteEntity
import com.example.data.local.RouteWithDetails
import com.example.data.local.TrackPointEntity
import com.example.data.local.WaypointCategory
import com.example.data.local.WaypointEntity
import com.example.util.GeoMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `kml generator outputs valid Google My Maps LineString and waypoints`() {
        val route = RouteEntity(
            id = 1L,
            name = "Alpine Ridge Loop",
            activityType = ActivityType.HIKE.name,
            distanceMeters = 1420.0,
            durationMillis = 1_200_000L,
            elevationGainMeters = 120.0,
            recordedOffline = true
        )
        val points = listOf(
            TrackPointEntity(routeId = 1L, latitude = 37.7749, longitude = -122.4194, altitudeMeters = 100.0, timestampMillis = 1000L, isOfflineFix = true),
            TrackPointEntity(routeId = 1L, latitude = 37.7799, longitude = -122.4150, altitudeMeters = 160.0, timestampMillis = 2000L, isOfflineFix = true),
            TrackPointEntity(routeId = 1L, latitude = 37.7849, longitude = -122.4100, altitudeMeters = 220.0, timestampMillis = 3000L, isOfflineFix = true)
        )
        val waypoints = listOf(
            WaypointEntity(
                routeId = 1L,
                latitude = 37.7799,
                longitude = -122.4150,
                altitudeMeters = 160.0,
                label = "Summit Cairn",
                category = WaypointCategory.VIEWPOINT.name,
                note = "Clear valley view"
            )
        )

        val kml = RouteExportImportManager.generateKml(RouteWithDetails(route, points, waypoints))
        assertTrue(kml.contains("<kml xmlns=\"http://www.opengis.net/kml/2.2\">"))
        assertTrue(kml.contains("<LineString>"))
        assertTrue(kml.contains("-122.419400,37.774900,100.0"))
        assertTrue(kml.contains("<name>Summit Cairn</name>"))
        assertTrue(kml.contains("Offline Recorded"))
    }

    @Test
    fun `gpx generator and geo metrics compute accurate distance and elevation gain`() {
        val route = RouteEntity(id = 2L, name = "Canyon Spur", activityType = ActivityType.CYCLE.name)
        val points = listOf(
            TrackPointEntity(routeId = 2L, latitude = 34.0522, longitude = -118.2437, altitudeMeters = 80.0, timestampMillis = 10_000L),
            TrackPointEntity(routeId = 2L, latitude = 34.0622, longitude = -118.2337, altitudeMeters = 110.0, timestampMillis = 20_000L)
        )
        val gpx = RouteExportImportManager.generateGpx(RouteWithDetails(route, points, emptyList()))
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("""<trkpt lat="34.052200" lon="-118.243700">"""))

        val stats = GeoMetrics.computeStats(points, 10_000L)
        assertTrue(stats.distanceMeters > 1000.0)
        assertEquals(30.0, stats.elevationGainMeters, 0.5)
    }
}
