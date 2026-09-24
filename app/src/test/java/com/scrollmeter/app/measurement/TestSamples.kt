package com.scrollmeter.app.measurement

import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.calibration.DisplaySnapshot

/** The test phone of CLAUDE.md: OnePlus CPH2399, 1080×2400, xdpi 403.411 / ydpi 401.052, densityDpi 480. */
object TestPhone {
    const val WIDTH_PX = 1080
    const val HEIGHT_PX = 2400
    const val XDPI = 403.411
    const val YDPI = 401.052
    const val DENSITY_DPI = 480
    const val MANUFACTURER = "OnePlus"
    const val MODEL = "CPH2399"

    val snapshot = DisplaySnapshot(WIDTH_PX, HEIGHT_PX, XDPI, YDPI, DENSITY_DPI, MANUFACTURER, MODEL)
    val geometry = DeviceGeometry(WIDTH_PX, HEIGHT_PX)
    val scale = PhysicalScaleProvider.fromDisplayMetrics(XDPI, YDPI, DENSITY_DPI)
    val display = ScrollMeasurementEngine.DisplayScale(geometry, scale)
}

fun uniformScale(mmPerPx: Double) =
    PhysicalScale(mmPerPx, mmPerPx, CalibrationMethod.DISPLAY_METRICS, CalibrationConfidence.MEDIUM)

const val OWN_PACKAGE = "com.scrollmeter.app.debug"
const val APP = "com.example.feed"

fun sample(
    dx: Int = 0,
    dy: Int = 0,
    packageName: String? = APP,
    uptimeMs: Long = 1_000L,
    windowId: Int = 7,
    className: String? = "androidx.recyclerview.widget.RecyclerView",
    scrollX: Int = 0,
    scrollY: Int = 0,
    maxScrollX: Int = 0,
    maxScrollY: Int = 0,
) = ScrollSample(
    uptimeMs = uptimeMs,
    wallTimeMs = 1_790_000_000_000L + uptimeMs,
    packageName = packageName,
    windowId = windowId,
    className = className,
    deltaX = dx,
    deltaY = dy,
    scrollX = scrollX,
    scrollY = scrollY,
    maxScrollX = maxScrollX,
    maxScrollY = maxScrollY,
)

/** An event whose app set no scroll delta: Android leaves both at UNDEFINED (-1). */
fun positionOnlySample(scrollX: Int = 0, scrollY: Int, uptimeMs: Long, windowId: Int = 7, packageName: String = APP) =
    sample(
        dx = MeasurementConfig.UNDEFINED_SCROLL_DELTA,
        dy = MeasurementConfig.UNDEFINED_SCROLL_DELTA,
        packageName = packageName,
        uptimeMs = uptimeMs,
        windowId = windowId,
        className = "android.webkit.WebView",
        scrollX = scrollX,
        scrollY = scrollY,
    )
