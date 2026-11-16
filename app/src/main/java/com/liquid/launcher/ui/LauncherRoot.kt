package com.liquid.launcher.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.liquid.launcher.LauncherApp
import com.liquid.launcher.data.BadgeStore
import com.liquid.launcher.data.HomeLayout
import com.liquid.launcher.data.LauncherPrefs
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.glass.GlassEnvironment
import com.liquid.launcher.glass.LocalGlassEnvironment
import com.liquid.launcher.glass.LocalLightSource
import com.liquid.launcher.glass.rememberTiltLight
import com.liquid.launcher.theme.LocalTheme
import com.liquid.launcher.theme.ThemeSpec
import com.liquid.launcher.theme.Themes
import com.liquid.launcher.wallpaper.WallpaperImage
import com.liquid.launcher.wallpaper.WallpaperLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun specFor(prefs: LauncherPrefs): ThemeSpec {
    val base = Themes.of(prefs.theme)
    if (prefs.theme == ThemeStyle.IOS) return base
    return base.copy(
        columns = if (prefs.columns in 3..6) prefs.columns else base.columns,
        rows = if (prefs.rows in 4..7) prefs.rows else base.rows,
    )
}

@Composable
fun isDark(prefs: LauncherPrefs): Boolean {
    val sys = isSystemInDarkTheme()
    return when (prefs.darkMode) { 1 -> false; 2 -> true; else -> sys }
}

@Composable
fun LauncherRoot(app: LauncherApp, controller: HomeController) {
    val prefs by app.prefs.state.collectAsState()
    val spec = specFor(prefs)
    val apps by app.apps.apps.collectAsState()
    val layout by app.layout.layout.collectAsState()
    val badges by BadgeStore.counts.collectAsState()
    val dark = isDark(prefs)
    LaunchedEffect(apps, spec.columns, spec.rows, layout == null) {
        app.layout.reconcile(apps, spec.columns, spec.rows)
    }
    controller.spec = spec
    controller.layout = layout ?: HomeLayout(emptyList(), emptyList())
    controller.appsByKey = remember(apps) { apps.associateBy { it.key } }
    val light = rememberTiltLight(prefs.gyroLighting)
    val env = GlassEnvironment(dark = dark, tinted = prefs.glassTinted, reduceTransparency = prefs.reduceTransparency)
    val context = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val wallpaper by produceState<WallpaperImage?>(null, prefs.wallpaperId, prefs.wallpaperVersion, wPx, hPx) {
            value = withContext(Dispatchers.Default) { WallpaperLoader.load(context, prefs.wallpaperId, wPx, hPx) }
        }
        val lum: (Rect) -> Float = { r -> wallpaper?.luminanceOf(r, wPx.toFloat(), hPx.toFloat()) ?: 0.4f }
        controller.wallpaperBackdrop.luminance = lum
        controller.homeBackdrop.luminance = lum
        controller.screenBackdrop.luminance = lum

        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val metrics = remember(spec, maxWidth, maxHeight, statusTop, navBottom, prefs.largeIcons, prefs.showLabels) {
            computeMetrics(spec, maxWidth, maxHeight, statusTop, navBottom, prefs.largeIcons, prefs.showLabels)
        }
        controller.metrics = metrics
        controller.density = density.density

        val view = LocalView.current
        val topLum = wallpaper?.luminance?.average(0f, 0f, 1f, 0.05f) ?: 0f
        val overlay by remember {
            androidx.compose.runtime.derivedStateOf { controller.folder != null || controller.spotlight.value > 0.3f || controller.drawer.value > 0.3f }
        }
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = topLum > 0.62f && !overlay
                isAppearanceLightNavigationBars = false
            }
        }

        CompositionLocalProvider(
            LocalTheme provides spec,
            LocalPrefs provides prefs,
            LocalHome provides controller,
            LocalBadges provides (if (prefs.badges) badges else emptyMap()),
            LocalWallpaper provides wallpaper,
            LocalLightSource provides light,
            LocalGlassEnvironment provides env,
        ) {
            if (layout != null) HomeScreen(controller, metrics)
        }
    }
}
