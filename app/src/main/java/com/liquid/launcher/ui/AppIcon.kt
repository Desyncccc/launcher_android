package com.liquid.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquid.launcher.LauncherApp
import com.liquid.launcher.data.AppEntry
import com.liquid.launcher.data.AppearanceTone
import com.liquid.launcher.data.FolderItem
import com.liquid.launcher.data.IconAppearance
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.GlassStyle
import com.liquid.launcher.glass.GlassVariant
import com.liquid.launcher.glass.LocalGlassEnvironment
import com.liquid.launcher.glass.LocalLightSource
import com.liquid.launcher.glass.continuousPath
import com.liquid.launcher.glass.liquidGlass
import com.liquid.launcher.icons.IconBundle
import com.liquid.launcher.theme.LocalTheme
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun rememberIconBundle(entry: AppEntry?, size: Dp): IconBundle? {
    val spec = LocalTheme.current
    val prefs = LocalPrefs.current
    val px = with(LocalDensity.current) { (size.toPx() / 16f).roundToInt().coerceAtLeast(4) * 16 }.coerceAtMost(288)
    val provider = LauncherApp.instance.icons
    val shape = spec.iconShape
    val key = entry?.let { provider.cacheKey(it.key, shape.radiusFraction, shape.exponent, px, prefs.iconPack) }
    val state by produceState(initialValue = key?.let { provider.cached(it) }, key) {
        if (entry != null && value == null) value = provider.load(entry, shape.radiusFraction, shape.exponent, px, prefs.iconPack)
    }
    return state
}

private fun glyphMatrix(r: Float, g: Float, b: Float, alpha: Float): ColorFilter {
    // RGB → constant colour, alpha → mix of coverage and luminance (iOS "glass glyph")
    val m = floatArrayOf(
        0f, 0f, 0f, 0f, r * 255f,
        0f, 0f, 0f, 0f, g * 255f,
        0f, 0f, 0f, 0f, b * 255f,
        0.195f * alpha, 0.3835f * alpha, 0.0715f * alpha, 0.35f * alpha, 0f,
    )
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

fun brighten(color: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    hsv[1] = (hsv[1] * 1.1f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceAtLeast(0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun tintColor(): Color {
    val p = LocalPrefs.current
    return Color.hsv(p.tintHue, p.tintSaturation, 1f)
}

@Composable
fun darkTone(): Boolean {
    val p = LocalPrefs.current
    val env = LocalGlassEnvironment.current
    return when (p.tone) {
        AppearanceTone.AUTO -> env.dark
        AppearanceTone.LIGHT -> false
        AppearanceTone.DARK -> true
    }
}

/** Thin specular edge that every iOS 26 icon carries; follows the gyro light. */
@Composable
fun BoxScope.SpecularRim(shape: GlassShape, strength: Float = 1f) {
    val light = LocalLightSource.current
    Canvas(Modifier.matchParentSize()) {
        val r = shape.radiusPx(size, this)
        val path = continuousPath(size.width, size.height, r, shape.effectiveExponent)
        val dir = light.direction
        val ext = size.maxDimension * 0.5f
        val c = center
        val w = 1.1.dp.toPx() * 2f
        clipPath(path) {
            drawPath(
                path,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.62f * strength),
                    0.32f to Color.White.copy(alpha = 0.10f * strength),
                    0.68f to Color.White.copy(alpha = 0.06f * strength),
                    1f to Color.White.copy(alpha = 0.40f * strength),
                    start = c + Offset(dir.x * ext, dir.y * ext), end = c - Offset(dir.x * ext, dir.y * ext),
                ),
                style = Stroke(w),
            )
        }
    }
}

/** The icon artwork in the selected Home Screen appearance (Default / Dark / Clear / Tinted). */
@Composable
fun IconArt(bundle: IconBundle?, size: Dp, modifier: Modifier = Modifier, appearanceOverride: IconAppearance? = null, toneOverride: Boolean? = null) {
    val spec = LocalTheme.current
    val prefs = LocalPrefs.current
    val home = LocalHome.current
    val shape = spec.iconShape
    val appearance = appearanceOverride ?: prefs.appearance
    val dark = toneOverride ?: darkTone()
    val tint = tintColor()
    Box(modifier.size(size)) {
        if (bundle == null) {
            Box(Modifier.matchParentSize().clip(shape).background(Color.White.copy(alpha = 0.18f)))
            return@Box
        }
        if (bundle.fromPack || appearance == IconAppearance.DEFAULT) {
            Image(bundle.full, null, Modifier.matchParentSize())
            if (spec.style == ThemeStyle.IOS) SpecularRim(shape, 0.9f)
            return@Box
        }
        when (appearance) {
            IconAppearance.DARK -> {
                Box(
                    Modifier.matchParentSize().clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3D), Color(0xFF141416))))
                )
                val glyph = bundle.mono ?: bundle.fg
                val filter = when {
                    bundle.mono != null -> ColorFilter.tint(brighten(bundle.background), BlendMode.SrcIn)
                    bundle.glyphIsLight -> ColorFilter.tint(brighten(bundle.background), BlendMode.SrcIn)
                    else -> null
                }
                Image(glyph, null, Modifier.matchParentSize(), colorFilter = filter)
                SpecularRim(shape, 0.75f)
            }
            IconAppearance.CLEAR, IconAppearance.TINTED -> {
                val glass: GlassStyle = when {
                    appearance == IconAppearance.TINTED && !dark -> spec.iconGlass.copy(tintColor = tint, tintStrength = 0.55f)
                    appearance == IconAppearance.TINTED -> spec.iconGlass.copy(tintColor = Color(0xFF0B0B0F), tintStrength = 0.58f)
                    dark -> spec.iconGlass.copy(frost = 0.32f)
                    else -> spec.iconGlass.copy(variant = GlassVariant.REGULAR, tintColor = Color.White, tintStrength = 0.30f)
                }
                val glyphColor = if (appearance == IconAppearance.TINTED && dark) tint else Color.White
                val glyph = bundle.mono ?: if (bundle.layered) bundle.fg else bundle.full
                val filter = if (bundle.mono != null) ColorFilter.tint(glyphColor.copy(alpha = 0.95f), BlendMode.SrcIn)
                else glyphMatrix(glyphColor.red, glyphColor.green, glyphColor.blue, 1f)
                Box(Modifier.matchParentSize().liquidGlass(home.wallpaperBackdrop, shape, glass)) {
                    Image(glyph, null, Modifier.matchParentSize(), colorFilter = filter)
                }
            }
            else -> {}
        }
    }
}

@Composable
fun IconLabel(text: String, width: Dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    val spec = LocalTheme.current
    val wallpaperBright = (LocalWallpaper.current?.luminance?.average(0f, 0.1f, 1f, 0.8f) ?: 0.3f) > 0.66f
    val shadow = if (spec.labelShadow || wallpaperBright) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f) else null
    BasicText(
        text,
        modifier.width(width).padding(horizontal = 2.dp),
        style = TextStyle(
            color = color, fontSize = spec.labelSize, fontWeight = spec.labelWeight,
            fontFamily = spec.fontFamily, textAlign = TextAlign.Center, shadow = shadow,
            letterSpacing = if (spec.style == ThemeStyle.IOS) (-0.1).sp else 0.sp,
        ),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun BoxScope.NotificationBadge(count: Int, iconSize: Dp) {
    if (count <= 0) return
    val spec = LocalTheme.current
    val h = when (spec.style) { ThemeStyle.IOS -> 21.dp; ThemeStyle.ONEUI -> 17.dp; ThemeStyle.HYPEROS -> 18.dp }
    val text = if (count > 99) "99+" else count.toString()
    Box(
        Modifier.align(Alignment.TopEnd)
            .offset(x = h * 0.38f, y = -h * 0.32f)
            .height(h).widthIn(min = h)
            .clip(GlassShape.Capsule)
            .background(spec.badgeColor)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontSize = (h.value * 0.62f).sp, fontWeight = FontWeight.Medium, fontFamily = spec.fontFamily),
        )
    }
}

/** iOS edit-mode delete badge. */
@Composable
fun BoxScope.DeleteBadge(onClick: () -> Unit) {
    Box(
        Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = (-7).dp).size(23.dp)
            .clip(CircleShape)
            .background(Color(0xFFE6E6EB).copy(alpha = 0.92f))
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyphs.Minus, Color(0xFF1C1C1E), 15.dp)
    }
}

@Composable
fun GlyphIcon(v: ImageVector, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(v, null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

/** Rotation used by the iOS jiggle. */
@Composable
fun Modifier.jiggle(enabled: Boolean, seed: String): Modifier {
    if (!enabled) return this
    val clock = LocalJiggleClock.current
    val phase = remember(seed) { (seed.hashCode() and 0xFFFF) / 65535f * 6.28f }
    val speed = remember(seed) { 23f + (seed.hashCode() ushr 16 and 0xF) * 0.35f }
    return this.graphicsLayer {
        val t = clock.value
        rotationZ = sin(t * speed + phase) * 1.7f
        translationY = sin(t * speed * 0.5f + phase * 1.3f) * 0.6f * density
    }
}

@Composable
fun MiniIcon(entry: AppEntry?, size: Dp) {
    val bundle = rememberIconBundle(entry, 64.dp)
    val prefs = LocalPrefs.current
    val shape = LocalTheme.current.iconShape
    Box(Modifier.size(size)) {
        if (bundle == null) return@Box
        when {
            bundle.fromPack || prefs.appearance == IconAppearance.DEFAULT -> Image(bundle.full, null, Modifier.fillMaxSize())
            prefs.appearance == IconAppearance.DARK -> {
                Box(Modifier.fillMaxSize().clip(shape).background(Color(0xFF242426)))
                Image(bundle.mono ?: bundle.fg, null, Modifier.fillMaxSize(),
                    colorFilter = if (bundle.glyphIsLight || bundle.mono != null) ColorFilter.tint(brighten(bundle.background), BlendMode.SrcIn) else null)
            }
            else -> {
                val c = if (prefs.appearance == IconAppearance.TINTED && darkTone()) tintColor() else Color.White
                Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.16f)))
                Image(bundle.mono ?: if (bundle.layered) bundle.fg else bundle.full, null, Modifier.fillMaxSize(),
                    colorFilter = if (bundle.mono != null) ColorFilter.tint(c, BlendMode.SrcIn) else glyphMatrix(c.red, c.green, c.blue, 1f))
            }
        }
    }
}

/** Folder as it sits on the Home Screen: a glass tile with miniature icons. */
@Composable
fun FolderTile(folder: FolderItem, size: Dp, hoverGrow: Boolean = false) {
    val spec = LocalTheme.current
    val home = LocalHome.current
    val n = spec.folderPreview
    val mini = if (n == 3) size * 0.205f else size * 0.30f
    val gap = if (n == 3) size * 0.055f else size * 0.075f
    val pad = (size - mini * n - gap * (n - 1)) / 2
    val tileStyle = when (LocalPrefs.current.appearance) {
        IconAppearance.TINTED -> spec.folderTileGlass.copy(tintColor = if (darkTone()) Color(0xFF0B0B0F) else tintColor(), tintStrength = 0.45f)
        IconAppearance.DARK -> spec.folderTileGlass.copy(tintColor = Color(0xFF1C1C1E), tintStrength = 0.45f)
        else -> spec.folderTileGlass
    }
    Box(
        Modifier.size(size)
            .graphicsLayer { val s = if (hoverGrow) 1.12f else 1f; scaleX = s; scaleY = s }
            .liquidGlass(home.wallpaperBackdrop, spec.folderShape, tileStyle)
    ) {
        folder.apps.take(n * n).forEachIndexed { i, key ->
            val x = pad + (mini + gap) * (i % n)
            val y = pad + (mini + gap) * (i / n)
            Box(Modifier.offset(x, y)) { MiniIcon(home.app(key), mini) }
        }
    }
}

@Composable
fun AppIconWithLabel(entry: AppEntry?, iconSize: Dp, cellWidth: Dp, showLabel: Boolean, badge: Int = 0, labelColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconArt(rememberIconBundle(entry, iconSize), iconSize)
            if (LocalPrefs.current.badges) NotificationBadge(badge, iconSize)
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            IconLabel(entry?.label ?: "", cellWidth, labelColor)
        }
    }
}

/** Removes the default ripple and gives an iOS-like press response. */
fun Modifier.drawDimOnPress(pressed: Boolean): Modifier = drawWithContent {
    drawContent()
    if (pressed) drawRect(Color.Black.copy(alpha = 0.12f))
}
