package dev.mockarr.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.mockarr.app.MainActivity
import dev.mockarr.app.R
import dev.mockarr.app.ui.errorMessageOrNull
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.mocklocation.MockLocationController
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.progressOrZero
import dev.mockarr.core.simulation.SimClock
import dev.mockarr.core.simulation.SimulationEngine
import dev.mockarr.core.simulation.SimulationParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * Foreground service (location type) that owns a playback session: it collects
 * the engine's fixes and feeds them into the mock location providers, keeping
 * playback alive while the app is backgrounded or the screen is off.
 */
@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject
    lateinit var repository: PlaybackSessionRepository

    @Inject
    lateinit var mockController: MockLocationController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var routeDistanceMeters: Double = 0.0
    private var lastNotified: Pair<Int, Boolean>? = null

    private val contentIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val pauseIntent by lazy { servicePendingIntent(ACTION_PAUSE, requestCode = 1) }
    private val resumeIntent by lazy { servicePendingIntent(ACTION_RESUME, requestCode = 2) }
    private val stopIntent by lazy { servicePendingIntent(ACTION_STOP, requestCode = 3) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_PAUSE -> {
                repository.pause()
                refreshNotification()
            }
            ACTION_RESUME -> {
                repository.resume()
                refreshNotification()
            }
            ACTION_STOP -> repository.stop() // engine decelerates, then the session ends
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanUp()
        scope.cancel()
        super.onDestroy()
    }

    private fun startSession() {
        if (sessionJob?.isActive == true) return
        val route = repository.consumePendingRoute()
        if (route != null && beginMocking() && promoteToForeground()) {
            runSession(route)
        } else {
            mockController.stop() // no-op when never started
            stopSelf()
        }
    }

    private fun beginMocking(): Boolean {
        val error = mockController.start().errorMessageOrNull()
        if (error != null) repository.reportError(error)
        return error == null
    }

    private fun runSession(route: Route) {
        acquireWakeLock()
        routeDistanceMeters = route.distanceMeters
        val settings = settingsRepository.settings.value
        val engine = SimulationEngine(
            route = route,
            params = SimulationParams(
                tickHz = settings.tickHz,
                jitterEnabled = settings.jitterEnabled,
                jitterSigmaMeters = settings.jitterSigmaMeters,
            ),
            clock = SimClock { SystemClock.elapsedRealtimeNanos() },
            random = Random(SystemClock.elapsedRealtimeNanos()),
        )
        repository.sessionStarted(engine)

        sessionJob = scope.launch {
            val stateJob = launch {
                engine.state.collect { repository.updateState(it) }
            }
            var tick = 0
            engine.fixes.collect { fix ->
                mockController.push(fix)
                repository.updateFix(fix)
                if (tick++ % NOTIFICATION_UPDATE_TICKS == 0) refreshNotification()
            }
            stateJob.cancel()
            endSession()
        }
    }

    private fun promoteToForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (e: IllegalStateException) {
        repository.reportError("Could not start playback: ${e.message}")
        false
    } catch (e: SecurityException) {
        repository.reportError(
            "Playback needs the location permission (Android requires it for background playback): ${e.message}",
        )
        false
    }

    private fun endSession() {
        mockController.stop()
        repository.sessionEnded()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanUp() {
        sessionJob?.cancel()
        sessionJob = null
        mockController.stop()
        repository.sessionEnded()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Route playback",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun refreshNotification() {
        val state = repository.state.value
        val key = (state.progressOrZero * PROGRESS_MAX).toInt() to (state is PlaybackState.Paused)
        if (key == lastNotified) return
        lastNotified = key
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = repository.state.value
        val paused = state is PlaybackState.Paused
        val progress = state.progressOrZero
        val km = routeDistanceMeters / 1000.0
        val text = "%.1f / %.1f km%s".format(km * progress, km, if (paused) " · paused" else "")

        val toggleAction = if (paused) {
            NotificationCompat.Action(0, "Resume", resumeIntent)
        } else {
            NotificationCompat.Action(0, "Pause", pauseIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("Mockarr — driving route")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(PROGRESS_MAX, (progress * PROGRESS_MAX).toInt(), false)
            .setContentIntent(contentIntent)
            .addAction(toggleAction)
            .addAction(NotificationCompat.Action(0, "Stop", stopIntent))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ACTION_START = "dev.mockarr.app.playback.START"
        const val ACTION_PAUSE = "dev.mockarr.app.playback.PAUSE"
        const val ACTION_RESUME = "dev.mockarr.app.playback.RESUME"
        const val ACTION_STOP = "dev.mockarr.app.playback.STOP"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 42
        private const val NOTIFICATION_UPDATE_TICKS = 5
        private const val PROGRESS_MAX = 100
        private const val WAKE_LOCK_TAG = "mockarr:playback"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6 * 60 * 60 * 1000L // 6 h safety cap
    }
}
