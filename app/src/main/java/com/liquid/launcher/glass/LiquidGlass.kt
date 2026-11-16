package com.liquid.launcher.glass

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Touch illumination state: "the material illuminates from within … starting right under your fingertips". */
@Stable
class GlassGlow {
    var point by mutableStateOf(Offset.Unspecified)
    val intensity = Animatable(0f)
}

/** Attaches a press tracker that drives [glow]. Does not consume events. */
fun Modifier.glassGlowOnPress(glow: GlassGlow): Modifier = pointerInput(glow) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            glow.point = down.position
            launch { glow.intensity.animateTo(1f, tween(140)) }
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull() ?: break
                glow.point = c.position
                if (!c.pressed) break
            }
            launch { glow.intensity.animateTo(0f, tween(420)) }
        }
    }
}

/**
 * Renders this node's bounds as a piece of Liquid Glass refracting [backdrop].
 * The node's own content is drawn on top (glyphs, labels…).
 *
 * @param progress 0..1 – materialisation. Apple: "objects materialize in and out
 *        by gradually modulating the light bending and lensing" – so this scales
 *        refraction as well as opacity.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: GlassShape,
    style: GlassStyle = GlassStyle(),
    glow: GlassGlow? = null,
    progress: () -> Float = { 1f },
): Modifier = this
    // own RenderNode: re-rendering the glass doesn't force the parent (and every
    // backdrop that contains it) to re-record
    .graphicsLayer()
    .then(LiquidGlassElement(backdrop, shape, style, glow, progress))

private data class LiquidGlassElement(
    val backdrop: Backdrop,
    val shape: GlassShape,
    val style: GlassStyle,
    val glow: GlassGlow?,
    val progress: () -> Float,
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create() = LiquidGlassNode(backdrop, shape, style, glow, progress)
    override fun update(node: LiquidGlassNode) {
        node.backdrop = backdrop
        node.shape = shape
        node.style = style
        node.glow = glow
        node.progress = progress
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "liquidGlass" }
}

private data class EffectKey(
    val m: Float, val w: Float, val h: Float, val sl: Float, val st: Float, val sr: Float, val sb: Float,
    val radius: Float, val exp: Float, val bevel: Float, val refract: Float, val dispersion: Float,
    val saturation: Float, val magnify: Float, val tint: ResolvedTint, val rim: Float, val rimW: Float,
    val lx: Float, val ly: Float, val gx: Float, val gy: Float, val gz: Float, val alpha: Float, val blur: Float,
)

private class LiquidGlassNode(
    var backdrop: Backdrop,
    var shape: GlassShape,
    var style: GlassStyle,
    var glow: GlassGlow?,
    var progress: () -> Float,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var graphicsContext: GraphicsContext? = null
    private var glassLayer: GraphicsLayer? = null
    private var position by mutableStateOf(Offset.Zero)
    private var lastKey: EffectKey? = null

    private var cachedPath: Path? = null
    private var cachedPathKey = Triple(Size.Zero, 0f, 0f)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowBlur = -1f

    override fun onAttach() {
        graphicsContext = currentValueOf(LocalGraphicsContext)
    }

    override fun onDetach() {
        glassLayer?.let { graphicsContext?.releaseGraphicsLayer(it) }
        glassLayer = null
        lastKey = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        if (p != position) position = p
    }

    private fun path(size: Size, r: Float, n: Float): Path {
        val key = Triple(size, r, n)
        val cached = cachedPath
        if (cached != null && key == cachedPathKey) return cached
        return continuousPath(size.width, size.height, r, n).also {
            cachedPath = it; cachedPathKey = key
        }
    }

    override fun ContentDrawScope.draw() {
        val src = backdrop.layer
        val amount = progress().coerceIn(0f, 1f)
        if (src == null || amount <= 0.001f || size.minDimension <= 1f) {
            drawContent(); return
        }
        val env = currentValueOf(LocalGlassEnvironment)
        val light = currentValueOf(LocalLightSource).direction
        val pos = position
        val rel = pos - backdrop.positionInRoot
        val r = shape.radiusPx(size, this)
        val n = shape.effectiveExponent
        val lum = backdrop.luminance?.invoke(Rect(pos, size)) ?: 0.45f
        val tint = resolveTint(style, env, lum)
        val outline = path(size, r, n)

        if (Build.VERSION.SDK_INT >= 33 && env.liquidEnabled && GlassShader.shaderOrNull() != null) {
            drawLiquid(src, rel, r, n, tint, light, amount, env)
        } else {
            drawFallback(src, rel, outline, tint, light, amount, env)
        }
        drawContent()
    }

    private fun DrawScope.drawShadow(outline: Path, amount: Float) {
        val a = style.shadowAlpha * amount
        if (a <= 0.003f) return
        val blurPx = style.shadowRadius.toPx()
        if (blurPx != shadowBlur) {
            shadowBlur = blurPx
            shadowPaint.maskFilter = if (blurPx > 0.5f) BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL) else null
        }
        shadowPaint.color = android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)
        val dy = style.shadowOffsetY.toPx()
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate(0f, dy)
            nc.drawPath(outline.asAndroidPath(), shadowPaint)
            nc.restore()
        }
    }

    private fun obtainLayer(): GraphicsLayer =
        glassLayer ?: graphicsContext!!.createGraphicsLayer().also { glassLayer = it }

    private fun DrawScope.drawLiquid(
        src: GraphicsLayer, rel: Offset, r: Float, n: Float, tint: ResolvedTint,
        light: Offset, amount: Float, env: GlassEnvironment,
    ) {
        if (Build.VERSION.SDK_INT < 33) return
        val blurPx = style.blur.toPx() * (if (env.reduceTransparency) 3f else 1f)
        val m = ceil(blurPx * 2f + 2f)
        val lw = ceil(size.width + 2 * m).toInt()
        val lh = ceil(size.height + 2 * m).toInt()
        val layer = obtainLayer()
        val dx = -rel.x + m
        val dy = -rel.y + m
        layer.record(size = IntSize(lw, lh)) {
            translate(dx, dy) { drawLayer(src) }
        }
        val srcSize = src.size
        val g = glow
        val gz = g?.intensity?.value ?: 0f
        val gp = g?.point ?: Offset.Unspecified
        val key = EffectKey(
            m = m, w = size.width, h = size.height,
            sl = dx, st = dy, sr = dx + srcSize.width, sb = dy + srcSize.height,
            radius = r, exp = n,
            bevel = style.bevel.toPx().coerceAtMost(size.minDimension / 2f).coerceAtLeast(1f),
            refract = style.refraction.toPx() * amount,
            dispersion = style.dispersion, saturation = style.saturation, magnify = style.magnify,
            tint = tint, rim = style.rim * amount, rimW = style.rimWidth.toPx(),
            lx = light.x, ly = light.y,
            gx = if (gp.isSpecified) gp.x else 0f, gy = if (gp.isSpecified) gp.y else 0f, gz = gz,
            alpha = amount, blur = blurPx,
        )
        if (key != lastKey) {
            lastKey = key
            val s = GlassShader.shaderOrNull() ?: return
            s.setFloatUniform("uOrigin", m, m)
            s.setFloatUniform("uSize", key.w, key.h)
            s.setFloatUniform("uSrc", key.sl, key.st, key.sr, key.sb)
            s.setFloatUniform("uRadius", key.radius)
            s.setFloatUniform("uExp", key.exp)
            s.setFloatUniform("uBevel", key.bevel)
            s.setFloatUniform("uRefract", key.refract)
            s.setFloatUniform("uDispersion", key.dispersion)
            s.setFloatUniform("uSaturation", key.saturation)
            s.setFloatUniform("uMagnify", key.magnify)
            s.setFloatUniform("uTint", tint.r, tint.g, tint.b, tint.strength)
            s.setFloatUniform("uLift", tint.lift)
            s.setFloatUniform("uRim", key.rim)
            s.setFloatUniform("uRimWidth", key.rimW)
            s.setFloatUniform("uLight", key.lx, key.ly)
            s.setFloatUniform("uGlow", key.gx, key.gy, key.gz)
            s.setFloatUniform("uAlpha", key.alpha)
            val lens = RenderEffect.createRuntimeShaderEffect(s, "content")
            val effect = if (blurPx >= 0.5f) {
                RenderEffect.createChainEffect(lens, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
            } else lens
            layer.renderEffect = effect.asComposeRenderEffect()
        }
        layer.topLeft = IntOffset(-m.toInt(), -m.toInt())
        drawLayer(layer)
    }

    /** API 28–32: frosted blur (31+) or plain tint, with an approximated specular rim. */
    private fun DrawScope.drawFallback(
        src: GraphicsLayer, rel: Offset, outline: Path, tint: ResolvedTint,
        light: Offset, amount: Float, env: GlassEnvironment,
    ) {
        val blurPx = style.blur.toPx() * 2.5f * (if (env.reduceTransparency) 3f else 1f)
        clipPath(outline) {
            if (Build.VERSION.SDK_INT >= 31 && blurPx > 0.5f) {
                val layer = obtainLayer()
                layer.record { translate(-rel.x, -rel.y) { drawLayer(src) } }
                layer.topLeft = IntOffset.Zero
                if (lastKey == null) {
                    layer.renderEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
                    lastKey = EffectKey(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, tint, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, blurPx)
                }
                drawLayer(layer)
            } else {
                translate(-rel.x, -rel.y) { drawLayer(src) }
            }
            val extra = if (Build.VERSION.SDK_INT >= 31) 0.06f else 0.22f
            drawRect(Color(tint.r, tint.g, tint.b, (tint.strength + extra).coerceAtMost(1f) * amount))
            if (tint.lift > 0f) drawRect(Color.White.copy(alpha = tint.lift * 2f * amount))
            glow?.let { g ->
                val z = g.intensity.value
                if (z > 0.01f && g.point.isSpecified) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.18f * z), Color.Transparent),
                            center = g.point, radius = size.maxDimension * 0.6f
                        ), radius = size.maxDimension * 0.6f, center = g.point
                    )
                }
            }
        }
        val c = center
        val ext = size.maxDimension / 2f
        drawPath(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f * style.rim * amount),
                0.45f to Color.White.copy(alpha = 0.06f * style.rim * amount),
                1f to Color.White.copy(alpha = 0.32f * style.rim * amount),
                start = c + light * ext, end = c - light * ext,
            ),
            style = Stroke(width = style.rimWidth.toPx() * 1.2f),
        )
    }
}
