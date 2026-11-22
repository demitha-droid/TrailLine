package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches TrailLine`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TrailLine", appName)
    }

    @Test
    fun `kml export and import round trip preserves track points and waypoints`() {
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
        assertTrue(kml.contains("<kml"))
        assertTrue(kml.contains("<LineString>"))
        assertTrue(kml.contains("-122.419400,37.774900,100.0"))

        val parsed = RouteExportImportManager.parseRouteFile(kml.byteInputStream(), "Fallback")
        assertEquals("Alpine Ridge Loop", parsed.name)
        assertEquals(3, parsed.points.size)
        assertEquals(1, parsed.waypoints.size)
        assertEquals("Summit Cairn", parsed.waypoints.first().label)
    }

    @Test
    fun `gpx export and import round trip preserves coordinates`() {
        val route = RouteEntity(id = 2L, name = "Canyon Spur", activityType = ActivityType.CYCLE.name)
        val points = listOf(
            TrackPointEntity(routeId = 2L, latitude = 34.0522, longitude = -118.2437, altitudeMeters = 80.0, timestampMillis = 10_000L),
            TrackPointEntity(routeId = 2L, latitude = 34.0622, longitude = -118.2337, altitudeMeters = 110.0, timestampMillis = 20_000L)
        )
        val gpx = RouteExportImportManager.generateGpx(RouteWithDetails(route, points, emptyList()))
        val parsed = RouteExportImportManager.parseRouteFile(gpx.byteInputStream(), "Fallback")
        assertEquals("Canyon Spur", parsed.name)
        assertEquals(2, parsed.points.size)
        val stats = GeoMetrics.computeStats(parsed.points, 10_000L)
        assertTrue(stats.distanceMeters > 1000.0)
        assertEquals(30.0, stats.elevationGainMeters, 0.5)
    }
}
