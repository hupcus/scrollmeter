package com.scrollmeter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.R
import com.scrollmeter.app.insights.LimitLevel
import com.scrollmeter.app.insights.LimitStatus
import com.scrollmeter.app.ui.theme.limitColors

/**
 * A distance held against the daily limit (ADR-036). The background is the limit level — green,
 * orange, red, the neutral surface without a limit — and [lines] say the same in words, so colour is
 * never the only signal. With [onClick] the whole card is one button, marked by a chevron.
 */
@Composable
fun LimitCard(title: String?, value: String, level: LimitLevel, lines: List<String>, onClick: (() -> Unit)? = null) {
    val colors = limitColors(level)
    val cardColors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.onContainer)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            if (onClick != null) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = cardColors, content = content)
    } else {
        Card(Modifier.fillMaxWidth(), colors = cardColors, content = content)
    }
}

/** One day against the limit: "Zbývá 80 m z limitu 500 m" / "Překročeno o 120 m (limit 500 m)"; null without a limit. */
@Composable
fun limitSentence(status: LimitStatus, distance: (Double) -> String): String? = when (status.level) {
    LimitLevel.NONE -> null
    LimitLevel.OVER -> stringResource(R.string.limit_over, distance(status.overMm), distance(status.limitMm))
    LimitLevel.UNDER, LimitLevel.NEAR -> stringResource(R.string.limit_remaining, distance(status.remainingMm), distance(status.limitMm))
}

/** A week or month: "Průměr 420 m / den · limit 500 m", without a limit only the average; null when no day counts. */
@Composable
fun averageSentence(averageMm: Double?, limitMm: Double, distance: (Double) -> String): String? = when {
    averageMm == null -> null
    limitMm > 0.0 -> stringResource(R.string.limit_average_of, distance(averageMm), distance(limitMm))
    else -> stringResource(R.string.limit_average, distance(averageMm))
}
