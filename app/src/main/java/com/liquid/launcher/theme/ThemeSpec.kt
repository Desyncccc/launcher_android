package com.liquid.launcher.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquid.launcher.R
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.GlassStyle
import com.liquid.launcher.glass.GlassVariant

enum class PageIndicatorStyle { SEARCH_PILL, DOTS, PILL_DOTS }
enum class SwipeAction { NONE, SPOTLIGHT, NOTIFICATIONS, DRAWER }
enum class FolderPanelStyle { IOS, ONEUI, HYPER }
enum class MenuStyle { IOS, ONEUI, HYPER }

@Immutable
data class ThemeSpec(
    val style: ThemeStyle,
    val columns: Int,
    val rows: Int,
    val dockSlots: Int,
    /** icon edge as a fraction of the screen width */
    val iconFraction: Float,
    val iconShape: GlassShape,
    val labelSize: TextUnit,
    val labelWeight: FontWeight,
    val labelShadow: Boolean,
    val fontFamily: FontFamily,
    val dockPlatter: Boolean,
    val dockShape: GlassShape,
    val dockGlass: GlassStyle,
    val indicator: PageIndicatorStyle,
    val pillGlass: GlassStyle,
    val folderTileGlass: GlassStyle,
    val folderPreview: Int,
    val folderShape: GlassShape,
    val folderPanel: FolderPanelStyle,
    val folderColumns: Int,
    val panelGlass: GlassStyle,
    val panelShape: GlassShape,
    val menuGlass: GlassStyle,
    val menuShape: GlassShape,
    val menuStyle: MenuStyle,
    val swipeDown: SwipeAction,
    val swipeUp: SwipeAction,
    val hasAppLibrary: Boolean,
    val hasTodayView: Boolean,
    val badgeColor: Color,
    val accent: Color,
    val widgetRadius: Dp,
    val defaultWallpaper: String,
    val iconGlass: GlassStyle,
    val drawerColumns: Int,
    val drawerPaged: Boolean,
    val topPadding: Dp,
)

object Fonts {
    /** Inter (SIL OFL) – the closest freely licensed face to SF Pro. */
    val inter = FontFamily(
        Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.inter, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )
}

object Themes {
    // Liquid Glass presets ---------------------------------------------------
    private val iosDock = GlassStyle(blur = 2.dp, refraction = 16.dp, bevel = 22.dp, dispersion = 0.14f, saturation = 1.5f, rim = 1f, shadowAlpha = 0.10f, shadowRadius = 20.dp)
    private val iosPill = GlassStyle(blur = 1.5.dp, refraction = 9.dp, bevel = 12.dp, dispersion = 0.12f, saturation = 1.55f, rim = 1.1f, shadowAlpha = 0.08f, shadowRadius = 10.dp, shadowOffsetY = 2.dp)
    private val iosFolderTile = GlassStyle(blur = 4.dp, refraction = 7.dp, bevel = 10.dp, dispersion = 0.10f, saturation = 1.6f, rim = 1.1f, shadowAlpha = 0.10f, shadowRadius = 8.dp, shadowOffsetY = 2.dp)
    private val iosPanel = GlassStyle(blur = 18.dp, refraction = 26.dp, bevel = 34.dp, dispersion = 0.10f, saturation = 1.7f, rim = 0.9f, frost = 0.04f, adaptive = false, shadowAlpha = 0.16f, shadowRadius = 40.dp, shadowOffsetY = 10.dp)
    private val iosMenu = GlassStyle(blur = 22.dp, refraction = 20.dp, bevel = 26.dp, dispersion = 0.08f, saturation = 1.8f, rim = 0.9f, frost = 0.30f, adaptive = false, shadowAlpha = 0.18f, shadowRadius = 36.dp, shadowOffsetY = 12.dp)
    private val iosIcon = GlassStyle(variant = GlassVariant.CLEAR, blur = 3.dp, refraction = 7.dp, bevel = 11.dp, dispersion = 0.12f, saturation = 1.35f, rim = 1.25f, rimWidth = 1.1.dp, shadowAlpha = 0.12f, shadowRadius = 8.dp, shadowOffsetY = 2.dp)

    // One UI 8: blur-driven "glass" (frosted), restrained lensing
    private val oneUiPanel = GlassStyle(blur = 28.dp, refraction = 4.dp, bevel = 10.dp, dispersion = 0f, saturation = 1.3f, rim = 0.35f, frost = 0.30f, adaptive = false, shadowAlpha = 0.14f, shadowRadius = 30.dp)
    private val oneUiTile = GlassStyle(blur = 14.dp, refraction = 3.dp, bevel = 8.dp, dispersion = 0f, saturation = 1.3f, rim = 0.45f, frost = 0.25f, shadowAlpha = 0.08f, shadowRadius = 8.dp)
    private val oneUiDock = GlassStyle(blur = 20.dp, refraction = 4.dp, bevel = 8.dp, dispersion = 0f, rim = 0.3f, frost = 0.15f, shadowAlpha = 0f)

    // HyperOS 3: frosted with light refraction
    private val hyperPanel = GlassStyle(blur = 24.dp, refraction = 10.dp, bevel = 18.dp, dispersion = 0.04f, saturation = 1.4f, rim = 0.6f, frost = 0.18f, adaptive = false, shadowAlpha = 0.14f, shadowRadius = 30.dp)
    private val hyperTile = GlassStyle(blur = 10.dp, refraction = 5.dp, bevel = 9.dp, dispersion = 0.05f, saturation = 1.4f, rim = 0.7f, frost = 0.14f, shadowAlpha = 0.08f, shadowRadius = 8.dp)

    val IOS = ThemeSpec(
        style = ThemeStyle.IOS,
        columns = 4, rows = 6, dockSlots = 4,
        iconFraction = 0.153f,
        iconShape = GlassShape.icon(0.28f, 2.4f),
        labelSize = 11.5.sp, labelWeight = FontWeight.Medium, labelShadow = false,
        fontFamily = Fonts.inter,
        dockPlatter = true,
        dockShape = GlassShape.rounded(38.dp, 2.4f),
        dockGlass = iosDock,
        indicator = PageIndicatorStyle.SEARCH_PILL,
        pillGlass = iosPill,
        folderTileGlass = iosFolderTile,
        folderPreview = 3,
        folderShape = GlassShape.icon(0.28f, 2.4f),
        folderPanel = FolderPanelStyle.IOS,
        folderColumns = 3,
        panelGlass = iosPanel,
        panelShape = GlassShape.rounded(40.dp, 2.4f),
        menuGlass = iosMenu,
        menuShape = GlassShape.rounded(26.dp, 2.4f),
        menuStyle = MenuStyle.IOS,
        swipeDown = SwipeAction.SPOTLIGHT,
        swipeUp = SwipeAction.NONE,
        hasAppLibrary = true,
        hasTodayView = true,
        badgeColor = Color(0xFFFF3B30),
        accent = Color(0xFF0A84FF),
        widgetRadius = 26.dp,
        defaultWallpaper = "tahoe",
        iconGlass = iosIcon,
        drawerColumns = 4, drawerPaged = false,
        topPadding = 18.dp,
    )

    val ONEUI = ThemeSpec(
        style = ThemeStyle.ONEUI,
        columns = 4, rows = 5, dockSlots = 5,
        iconFraction = 0.158f,
        iconShape = GlassShape.icon(0.36f, 2.5f),
        labelSize = 12.sp, labelWeight = FontWeight.Normal, labelShadow = true,
        fontFamily = FontFamily.Default,
        dockPlatter = false,
        dockShape = GlassShape.rounded(30.dp, 2.4f),
        dockGlass = oneUiDock,
        indicator = PageIndicatorStyle.DOTS,
        pillGlass = oneUiTile,
        folderTileGlass = oneUiTile,
        folderPreview = 2,
        folderShape = GlassShape.icon(0.36f, 2.5f),
        folderPanel = FolderPanelStyle.ONEUI,
        folderColumns = 4,
        panelGlass = oneUiPanel,
        panelShape = GlassShape.rounded(30.dp, 2.3f),
        menuGlass = oneUiPanel,
        menuShape = GlassShape.rounded(26.dp, 2.3f),
        menuStyle = MenuStyle.ONEUI,
        swipeDown = SwipeAction.NOTIFICATIONS,
        swipeUp = SwipeAction.DRAWER,
        hasAppLibrary = false,
        hasTodayView = false,
        badgeColor = Color(0xFFFF6E40),
        accent = Color(0xFF3E91FF),
        widgetRadius = 26.dp,
        defaultWallpaper = "bloom",
        iconGlass = iosIcon.copy(refraction = 4.dp, dispersion = 0f, rim = 0.6f),
        drawerColumns = 5, drawerPaged = true,
        topPadding = 30.dp,
    )

    val HYPEROS = ThemeSpec(
        style = ThemeStyle.HYPEROS,
        columns = 4, rows = 6, dockSlots = 4,
        iconFraction = 0.160f,
        iconShape = GlassShape.icon(0.30f, 2.6f),
        labelSize = 11.5.sp, labelWeight = FontWeight.Normal, labelShadow = true,
        fontFamily = FontFamily.Default,
        dockPlatter = false,
        dockShape = GlassShape.rounded(32.dp, 2.4f),
        dockGlass = hyperTile,
        indicator = PageIndicatorStyle.PILL_DOTS,
        pillGlass = hyperTile,
        folderTileGlass = hyperTile,
        folderPreview = 3,
        folderShape = GlassShape.icon(0.30f, 2.6f),
        folderPanel = FolderPanelStyle.HYPER,
        folderColumns = 3,
        panelGlass = hyperPanel,
        panelShape = GlassShape.rounded(36.dp, 2.4f),
        menuGlass = hyperPanel,
        menuShape = GlassShape.rounded(24.dp, 2.4f),
        menuStyle = MenuStyle.HYPER,
        swipeDown = SwipeAction.NOTIFICATIONS,
        swipeUp = SwipeAction.DRAWER,
        hasAppLibrary = false,
        hasTodayView = false,
        badgeColor = Color(0xFFFF3B30),
        accent = Color(0xFF3482FF),
        widgetRadius = 24.dp,
        defaultWallpaper = "aurora",
        iconGlass = iosIcon.copy(refraction = 5.dp, dispersion = 0.05f, rim = 0.8f),
        drawerColumns = 4, drawerPaged = false,
        topPadding = 24.dp,
    )

    fun of(style: ThemeStyle) = when (style) {
        ThemeStyle.IOS -> IOS
        ThemeStyle.ONEUI -> ONEUI
        ThemeStyle.HYPEROS -> HYPEROS
    }
}

val LocalTheme = staticCompositionLocalOf { Themes.IOS }
