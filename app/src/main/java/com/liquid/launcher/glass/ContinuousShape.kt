package com.liquid.launcher.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A rounded rectangle whose corners are superellipse quadrants
 * (|x|^n + |y|^n = r^n). With n ≈ 2.4 this matches Apple's "continuous corner"
 * curve to within ~0.1% of the shape size (fitted against the Figma
 * corner-smoothing construction), and with n = 2 it is a plain circular corner,
 * which is what capsules use.
 *
 * The very same geometry is evaluated as a signed distance field in the glass
 * shader, so clip paths, shadows and the refraction mask always line up.
 */
@Immutable
data class GlassShape(
    /** Corner radius. Ignored when [capsule] is true. */
    val radius: Dp = 0.dp,
    /** Corner radius as a fraction of the smaller side (used by icons). */
    val radiusFraction: Float = -1f,
    val exponent: Float = 2.4f,
    val capsule: Boolean = false,
) : Shape {

    fun radiusPx(size: Size, density: Density): Float {
        val maxR = min(size.width, size.height) / 2f
        val r = when {
            capsule -> maxR
            radiusFraction >= 0f -> radiusFraction * min(size.width, size.height)
            else -> with(density) { radius.toPx() }
        }
        return r.coerceIn(0f, maxR)
    }

    val effectiveExponent: Float get() = if (capsule) 2f else exponent

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(
            continuousPath(size.width, size.height, radiusPx(size, density), effectiveExponent)
        )
    }

    companion object {
        val Capsule = GlassShape(capsule = true)
        fun rounded(radius: Dp, exponent: Float = 2.4f) = GlassShape(radius = radius, exponent = exponent)
        fun icon(fraction: Float, exponent: Float) = GlassShape(radiusFraction = fraction, exponent = exponent)
    }
}

fun continuousPath(
    w: Float,
    h: Float,
    radius: Float,
    exponent: Float,
    left: Float = 0f,
    top: Float = 0f,
    segments: Int = 18,
): Path {
    val path = Path()
    val r = radius.coerceIn(0f, min(w, h) / 2f)
    if (r <= 0.01f) {
        path.addRect(androidx.compose.ui.geometry.Rect(left, top, left + w, top + h))
        return path
    }
    val e = 2f / exponent
    fun cx(t: Double) = cos(t).coerceAtLeast(0.0).pow(e.toDouble()).toFloat() * r
    fun sy(t: Double) = sin(t).coerceAtLeast(0.0).pow(e.toDouble()).toFloat() * r
    val half = PI / 2
    // top edge start
    path.moveTo(left + r, top)
    // TR corner: centre (w-r, r), t from pi/2 -> 0
    var c0 = left + w - r
    var c1 = top + r
    for (i in 0..segments) {
        val t = half * (1.0 - i.toDouble() / segments)
        path.lineTo(c0 + cx(t), c1 - sy(t))
    }
    // BR corner: centre (w-r, h-r), t 0 -> pi/2
    c1 = top + h - r
    for (i in 0..segments) {
        val t = half * (i.toDouble() / segments)
        path.lineTo(c0 + cx(t), c1 + sy(t))
    }
    // BL corner: centre (r, h-r), t pi/2 -> 0
    c0 = left + r
    for (i in 0..segments) {
        val t = half * (1.0 - i.toDouble() / segments)
        path.lineTo(c0 - cx(t), c1 + sy(t))
    }
    // TL corner: centre (r, r), t 0 -> pi/2
    c1 = top + r
    for (i in 0..segments) {
        val t = half * (i.toDouble() / segments)
        path.lineTo(c0 - cx(t), c1 - sy(t))
    }
    path.close()
    return path
}
