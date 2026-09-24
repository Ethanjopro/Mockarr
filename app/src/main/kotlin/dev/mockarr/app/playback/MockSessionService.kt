package dev.mockarr.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
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
import dev.mockarr.app.ui.Formatter
import dev.mockarr.app.ui.screens.endLabelRes
import dev.mockarr.app.ui.screens.movingLabelRes
import dev.mockarr.core.data.SessionSnapshotStore
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.mocklocation.MockLocationController
import dev.mockarr.core.mocklocation.MockStartResult
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.SessionSnapshot
import dev.mockarr.core.model.SimulatedFix
import dev.mockarr.core.model.progressOrZero
import dev.mockarr.core.model.remainingSecondsOrNull
import dev.mockarr.core.routing.ElevationProvider
import dev.mockarr.core.routing.Geocoder
import dev.mockarr.core.simulation.Jitter
import dev.mockarr.core.simulation.SimClock
import dev.mockarr.core.simulation.SimulationEngine
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
    lateinit var elevationClient: ElevationProvider

    @Inject
    lateinit var geocoder: Geocoder

    @Inject
    lateinit var snapshotStore: SessionSnapshotStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionJob: Job? = null
    private var holdJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var routeDistanceMeters: Double = 0.0

    /** The drive's legs and stops as the notification's progress bar shows them (Android 16 Live Updates). */
    private var progressLayout: ProgressLayout? = null

    /** The drive in flight, for snapshots; null while holding or idle. */
    private var activeDrive: ActiveDrive? = null

    private class ActiveDrive(val route: Route, val profile: RoutingProfile, val engine: SimulationEngine)
    private var lastNotified: DriveNotificationKey? = null

    /** The pin held when playback began — the fallback if "stay at destination" is off. */
    private var rememberedPin: LatLng? = null

    /** A parked receiver still wobbles: the hold keepalive reports a jittered copy of the spot. */
    private val holdJitter = Jitter(Random(SystemClock.elapsedRealtimeNanos()))

    // The launcher's own intent (MAIN + LAUNCHER): Android matches it to the
    // running task and brings that forward. A bare component intent does not
    // match, and stacks a second, empty MainActivity on top of the drive.
    private val contentIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
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
        val pending = repository.consumePendingSession()
        if (pending == null) {
            if (previousHold == null) stopSelf()
            return
        }
        if (previousHold?.source == HoldSource.PIN) rememberedPin = previousHold.position
        holdJob?.cancel()
        holdJob = null
        // beginMocking() never touches already-registered providers (start() is
        // idempotent), so a Holding → Playing transition has no provider gap.
        // Foreground FIRST: startForegroundService obliges it even when mocking then
        // fails (not set up) — skipping it crashed the app (critique, session 42).
        if (promoteToForeground() && beginMocking()) {
            runSession(pending)
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
        if (!mockController.isRunning && !(promoteToForeground() && beginMocking())) {
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

    private fun runSession(pending: MockSessionRepository.PendingSession) {
        val route = pending.route
        acquireWakeLock()
        lastNotified = null
        routeDistanceMeters = route.distanceMeters
        progressLayout = progressLayoutOf(route, pending.profile)
        val engine = buildSimulationEngine(route, pending)
        activeDrive = ActiveDrive(route, pending.profile, engine)
        repository.playingStarted(
            engine,
            MockSessionRepository.LiveDrive(route, pending.profile, pending.saved, planned = pending.planned ?: route),
        )
        saveSnapshot()
        launchSessionJob(engine)
    }

    private fun buildSimulationEngine(
        route: Route,
        pending: MockSessionRepository.PendingSession,
    ): SimulationEngine {
        val settings = settingsRepository.settings.value
        // Congestion factor is captured once at playback start, never mid-route.
        return SimulationEngine(
            route = route,
            params = driveParams(settings, trafficFactorAt(settings)),
            clock = SimClock { SystemClock.elapsedRealtimeNanos() },
            random = Random(SystemClock.elapsedRealtimeNanos()),
            // New drives start at 1× (MockSessionViewModel.play resets it); a resumed drive brings its own pace.
            initialSpeedMultiplier = repository.speedMultiplier.value,
            resumeFrom = pending.resumeFrom,
        )
    }

    private fun launchSessionJob(engine: SimulationEngine) {
        sessionJob = scope.launch {
            val stateJob = launch {
                var lastKind: Class<out PlaybackState>? = null
                engine.state.collect { state ->
                    repository.updateState(state)
                    // Pause, a wait starting or ending: worth a snapshot of its own,
                    // and shown in the notification now rather than at the next second.
                    if (state::class.java != lastKind) {
                        lastKind = state::class.java
                        saveSnapshot()
                        refreshNotification()
                    }
                }
            }
            // Wall-clock cadence, whatever the tick rate: the bar and the time left
            // move every second while the app is away (refreshNotification skips
            // posts that would change nothing).
            val notifyJob = launch {
                var seconds = 0
                while (isActive) {
                    refreshNotification()
                    if (seconds++ % SNAPSHOT_EVERY_SECONDS == 0) saveSnapshot()
                    delay(NOTIFICATION_REFRESH_MILLIS)
                }
            }
            engine.fixes.collect { fix ->
                mockController.push(fix)
                repository.updateFix(fix)
            }
            stateJob.cancel()
            notifyJob.cancel()
            activeDrive = null
            onEngineEnded(stoppedEarly = engine.stoppedBeforeArrival)
        }
    }

    /**
     * Writes down what the session is doing so a process death can be
     * resumed. Cheap (one small file, off the main thread) and idempotent;
     * [release] is the only thing that clears it — [onDestroy] deliberately
     * leaves it behind.
     */
    private fun saveSnapshot() {
        val drive = activeDrive
        val holding = repository.session.value as? MockSessionState.Holding
        val now = System.currentTimeMillis()
        val snapshot = when {
            drive != null && repository.state.value !is PlaybackState.Finished -> SessionSnapshot(
                kind = SessionSnapshot.Kind.PLAYING,
                route = drive.route,
                profile = drive.profile,
                speedMultiplier = repository.speedMultiplier.value,
                distanceMeters = drive.engine.distanceMeters,
                dwellSecondsLeft = drive.engine.activeDwellSecondsLeft,
                savedAtEpochMillis = now,
            )
            holding != null -> SessionSnapshot(
                kind = SessionSnapshot.Kind.HOLDING,
                holdPosition = holding.position,
                savedAtEpochMillis = now,
            )
            else -> null
        }
        if (snapshot != null) snapshotStore.write(snapshot)
    }

    /** End-of-route chain: destination hold → remembered pin → real location. */
    private fun onEngineEnded(stoppedEarly: Boolean) {
        // The clean spot, not the last wobbled report: the hold scatters around it by itself.
        val endPosition = repository.latestFix.value?.truePosition
        repository.engineEnded()
        lastNotified = null
        val pin = rememberedPin
        when {
            settingsRepository.settings.value.stayAtDestination && endPosition != null ->
                enterHold(endPosition, if (stoppedEarly) HoldSource.STOPPED else HoldSource.DESTINATION)
            pin != null -> enterHold(pin, HoldSource.PIN)
            else -> release()
        }
    }

    private fun enterHold(position: LatLng, source: HoldSource) {
        holdJob?.cancel()
        repository.holdStarted(position, source)
        saveSnapshot()
        var fix = SimulatedFix(
            position = position,
            truePosition = position,
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
                    fix = fix.copy(position = target, truePosition = target)
                    mockController.push(fix)
                    repository.holdMoved(target)
                }
            }
            // One settle per nudge burst (collectLatest restarts the delay):
            // re-resolve terrain altitude and the spot's name after movement stops.
            launch {
                repository.holdMoves.collectLatest { target ->
                    delay(HOLD_SETTLE_MILLIS)
                    saveSnapshot() // the nudged spot is the one to come back to
                    elevationClient.elevations(listOf(target)).getOrNull()?.firstOrNull()?.let {
                        fix = fix.copy(altitudeMeters = it)
                    }
                    resolveHoldName(target)
                }
            }
            while (isActive) {
                delay(HOLD_TICK_MILLIS)
                mockController.push(wobbled(fix))
            }
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    /** The pushed copy only — the held position, the banner and the thumbstick target never move. */
    private fun wobbled(fix: SimulatedFix): SimulatedFix {
        val settings = settingsRepository.settings.value
        if (!settings.jitterEnabled) return fix
        return fix.copy(position = holdJitter.offset(fix.position, settings.jitterSigmaMeters))
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
        activeDrive = null
        lastNotified = null
        snapshotStore.clear() // ended on purpose: nothing to resume
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
        repository.reportError(getString(R.string.error_mock_start, e.message))
        false
    } catch (e: SecurityException) {
        repository.reportError(getString(R.string.error_mock_permission, e.message))
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
        val units = settingsRepository.settings.value.units
        val key = driveNotificationKey(repository.state.value, routeDistanceMeters, units) ?: return
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
        val text = holding.placeName?.let { getString(R.string.notification_hold_at, it) }
            ?: when {
                holding.source == HoldSource.DESTINATION -> getString(R.string.notification_hold_destination)
                holding.source == HoldSource.STOPPED -> getString(R.string.notification_hold_stopped)
                holding.nameFailed -> getString(R.string.notification_hold_pin)
                else -> getString(R.string.notification_holding_pending)
            }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(getString(R.string.notification_title_holding))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            // The app's own words: this one hands the real location back.
            .addAction(NotificationCompat.Action(0, getString(R.string.strip_stop_hold), releaseIntent))
            .build()
    }

    private fun playingNotification(): Notification {
        val state = repository.state.value
        val paused = state is PlaybackState.Paused
        val progress = state.progressOrZero
        val units = settingsRepository.settings.value.units
        val formatter = Formatter(resources)
        val timeLeft = state.remainingSecondsOrNull?.let(formatter::duration)
        val parts = listOfNotNull(
            formatter.distanceProgress(routeDistanceMeters * progress, routeDistanceMeters, units),
            timeLeft?.let { getString(R.string.notification_time_left, it) },
            if (paused) getString(R.string.notification_paused) else null,
        )
        val text = parts.joinToString(getString(R.string.separator))

        val toggleAction = if (paused) {
            NotificationCompat.Action(0, getString(R.string.notification_action_resume), resumeIntent)
        } else {
            NotificationCompat.Action(0, getString(R.string.notification_action_pause), pauseIntent)
        }

        // Android 16+: a promoted Live Update — status-bar chip with the time
        // left, lock-screen card whose bar has the legs as segments and the stops
        // as points. Older versions ignore the style and keep the plain bar.
        val chip = if (paused) getString(R.string.notification_chip_paused) else timeLeft
        val drive = repository.liveDrive.value
        val profile = activeDrive?.profile ?: drive?.profile ?: RoutingProfile.DRIVING
        // "Driving" / "Walking" / "Cycling", as the app's band says it (the header already names
        // Mockarr) — or, at a stop, where: "Waiting at Reunion Tower", not "Driving · waiting".
        val title = waitingTitle(resources, state, drive) ?: getString(profile.movingLabelRes())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(PROGRESS_MAX, (progress * PROGRESS_MAX).toInt(), false)
            .setContentIntent(contentIntent)
            .addAction(toggleAction)
            // "End drive", as in the app: the drive stops, and the location holds or returns per the setting.
            .addAction(NotificationCompat.Action(0, getString(profile.endLabelRes()), stopIntent))
            .setRequestPromotedOngoing(true)
            .apply { chip?.let(::setShortCriticalText) }
            .apply { progressLayout?.let { setStyle(progressStyle(it, progress)) } }
            .build()
    }

    /** User-facing failure copy for a mock session start, or null on success. */
    private fun MockStartResult.errorMessageOrNull(): String? = when (this) {
        MockStartResult.Ok -> null
        MockStartResult.NotSelectedAsMockApp -> getString(R.string.error_mock_not_selected)
        is MockStartResult.ProviderError -> message
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
        private const val NOTIFICATION_ID = 42
        private const val NOTIFICATION_REFRESH_MILLIS = 1_000L
        private const val SNAPSHOT_EVERY_SECONDS = 5
        private const val PROGRESS_MAX = 1_000
        private const val HOLD_TICK_MILLIS = 1_000L
        private const val HOLD_SETTLE_MILLIS = 1_500L
        private const val HOLD_NAME_TIMEOUT_MILLIS = 5_000L
        private const val HOLD_ACCURACY_METERS = 5.0
        private const val HOLD_ALTITUDE_METERS = 35.0
        private const val WAKE_LOCK_TAG = "mockarr:playback"

        // 6 h cap: long holds in deep doze may see deferred ticks after this — acceptable.
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6 * 60 * 60 * 1000L
    }
}
