package com.liquid.launcher.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.glass.Backdrop
import com.liquid.launcher.glass.GlassGlow
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.GlassStyle
import com.liquid.launcher.glass.glassGlowOnPress
import com.liquid.launcher.glass.liquidGlass
import com.liquid.launcher.theme.LocalTheme

/** Click without ripple; scales like iOS controls. */
fun Modifier.pressable(onClick: () -> Unit, scaleTo: Float = 0.94f): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/**
 * Liquid Glass reacts to touch by "instantly flexing and energizing with light":
 * a slight scale-up with a springy (gel-like) release, plus inner illumination.
 */
fun Modifier.glassPress(glow: GlassGlow, onClick: () -> Unit, grow: Float = 1.06f): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) grow else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "glasspress")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .glassGlowOnPress(glow)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun GlassCapsuleButton(
    text: String?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: GlassStyle = LocalTheme.current.pillGlass,
    height: Dp = 36.dp,
    textColor: Color = Color.White,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val glow = remember { GlassGlow() }
    val spec = LocalTheme.current
    val st = if (tint != Color.Unspecified) style.copy(tintColor = tint, tintStrength = 0.85f) else style
    Row(
        modifier
            .glassPress(glow, onClick)
            .height(height)
            .liquidGlass(backdrop, GlassShape.Capsule, st, glow)
            .padding(horizontal = if (text == null) 0.dp else 16.dp)
            .then(if (text == null) Modifier.width(height) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (icon != null) GlyphIcon(icon, textColor, 18.dp)
        if (icon != null && text != null) Box(Modifier.width(6.dp))
        if (text != null) BasicText(text, style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = spec.fontFamily))
    }
}

/** Draws [backdrop] blurred – the frosted background behind folders, menus, Spotlight… */
fun Modifier.backdropBlur(backdrop: Backdrop, radius: Dp, progress: () -> Float, dim: Color = Color.Black.copy(alpha = 0.12f)): Modifier = composed {
    val layer = rememberGraphicsLayer()
    val wp = LocalWallpaper.current
    this.drawBehind {
        val p = progress().coerceIn(0f, 1f)
        if (p <= 0.001f) return@drawBehind
        val src = backdrop.layer
        if (Build.VERSION.SDK_INT >= 31 && src != null) {
            val r = radius.toPx() * p
            layer.record { drawLayer(src) }
            layer.renderEffect = if (r > 0.5f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP).asComposeRenderEffect() else null
            layer.alpha = p.coerceAtMost(1f)
            drawLayer(layer)
        } else if (wp != null) {
            drawImage(
                wp.blurred, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                alpha = p,
            )
        }
        drawRect(dim.copy(alpha = dim.alpha * p))
    }
}

// ------------------------------------------------------------------ themed controls

@Composable
fun ThemedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val spec = LocalTheme.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    when (spec.style) {
        ThemeStyle.IOS -> {
            // iOS 26: wider track, pill-shaped knob that swells while pressed
            var pressed by remember { mutableStateOf(false) }
            val track by animateColorAsState(if (checked) Color(0xFF34C759) else if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA), label = "t")
            val knobW by animateDpAsState(if (pressed) 46.dp else 37.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "kw")
            val x by animateDpAsState(if (checked) 63.dp - 2.dp - knobW else 2.dp, spring(dampingRatio = 0.62f, stiffness = 520f), label = "kx")
            Box(
                Modifier.size(63.dp, 28.dp).clip(GlassShape.Capsule).background(track)
                    .pointerInput(checked) {
                        detectTapGestures(onPress = {
                            pressed = true
                            val up = tryAwaitRelease()
                            pressed = false
                            if (up) onChange(!checked)
                        })
                    }
            ) {
                Box(
                    Modifier.offset(x = x, y = 2.dp).size(knobW, 24.dp)
                        .shadow(3.dp, GlassShape.Capsule)
                        .clip(GlassShape.Capsule)
                        .background(if (pressed) Color.White.copy(alpha = 0.75f) else Color.White)
                        .then(if (pressed) Modifier.border(1.dp, Color.White, GlassShape.Capsule) else Modifier)
                )
            }
        }
        else -> {
            val on = spec.accent
            val track by animateColorAsState(if (checked) on else if (dark) Color(0xFF3A3A3C) else Color(0xFFDADADA), label = "t")
            val x by animateDpAsState(if (checked) 22.dp else 3.dp, spring(dampingRatio = 0.8f, stiffness = 600f), label = "kx")
            Box(
                Modifier.size(46.dp, 26.dp).clip(GlassShape.Capsule).background(track)
                    .clickable(remember { MutableInteractionSource() }, null) { onChange(!checked) }
            ) {
                Box(Modifier.offset(x = x, y = 3.dp).size(20.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, dark: Boolean, modifier: Modifier = Modifier, fontSize: TextUnit = 13.sp) {
    val spec = LocalTheme.current
    BoxWithConstraints(
        modifier.height(34.dp).clip(GlassShape.Capsule)
            .background(if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
            .padding(3.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.75f, stiffness = 500f), label = "seg")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .shadow(2.dp, GlassShape.Capsule).clip(GlassShape.Capsule)
                .background(if (dark) Color(0xFF636366) else Color.White)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, s ->
                Box(
                    Modifier.width(w).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        s, style = TextStyle(
                            color = if (dark) Color.White else Color.Black, fontSize = fontSize,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium, fontFamily = spec.fontFamily,
                        ), maxLines = 1
                    )
                }
            }
        }
    }
}

/** Slider with a gradient track (used for the Tinted hue / intensity). */
@Composable
fun GradientSlider(value: Float, onChange: (Float) -> Unit, colors: List<Color>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.height(30.dp).fillMaxWidth()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val knob = 28.dp
        val knobPx = with(LocalDensity.current) { knob.toPx() }
        Box(
            Modifier.fillMaxWidth().height(10.dp).align(Alignment.Center).clip(GlassShape.Capsule)
                .background(Brush.horizontalGradient(colors))
        )
        val pos = (value.coerceIn(0f, 1f) * (wPx - knobPx))
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(pos.toInt(), 0) }.size(knob)
                .shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White)
        )
        Box(
            Modifier.fillMaxSize().pointerInput(wPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(((down.position.x - knobPx / 2) / (wPx - knobPx)).coerceIn(0f, 1f))
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        c.consume()
                        onChange(((c.position.x - knobPx / 2) / (wPx - knobPx)).coerceIn(0f, 1f))
                    }
                }
            }
        )
    }
}

// ------------------------------------------------------------------ home item gestures

/**
 * Tap / long-press / drag recogniser for Home Screen items.
 *  - normal mode: tap → [onTap]; hold → [onLongPress] (context menu); hold + move → [onDragStart]
 *  - edit mode: move immediately starts a drag (the iOS jiggle-mode behaviour)
 * Once a drag starts, the root-level tracker in HomeScreen takes over the pointer.
 */
fun Modifier.itemGestures(
    key: Any,
    editMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
): Modifier = pointerInput(key, editMode) {
    val slop = viewConfiguration.touchSlop
    val lpTimeout = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // consume the down so the page behind doesn't also treat this as its own tap/long-press;
        // pager scrolling and the swipe detector use requireUnconsumed = false so they still work
        down.consume()
        val start = down.position
        if (editMode) {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (c.isConsumed) return@awaitEachGesture
                if (!c.pressed) { c.consume(); onTap(); return@awaitEachGesture }
                if ((c.position - start).getDistance() > slop) {
                    c.consume()
                    onDragStart(c.position)
                    return@awaitEachGesture
                }
            }
        }
        var outcome = 0 // 0 = timeout(long press), 1 = up(tap), 2 = moved/consumed
        withTimeoutOrNull(lpTimeout) {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: run { outcome = 2; return@withTimeoutOrNull }
                if (c.isConsumed) { outcome = 2; return@withTimeoutOrNull }
                if (!c.pressed) { c.consume(); outcome = 1; return@withTimeoutOrNull }
                if ((c.position - start).getDistance() > slop) { outcome = 2; return@withTimeoutOrNull }
            }
        }
        when (outcome) {
            1 -> { onTap(); return@awaitEachGesture }
            2 -> return@awaitEachGesture
        }
        onLongPress()
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!c.pressed) { c.consume(); return@awaitEachGesture }
            c.consume()
            if ((c.position - start).getDistance() > slop * 1.6f) {
                onDragStart(c.position)
                return@awaitEachGesture
            }
        }
    }
}

@Composable
fun Txt(text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, align: androidx.compose.ui.text.style.TextAlign? = null, letter: TextUnit = TextUnit.Unspecified) {
    val spec = LocalTheme.current
    BasicText(
        text, modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = spec.fontFamily, textAlign = align ?: androidx.compose.ui.text.style.TextAlign.Unspecified, letterSpacing = letter),
        maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Composable
fun GlassCircleButton(icon: ImageVector, backdrop: Backdrop, size: Dp = 44.dp, iconColor: Color = Color.White, style: GlassStyle = LocalTheme.current.pillGlass, onClick: () -> Unit) {
    val glow = remember { GlassGlow() }
    Box(
        Modifier.glassPress(glow, onClick).size(size).liquidGlass(backdrop, GlassShape.Capsule, style, glow),
        contentAlignment = Alignment.Center
    ) { GlyphIcon(icon, iconColor, size * 0.45f) }
}
