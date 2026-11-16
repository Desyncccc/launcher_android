package com.liquid.launcher.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.liquid.launcher.data.LauncherPrefs
import com.liquid.launcher.wallpaper.WallpaperImage

val LocalPrefs = staticCompositionLocalOf { LauncherPrefs() }
val LocalHome = staticCompositionLocalOf<HomeController> { error("no controller") }
val LocalBadges = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }
val LocalWallpaper = staticCompositionLocalOf<WallpaperImage?> { null }
/** Seconds since edit mode began – drives the iOS "jiggle". */
val LocalJiggleClock = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }
