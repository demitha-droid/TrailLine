package com.example.data.export

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.content.FileProvider
import com.example.data.local.ActivityType
import com.example.data.local.RouteEntity
import com.example.data.local.RouteWithDetails
import com.example.data.local.TrackPointEntity
import com.example.data.local.WaypointCategory
import com.example.data.local.WaypointEntity
import com.example.util.GeoMetrics
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ImportedRouteData(
    val name: String,
    val notes: String,
    val sourceFormat: String,
    val points: List<TrackPointEntity>,
    val waypoints: List<WaypointEntity>
)

object RouteExportImportManager {

    private val isoUtcFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /**
     * Generates a Google My Maps / Google Earth compatible KML 2.2 document.
     * Coordinates follow KML spec order: longitude,latitude,altitude.
     */
    fun generateKml(details: RouteWithDetails): String {
        val route = details.route
        val points = details.points.sortedBy { it.timestampMillis }
        val waypoints = details.waypoints.sortedBy { it.timestampMillis }
        val kmlColor = hexRgbToKmlAbgr(route.colorHex)

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${escapeXml(route.name)}</name>")
            appendLine(
                "    <description>${
                    escapeXml(
                        buildString {
                            append("Tracked with TrailLine")
                            append(" | Activity: ${ActivityType.fromName(route.activityType).label}")
                            append(" | Distance: ${GeoMetrics.formatDistance(route.distanceMeters, true)}")
                            append(" | Duration: ${GeoMetrics.formatDuration(route.durationMillis)}")
                            if (route.elevationGainMeters > 0) {
                                append(" | Elev Gain: ${GeoMetrics.formatElevation(route.elevationGainMeters, true)}")
                            }
                            if (route.recordedOffline) {
                                append(" | Offline Recorded")
                            }
                            if (route.notes.isNotBlank()) {
                                append(" | Notes: ${route.notes}")
                            }
                        }
                    )
                }</description>"
            )
            appendLine("""    <Style id="trailLineStyle">""")
            appendLine("      <LineStyle>")
            appendLine("        <color>$kmlColor</color>")
            appendLine("        <width>4</width>")
            appendLine("      </LineStyle>")
            appendLine("    </Style>")
            appendLine("""    <Style id="startPinStyle">""")
            appendLine("      <IconStyle>")
            appendLine("        <color>ff9de52e</color>")
            appendLine("        <scale>1.1</scale>")
            appendLine("      </IconStyle>")
            appendLine("    </Style>")
            appendLine("""    <Style id="endPinStyle">""")
            appendLine("      <IconStyle>")
            appendLine("        <color>ff2c6bff</color>")
            appendLine("        <scale>1.1</scale>")
            appendLine("      </IconStyle>")
            appendLine("    </Style>")
            appendLine("""    <Style id="waypointPinStyle">""")
            appendLine("      <IconStyle>")
            appendLine("        <color>ff47b5ff</color>")
            appendLine("        <scale>1.0</scale>")
            appendLine("      </IconStyle>")
            appendLine("    </Style>")

            if (points.isNotEmpty()) {
                appendLine("    <Placemark>")
                appendLine("      <name>${escapeXml(route.name)} Path</name>")
                appendLine("      <styleUrl>#trailLineStyle</styleUrl>")
                appendLine("      <LineString>")
                appendLine("        <tessellate>1</tessellate>")
                appendLine("        <altitudeMode>clampToGround</altitudeMode>")
                appendLine("        <coordinates>")
                points.forEach { pt ->
                    val alt = pt.altitudeMeters ?: 0.0
                    appendLine(
                        String.format(
                            Locale.US,
                            "          %.6f,%.6f,%.1f",
                            pt.longitude,
                            pt.latitude,
                            alt
                        )
                    )
                }
                appendLine("        </coordinates>")
                appendLine("      </LineString>")
                appendLine("    </Placemark>")

                val start = points.first()
                appendLine("    <Placemark>")
                appendLine("      <name>Start: ${escapeXml(route.name)}</name>")
                appendLine("      <styleUrl>#startPinStyle</styleUrl>")
                appendLine("      <Point>")
                appendLine(
                    String.format(
                        Locale.US,
                        "        <coordinates>%.6f,%.6f,%.1f</coordinates>",
                        start.longitude,
                        start.latitude,
                        start.altitudeMeters ?: 0.0
                    )
                )
                appendLine("      </Point>")
                appendLine("    </Placemark>")

                if (points.size > 1) {
                    val end = points.last()
                    appendLine("    <Placemark>")
                    appendLine("      <name>Finish: ${escapeXml(route.name)}</name>")
                    appendLine("      <styleUrl>#endPinStyle</styleUrl>")
                    appendLine("      <Point>")
                    appendLine(
                        String.format(
                            Locale.US,
                            "        <coordinates>%.6f,%.6f,%.1f</coordinates>",
                            end.longitude,
                            end.latitude,
                            end.altitudeMeters ?: 0.0
                        )
                    )
                    appendLine("      </Point>")
                    appendLine("    </Placemark>")
                }
            }

            waypoints.forEach { wp ->
                appendLine("    <Placemark>")
                appendLine("      <name>${escapeXml(wp.label)}</name>")
                val desc = buildString {
                    append(WaypointCategory.fromName(wp.category).label)
                    if (wp.note.isNotBlank()) append(" — ${wp.note}")
                }
                appendLine("      <description>${escapeXml(desc)}</description>")
                appendLine("      <styleUrl>#waypointPinStyle</styleUrl>")
                appendLine("      <Point>")
                appendLine(
                    String.format(
                        Locale.US,
                        "        <coordinates>%.6f,%.6f,%.1f</coordinates>",
                        wp.longitude,
                        wp.latitude,
                        wp.altitudeMeters ?: 0.0
                    )
                )
                appendLine("      </Point>")
                appendLine("    </Placemark>")
            }

            appendLine("  </Document>")
            appendLine("</kml>")
        }
    }

    /**
     * Generates a standards-compliant GPX 1.1 document for interoperability with GPS units and mapping platforms.
     */
    fun generateGpx(details: RouteWithDetails): String {
        val route = details.route
        val points = details.points.sortedBy { it.timestampMillis }
        val waypoints = details.waypoints.sortedBy { it.timestampMillis }
        val utcFmt = isoUtcFormat

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="TrailLine Android" xmlns="http://www.topografix.com/GPX/1/1">"""
            )
            appendLine("  <metadata>")
            appendLine("    <name>${escapeXml(route.name)}</name>")
            if (route.notes.isNotBlank()) {
                appendLine("    <desc>${escapeXml(route.notes)}</desc>")
            }
            appendLine("    <time>${utcFmt.format(Date(route.startedAtMillis))}</time>")
            appendLine("  </metadata>")

            waypoints.forEach { wp ->
                appendLine(
                    String.format(
                        Locale.US,
                        """  <wpt lat="%.6f" lon="%.6f">""",
                        wp.latitude,
                        wp.longitude
                    )
                )
                wp.altitudeMeters?.let {
                    appendLine(String.format(Locale.US, "    <ele>%.1f</ele>", it))
                }
                appendLine("    <time>${utcFmt.format(Date(wp.timestampMillis))}</time>")
                appendLine("    <name>${escapeXml(wp.label)}</name>")
                if (wp.note.isNotBlank()) {
                    appendLine("    <desc>${escapeXml(wp.note)}</desc>")
                }
                appendLine("    <sym>${escapeXml(wp.category)}</sym>")
                appendLine("  </wpt>")
            }

            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(route.name)}</name>")
            appendLine("    <type>${escapeXml(route.activityType)}</type>")
            appendLine("    <trkseg>")
            points.forEach { pt ->
                appendLine(
                    String.format(
                        Locale.US,
                        """      <trkpt lat="%.6f" lon="%.6f">""",
                        pt.latitude,
                        pt.longitude
                    )
                )
                pt.altitudeMeters?.let {
                    appendLine(String.format(Locale.US, "        <ele>%.1f</ele>", it))
                }
                appendLine("        <time>${utcFmt.format(Date(pt.timestampMillis))}</time>")
                appendLine("      </trkpt>")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    /**
     * Writes an export file into cache/exports and returns a FileProvider content URI ready for Intent.ACTION_SEND.
     */
    fun createShareableFileUri(
        context: Context,
        details: RouteWithDetails,
        format: String
    ): Pair<Uri, String> {
        val isKml = format.equals("kml", ignoreCase = true)
        val ext = if (isKml) "kml" else "gpx"
        val mime = if (isKml) "application/vnd.google-earth.kml+xml" else "application/gpx+xml"
        val content = if (isKml) generateKml(details) else generateGpx(details)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = GeoMetrics.sanitizeFilename(details.route.name, ext)
        val outFile = File(exportDir, fileName)
        outFile.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        return uri to mime
    }

    /**
     * Builds a Google Maps web/app URI visualizing the route via start, destination, and up to 8 sampled intermediate waypoints.
     */
    fun buildGoogleMapsDirectionsUri(points: List<TrackPointEntity>, activityType: ActivityType): Uri? {
        if (points.isEmpty()) return null
        val sorted = points.sortedBy { it.timestampMillis }
        if (sorted.size == 1) {
            val pt = sorted.first()
            return Uri.parse(
                String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", pt.latitude, pt.longitude)
            )
        }
        val origin = sorted.first()
        val destination = sorted.last()
        val mode = when (activityType) {
            ActivityType.CYCLE -> "bicycling"
            ActivityType.DRIVE -> "driving"
            else -> "walking"
        }

        val intermediate = if (sorted.size > 2) {
            val maxWaypoints = 8
            val inner = sorted.subList(1, sorted.size - 1)
            val step = (inner.size.toDouble() / maxWaypoints).coerceAtLeast(1.0)
            buildList {
                var idx = 0.0
                while (idx < inner.size && size < maxWaypoints) {
                    val p = inner[idx.toInt()]
                    add(String.format(Locale.US, "%.6f,%.6f", p.latitude, p.longitude))
                    idx += step
                }
            }
        } else {
            emptyList()
        }

        val url = buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append(String.format(Locale.US, "&origin=%.6f,%.6f", origin.latitude, origin.longitude))
            append(String.format(Locale.US, "&destination=%.6f,%.6f", destination.latitude, destination.longitude))
            if (intermediate.isNotEmpty()) {
                append("&waypoints=")
                append(Uri.encode(intermediate.joinToString("|")))
            }
            append("&travelmode=$mode")
        }
        return Uri.parse(url)
    }

    /**
     * Parses a KML or GPX file from an InputStream into an ImportedRouteData object.
     */
    fun parseRouteFile(inputStream: InputStream, fallbackName: String): ImportedRouteData {
        val raw = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val trimmed = raw.trimStart()
        return if (trimmed.contains("<kml", ignoreCase = true)) {
            parseKml(raw, fallbackName)
        } else if (trimmed.contains("<gpx", ignoreCase = true)) {
            parseGpx(raw, fallbackName)
        } else {
            throw IllegalArgumentException("Unsupported file format. Please choose a valid .kml or .gpx route file.")
        }
    }

    private fun parseKml(xmlText: String, fallbackName: String): ImportedRouteData {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xmlText.reader())
        }

        var docName: String? = null
        var docDesc = ""
        var inDocument = false
        var inPlacemark = false
        var inLineString = false
        var inPoint = false
        var currentPlacemarkName = ""
        var currentPlacemarkDesc = ""
        val points = mutableListOf<TrackPointEntity>()
        val waypoints = mutableListOf<WaypointEntity>()
        val baseTime = System.currentTimeMillis()
        var ptIndex = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.US)) {
                        "document" -> inDocument = true
                        "placemark" -> {
                            inPlacemark = true
                            currentPlacemarkName = ""
                            currentPlacemarkDesc = ""
                        }
                        "linestring" -> inLineString = true
                        "point" -> inPoint = true
                        "name" -> {
                            val text = parser.nextText().trim()
                            if (inPlacemark) {
                                currentPlacemarkName = text
                            } else if (inDocument && docName == null && text.isNotBlank()) {
                                docName = text
                            }
                        }
                        "description" -> {
                            val text = parser.nextText().trim()
                            if (inPlacemark) {
                                currentPlacemarkDesc = text
                            } else if (inDocument && docDesc.isBlank()) {
                                docDesc = text
                            }
                        }
                        "coordinates" -> {
                            val coordBlock = parser.nextText().trim()
                            val tokens = coordBlock.split(Regex("\\s+")).filter { it.isNotBlank() }
                            if (inLineString) {
                                tokens.forEach { token ->
                                    val parts = token.split(",")
                                    if (parts.size >= 2) {
                                        val lon = parts[0].toDoubleOrNull()
                                        val lat = parts[1].toDoubleOrNull()
                                        val alt = parts.getOrNull(2)?.toDoubleOrNull()
                                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                                            points.add(
                                                TrackPointEntity(
                                                    routeId = 0L,
                                                    latitude = lat,
                                                    longitude = lon,
                                                    altitudeMeters = alt,
                                                    timestampMillis = baseTime + (ptIndex++ * 5_000L),
                                                    isOfflineFix = false
                                                )
                                            )
                                        }
                                    }
                                }
                            } else if (inPoint && tokens.isNotEmpty()) {
                                val parts = tokens.first().split(",")
                                if (parts.size >= 2) {
                                    val lon = parts[0].toDoubleOrNull()
                                    val lat = parts[1].toDoubleOrNull()
                                    val alt = parts.getOrNull(2)?.toDoubleOrNull()
                                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                                        val wpName = currentPlacemarkName.ifBlank { "Waypoint ${waypoints.size + 1}" }
                                        if (!wpName.startsWith("Start:") && !wpName.startsWith("Finish:")) {
                                            waypoints.add(
                                                WaypointEntity(
                                                    routeId = 0L,
                                                    latitude = lat,
                                                    longitude = lon,
                                                    altitudeMeters = alt,
                                                    label = wpName,
                                                    note = currentPlacemarkDesc,
                                                    timestampMillis = baseTime + waypoints.size * 1000L
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase(Locale.US)) {
                        "placemark" -> inPlacemark = false
                        "linestring" -> inLineString = false
                        "point" -> inPoint = false
                    }
                }
            }
            eventType = parser.next()
        }

        if (points.isEmpty() && waypoints.isNotEmpty()) {
            // Convert waypoint-only KML into a connectable path
            waypoints.forEachIndexed { idx, wp ->
                points.add(
                    TrackPointEntity(
                        routeId = 0L,
                        latitude = wp.latitude,
                        longitude = wp.longitude,
                        altitudeMeters = wp.altitudeMeters,
                        timestampMillis = baseTime + idx * 5_000L
                    )
                )
            }
        }

        if (points.isEmpty()) {
            throw IllegalArgumentException("No valid track coordinates were found in this KML file.")
        }

        return ImportedRouteData(
            name = docName?.ifBlank { fallbackName } ?: fallbackName,
            notes = docDesc,
            sourceFormat = "KML_IMPORT",
            points = points,
            waypoints = waypoints
        )
    }

    private fun parseGpx(xmlText: String, fallbackName: String): ImportedRouteData {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xmlText.reader())
        }

        var trackName: String? = null
        var trackDesc = ""
        val points = mutableListOf<TrackPointEntity>()
        val waypoints = mutableListOf<WaypointEntity>()

        var inTrkPt = false
        var inWpt = false
        var curLat = 0.0
        var curLon = 0.0
        var curEle: Double? = null
        var curTime: Long? = null
        var curName = ""
        var curDesc = ""
        val baseTime = System.currentTimeMillis()
        var idx = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.US)) {
                        "trkpt", "rtept" -> {
                            curLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                            curLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                            curEle = null
                            curTime = null
                            inTrkPt = !curLat.isNaN() && !curLon.isNaN()
                        }
                        "wpt" -> {
                            curLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                            curLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                            curEle = null
                            curTime = null
                            curName = ""
                            curDesc = ""
                            inWpt = !curLat.isNaN() && !curLon.isNaN()
                        }
                        "ele" -> {
                            curEle = parser.nextText().trim().toDoubleOrNull()
                        }
                        "time" -> {
                            val rawTime = parser.nextText().trim()
                            curTime = parseIsoTime(rawTime)
                        }
                        "name" -> {
                            val text = parser.nextText().trim()
                            if (inWpt) {
                                curName = text
                            } else if (trackName == null && text.isNotBlank()) {
                                trackName = text
                            }
                        }
                        "desc" -> {
                            val text = parser.nextText().trim()
                            if (inWpt) curDesc = text else if (trackDesc.isBlank()) trackDesc = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase(Locale.US)) {
                        "trkpt", "rtept" -> {
                            if (inTrkPt && curLat in -90.0..90.0 && curLon in -180.0..180.0) {
                                points.add(
                                    TrackPointEntity(
                                        routeId = 0L,
                                        latitude = curLat,
                                        longitude = curLon,
                                        altitudeMeters = curEle,
                                        timestampMillis = curTime ?: (baseTime + (idx++ * 5_000L))
                                    )
                                )
                            }
                            inTrkPt = false
                        }
                        "wpt" -> {
                            if (inWpt && curLat in -90.0..90.0 && curLon in -180.0..180.0) {
                                waypoints.add(
                                    WaypointEntity(
                                        routeId = 0L,
                                        latitude = curLat,
                                        longitude = curLon,
                                        altitudeMeters = curEle,
                                        label = curName.ifBlank { "Waypoint ${waypoints.size + 1}" },
                                        note = curDesc,
                                        timestampMillis = curTime ?: baseTime
                                    )
                                )
                            }
                            inWpt = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (points.isEmpty()) {
            throw IllegalArgumentException("No valid track points were found in this GPX file.")
        }

        return ImportedRouteData(
            name = trackName?.ifBlank { fallbackName } ?: fallbackName,
            notes = trackDesc,
            sourceFormat = "GPX_IMPORT",
            points = points,
            waypoints = waypoints
        )
    }

    private fun parseIsoTime(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return fmt.parse(value)?.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    /**
     * Converts standard #RRGGBB hex to KML's aabbggrr color format (full opacity ff).
     */
    private fun hexRgbToKmlAbgr(hex: String): String {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return "ff2c6bff"
        val rr = clean.substring(0, 2)
        val gg = clean.substring(2, 4)
        val bb = clean.substring(4, 6)
        return "ff$bb$gg$rr".lowercase(Locale.US)
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
