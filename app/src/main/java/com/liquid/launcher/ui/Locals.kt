package com.liquid.launcher.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import com.liquid.launcher.data.LauncherPrefs
import com.liquid.launcher.wallpaper.WallpaperImage

val LocalPrefs = staticCompositionLocalOf { LauncherPrefs() }
val LocalHome = staticCompositionLocalOf<HomeController> { error("no controller") }
val LocalBadges = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }
val LocalWallpaper = staticCompositionLocalOf<WallpaperImage?> { null }
/** Seconds since edit mode began – drives the iOS "jiggle". */
val LocalJiggleClock = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }


/**
 * Remembers where something was laid out without writing Compose state every frame.
 * (onGloballyPositioned + mutableState would fire on every frame of a page swipe for every icon.)
 */
class LayoutRef {
    var coords: LayoutCoordinates? = null
    fun rect(): Rect {
        val c = coords?.takeIf { it.isAttached } ?: return Rect.Zero
        return Rect(c.localToRoot(Offset.Zero), Size(c.size.width.toFloat(), c.size.height.toFloat()))
    }
    fun pos(): Offset = rect().topLeft
}

fun Modifier.track(ref: LayoutRef): Modifier = this.onPlaced { ref.coords = it }
