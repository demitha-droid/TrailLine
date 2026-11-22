package com.example.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.GeoMetrics
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessionManager: TrackingSessionManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationManager: LocationManager? = null

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                sessionManager.onTrackingLocationFix(location)
            }
        }
    }

    private val nativeGpsListener = LocationListener { location: Location ->
        sessionManager.onTrackingLocationFix(location)
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = TrackingSessionManager.getInstance(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        createNotificationChannel()

        serviceScope.launch {
            sessionManager.state.collect { state ->
                if (state.status == TrackingStatus.RECORDING) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification(sessionManager.state.value)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            stopSelf()
            return
        }

        serviceScope.launch {
            val prefs = sessionManager.preferencesRepository.preferencesFlow.first()
            val intervalMs = (prefs.gpsIntervalSeconds * 1000L).coerceIn(1000L, 30_000L)

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(1000L))
                .setMinUpdateDistanceMeters(2f)
                .build()

            try {
                fusedLocationClient.requestLocationUpdates(
                    request,
                    fusedCallback,
                    Looper.getMainLooper()
                )
            } catch (_: SecurityException) {
                // Permission revoked mid-session
            }

            // Native satellite GPS fallback ensures fixes keep arriving in deep offline areas
            if (hasFine) {
                try {
                    val lm = locationManager
                    if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            intervalMs,
                            2f,
                            nativeGpsListener,
                            Looper.getMainLooper()
                        )
                    }
                } catch (_: Exception) {
                    // Hardware GPS unavailable on this device
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(fusedCallback)
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
        try {
            locationManager?.removeUpdates(nativeGpsListener)
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TrailLine Route Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active GPS route tracking and offline breadcrumb recording"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: LiveTrackingState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = if (state.isOfflineRecording) "Offline Vault Active" else "Live GPS"
        val contentText = "${GeoMetrics.formatDistance(state.distanceMeters, true)} • " +
            "${GeoMetrics.formatDuration(state.elapsedMillis)} • ${state.livePoints.size} fixes • $modeLabel"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(state.routeName.ifBlank { "TrailLine Recording" })
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.trailline.tracker.ACTION_START_TRACKING"
        const val ACTION_STOP = "com.trailline.tracker.ACTION_STOP_TRACKING"
        private const val CHANNEL_ID = "trailline_tracking_channel"
        private const val NOTIFICATION_ID = 4107
    }
}
