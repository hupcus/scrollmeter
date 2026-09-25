package com.scrollmeter.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.insights.ChartScale
import kotlin.math.ceil
import kotlin.math.max

/**
 * A plain bar chart (D13, ADR-012, spec §23): y grid lines from [scale] labelled by [axisLabel],
 * one bar per value, x labels from [barLabel] thinned out so they never overlap — counted from the
 * newest bar, which is always labelled. Tapping a bar selects it ([onSelect] null = deselect); the
 * caller shows the selected value. TalkBack reads [contentDescription] instead of the bars.
 * [barColor] colours each bar on its own (the daily limit, ADR-036); [referenceLine] draws a dashed
 * line at that value, in [referenceColor], when it lies inside the scale.
 */
@Composable
fun BarChart(
    values: List<Double>,
    scale: ChartScale,
    axisLabel: (Double) -> String,
    barLabel: (Int) -> String,
    contentDescription: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    barColor: ((Int) -> Color)? = null,
    referenceLine: Double? = null,
    referenceColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val measurer = rememberTextMeasurer()
    // The tap handler outlives recompositions; it must see the current selection and callback.
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bar = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.toPx() }
    val axisLabels = remember(scale, axisLabel, style) { scale.gridLines.map { measurer.measure(axisLabel(it), style) } }
    val xLabels = remember(values.size, barLabel, style) { List(values.size) { measurer.measure(barLabel(it), style) } }
    val axisWidth = (axisLabels.maxOfOrNull { it.size.width } ?: 0) + gapPx
    val xLabelHeight = xLabels.maxOfOrNull { it.size.height } ?: 0
    val topPad = (axisLabels.firstOrNull()?.size?.height ?: 0) / 2f

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(values.size, axisWidth) {
                detectTapGestures { offset ->
                    val slot = (size.width - axisWidth) / values.size.coerceAtLeast(1)
                    val index = ((offset.x - axisWidth) / slot).toInt()
                    currentOnSelect(if (offset.x < axisWidth || index !in values.indices || index == currentSelected) null else index)
                }
            },
    ) {
        val plotLeft = axisWidth
        val plotTop = topPad
        val plotBottom = size.height - xLabelHeight - gapPx / 2
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val stroke = 1.dp.toPx()

        scale.gridLines.forEachIndexed { i, line ->
            val y = plotBottom - scale.fraction(line) * plotHeight
            drawLine(grid, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = stroke)
            val label = axisLabels[i]
            drawText(label, topLeft = Offset(plotLeft - gapPx - label.size.width, y - label.size.height / 2f))
        }

        if (values.isEmpty()) return@Canvas
        val slot = (size.width - plotLeft) / values.size
        val barWidth = max(slot * 0.6f, 2.dp.toPx())
        val radius = CornerRadius(minOf(barWidth / 2, 4.dp.toPx()))
        values.forEachIndexed { i, value ->
            val h = scale.fraction(value) * plotHeight
            if (h <= 0f) return@forEachIndexed
            val x = plotLeft + i * slot + (slot - barWidth) / 2
            val own = barColor?.invoke(i) ?: bar
            drawRoundRect(
                color = if (selected == null || selected == i) own else own.copy(alpha = 0.35f),
                topLeft = Offset(x, plotBottom - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }

        if (referenceLine != null && referenceLine > 0 && referenceLine <= scale.max) {
            val y = plotBottom - scale.fraction(referenceLine) * plotHeight
            val dash = 6.dp.toPx()
            drawLine(
                referenceColor, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash / 1.5f)),
            )
        }

        val widest = xLabels.maxOfOrNull { it.size.width } ?: 0
        val every = ceil((widest + gapPx) / slot).toInt().coerceAtLeast(1)
        xLabels.forEachIndexed { i, label ->
            if ((values.lastIndex - i) % every != 0 || label.size.width == 0) return@forEachIndexed
            val center = plotLeft + i * slot + slot / 2
            // max(): on a plot narrower than the label (split screen) the range must not invert.
            val x = (center - label.size.width / 2f).coerceIn(plotLeft, max(plotLeft, size.width - label.size.width))
            drawText(label, topLeft = Offset(x, plotBottom + gapPx / 2))
        }
    }
}
