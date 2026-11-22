package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.trailLineDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trailline_preferences"
)

enum class MapLayerChoice(val label: String) {
    TERRAIN("Terrain"),
    NORMAL("Standard"),
    SATELLITE("Satellite"),
    HYBRID("Hybrid");

    companion object {
        fun fromName(value: String?): MapLayerChoice =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TERRAIN
    }
}

data class TrailPreferences(
    val offlineModeEnabled: Boolean = false,
    val useMetricUnits: Boolean = true,
    val gpsIntervalSeconds: Int = 3,
    val mapLayer: MapLayerChoice = MapLayerChoice.TERRAIN,
    val autoFollowCamera: Boolean = true,
    val preferTopoCanvas: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode_enabled")
        val USE_METRIC = booleanPreferencesKey("use_metric_units")
        val GPS_INTERVAL_SEC = intPreferencesKey("gps_interval_seconds")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val AUTO_FOLLOW = booleanPreferencesKey("auto_follow_camera")
        val PREFER_TOPO_CANVAS = booleanPreferencesKey("prefer_topo_canvas")
    }

    val preferencesFlow: Flow<TrailPreferences> = context.trailLineDataStore.data.map { prefs ->
        TrailPreferences(
            offlineModeEnabled = prefs[Keys.OFFLINE_MODE] ?: false,
            useMetricUnits = prefs[Keys.USE_METRIC] ?: true,
            gpsIntervalSeconds = prefs[Keys.GPS_INTERVAL_SEC] ?: 3,
            mapLayer = MapLayerChoice.fromName(prefs[Keys.MAP_LAYER]),
            autoFollowCamera = prefs[Keys.AUTO_FOLLOW] ?: true,
            preferTopoCanvas = prefs[Keys.PREFER_TOPO_CANVAS] ?: false
        )
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.trailLineDataStore.edit { it[Keys.OFFLINE_MODE] = enabled }
    }

    suspend fun setUseMetricUnits(metric: Boolean) {
        context.trailLineDataStore.edit { it[Keys.USE_METRIC] = metric }
    }

    suspend fun setGpsIntervalSeconds(seconds: Int) {
        context.trailLineDataStore.edit { it[Keys.GPS_INTERVAL_SEC] = seconds.coerceIn(1, 30) }
    }

    suspend fun setMapLayer(layer: MapLayerChoice) {
        context.trailLineDataStore.edit { it[Keys.MAP_LAYER] = layer.name }
    }

    suspend fun setAutoFollowCamera(enabled: Boolean) {
        context.trailLineDataStore.edit { it[Keys.AUTO_FOLLOW] = enabled }
    }

    suspend fun setPreferTopoCanvas(prefer: Boolean) {
        context.trailLineDataStore.edit { it[Keys.PREFER_TOPO_CANVAS] = prefer }
    }
}
