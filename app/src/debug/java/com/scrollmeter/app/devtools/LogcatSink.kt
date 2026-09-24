package com.scrollmeter.app.devtools

import android.util.Log
import com.scrollmeter.app.measurement.MeasurementResult
import com.scrollmeter.app.measurement.MeasurementSink
import java.util.Locale

const val LOG_TAG = "ScrollMeter"

/**
 * One logcat line per event, debug builds only: `adb logcat -s ScrollMeter:D`. Numbers and
 * identifiers only (package, window id, view class) — the same fields as the debug CSV, plus the
 * calibration version the distance was computed under (`cv`).
 */
class LogcatSink : MeasurementSink {
    override fun onResult(result: MeasurementResult) {
        val s = result.sample
        Log.d(
            LOG_TAG,
            String.format(
                Locale.ROOT,
                "ev up=%d pkg=%s win=%d cls=%s dx=%d dy=%d sx=%d sy=%d msx=%d msy=%d used=%d,%d mm=%.3f src=%s ok=%d cv=%d",
                s.uptimeMs, s.packageName, s.windowId, s.className, s.deltaX, s.deltaY,
                s.scrollX, s.scrollY, s.maxScrollX, s.maxScrollY, result.dxPx, result.dyPx,
                result.distance.totalMm, result.source.name, if (result.accepted) 1 else 0, result.calibrationVersion,
            ),
        )
    }
}
