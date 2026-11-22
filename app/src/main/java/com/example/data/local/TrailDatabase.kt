package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Local crash-safe SQLite database (WAL mode + foreign keys ON) storing routes,
 * GPS track points, and waypoints on-device without requiring KSP annotation processing.
 */
class TrailDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val daoImpl: TrailDao = SqliteTrailDao()

    init {
        setWriteAheadLoggingEnabled(true)
    }

    fun trailDao(): TrailDao = daoImpl

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                activityType TEXT NOT NULL,
                startedAtMillis INTEGER NOT NULL,
                endedAtMillis INTEGER,
                distanceMeters REAL NOT NULL,
                durationMillis INTEGER NOT NULL,
                elevationGainMeters REAL NOT NULL,
                maxAltitudeMeters REAL,
                minAltitudeMeters REAL,
                avgSpeedMps REAL NOT NULL,
                isRecording INTEGER NOT NULL,
                recordedOffline INTEGER NOT NULL,
                pointCount INTEGER NOT NULL,
                notes TEXT NOT NULL,
                colorHex TEXT NOT NULL,
                sourceFormat TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                routeId INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitudeMeters REAL,
                accuracyMeters REAL NOT NULL,
                speedMps REAL NOT NULL,
                bearingDegrees REAL NOT NULL,
                timestampMillis INTEGER NOT NULL,
                isOfflineFix INTEGER NOT NULL,
                FOREIGN KEY(routeId) REFERENCES routes(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_track_points_route_time
            ON track_points(routeId, timestampMillis)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS waypoints (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                routeId INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitudeMeters REAL,
                label TEXT NOT NULL,
                category TEXT NOT NULL,
                note TEXT NOT NULL,
                timestampMillis INTEGER NOT NULL,
                FOREIGN KEY(routeId) REFERENCES routes(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_waypoints_route
            ON waypoints(routeId)
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema v1 initial release
    }

    private fun notifyDataChanged() {
        changeSignal.tryEmit(Unit)
    }

    private fun <T> observeQuery(loader: () -> T): Flow<T> = changeSignal
        .onStart { emit(Unit) }
        .map { loader() }
        .flowOn(Dispatchers.IO)

    private inner class SqliteTrailDao : TrailDao {

        override fun observeCompletedRoutesWithDetails(): Flow<List<RouteWithDetails>> =
            observeQuery { loadCompletedRoutesWithDetailsSync() }

        override fun observeRouteWithDetails(routeId: Long): Flow<RouteWithDetails?> =
            observeQuery { loadRouteWithDetailsSync(routeId) }

        override suspend fun getRouteWithDetails(routeId: Long): RouteWithDetails? =
            withContext(Dispatchers.IO) { loadRouteWithDetailsSync(routeId) }

        override suspend fun getActiveRecordingRoute(): RouteEntity? =
            withContext(Dispatchers.IO) {
                readableDatabase.rawQuery(
                    "SELECT * FROM routes WHERE isRecording = 1 ORDER BY startedAtMillis DESC LIMIT 1",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.toRouteEntity() else null
                }
            }

        override fun observePointsForRoute(routeId: Long): Flow<List<TrackPointEntity>> =
            observeQuery { loadPointsSync(routeId) }

        override suspend fun getPointsForRoute(routeId: Long): List<TrackPointEntity> =
            withContext(Dispatchers.IO) { loadPointsSync(routeId) }

        override fun observeWaypointsForRoute(routeId: Long): Flow<List<WaypointEntity>> =
            observeQuery { loadWaypointsSync(routeId) }

        override fun observeSavedRouteCount(): Flow<Int> =
            observeQuery {
                queryCountSync("SELECT COUNT(*) FROM routes WHERE isRecording = 0")
            }

        override fun observeTotalTrackPointCount(): Flow<Int> =
            observeQuery {
                queryCountSync("SELECT COUNT(*) FROM track_points")
            }

        override fun observeOfflineTrackPointCount(): Flow<Int> =
            observeQuery {
                queryCountSync("SELECT COUNT(*) FROM track_points WHERE isOfflineFix = 1")
            }

        override suspend fun insertRoute(route: RouteEntity): Long =
            withContext(Dispatchers.IO) {
                val id = writableDatabase.insertWithOnConflict(
                    "routes",
                    null,
                    route.toContentValues(includeId = route.id > 0L),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyDataChanged()
                id
            }

        override suspend fun updateRoute(route: RouteEntity) {
            withContext(Dispatchers.IO) {
                writableDatabase.update(
                    "routes",
                    route.toContentValues(includeId = false),
                    "id = ?",
                    arrayOf(route.id.toString())
                )
                notifyDataChanged()
            }
        }

        override suspend fun insertTrackPoint(point: TrackPointEntity): Long =
            withContext(Dispatchers.IO) {
                val id = writableDatabase.insertWithOnConflict(
                    "track_points",
                    null,
                    point.toContentValues(includeId = point.id > 0L),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyDataChanged()
                id
            }

        override suspend fun insertTrackPoints(points: List<TrackPointEntity>) {
            if (points.isEmpty()) return
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    points.forEach { pt ->
                        db.insertWithOnConflict(
                            "track_points",
                            null,
                            pt.toContentValues(includeId = pt.id > 0L),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                notifyDataChanged()
            }
        }

        override suspend fun insertWaypoint(waypoint: WaypointEntity): Long =
            withContext(Dispatchers.IO) {
                val id = writableDatabase.insertWithOnConflict(
                    "waypoints",
                    null,
                    waypoint.toContentValues(includeId = waypoint.id > 0L),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyDataChanged()
                id
            }

        override suspend fun insertWaypoints(waypoints: List<WaypointEntity>) {
            if (waypoints.isEmpty()) return
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    waypoints.forEach { wp ->
                        db.insertWithOnConflict(
                            "waypoints",
                            null,
                            wp.toContentValues(includeId = wp.id > 0L),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                notifyDataChanged()
            }
        }

        override suspend fun deleteWaypointById(waypointId: Long) {
            withContext(Dispatchers.IO) {
                writableDatabase.delete("waypoints", "id = ?", arrayOf(waypointId.toString()))
                notifyDataChanged()
            }
        }

        override suspend fun deleteRouteById(routeId: Long) {
            withContext(Dispatchers.IO) {
                writableDatabase.delete("routes", "id = ?", arrayOf(routeId.toString()))
                notifyDataChanged()
            }
        }
    }

    private fun loadCompletedRoutesWithDetailsSync(): List<RouteWithDetails> {
        val routes = mutableListOf<RouteEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM routes WHERE isRecording = 0 ORDER BY startedAtMillis DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                routes.add(cursor.toRouteEntity())
            }
        }
        return routes.map { route ->
            RouteWithDetails(
                route = route,
                points = loadPointsSync(route.id),
                waypoints = loadWaypointsSync(route.id)
            )
        }
    }

    private fun loadRouteWithDetailsSync(routeId: Long): RouteWithDetails? {
        val route = readableDatabase.rawQuery(
            "SELECT * FROM routes WHERE id = ?",
            arrayOf(routeId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toRouteEntity() else null
        } ?: return null

        return RouteWithDetails(
            route = route,
            points = loadPointsSync(routeId),
            waypoints = loadWaypointsSync(routeId)
        )
    }

    private fun loadPointsSync(routeId: Long): List<TrackPointEntity> {
        val list = mutableListOf<TrackPointEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM track_points WHERE routeId = ? ORDER BY timestampMillis ASC",
            arrayOf(routeId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toTrackPointEntity())
            }
        }
        return list
    }

    private fun loadWaypointsSync(routeId: Long): List<WaypointEntity> {
        val list = mutableListOf<WaypointEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM waypoints WHERE routeId = ? ORDER BY timestampMillis ASC",
            arrayOf(routeId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toWaypointEntity())
            }
        }
        return list
    }

    private fun queryCountSync(sql: String): Int {
        return readableDatabase.rawQuery(sql, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun RouteEntity.toContentValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("name", name)
        put("activityType", activityType)
        put("startedAtMillis", startedAtMillis)
        if (endedAtMillis != null) put("endedAtMillis", endedAtMillis) else putNull("endedAtMillis")
        put("distanceMeters", distanceMeters)
        put("durationMillis", durationMillis)
        put("elevationGainMeters", elevationGainMeters)
        if (maxAltitudeMeters != null) put("maxAltitudeMeters", maxAltitudeMeters) else putNull("maxAltitudeMeters")
        if (minAltitudeMeters != null) put("minAltitudeMeters", minAltitudeMeters) else putNull("minAltitudeMeters")
        put("avgSpeedMps", avgSpeedMps)
        put("isRecording", if (isRecording) 1 else 0)
        put("recordedOffline", if (recordedOffline) 1 else 0)
        put("pointCount", pointCount)
        put("notes", notes)
        put("colorHex", colorHex)
        put("sourceFormat", sourceFormat)
    }

    private fun TrackPointEntity.toContentValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("routeId", routeId)
        put("latitude", latitude)
        put("longitude", longitude)
        if (altitudeMeters != null) put("altitudeMeters", altitudeMeters) else putNull("altitudeMeters")
        put("accuracyMeters", accuracyMeters)
        put("speedMps", speedMps)
        put("bearingDegrees", bearingDegrees)
        put("timestampMillis", timestampMillis)
        put("isOfflineFix", if (isOfflineFix) 1 else 0)
    }

    private fun WaypointEntity.toContentValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("routeId", routeId)
        put("latitude", latitude)
        put("longitude", longitude)
        if (altitudeMeters != null) put("altitudeMeters", altitudeMeters) else putNull("altitudeMeters")
        put("label", label)
        put("category", category)
        put("note", note)
        put("timestampMillis", timestampMillis)
    }

    private fun Cursor.toRouteEntity(): RouteEntity = RouteEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        activityType = getString(getColumnIndexOrThrow("activityType")),
        startedAtMillis = getLong(getColumnIndexOrThrow("startedAtMillis")),
        endedAtMillis = getColumnIndexOrThrow("endedAtMillis").let { if (isNull(it)) null else getLong(it) },
        distanceMeters = getDouble(getColumnIndexOrThrow("distanceMeters")),
        durationMillis = getLong(getColumnIndexOrThrow("durationMillis")),
        elevationGainMeters = getDouble(getColumnIndexOrThrow("elevationGainMeters")),
        maxAltitudeMeters = getColumnIndexOrThrow("maxAltitudeMeters").let { if (isNull(it)) null else getDouble(it) },
        minAltitudeMeters = getColumnIndexOrThrow("minAltitudeMeters").let { if (isNull(it)) null else getDouble(it) },
        avgSpeedMps = getDouble(getColumnIndexOrThrow("avgSpeedMps")),
        isRecording = getInt(getColumnIndexOrThrow("isRecording")) == 1,
        recordedOffline = getInt(getColumnIndexOrThrow("recordedOffline")) == 1,
        pointCount = getInt(getColumnIndexOrThrow("pointCount")),
        notes = getString(getColumnIndexOrThrow("notes")),
        colorHex = getString(getColumnIndexOrThrow("colorHex")),
        sourceFormat = getString(getColumnIndexOrThrow("sourceFormat"))
    )

    private fun Cursor.toTrackPointEntity(): TrackPointEntity = TrackPointEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        routeId = getLong(getColumnIndexOrThrow("routeId")),
        latitude = getDouble(getColumnIndexOrThrow("latitude")),
        longitude = getDouble(getColumnIndexOrThrow("longitude")),
        altitudeMeters = getColumnIndexOrThrow("altitudeMeters").let { if (isNull(it)) null else getDouble(it) },
        accuracyMeters = getFloat(getColumnIndexOrThrow("accuracyMeters")),
        speedMps = getFloat(getColumnIndexOrThrow("speedMps")),
        bearingDegrees = getFloat(getColumnIndexOrThrow("bearingDegrees")),
        timestampMillis = getLong(getColumnIndexOrThrow("timestampMillis")),
        isOfflineFix = getInt(getColumnIndexOrThrow("isOfflineFix")) == 1
    )

    private fun Cursor.toWaypointEntity(): WaypointEntity = WaypointEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        routeId = getLong(getColumnIndexOrThrow("routeId")),
        latitude = getDouble(getColumnIndexOrThrow("latitude")),
        longitude = getDouble(getColumnIndexOrThrow("longitude")),
        altitudeMeters = getColumnIndexOrThrow("altitudeMeters").let { if (isNull(it)) null else getDouble(it) },
        label = getString(getColumnIndexOrThrow("label")),
        category = getString(getColumnIndexOrThrow("category")),
        note = getString(getColumnIndexOrThrow("note")),
        timestampMillis = getLong(getColumnIndexOrThrow("timestampMillis"))
    )

    companion object {
        private const val DATABASE_NAME = "trailline_offline_vault.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: TrailDatabase? = null

        fun getInstance(context: Context): TrailDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrailDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
