package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.local.TrackPointEntity
import com.example.ui.theme.ElevationAmber
import com.example.ui.theme.TrailBlazeOrange
import com.example.util.GeoMetrics

@Composable
fun ElevationProfileChart(
    points: List<TrackPointEntity>,
    useMetric: Boolean,
    modifier: Modifier = Modifier
) {
    val altitudes = remember(points) {
        points.mapNotNull { it.altitudeMeters }.filter { it > -500.0 && it < 10_000.0 }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Elevation Profile",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (altitudes.isNotEmpty()) {
                    Text(
                        text = "${GeoMetrics.formatElevation(altitudes.min(), useMetric)} – ${
                            GeoMetrics.formatElevation(altitudes.max(), useMetric)
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElevationAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (altitudes.size < 2) {
                Text(
                    text = "Not enough altitude samples recorded on this route yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val minAlt = altitudes.min()
                val maxAlt = altitudes.max()
                val altSpan = (maxAlt - minAlt).coerceAtLeast(10.0)
                val gridColor = MaterialTheme.colorScheme.outlineVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val topPad = 10.dp.toPx()
                    val bottomPad = 10.dp.toPx()
                    val usableH = (h - topPad - bottomPad).coerceAtLeast(10f)

                    // Horizontal elevation reference lines
                    for (i in 0..2) {
                        val y = topPad + (usableH * i / 2f)
                        drawLine(
                            color = gridColor.copy(alpha = 0.45f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    val linePath = Path()
                    val fillPath = Path()

                    altitudes.forEachIndexed { idx, alt ->
                        val x = (idx.toFloat() / (altitudes.size - 1).coerceAtLeast(1)) * w
                        val normalized = ((alt - minAlt) / altSpan).toFloat().coerceIn(0f, 1f)
                        val y = topPad + usableH * (1f - normalized)

                        if (idx == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(w, h)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TrailBlazeOrange.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = TrailBlazeOrange,
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}
