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
import dev.mockarr.app.ui.formatDistanceProgress
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.mocklocation.MockLocationController
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.SimulatedFix
import dev.mockarr.core.model.progressOrZero
import dev.mockarr.core.model.remainingSecondsOrNull
import dev.mockarr.core.routing.OpenMeteoElevationClient
import dev.mockarr.core.routing.PhotonGeocoder
import dev.mockarr.core.simulation.SimClock
import dev.mockarr.core.simulation.SimulationEngine
import dev.mockarr.core.simulation.SimulationParams
import dev.mockarr.core.simulation.TrafficModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random

/**
 * Foreground service (location type) that owns every mock session: route
 * playback and stationary holds. Owning both in one place is what guarantees
 * zero real-location leakage — test providers are registered once and removed
 * in exactly one place ([release]), never torn down between transitions.
 */
@AndroidEntryPoint
class MockSessionService : Service() {

    @Inject
    lateinit var repository: MockSessionRepository

    @Inject
    lateinit var mockController: MockLocationController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var elevationClient: OpenMeteoElevationClient

    @Inject
    lateinit var geocoder: PhotonGeocoder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionJob: Job? = null
    private var holdJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var routeDistanceMeters: Double = 0.0

    /** Wait set on the destination stop; the engine rests there, so the service times it. */
    private var destinationWaitSeconds: Int = 0
    private var lastNotified: Triple<Int, Int, Int>? = null

    /** The pin held when playback began — the fallback if "stay at destination" is off. */
    private var rememberedPin: LatLng? = null

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
    private val releaseIntent by lazy { servicePendingIntent(ACTION_RELEASE, requestCode = 4) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mock location session", NotificationManager.IMPORTANCE_LOW),
        )
        // Progress alerts were removed; shed the channel on devices that have it.
        manager.deleteNotificationChannel("progress_alerts")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRouteSession()
            ACTION_HOLD -> startPinHold(intent)
            ACTION_PAUSE -> {
                repository.pause()
                refreshNotification()
            }
            ACTION_RESUME -> {
                repository.resume()
                refreshNotification()
            }
            ACTION_STOP -> repository.stop() // engine decelerates, then the end-of-route chain runs
            ACTION_RELEASE -> release()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Safety net (system kill): stale enabled test providers would freeze
        // the device's location, so always clean up here.
        sessionJob?.cancel()
        sessionJob = null
        holdJob?.cancel()
        holdJob = null
        mockController.stop()
        repository.sessionReleased()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRouteSession() {
        if (sessionJob?.isActive == true) return
        val previousHold = repository.session.value as? MockSessionState.Holding
        val route = repository.consumePendingRoute()
        if (route == null) {
            if (previousHold == null) stopSelf()
            return
        }
        if (previousHold?.source == HoldSource.PIN) rememberedPin = previousHold.position
        holdJob?.cancel()
        holdJob = null
        // beginMocking() never touches already-registered providers (start() is
        // idempotent), so a Holding → Playing transition has no provider gap.
        if (beginMocking() && promoteToForeground()) {
            runSession(route)
        } else if (previousHold != null && mockController.isRunning) {
            enterHold(previousHold.position, previousHold.source) // resume the hold untouched
        } else {
            release()
        }
    }

    private fun startPinHold(intent: Intent) {
        if (sessionJob?.isActive == true) return // no pin swaps mid-playback
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return
        if (!mockController.isRunning && !(beginMocking() && promoteToForeground())) {
            release()
            return
        }
        acquireWakeLock()
        enterHold(LatLng(lat, lng), HoldSource.PIN)
    }

    private fun beginMocking(): Boolean {
        val error = mockController.start().errorMessageOrNull()
        if (error != null) repository.reportError(error)
        return error == null
    }

    private fun runSession(route: Route) {
        acquireWakeLock()
        routeDistanceMeters = route.distanceMeters
        destinationWaitSeconds = route.waypointWaitsSeconds.lastOrNull() ?: 0
        val settings = settingsRepository.settings.value
        // Congestion factor is captured once at playback start, never mid-route.
        val trafficFactor = if (settings.trafficSimEnabled) {
            val now = LocalDateTime.now()
            TrafficModel.congestionFactor(now.dayOfWeek, now.hour, now.minute)
        } else {
            1.0
        }
        val engine = SimulationEngine(
            route = route,
            params = SimulationParams(
                tickHz = settings.tickHz,
                durationScale = trafficFactor,
                speedVarianceFraction = SPEED_VARIANCE_FRACTION,
                jitterEnabled = settings.jitterEnabled,
                jitterSigmaMeters = settings.jitterSigmaMeters,
            ),
            clock = SimClock { SystemClock.elapsedRealtimeNanos() },
            random = Random(SystemClock.elapsedRealtimeNanos()),
            // The speed chips outlive a drive: start the next one at the pace they show.
            initialSpeedMultiplier = repository.speedMultiplier,
        )
        repository.playingStarted(engine)

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
            onEngineEnded()
        }
    }

    /** End-of-route chain: destination hold (open-ended or timed) → remembered pin → real location. */
    private fun onEngineEnded() {
        val endPosition = repository.latestFix.value?.position
        repository.engineEnded()
        lastNotified = null
        val wait = destinationWaitSeconds
        destinationWaitSeconds = 0
        when {
            settingsRepository.settings.value.stayAtDestination && endPosition != null ->
                enterHold(endPosition, HoldSource.DESTINATION)
            wait > 0 && endPosition != null -> holdAtDestinationFor(endPosition, wait)
            else -> afterDestination()
        }
    }

    /** Remembered pin, else the real location. */
    private fun afterDestination() {
        val pin = rememberedPin
        if (pin != null) enterHold(pin, HoldSource.PIN) else release()
    }

    /**
     * A timed destination wait: hold there for [seconds] (fast-forwarded by the
     * playback multiplier, like mid-route dwells), then continue the chain. The
     * timer runs beside the hold's keepalive; a manual Stop cancels both.
     */
    private fun holdAtDestinationFor(position: LatLng, seconds: Int) {
        enterHold(position, HoldSource.DESTINATION)
        val timed = holdJob
        val multiplier = repository.speedMultiplier.coerceAtLeast(MIN_WAIT_MULTIPLIER)
        scope.launch {
            delay((seconds * MILLIS_PER_SECOND / multiplier).toLong())
            // Only move on if this hold is still the live one (a manual hold or Stop replaces it).
            if (holdJob === timed && timed?.isActive == true) afterDestination()
        }
    }

    private fun enterHold(position: LatLng, source: HoldSource) {
        holdJob?.cancel()
        repository.holdStarted(position, source)
        var fix = SimulatedFix(
            position = position,
            speedMetersPerSecond = 0.0,
            bearingDegrees = 0.0,
            accuracyMeters = HOLD_ACCURACY_METERS,
            altitudeMeters = HOLD_ALTITUDE_METERS,
        )
        mockController.push(fix) // synchronous first push — no gap in the handoff
        holdJob = scope.launch {
            // Upgrade later pushes to real terrain altitude; the constant stands on failure.
            launch {
                elevationClient.elevations(listOf(position)).getOrNull()?.firstOrNull()?.let {
                    fix = fix.copy(altitudeMeters = it)
                }
            }
            // Name the spot for the banner/notification. The banner waits for
            // this (never shows raw coordinates), so a failure must still land.
            launch { resolveHoldName(position) }
            // Thumbstick nudges: push the moved fix immediately; the keepalive
            // below re-pushes it. Lives in holdJob, so it dies with the hold.
            launch {
                repository.holdMoves.collect { target ->
                    fix = fix.copy(position = target)
                    mockController.push(fix)
                    repository.holdMoved(target)
                }
            }
            // One settle per nudge burst (collectLatest restarts the delay):
            // re-resolve terrain altitude and the spot's name after movement stops.
            launch {
                repository.holdMoves.collectLatest { target ->
                    delay(HOLD_SETTLE_MILLIS)
                    elevationClient.elevations(listOf(target)).getOrNull()?.firstOrNull()?.let {
                        fix = fix.copy(altitudeMeters = it)
                    }
                    resolveHoldName(target)
                }
            }
            while (isActive) {
                delay(HOLD_TICK_MILLIS)
                mockController.push(fix)
            }
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private suspend fun resolveHoldName(position: LatLng) {
        val name = withTimeoutOrNull(HOLD_NAME_TIMEOUT_MILLIS) {
            geocoder.reverse(position).getOrNull()?.name
        }
        repository.holdNameResolved(position, name)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    /** The ONLY path that removes test providers and reveals the real location. */
    private fun release() {
        sessionJob?.cancel()
        sessionJob = null
        holdJob?.cancel()
        holdJob = null
        rememberedPin = null
        mockController.stop()
        repository.sessionReleased()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        repository.reportError("Could not start mocking: ${e.message}")
        false
    } catch (e: SecurityException) {
        repository.reportError(
            "Mocking needs the location permission (Android requires it for background use): ${e.message}",
        )
        false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
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

    private fun refreshNotification() {
        val state = repository.state.value
        val stateOrdinal = when (state) {
            is PlaybackState.Paused -> 1
            is PlaybackState.Dwelling -> 2
            else -> 0
        }
        val key = Triple(
            (state.progressOrZero * PROGRESS_MAX).toInt(),
            stateOrdinal,
            ((state.remainingSecondsOrNull ?: 0.0) / SECONDS_PER_MINUTE).toInt(),
        )
        if (key == lastNotified) return
        lastNotified = key
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val holding = repository.session.value as? MockSessionState.Holding
        return if (holding != null) holdingNotification(holding) else playingNotification()
    }

    private fun holdingNotification(holding: MockSessionState.Holding): Notification {
        // Never raw coordinates: a generic label until (or unless) the name resolves.
        val text = holding.placeName?.let { "Holding at $it" }
            ?: when {
                holding.source == HoldSource.DESTINATION -> "Holding at destination"
                holding.nameFailed -> "Holding at dropped pin"
                else -> "Holding\u2026"
            }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("Mockarr — holding location")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(NotificationCompat.Action(0, "Stop", releaseIntent))
            .build()
    }

    private fun playingNotification(): Notification {
        val state = repository.state.value
        val paused = state is PlaybackState.Paused
        val progress = state.progressOrZero
        val units = settingsRepository.settings.value.units
        val progressText =
            formatDistanceProgress(routeDistanceMeters * progress, routeDistanceMeters, units)
        val etaSuffix = state.remainingSecondsOrNull
            ?.let { " · ${formatDurationShort(it)} left" }
            .orEmpty()
        val statusSuffix = when {
            paused -> " · paused"
            state is PlaybackState.Dwelling -> " · waiting"
            else -> ""
        }
        val text = progressText + etaSuffix + statusSuffix

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
            Intent(this, MockSessionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ACTION_START = "dev.mockarr.app.playback.START"
        const val ACTION_PAUSE = "dev.mockarr.app.playback.PAUSE"
        const val ACTION_RESUME = "dev.mockarr.app.playback.RESUME"
        const val ACTION_STOP = "dev.mockarr.app.playback.STOP"
        const val ACTION_HOLD = "dev.mockarr.app.playback.HOLD"
        const val ACTION_RELEASE = "dev.mockarr.app.playback.RELEASE"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        private const val CHANNEL_ID = "playback"
        private const val MILLIS_PER_SECOND = 1000.0
        private const val MIN_WAIT_MULTIPLIER = 0.1
        private const val NOTIFICATION_ID = 42
        private const val NOTIFICATION_UPDATE_TICKS = 5
        private const val PROGRESS_MAX = 100
        private const val HOLD_TICK_MILLIS = 1_000L
        private const val HOLD_SETTLE_MILLIS = 1_500L
        private const val HOLD_NAME_TIMEOUT_MILLIS = 5_000L
        private const val SECONDS_PER_MINUTE = 60.0
        private const val SPEED_VARIANCE_FRACTION = 0.08
        private const val HOLD_ACCURACY_METERS = 5.0
        private const val HOLD_ALTITUDE_METERS = 35.0
        private const val WAKE_LOCK_TAG = "mockarr:playback"

        // 6 h cap: long holds in deep doze may see deferred ticks after this — acceptable.
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6 * 60 * 60 * 1000L
    }
}
