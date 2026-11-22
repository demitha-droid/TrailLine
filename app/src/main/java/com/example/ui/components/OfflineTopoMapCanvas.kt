package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.TrackPointEntity
import com.example.data.local.WaypointEntity
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.ElevationAmber
import com.example.ui.theme.SignalMint
import com.example.ui.theme.TrailBlazeOrange
import com.example.util.GeoMetrics
import java.util.Locale
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

private data class ProjectedCoord(val x: Double, val y: Double)

private fun mercatorProject(lat: Double, lon: Double): ProjectedCoord {
    val clampedLat = lat.coerceIn(-85.0, 85.0)
    val rad = Math.toRadians(clampedLat)
    val x = Math.toRadians(lon)
    val y = -ln(tan(Math.PI / 4.0 + rad / 2.0))
    return ProjectedCoord(x, y)
}

@Composable
fun OfflineTopoMapCanvas(
    points: List<TrackPointEntity>,
    waypoints: List<WaypointEntity>,
    currentLatitude: Double?,
    currentLongitude: Double?,
    currentAccuracyMeters: Float?,
    useMetric: Boolean,
    interactive: Boolean = true,
    routeColor: Color = TrailBlazeOrange,
    modifier: Modifier = Modifier
) {
    var userZoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val pulseTransition = rememberInfiniteTransition(label = "gps_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    // Determine coordinate bounds from points, waypoints, and current location
    val allCoords = remember(points, waypoints, currentLatitude, currentLongitude) {
        buildList {
            points.forEach { add(it.latitude to it.longitude) }
            waypoints.forEach { add(it.latitude to it.longitude) }
            if (currentLatitude != null && currentLongitude != null) {
                add(currentLatitude to currentLongitude)
            }
        }
    }

    val spanMetersEstimate = remember(allCoords, userZoom) {
        if (allCoords.size < 2) {
            (400.0 / userZoom.coerceAtLeast(0.4f))
        } else {
            val minLat = allCoords.minOf { it.first }
            val maxLat = allCoords.maxOf { it.first }
            val minLon = allCoords.minOf { it.second }
            val maxLon = allCoords.maxOf { it.second }
            val raw = GeoMetrics.haversineMeters(minLat, minLon, maxLat, maxLon).coerceAtLeast(80.0)
            (raw / userZoom.coerceAtLeast(0.4f)) * 0.35
        }
    }

    val centerLat = allCoords.lastOrNull()?.first
    val centerLon = allCoords.lastOrNull()?.second

    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF142720), Color(0xFF0A1410))
                )
            )
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            userZoom = (userZoom * zoom).coerceIn(0.45f, 8f)
                            panOffset += pan
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val gridStep = 64.dp.toPx()
            val gridColor = Color(0xFF1F3B31)
            val contourColor = Color(0xFF1A352B)

            // 1. Topographic subtle contour arcs
            val center = Offset(w * 0.52f + panOffset.x * 0.15f, h * 0.48f + panOffset.y * 0.15f)
            for (ring in 1..7) {
                val rx = ring * min(w, h) * 0.13f * userZoom.coerceIn(0.7f, 2.2f)
                val ry = rx * 0.68f
                val path = Path()
                val steps = 48
                for (s in 0..steps) {
                    val angle = (s.toFloat() / steps) * 2.0 * Math.PI
                    val wobble = 1.0 + 0.08 * kotlin.math.sin(angle * 3.0 + ring) +
                        0.05 * cos(angle * 5.0 - ring * 0.7)
                    val px = center.x + (rx * wobble * cos(angle)).toFloat()
                    val py = center.y + (ry * wobble * kotlin.math.sin(angle)).toFloat()
                    if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(
                    path = path,
                    color = contourColor.copy(alpha = 0.65f),
                    style = Stroke(width = if (ring % 3 == 0) 1.6f else 0.9f)
                )
            }

            // 2. Cartographic coordinate grid
            val offsetX = panOffset.x % gridStep
            val offsetY = panOffset.y % gridStep
            var gx = offsetX
            while (gx < w) {
                drawLine(
                    color = gridColor.copy(alpha = 0.45f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
            var gy = offsetY
            while (gy < h) {
                drawLine(
                    color = gridColor.copy(alpha = 0.45f),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1f
                )
                gy += gridStep
            }

            if (allCoords.isEmpty()) {
                // Draw subtle crosshair radar waiting for GPS fix
                val cx = w / 2f
                val cy = h / 2f
                drawCircle(
                    color = CompassCyan.copy(alpha = 0.22f),
                    radius = 48.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = CompassCyan.copy(alpha = 0.12f),
                    radius = 92.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2f)
                )
                drawCircle(
                    color = CompassCyan.copy(alpha = pulseAlpha),
                    radius = 24.dp.toPx() * pulseScale,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = CompassCyan,
                    radius = 6.dp.toPx(),
                    center = Offset(cx, cy)
                )
                return@Canvas
            }

            // 3. Project all coordinates into viewport
            val projectedAll = allCoords.map { mercatorProject(it.first, it.second) }
            val minX = projectedAll.minOf { it.x }
            val maxX = projectedAll.maxOf { it.x }
            val minY = projectedAll.minOf { it.y }
            val maxY = projectedAll.maxOf { it.y }

            val midX = (minX + maxX) / 2.0
            val midY = (minY + maxY) / 2.0
            // Minimum span of ~0.00018 rad (~1.1 km) when stationary or short track
            val spanX = max(maxX - minX, 0.00008)
            val spanY = max(maxY - minY, 0.00008)

            val pad = 56.dp.toPx()
            val availW = (w - pad * 2).coerceAtLeast(60f)
            val availH = (h - pad * 2).coerceAtLeast(60f)
            val baseScale = min(availW / spanX, availH / spanY) * userZoom

            fun toScreen(lat: Double, lon: Double): Offset {
                val p = mercatorProject(lat, lon)
                val sx = w / 2f + ((p.x - midX) * baseScale).toFloat() + panOffset.x
                val sy = h / 2f + ((p.y - midY) * baseScale).toFloat() + panOffset.y
                return Offset(sx, sy)
            }

            // 4. Draw route polyline with luminous halo
            if (points.size >= 2) {
                val routePath = Path()
                points.forEachIndexed { index, pt ->
                    val off = toScreen(pt.latitude, pt.longitude)
                    if (index == 0) routePath.moveTo(off.x, off.y) else routePath.lineTo(off.x, off.y)
                }

                // Outer trail glow
                drawPath(
                    path = routePath,
                    color = routeColor.copy(alpha = 0.25f),
                    style = Stroke(
                        width = 12.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // Core trail ribbon
                drawPath(
                    path = routePath,
                    color = routeColor,
                    style = Stroke(
                        width = 4.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 5. Start & Finish pins
            if (points.isNotEmpty()) {
                val startOff = toScreen(points.first().latitude, points.first().longitude)
                drawCircle(color = Color(0xFF0A1410), radius = 8.dp.toPx(), center = startOff)
                drawCircle(color = SignalMint, radius = 5.5.dp.toPx(), center = startOff)

                if (points.size > 1) {
                    val endOff = toScreen(points.last().latitude, points.last().longitude)
                    drawCircle(color = Color(0xFF0A1410), radius = 8.dp.toPx(), center = endOff)
                    drawCircle(color = routeColor, radius = 5.5.dp.toPx(), center = endOff)
                }
            }

            // 6. Waypoint pins
            waypoints.forEach { wp ->
                val wpOff = toScreen(wp.latitude, wp.longitude)
                drawCircle(color = Color(0xFF0A1410), radius = 9.dp.toPx(), center = wpOff)
                drawCircle(color = ElevationAmber, radius = 6.dp.toPx(), center = wpOff)
                drawCircle(color = Color(0xFF0A1410), radius = 2.2.dp.toPx(), center = wpOff)
            }

            // 7. Live GPS fix beacon
            if (currentLatitude != null && currentLongitude != null) {
                val curOff = toScreen(currentLatitude, currentLongitude)
                drawCircle(
                    color = CompassCyan.copy(alpha = pulseAlpha),
                    radius = 18.dp.toPx() * pulseScale,
                    center = curOff
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.5.dp.toPx(),
                    center = curOff
                )
                drawCircle(
                    color = CompassCyan,
                    radius = 5.dp.toPx(),
                    center = curOff
                )
            }
        }

        // Compass badge top-left
        Surface(
            color = Color(0xCC0D1A15),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = "True North Compass",
                    tint = CompassCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (centerLat != null && centerLon != null) {
                        GeoMetrics.formatCoordinate(centerLat, centerLon)
                    } else {
                        "TOPO RADAR • WAITING FOR GPS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE2F1EB)
                )
            }
        }

        // Scale bar & accuracy bottom-left
        Surface(
            color = Color(0xCC0D1A15),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = "Scale ~ ${GeoMetrics.formatDistance(spanMetersEstimate, useMetric)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA7C4B8)
                )
                if (currentAccuracyMeters != null) {
                    Text(
                        text = String.format(Locale.US, "±%.0fm GPS fix", currentAccuracyMeters),
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalMint
                    )
                }
            }
        }

        // Reset zoom/pan button
        if (interactive && (userZoom != 1f || panOffset != Offset.Zero)) {
            FilledTonalIconButton(
                onClick = {
                    userZoom = 1f
                    panOffset = Offset.Zero
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xDD142620),
                    contentColor = Color(0xFFE7F0EC)
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .testTag("topo_recenter_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "Recenter topographic map"
                )
            }
        }
    }
}

/**
 * Compact vector route silhouette preview rendered inside each saved route card.
 */
@Composable
fun RouteMiniMapPreview(
    points: List<TrackPointEntity>,
    routeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF10201A), Color(0xFF193028))
                ),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Subtle topo grid lines
            val gridColor = Color(0xFF27453A).copy(alpha = 0.55f)
            drawLine(gridColor, Offset(w * 0.33f, 0f), Offset(w * 0.33f, h), strokeWidth = 1f)
            drawLine(gridColor, Offset(w * 0.66f, 0f), Offset(w * 0.66f, h), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

            if (points.size < 2) {
                if (points.size == 1) {
                    drawCircle(color = routeColor, radius = 5.dp.toPx(), center = Offset(w / 2f, h / 2f))
                }
                return@Canvas
            }

            val projected = points.map { mercatorProject(it.latitude, it.longitude) }
            val minX = projected.minOf { it.x }
            val maxX = projected.maxOf { it.x }
            val minY = projected.minOf { it.y }
            val maxY = projected.maxOf { it.y }

            val spanX = max(maxX - minX, 0.00001)
            val spanY = max(maxY - minY, 0.00001)
            val pad = 12.dp.toPx()
            val scale = min((w - pad * 2) / spanX, (h - pad * 2) / spanY)
            val midX = (minX + maxX) / 2.0
            val midY = (minY + maxY) / 2.0

            val path = Path()
            projected.forEachIndexed { idx, p ->
                val sx = w / 2f + ((p.x - midX) * scale).toFloat()
                val sy = h / 2f + ((p.y - midY) * scale).toFloat()
                if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }

            drawPath(
                path = path,
                color = routeColor.copy(alpha = 0.25f),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = path,
                color = routeColor,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val start = projected.first()
            val end = projected.last()
            drawCircle(
                color = SignalMint,
                radius = 3.5.dp.toPx(),
                center = Offset(
                    w / 2f + ((start.x - midX) * scale).toFloat(),
                    h / 2f + ((start.y - midY) * scale).toFloat()
                )
            )
            drawCircle(
                color = routeColor,
                radius = 3.5.dp.toPx(),
                center = Offset(
                    w / 2f + ((end.x - midX) * scale).toFloat(),
                    h / 2f + ((end.y - midY) * scale).toFloat()
                )
            )
        }
    }
}
