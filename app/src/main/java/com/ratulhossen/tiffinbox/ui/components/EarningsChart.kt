package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

private val DayLabelsKey = ExtraStore.Key<List<String>>()

/** Simple daily-earnings bar chart. [dailyTotals] is (day label, total ৳) pairs, oldest first. */
@Composable
fun EarningsChart(dailyTotals: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(dailyTotals) {
        if (dailyTotals.isNotEmpty()) {
            modelProducer.runTransaction {
                columnModel { series(dailyTotals.map { it.second }) }
                extras { it[DayLabelsKey] = dailyTotals.map { it.first } }
            }
        }
    }

    val bottomAxisFormatter = remember {
        CartesianValueFormatter { context, value, _ ->
            context.model.extraStore[DayLabelsKey].getOrNull(value.toInt()).orEmpty()
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter)
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    )
}
