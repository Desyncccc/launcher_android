package com.liquid.launcher.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple ships two Liquid Glass variants (HIG › Materials):
 *  - REGULAR: adaptive. Blurs + adjusts luminosity of what's behind, flips light/dark
 *    for small elements, gets a stronger tint over busy/bright content.
 *  - CLEAR: permanently more transparent, no adaptivity, needs a dimming layer over
 *    bright content (HIG: "consider adding a dark dimming layer of 35% opacity").
 */
enum class GlassVariant { REGULAR, CLEAR }

@Immutable
data class GlassStyle(
    val variant: GlassVariant = GlassVariant.REGULAR,
    /** Frost. Liquid Glass lenses first and blurs second – keep this small. */
    val blur: Dp = 3.dp,
    /** Maximum lateral displacement of the backdrop at the very edge. */
    val refraction: Dp = 12.dp,
    /** Width of the curved rim where lensing happens (the "thickness"). */
    val bevel: Dp = 16.dp,
    /** Chromatic dispersion at the rim (0 = none). */
    val dispersion: Float = 0.10f,
    val saturation: Float = 1.45f,
    /** Uniform magnification of the content seen through the centre. */
    val magnify: Float = 0.0f,
    /** Specular rim intensity. */
    val rim: Float = 1f,
    val rimWidth: Dp = 1.3.dp,
    val shadowAlpha: Float = 0.10f,
    val shadowRadius: Dp = 16.dp,
    val shadowOffsetY: Dp = 3.dp,
    /** Extra opacity on top of the adaptive tint (menus/sheets are "thicker"). */
    val frost: Float = 0f,
    /** Coloured glass. When specified the material keeps this hue. */
    val tintColor: Color = Color.Unspecified,
    val tintStrength: Float = 0.35f,
    /** Light/dark flipping of the material (small elements only, per Apple). */
    val adaptive: Boolean = true,
)

/** Global switches: dark mode, iOS 26.1 "Tinted" glass option, accessibility. */
@Immutable
data class GlassEnvironment(
    val dark: Boolean = false,
    val tinted: Boolean = false,
    val reduceTransparency: Boolean = false,
    val liquidEnabled: Boolean = true,
)

val LocalGlassEnvironment = staticCompositionLocalOf { GlassEnvironment() }

internal data class ResolvedTint(val r: Float, val g: Float, val b: Float, val strength: Float, val lift: Float)

internal fun resolveTint(style: GlassStyle, env: GlassEnvironment, luminance: Float): ResolvedTint {
    if (style.tintColor != Color.Unspecified) {
        val c = style.tintColor
        // Coloured glass: hue stays, brightness maps to content underneath.
        val lift = if (luminance > 0.6f) -0.03f else 0.02f
        return ResolvedTint(c.red, c.green, c.blue, style.tintStrength + if (env.tinted) 0.2f else 0f, lift)
    }
    var r: Float
    var strength: Float
    var lift: Float
    when (style.variant) {
        GlassVariant.REGULAR -> {
            val goDark = env.dark || (style.adaptive && luminance < 0.18f)
            if (goDark) {
                r = 0.08f; strength = 0.20f + style.frost; lift = 0.0f
            } else if (luminance > 0.58f) {
                r = 1f; strength = 0.22f + style.frost; lift = 0.03f
            } else {
                r = 1f; strength = 0.10f + style.frost * 0.8f; lift = 0.025f
            }
            if (env.tinted) strength += 0.32f
        }
        GlassVariant.CLEAR -> {
            // dimming layer: 35% over bright content, almost none over dark content
            r = 0f
            strength = when {
                luminance > 0.65f -> 0.30f
                luminance > 0.4f -> 0.16f
                else -> 0.06f
            } + style.frost
            lift = 0f
            if (env.tinted) strength += 0.18f
        }
    }
    if (env.reduceTransparency) strength = maxOf(strength, 0.82f)
    return ResolvedTint(r, r, r, strength.coerceIn(0f, 1f), lift)
}
