package com.scrollmeter.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.BuildConfig
import com.scrollmeter.app.ScrollMeterApplication
import com.scrollmeter.app.aggregation.ScrollPipeline
import com.scrollmeter.app.calibration.CalibrationState
import com.scrollmeter.app.measurement.ScrollMeasurementEngine
import com.scrollmeter.app.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Do not expand requested accessibility capabilities without a
 * documented product need and privacy/policy review.
 *
 * Receives only `TYPE_VIEW_SCROLLED` (res/xml/accessibility_service_config.xml) with
 * `canRetrieveWindowContent="false"`: it cannot read screen content and does not try.
 *
 * Pipeline (spec §16, §62, §63, D5): the callback parses primitives and hands them to
 * [ScrollPipeline]'s bounded channel, then returns — no Room, no I/O, no PackageManager on the
 * callback path. The pipeline runs on one thread at a time: it measures, sums in memory and
 * writes to Room every 10 s / 50 events / new day, and once more on unbind, interrupt and destroy.
 *
 * Nothing is converted before the stored calibration and settings are loaded (samples wait in
 * the channel); afterwards every change applies to new events only (spec §65). Time in app is
 * re-synced on connect when the last sync is older than 6 h, and when the day changes (ADR-021).
 */
class ScrollAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Engine, accumulator and sessions are touched from this dispatcher only — never in parallel. */
    private val pipelineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var graph: AppGraph? = null
    private var engine: ScrollMeasurementEngine? = null
    private var pipeline: ScrollPipeline? = null
    private var pipelineJob: Job? = null

    /** The latest stored calibration — re-applied when rotation changes the display. */
    @Volatile
    private var calibration: CalibrationState = CalibrationState.NONE

    override fun onServiceConnected() {
        super.onServiceConnected()
        // A repeated connect replaces the pipeline; the old one flushes and ends on its own.
        stopPipeline()
        val graph = (application as ScrollMeterApplication).graph
        // Replaced by the stored calibration before the pipeline processes its first sample.
        val engine = graph.newEngine(calibration)
        val pipeline = ScrollPipeline(
            engine = engine,
            ownPackage = graph.ownPackage,
            store = graph.scrollRepository,
            monitor = graph.monitor,
            sinks = graph.measurementSinks,
            uptimeMs = SystemClock::uptimeMillis,
            onDayChanged = { syncUsage(graph, onlyIfStale = false) },
        )
        this.graph = graph
        this.engine = engine
        this.pipeline = pipeline
        graph.monitor.onServiceConnected(System.currentTimeMillis())
        lifecycle("connected")

        pipelineJob = serviceScope.launch(pipelineDispatcher) {
            val calibrations = graph.calibrationRepository.state
            // A read error keeps the exclusions applied last (none before the first read) — it never
            // resets them to "measure everything".
            val settings = graph.settingsRepository.stored.catch { graph.monitor.processingFailures.incrementAndGet() }
            applyCalibration(graph, engine, calibrations.first())
            settings.firstOrNull()?.let { applySettings(graph, it) }
            val followers = listOf(
                launch { calibrations.collect { applyCalibration(graph, engine, it) } },
                launch { settings.collect { applySettings(graph, it) } },
            )
            try {
                pipeline.run()
            } finally {
                // The pipeline ended (unbind / reconnect / destroy): stop following this engine.
                followers.forEach { it.cancel() }
            }
        }
        syncUsage(graph, onlyIfStale = true)
    }

    private fun applyCalibration(graph: AppGraph, engine: ScrollMeasurementEngine, state: CalibrationState) {
        calibration = state
        try {
            engine.display = graph.readDisplayScale(state)
        } catch (e: Exception) {
            // Spec §61: keep measuring with the previous scale rather than stop the pipeline.
            graph.monitor.processingFailures.incrementAndGet()
        }
    }

    /** Spec §14: user-excluded apps become EXCLUDED from the next event on. */
    private fun applySettings(graph: AppGraph, settings: Settings) {
        graph.measurementSettings.excludedPackages = settings.excludedPackages
    }

    /** Off the pipeline: reading usage events is I/O. Without Usage access this is a no-op. */
    private fun syncUsage(graph: AppGraph, onlyIfStale: Boolean) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (onlyIfStale) graph.usageSyncer.syncIfStale() else graph.usageSyncer.sync()
            } catch (e: Exception) {
                // Spec §61: time in app stays as last synced; the next trigger retries.
                lifecycle("usage sync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pipeline = pipeline ?: return
        try {
            val sample = AccessibilityEventParser.parse(event ?: return, System.currentTimeMillis()) ?: return
            pipeline.offer(sample)
        } catch (e: Exception) {
            // Spec §61: ignore the event and count it; never let the service die.
            graph?.monitor?.processingFailures?.incrementAndGet()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation swaps width/height; the diagonal (outlier limit) and scale are re-read.
        val graph = graph ?: return
        engine?.display = graph.readDisplayScale(calibration)
    }

    override fun onInterrupt() {
        lifecycle("interrupt")
        val pipeline = pipeline ?: return
        serviceScope.launch(pipelineDispatcher) { pipeline.flush() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        lifecycle("unbind")
        stopPipeline()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        lifecycle("destroy")
        stopPipeline()
        // Let the pipeline process what is queued and write its final flush, then stop the rest.
        pipelineJob?.invokeOnCompletion { serviceScope.cancel() } ?: serviceScope.cancel()
        super.onDestroy()
    }

    /** Closes the channel: the pipeline drains it, writes a last flush and ends. */
    private fun stopPipeline() {
        pipeline?.close()
        pipeline = null
        graph?.monitor?.onServiceDisconnected()
    }

    /** Service lifecycle only — never event content. Debug builds; R8 drops it from release. */
    private fun lifecycle(what: String) {
        if (BuildConfig.DEBUG) {
            Log.d("ScrollMeter", "service $what id=${System.identityHashCode(this)} connected=${graph?.monitor?.serviceConnected?.value}")
        }
    }
}
