package com.liquid.launcher.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * AGSL implementation of the Liquid Glass lens.
 *
 * What it does, following WWDC25 "Meet Liquid Glass":
 *  1. Lensing – instead of scattering light (classic blur/"glassmorphism"), the slab
 *     bends it. The shape is modelled as a flat top with a circular bevel of width
 *     uBevel; within the bevel the refracted ray is displaced along the surface
 *     normal (inwards, like a real convex edge), so content is magnified and
 *     wrapped around the rim.
 *  2. Light dispersion at the rim (tiny chromatic split).
 *  3. Specular highlights that "travel along the edges": brightest where the edge
 *     normal faces the light (top-left by default, steered by the gyroscope) plus
 *     a weaker reflection on the opposite side.
 *  4. Adaptive tint / dimming layer and vibrancy (saturation boost).
 *  5. Interaction: an inner glow that starts under the fingertip.
 *
 * The shape SDF uses superellipse corners and is identical to [continuousPath].
 */
object GlassShader {
    const val SOURCE = """
uniform shader content;
uniform float2 uOrigin;
uniform float2 uSize;
uniform float4 uSrc;
uniform float uRadius;
uniform float uExp;
uniform float uBevel;
uniform float uRefract;
uniform float uDispersion;
uniform float uSaturation;
uniform float uMagnify;
uniform float4 uTint;
uniform float uLift;
uniform float uRim;
uniform float uRimWidth;
uniform float2 uLight;
uniform float3 uGlow;
uniform float uAlpha;

float sdShape(float2 p) {
    float2 hs = uSize * 0.5;
    float2 q = abs(p - hs) - hs + uRadius;
    float2 m = max(q, 0.0);
    float c = pow(pow(m.x, uExp) + pow(m.y, uExp), 1.0 / uExp);
    return c + min(max(q.x, q.y), 0.0) - uRadius;
}

float4 sampleAt(float2 c) {
    c = clamp(c, uSrc.xy + 0.5, uSrc.zw - 0.5);
    return content.eval(c);
}

half4 main(float2 fc) {
    float2 p = fc - uOrigin;
    float sd = sdShape(p);
    if (sd > 1.0) { return half4(0.0); }
    float aa = clamp(0.5 - sd, 0.0, 1.0);
    float d = max(-sd, 0.0);

    float e = 0.75;
    float2 g = float2(sdShape(p + float2(e, 0.0)) - sdShape(p - float2(e, 0.0)),
                      sdShape(p + float2(0.0, e)) - sdShape(p - float2(0.0, e)));
    float gl = length(g);
    float2 n = gl > 0.0001 ? g / gl : float2(0.0);

    // circular bevel profile: 1 at the very edge, 0 where the flat top starts
    float x = clamp(1.0 - d / uBevel, 0.0, 1.0);
    float bend = 1.0 - sqrt(max(1.0 - x * x, 0.0));

    float2 hs = uSize * 0.5;
    float2 base = hs + (p - hs) * (1.0 - uMagnify);
    float2 off = n * (bend * uRefract);
    float2 sp = uOrigin + base - off;

    float3 col;
    if (uDispersion > 0.001) {
        float2 dOff = off * uDispersion;
        col.r = sampleAt(sp + dOff).r;
        col.g = sampleAt(sp).g;
        col.b = sampleAt(sp - dOff).b;
    } else {
        col = sampleAt(sp).rgb;
    }

    // vibrancy + luminosity adjustment + adaptive tint (or dimming layer)
    float l = dot(col, float3(0.2126, 0.7152, 0.0722));
    col = mix(float3(l), col, uSaturation);
    col = col + uLift;
    col = mix(col, uTint.rgb, uTint.a);

    // specular: light travels along the silhouette
    float facing = dot(n, uLight);
    float spec = pow(max(facing, 0.0), 2.5) + 0.55 * pow(max(-facing, 0.0), 2.5);
    float rimBand = 1.0 - smoothstep(0.0, uRimWidth, d);
    float inner = x * x * x;
    col += uRim * (rimBand * (0.10 + 0.90 * spec) * 0.9 + inner * spec * 0.10);

    // illumination from the touch point
    if (uGlow.z > 0.001) {
        float rr = max(uSize.x, uSize.y) * 0.6;
        float2 dv = p - uGlow.xy;
        col += uGlow.z * 0.16 * exp(-dot(dv, dv) / (rr * rr));
    }

    col = clamp(col, 0.0, 1.0);
    float a = aa * uAlpha;
    return half4(half3(col * a), half(a));
}
"""

    private var compiled = false
    private var cached: RuntimeShader? = null

    /** The shared lens shader, or null if this GPU/driver refuses to compile it. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun shaderOrNull(): RuntimeShader? {
        if (!compiled) {
            compiled = true
            cached = runCatching { RuntimeShader(SOURCE) }
                .onFailure { android.util.Log.e("LiquidGlass", "AGSL compile failed – using fallback", it) }
                .getOrNull()
        }
        return cached
    }
}
