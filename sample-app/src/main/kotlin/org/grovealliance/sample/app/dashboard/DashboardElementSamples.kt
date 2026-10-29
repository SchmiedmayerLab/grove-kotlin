//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.sample.app.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.ImageResource
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.dashboard.ColorBandList
import org.grovealliance.ui.dashboard.CounterCard
import org.grovealliance.ui.dashboard.DashboardColorPalette
import org.grovealliance.ui.dashboard.DashboardSection
import org.grovealliance.ui.dashboard.Gauge
import org.grovealliance.ui.dashboard.GaugeCard
import org.grovealliance.ui.dashboard.GaugeHeader
import org.grovealliance.ui.dashboard.GaugeTile
import org.grovealliance.ui.dashboard.GaugeTileGrid
import org.grovealliance.ui.dashboard.IconTextCard
import org.grovealliance.ui.dashboard.IconTextCardRow
import org.grovealliance.ui.dashboard.LabeledValue
import org.grovealliance.ui.dashboard.ProgressBadge
import org.grovealliance.ui.dashboard.ProgressRing
import org.grovealliance.ui.dashboard.ProgressRingCard
import org.grovealliance.ui.dashboard.ProgressRingRow
import org.grovealliance.ui.dashboard.ProgressRingRowList
import org.grovealliance.ui.dashboard.StatTile
import org.grovealliance.ui.dashboard.StatTileGrid
import org.grovealliance.ui.dashboard.TextBadge
import org.grovealliance.ui.theme.ColorToken
import org.grovealliance.ui.theme.resolve

/**
 * Representative score, activity and achievement components with deterministic sample values.
 */
@Suppress("MagicNumber") // Numeric values are realistic demo fixtures.
internal class DashboardElementSamples {
    private val streak = ringRow(
        title = "A week of consistency",
        subtitle = "Completed all seven daily check-ins",
        progress = 1f,
        color = DashboardColorPalette.green,
        icon = Icons.Default.Star,
        badge = "Complete",
    )
    private val walking = ringRow(
        title = "Walk 50 kilometres",
        subtitle = "39 of 50 km this month",
        progress = 0.78f,
        color = DashboardColorPalette.orange,
        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        badge = "78%",
    )
    private val activeDays = ringRow(
        title = "Twenty active days",
        subtitle = "14 of 20 days recorded",
        progress = 0.7f,
        color = DashboardColorPalette.blue,
        icon = Icons.Default.EmojiEvents,
        badge = null,
    )

    fun sections(onClick: (StringResource) -> Unit): List<DashboardSection> = listOf(
        section(title = "Overall score", content = overallScore(), footnote = "Sample heart health score, from 0 to 100."),
        section(title = "Most recent value", content = recentValue(), footnote = null),
        section(title = "Score tiles", content = scoreTiles(onClick = onClick), footnote = "Tap a score tile for its sample summary."),
        section(title = "Score ranges", content = scoreRanges(), footnote = "The current sample score is 86, in the excellent range."),
        section(title = "Daily activity", content = activity(), footnote = "Today's sample activity totals."),
        section(title = "Participation", content = participation(onClick = onClick), footnote = "Tap the card to see the next milestone."),
        section(title = "Achievements", content = ProgressRingCard(rows = listOf(streak, walking, activeDays)), footnote = null),
        section(title = "Highlights", content = facts(), footnote = "All values on this screen are demonstration data."),
    )

    private fun overallScore(): GaugeCard = GaugeCard(
        gauge = Gauge(
            progress = 0.86f,
            colors = DashboardColorPalette.redToGreen,
            lineWidth = Gauge.defaultLineWidth * 2,
            size = Gauge.Size.Large,
            label = Gauge.Label(
                value = StringResource("86"),
                caption = Gauge.Caption.Range(minimum = StringResource("0"), maximum = StringResource("100")),
            ),
        ),
    )

    private fun recentValue(): GaugeHeader = GaugeHeader(
        label = StringResource("Most recent night"),
        value = GaugeHeader.Value.Measured(
            parts = listOf(
                GaugeHeader.Part(text = StringResource("8"), unit = StringResource("hr")),
                GaugeHeader.Part(text = StringResource("45"), unit = StringResource("min")),
            ),
        ),
        footnote = StringResource("25 October 2026"),
        gauge = gauge(progress = 0.92f, value = "92", caption = "Sleep score"),
    )

    private fun scoreTiles(onClick: (StringResource) -> Unit): GaugeTileGrid = GaugeTileGrid(
        tiles = listOf(
            scoreTile(title = "Exercise", progress = 0.87f, value = "87", caption = "52 min today", onClick = onClick),
            scoreTile(title = "Sleep", progress = 0.92f, value = "92", caption = "8 hr 45 min", onClick = onClick),
            scoreTile(title = "Blood pressure", progress = 0.76f, value = "76", caption = "124 / 78 mmHg", onClick = onClick),
            scoreTile(title = "Blood oxygen", progress = null, value = "-", caption = "No measurements", onClick = onClick),
        ),
    )

    private fun scoreTile(
        title: String,
        progress: Float?,
        value: String,
        caption: String,
        onClick: (StringResource) -> Unit,
    ): GaugeTile = GaugeTile(
        title = StringResource(title),
        gauge = gauge(progress = progress, value = value, caption = caption),
        onClick = { onClick(StringResource("$title: $caption")) },
    )

    private fun gauge(progress: Float?, value: String, caption: String): Gauge = Gauge(
        progress = progress,
        colors = if (progress == null) listOf(ColorToken.Outline) else DashboardColorPalette.redToGreen,
        lineWidth = Gauge.defaultLineWidth,
        size = Gauge.Size.Regular,
        label = Gauge.Label(value = StringResource(value), caption = Gauge.Caption.Description(text = StringResource(caption))),
    )

    private fun scoreRanges(): ColorBandList = ColorBandList(
        bands = listOf(
            Triple("Excellent", "80-100", DashboardColorPalette.green),
            Triple("Good", "60-79", DashboardColorPalette.yellow),
            Triple("Fair", "40-59", DashboardColorPalette.orange),
            Triple("Needs attention", "0-39", DashboardColorPalette.red),
        ).mapIndexed { index, (label, range, color) ->
            ColorBandList.Band(
                leadingText = StringResource(label),
                trailingText = StringResource(range),
                fillColors = listOf(color),
                isHighlighted = index == 0,
            )
        },
    )

    private fun activity(): StatTileGrid = StatTileGrid(
        tiles = listOf(
            stat(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                title = "Steps",
                value = "8,420",
                unit = null,
                footnote = "+12% this week",
            ),
            stat(
                icon = Icons.Default.Timer,
                title = "Exercise",
                value = "52",
                unit = "min",
                footnote = "Daily goal: 60 min",
            ),
            stat(
                icon = Icons.Default.Route,
                title = "Distance",
                value = "5.8",
                unit = "km",
                footnote = "Two walks today",
            ),
            stat(
                icon = Icons.Default.LocalFireDepartment,
                title = "Active energy",
                value = "410",
                unit = "kcal",
                footnote = "Most active at 16:00",
            ),
        ),
    )

    private fun stat(icon: ImageVector, title: String, value: String, unit: String?, footnote: String): StatTile = StatTile(
        icon = image(vector = icon, color = DashboardColorPalette.orange),
        title = StringResource(title),
        value = StatTile.Value(text = StringResource(value), unit = unit?.let { StringResource(it) }),
        footnote = StringResource(footnote),
    )

    private fun participation(onClick: (StringResource) -> Unit): CounterCard = CounterCard(
        value = StringResource("6"),
        unit = StringResource("days"),
        badge = ProgressBadge(
            icon = image(vector = Icons.Default.MilitaryTech, color = ColorToken.OnSurface),
            label = StringResource("5 / 20"),
            progress = 0.25f,
            progressColor = DashboardColorPalette.red,
        ),
        detail = LabeledValue(label = StringResource("Enrolled since"), value = StringResource("20 October 2026")),
        trailingRow = ringRow(
            title = "Paper anniversary",
            subtitle = "One week of enrollment",
            progress = 0f,
            color = DashboardColorPalette.green,
            icon = Icons.Default.CalendarMonth,
            badge = null,
        ),
        list = ProgressRingRowList(
            label = StringResource("Upcoming achievements"),
            rows = listOf(walking.copy(trailingBadge = null), activeDays),
        ),
        onClick = { onClick(StringResource("Next milestone: one week of enrollment.")) },
    )

    private fun ringRow(
        title: String,
        subtitle: String,
        progress: Float,
        color: ColorToken,
        icon: ImageVector,
        badge: String?,
    ): ProgressRingRow = ProgressRingRow(
        ring = ProgressRing(progress = progress, progressColor = color, icon = image(vector = icon, color = color)),
        title = StringResource(title),
        subtitle = StringResource(subtitle),
        trailingBadge = badge?.let { TextBadge(text = StringResource(it), color = color) },
    )

    private fun facts(): IconTextCard = IconTextCard(
        rows = listOf(
            IconTextCardRow(
                icon = image(vector = Icons.AutoMirrored.Filled.DirectionsWalk, color = DashboardColorPalette.orange),
                text = StringResource("You've walked 39 kilometres this month - almost the length of a marathon."),
            ),
            IconTextCardRow(
                icon = image(vector = Icons.Default.Hotel, color = DashboardColorPalette.blue),
                text = StringResource("Your average sleep duration increased by 35 minutes compared with last week."),
            ),
            IconTextCardRow(
                icon = image(vector = Icons.Default.Favorite, color = DashboardColorPalette.red),
                text = StringResource("You met your activity goal on five of the last seven days."),
            ),
        ),
    )

    private fun section(title: String, content: ComposableContent, footnote: String?): DashboardSection = DashboardSection(
        title = StringResource(title),
        content = content,
        footnote = footnote?.let { StringResource(it) },
    )

    private fun image(vector: ImageVector, color: ColorToken): ImageResource = ImageResource(
        image = vector,
        tint = { color.resolve() },
    )
}
