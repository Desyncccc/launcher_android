package com.liquid.launcher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.liquid.launcher.theme.PageIndicatorStyle
import com.liquid.launcher.theme.ThemeSpec

@Immutable
data class GridMetrics(
    val width: Dp,
    val height: Dp,
    val cols: Int,
    val rows: Int,
    val icon: Dp,
    val cellW: Dp,
    val cellH: Dp,
    val gridLeft: Dp,
    val gridTop: Dp,
    val gridBottom: Dp,
    val iconTop: Dp,
    val dockLeft: Dp,
    val dockWidth: Dp,
    val dockTop: Dp,
    val dockHeight: Dp,
    val indicatorTop: Dp,
    val indicatorHeight: Dp,
    val statusTop: Dp,
    val navBottom: Dp,
    val labelGap: Dp,
) {
    fun cellRect(x: Int, y: Int, sx: Int = 1, sy: Int = 1, density: Float): Rect {
        val l = (gridLeft + cellW * x).value * density
        val t = (gridTop + cellH * y).value * density
        return Rect(l, t, l + (cellW * sx).value * density, t + (cellH * sy).value * density)
    }

    fun gridContains(p: Offset, density: Float): Boolean =
        p.y >= gridTop.value * density && p.y <= gridBottom.value * density

    fun dockContains(p: Offset, density: Float): Boolean =
        p.y >= (dockTop - 10.dp).value * density
}

fun computeMetrics(spec: ThemeSpec, width: Dp, height: Dp, statusTop: Dp, navBottom: Dp, large: Boolean, showLabels: Boolean): GridMetrics {
    val icon = width * spec.iconFraction * (if (large) 1.1f else 1f)
    val hPad = width * 0.034f
    val cellW = (width - hPad * 2) / spec.columns
    val dockPadV = if (spec.dockPlatter) 15.dp else 10.dp
    val dockHeight = icon + dockPadV * 2
    val dockBottomMargin = if (spec.dockPlatter) max(navBottom - 4.dp, 10.dp) else max(navBottom, 8.dp)
    val dockTop = height - dockBottomMargin - dockHeight
    val dockSide = if (spec.dockPlatter) width * 0.032f else hPad
    val indicatorHeight = when (spec.indicator) {
        PageIndicatorStyle.SEARCH_PILL -> 30.dp
        else -> 16.dp
    }
    val indicatorGap = if (spec.dockPlatter) 12.dp else 6.dp
    val indicatorTop = dockTop - indicatorGap - indicatorHeight
    val gridTop = statusTop + spec.topPadding
    val gridBottom = indicatorTop - 8.dp
    val cellH = (gridBottom - gridTop) / spec.rows
    val labelBlock = if (showLabels) 22.dp else 0.dp
    val iconTop = max(((cellH - icon - labelBlock) / 2), 0.dp)
    return GridMetrics(
        width = width, height = height,
        cols = spec.columns, rows = spec.rows,
        icon = min(icon, cellH - labelBlock),
        cellW = cellW, cellH = cellH,
        gridLeft = hPad, gridTop = gridTop, gridBottom = gridBottom,
        iconTop = iconTop,
        dockLeft = dockSide, dockWidth = width - dockSide * 2, dockTop = dockTop, dockHeight = dockHeight,
        indicatorTop = indicatorTop, indicatorHeight = indicatorHeight,
        statusTop = statusTop, navBottom = navBottom,
        labelGap = 5.dp,
    )
}
