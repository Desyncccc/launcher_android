package com.liquid.launcher.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import com.liquid.launcher.data.AppEntry
import com.liquid.launcher.glass.continuousPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Real app artwork, layered.
 *  full – the icon as it should look in Default appearance (masked to the theme shape)
 *  fg   – foreground layer only (used by Dark / Clear / Tinted)
 *  mono – Android 13 monochrome layer if the app ships one (best for Clear/Tinted)
 */
class IconBundle(
    val full: ImageBitmap,
    val fg: ImageBitmap,
    val mono: ImageBitmap?,
    val background: Int,
    val glyphIsLight: Boolean,
    val layered: Boolean,
    val fromPack: Boolean,
)

class IconProvider(private val context: Context, private val packs: IconPackManager) {
    private val cache = object : LruCache<String, IconBundle>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: IconBundle): Int {
            fun b(i: ImageBitmap?) = if (i == null) 0 else i.width * i.height * 4
            return b(value.full) + (if (value.fg !== value.full) b(value.fg) else 0) + b(value.mono)
        }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val loader = Dispatchers.IO.limitedParallelism(2)
    private val density = DisplayMetrics.DENSITY_XXXHIGH

    fun cached(key: String): IconBundle? = cache.get(key)

    fun clear() = cache.evictAll()

    suspend fun load(app: AppEntry, shapeFraction: Float, exponent: Float, sizePx: Int, pack: String): IconBundle =
        withContext(loader) {
            val key = cacheKey(app.key, shapeFraction, exponent, sizePx, pack)
            cache.get(key) ?: render(app, shapeFraction, exponent, sizePx, pack).also { cache.put(key, it) }
        }

    fun cacheKey(appKey: String, f: Float, e: Float, size: Int, pack: String) = "$appKey|$f|$e|$size|$pack"

    private fun render(app: AppEntry, fraction: Float, exponent: Float, s: Int, pack: String): IconBundle {
        packs.load(pack)
        packs.drawableFor(app.component, density)?.let { d ->
            val bmp = draw(d, s, 0f, 0f, s.toFloat(), s.toFloat())
            val img = bmp.asImageBitmap()
            return IconBundle(img, img, null, averageColor(bmp), false, false, true)
        }
        val d: Drawable = runCatching { app.info.getIcon(density) }.getOrNull()
            ?: context.packageManager.defaultActivityIcon
        val shape = continuousPath(s.toFloat(), s.toFloat(), fraction * s, exponent).asAndroidPath()

        if (d is AdaptiveIconDrawable) {
            // adaptive layers are 108dp with the 72dp centre visible: inset by 1/6 on each side
            val o = -s * 0.25f
            val e = s * 1.25f
            val bgLayer = d.background
            val fgLayer = d.foreground
            val full = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            Canvas(full).apply {
                clipPath(shape)
                drawColor(Color.WHITE)
                bgLayer?.let { drawInto(this, it, o, e) }
                fgLayer?.let { drawInto(this, it, o, e) }
            }
            val fg = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            Canvas(fg).apply { clipPath(shape); fgLayer?.let { drawInto(this, it, o, e) } }
            val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome?.let { m ->
                Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
                    Canvas(b).apply {
                        clipPath(shape)
                        m.mutate().setTint(Color.WHITE)
                        drawInto(this, m, o, e)
                    }
                }
            } else null
            val bgColor = bgLayer?.let { b ->
                val small = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
                Canvas(small).apply { drawInto(this, b, -3f, 15f) }
                averageColor(small)
            } ?: Color.WHITE
            return IconBundle(
                full.asImageBitmap(), fg.asImageBitmap(), mono?.asImageBitmap(), bgColor,
                glyphIsLight(fg), true, false
            )
        }

        // Legacy icon: if it's full-bleed square art, fill the mask; otherwise sit it on white.
        val raw = draw(d, s, 0f, 0f, s.toFloat(), s.toFloat())
        val fullBleed = isOpaqueEdges(raw)
        val full = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(full).apply {
            clipPath(shape)
            if (fullBleed) {
                drawBitmap(raw, null, android.graphics.RectF(-s * 0.04f, -s * 0.04f, s * 1.04f, s * 1.04f), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                drawColor(Color.WHITE)
                val inset = s * 0.14f
                drawBitmap(raw, null, android.graphics.RectF(inset, inset, s - inset, s - inset), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        val fg = if (fullBleed) full else Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
            val inset = s * 0.14f
            Canvas(b).drawBitmap(raw, null, android.graphics.RectF(inset, inset, s - inset, s - inset), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return IconBundle(
            full.asImageBitmap(), fg.asImageBitmap(), null,
            if (fullBleed) averageColor(raw) else Color.WHITE, false, !fullBleed, false
        )
    }

    private fun drawInto(c: Canvas, d: Drawable, o: Float, e: Float) {
        d.setBounds(o.toInt(), o.toInt(), e.toInt(), e.toInt())
        d.draw(c)
    }

    private fun draw(d: Drawable, s: Int, l: Float, t: Float, r: Float, b: Float): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == s && d.bitmap.height == s) return d.bitmap
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun isOpaqueEdges(b: Bitmap): Boolean {
        val w = b.width
        val h = b.height
        val pts = listOf(
            w / 2 to 1, w / 2 to h - 2, 1 to h / 2, w - 2 to h / 2,
            (w * 0.12f).toInt() to (h * 0.12f).toInt(), (w * 0.88f).toInt() to (h * 0.88f).toInt(),
        )
        return pts.all { (x, y) -> Color.alpha(b.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))) > 240 }
    }

    private fun averageColor(b: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(b, 8, 8, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            val c = small.getPixel(x, y)
            val a = Color.alpha(c)
            if (a < 60) continue
            r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++
        }
        if (n == 0L) return Color.WHITE
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    /** True when the foreground is mostly a light, low-saturation glyph (e.g. white phone handset). */
    private fun glyphIsLight(fg: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(fg, 24, 24, true)
        var light = 0; var total = 0
        val hsv = FloatArray(3)
        for (y in 0 until 24) for (x in 0 until 24) {
            val c = small.getPixel(x, y)
            if (Color.alpha(c) < 128) continue
            total++
            Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.22f && hsv[2] > 0.82f) light++
        }
        return total > 0 && light.toFloat() / max(total, 1) > 0.7f
    }
}
