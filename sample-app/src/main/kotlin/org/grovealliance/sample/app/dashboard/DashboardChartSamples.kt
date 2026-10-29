//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.sample.app.dashboard

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.dashboard.DashboardColorPalette
import org.grovealliance.ui.dashboard.DashboardSection
import org.grovealliance.ui.dashboard.chart.AxisFormat
import org.grovealliance.ui.dashboard.chart.CalendarTimeAxisFormat
import org.grovealliance.ui.dashboard.chart.CartesianChart
import org.grovealliance.ui.dashboard.chart.CartesianChartBuilder
import org.grovealliance.ui.dashboard.chart.ChartCard
import org.grovealliance.ui.dashboard.chart.Highlight
import org.grovealliance.ui.dashboard.chart.HighlightFormat
import org.grovealliance.ui.dashboard.chart.MenuPicker
import org.grovealliance.ui.dashboard.chart.NumberAxisFormat
import org.grovealliance.ui.dashboard.chart.TimeCoordinates
import org.grovealliance.ui.dashboard.chart.cartesianChart
import org.grovealliance.ui.theme.ColorToken
import org.grovealliance.ui.theme.SizeToken
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Fixed, realistic chart fixtures built outside composition with the public DSL.
 */
@Suppress("MagicNumber") // Numeric values are deterministic demo data.
internal class DashboardChartSamples {
    private val today = LocalDate.of(2026, 10, 25)
    private val locale = Locale.getDefault()
    private val coordinates = TimeCoordinates(origin = today.minusDays(29).atStartOfDay(ZoneOffset.UTC).toInstant())
    private val rangeDays = listOf(7L, 14L, 30L)
    private val rangeLabels = rangeDays.map { StringResource("Last $it days") }
    private val labels = CartesianChart.AccessibilityLabels(
        nextPoint = StringResource("Next point"),
        previousPoint = StringResource("Previous point"),
    )

    fun sleepCard(picker: StateFlow<MenuPicker?>, chart: StateFlow<CartesianChart>): ChartCard = ChartCard(
        title = StringResource("Sleep"),
        picker = picker,
        chart = chart,
    )

    fun sleepPicker(selectedIndex: Int, onSelected: (Int) -> Unit): MenuPicker = MenuPicker(
        label = StringResource("Time range"),
        options = rangeLabels,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
    )

    fun sleepChart(selectedIndex: Int): CartesianChart {
        val days = rangeDays[selectedIndex]
        val firstDay = today.minusDays(days - 1)
        return chart(description = "Hours asleep over the last $days days") {
            xAxis(
                range = coordinates.range(start = instant(day = firstDay), end = instant(day = today.plusDays(1))),
                format = CalendarTimeAxisFormat(coordinates = coordinates, zone = ZoneOffset.UTC, locale = locale),
            )
            yAxis(
                range = CartesianChart.Range(start = 0.0, end = 10.0),
                format = NumberAxisFormat(locale = locale),
            )
            bar(color = DashboardColorPalette.blue, highlightFormat = SleepHighlightFormat(coordinates = coordinates, locale = locale)) {
                repeat(days.toInt()) { index ->
                    val day = firstDay.plusDays(index.toLong())
                    val hours = if (day == today) 8.75 else 7.1 + 1.1 * sin(day.dayOfMonth * 0.7)
                    point(point = coordinates.point(start = instant(day = day), end = instant(day = day.plusDays(1)), value = hours))
                }
            }
        }
    }

    fun sections(): List<DashboardSection> = listOf(
        DashboardSection(
            title = StringResource("Blood pressure"),
            content = pressureCard(),
            footnote = StringResource("Red: systolic · Blue: diastolic. Drag to compare both readings."),
        ),
        DashboardSection(
            title = StringResource("Heart rate"),
            content = heartRateCard(),
            footnote = StringResource("Sample measurements throughout one day. Touch a point to see its time and value."),
        ),
        DashboardSection(
            title = StringResource("Distance and elevation"),
            content = elevationCard(),
            footnote = StringResource("An afternoon walk, with distance in kilometres and elevation in metres."),
        ),
        DashboardSection(
            title = StringResource("Empty state"),
            content = card(
                title = StringResource("Blood oxygen"),
                chart = chart(description = "Blood oxygen measurements") {},
            ),
            footnote = StringResource("Your blood oxygen readings will appear here when measurements are available."),
        ),
    )

    private fun pressureCard(): ChartCard {
        val firstDay = today.minusDays(13)
        return card(
            title = StringResource("Blood pressure"),
            chart = chart(description = "Systolic and diastolic blood pressure over two weeks") {
                xAxis(
                    range = coordinates.range(start = instant(day = firstDay), end = instant(day = today.plusDays(1))),
                    format = CalendarTimeAxisFormat(coordinates = coordinates, zone = ZoneOffset.UTC, locale = locale),
                )
                yAxis(range = CartesianChart.Range(start = 0.0, end = 150.0), format = NumberAxisFormat(locale = locale))
                val seriesSpecs = listOf("Systolic" to DashboardColorPalette.red, "Diastolic" to DashboardColorPalette.blue)
                seriesSpecs.forEachIndexed { seriesIndex, (name, color) ->
                    line(
                        color = color,
                        highlightFormat = MeasurementHighlightFormat(
                            coordinates = coordinates,
                            locale = locale,
                            unit = "mmHg",
                            name = name,
                        ),
                    ) {
                        repeat(14) { index ->
                            val at = instant(day = firstDay.plusDays(index.toLong())).plusSeconds(12 * 60 * 60)
                            val value = if (seriesIndex == 0) 124 + 8 * sin(index * 0.8) else 78 + 5 * sin(index * 0.6)
                            point(point = coordinates.point(start = at, end = at, value = value))
                        }
                    }
                }
            },
        )
    }

    private fun heartRateCard(): ChartCard {
        val start = instant(day = today)
        return card(
            title = StringResource("Heart rate throughout the day"),
            chart = chart(description = "Heart rate measurements throughout one day") {
                xAxis(
                    range = coordinates.range(start = start, end = instant(day = today.plusDays(1))),
                    format = HourAxisFormat(coordinates = coordinates, start = start, locale = locale),
                )
                yAxis(range = CartesianChart.Range(start = 40.0, end = 120.0), format = NumberAxisFormat(locale = locale))
                points(
                    color = DashboardColorPalette.red,
                    highlightFormat = MeasurementHighlightFormat(coordinates = coordinates, locale = locale, unit = "bpm", name = null),
                ) {
                    repeat(48) { index ->
                        val at = start.plusSeconds(index * 30 * 60L)
                        point(point = coordinates.point(start = at, end = at, value = 72 + 15 * sin(index * 0.45) + 5 * sin(index * 1.7)))
                    }
                }
            },
        )
    }

    private fun elevationCard(): ChartCard = card(
        title = StringResource("Riverside walk"),
        chart = chart(description = "Elevation in metres over a five kilometre walk") {
            plotBackgroundColor(color = ColorToken.SurfaceContainerLow)
            gridColor(color = ColorToken.OutlineVariant)
            lineWidth(width = SizeToken.Border.Medium)
            pointRadius(radius = 3.dp)
            highlightBackgroundColor(color = ColorToken.SecondaryContainer)
            highlightValueColor(color = ColorToken.OnSecondaryContainer)
            highlightTitleColor(color = ColorToken.OnSecondaryContainer)
            xAxis(range = CartesianChart.Range(start = 0.0, end = 5.0), format = UnitAxisFormat(locale = locale, unit = "km"))
            yAxis(range = CartesianChart.Range(start = 100.0, end = 250.0), format = UnitAxisFormat(locale = locale, unit = "m"))
            line(color = DashboardColorPalette.green, highlightFormat = ElevationHighlightFormat(locale = locale)) {
                listOf(120.0, 142.0, 138.0, 178.0, 223.0, 205.0).forEachIndexed { index, height ->
                    point(x = index.toDouble(), y = height)
                }
            }
        },
    )

    private fun card(title: StringResource, chart: CartesianChart): ChartCard = ChartCard(
        title = title,
        picker = MutableStateFlow(null),
        chart = MutableStateFlow(chart),
    )

    private fun chart(description: String, block: CartesianChartBuilder.() -> Unit): CartesianChart = cartesianChart(
        emptyText = StringResource("No Data"),
        description = StringResource(description),
        accessibilityLabels = labels,
        block = block,
    )

    private fun instant(day: LocalDate): Instant = day.atStartOfDay(ZoneOffset.UTC).toInstant()
}

private data class SleepHighlightFormat(val coordinates: TimeCoordinates, val locale: Locale) : HighlightFormat {
    override fun label(point: CartesianChart.Point): Highlight {
        val minutes = (point.y * MINUTES_PER_HOUR).roundToInt()
        val dateFormat = DateTimeFormatter.ofPattern("d MMM", locale).withZone(ZoneOffset.UTC)
        return Highlight(
            value = StringResource("${minutes / MINUTES_PER_HOUR} hr ${minutes % MINUTES_PER_HOUR} min"),
            title = StringResource(dateFormat.format(coordinates.instant(point.x))),
        )
    }

    private companion object {
        /**
         * Converts numeric hours to the sample's hours-and-minutes label.
         */
        const val MINUTES_PER_HOUR = 60
    }
}

private data class MeasurementHighlightFormat(
    val coordinates: TimeCoordinates,
    val locale: Locale,
    val unit: String,
    val name: String?,
) : HighlightFormat {
    override fun label(point: CartesianChart.Point): Highlight {
        val value = NumberFormat.getIntegerInstance(locale).format(point.y)
        val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", locale).withZone(ZoneOffset.UTC)
        return Highlight(
            value = StringResource(listOfNotNull(name, "$value $unit").joinToString(separator = " ")),
            title = StringResource(dateFormat.format(coordinates.instant(point.x))),
        )
    }
}

private data class ElevationHighlightFormat(val locale: Locale) : HighlightFormat {
    override fun label(point: CartesianChart.Point): Highlight {
        val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
        return Highlight(
            value = StringResource("${format.format(point.y)} m elevation"),
            title = StringResource("${format.format(point.x)} km into the walk"),
        )
    }
}

private data class UnitAxisFormat(val locale: Locale, val unit: String) : AxisFormat {
    override fun ticks(
        range: CartesianChart.Range,
    ): List<AxisFormat.Tick> = NumberAxisFormat(locale = locale).ticks(range).map {
        it.copy(label = it.label + StringResource(" $unit"))
    }
}

private data class HourAxisFormat(val coordinates: TimeCoordinates, val start: Instant, val locale: Locale) : AxisFormat {
    override fun ticks(range: CartesianChart.Range): List<AxisFormat.Tick> {
        val format = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(ZoneOffset.UTC)
        return (0..HOURS_PER_DAY step TICK_HOURS).map { hour ->
            val at = start.plusSeconds(hour * SECONDS_PER_HOUR)
            AxisFormat.Tick(at = coordinates.coordinate(at), label = StringResource(format.format(at)))
        }
    }

    private companion object {
        /**
         * The hourly axis covers one day.
         */
        const val HOURS_PER_DAY = 24

        /**
         * The number of hours between labels.
         */
        const val TICK_HOURS = 6

        /**
         * Converts the hour tick positions to seconds.
         */
        const val SECONDS_PER_HOUR = 3_600L
    }
}
