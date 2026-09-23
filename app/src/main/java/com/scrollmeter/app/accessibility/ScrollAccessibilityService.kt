package com.scrollmeter.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.BuildConfig
import com.scrollmeter.app.ScrollMeterApplication
import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.ScrollMeasurementEngine
import com.scrollmeter.app.measurement.ScrollSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Do not expand requested accessibility capabilities without a
 * documented product need and privacy/policy review.
 *
 * Receives only `TYPE_VIEW_SCROLLED` (res/xml/accessibility_service_config.xml) with
 * `canRetrieveWindowContent="false"`: it cannot read screen content and does not try.
 *
 * Pipeline (spec §62, §63, D5): the callback parses primitives and hands them to a bounded
 * channel, then returns. One consumer coroutine on Dispatchers.Default runs the engine and
 * the sinks. No Room, no I/O, no PackageManager on the callback path.
 */
class ScrollAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var graph: AppGraph? = null
    private var engine: ScrollMeasurementEngine? = null
    private var samples: Channel<ScrollSample>? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val graph = (application as ScrollMeterApplication).graph
        val engine = graph.newEngine()
        val channel = Channel<ScrollSample>(
            capacity = MeasurementConfig.SAMPLE_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { graph.monitor.droppedSamples.incrementAndGet() },
        )
        this.graph = graph
        this.engine = engine
        this.samples = channel
        graph.monitor.onServiceConnected(System.currentTimeMillis())
        lifecycle("connected")

        serviceScope.launch {
            for (sample in channel) {
                try {
                    val result = engine.process(sample)
                    graph.monitor.record(result)
                    graph.measurementSinks.forEach { it.onResult(result) }
                } catch (e: Exception) {
                    // Spec §61: one bad sample must not stop the consumer.
                    graph.monitor.processingFailures.incrementAndGet()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val channel = samples ?: return
        try {
            val sample = AccessibilityEventParser.parse(event ?: return, System.currentTimeMillis()) ?: return
            channel.trySend(sample)
        } catch (e: Exception) {
            // Spec §61: ignore the event and count it; never let the service die.
            graph?.monitor?.processingFailures?.incrementAndGet()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation swaps width/height; the diagonal (outlier limit) and scale are re-read.
        val graph = graph ?: return
        engine?.display = graph.readDisplayScale()
    }

    override fun onInterrupt() {
        // Nothing to flush yet: Phase 1 keeps totals in RAM only.
        lifecycle("interrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        lifecycle("unbind")
        stopPipeline()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        lifecycle("destroy")
        stopPipeline()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopPipeline() {
        samples?.close()
        samples = null
        graph?.monitor?.onServiceDisconnected()
    }

    /** Service lifecycle only — never event content. Debug builds; R8 drops it from release. */
    private fun lifecycle(what: String) {
        if (BuildConfig.DEBUG) {
            Log.d("ScrollMeter", "service $what id=${System.identityHashCode(this)} connected=${graph?.monitor?.serviceConnected?.value}")
        }
    }
}
