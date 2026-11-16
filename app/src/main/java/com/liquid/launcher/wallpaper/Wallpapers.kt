package com.liquid.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Original, procedurally generated wallpapers (no third-party artwork). */
data class WallpaperPreset(
    val id: String,
    val name: String,
    val base: IntArray,
    val blobs: List<Blob>,
    val ribbons: List<Ribbon> = emptyList(),
) {
    data class Blob(val x: Float, val y: Float, val r: Float, val color: Int, val alpha: Float = 1f)
    data class Ribbon(
        val points: FloatArray, // x0,y0, c1x,c1y, c2x,c2y, x1,y1 (fractions)
        val width: Float, val colors: IntArray, val alpha: Float = 1f,
    )
}

object WallpaperPresets {
    private fun c(hex: Long) = hex.toInt()

    val all = listOf(
        WallpaperPreset(
            "tahoe", "Tahoe",
            intArrayOf(c(0xFF04103A), c(0xFF0A2A8A), c(0xFF0B1B55)),
            listOf(
                WallpaperPreset.Blob(0.15f, 0.25f, 0.75f, c(0xFF1E6BFF), 0.9f),
                WallpaperPreset.Blob(0.95f, 0.55f, 0.65f, c(0xFF00C2FF), 0.55f),
                WallpaperPreset.Blob(0.70f, 0.95f, 0.70f, c(0xFFFF7A2E), 0.55f),
            ),
            listOf(
                WallpaperPreset.Ribbon(floatArrayOf(-0.2f, 0.62f, 0.25f, 0.30f, 0.65f, 0.95f, 1.2f, 0.42f), 0.30f, intArrayOf(c(0xFF60C8FF), c(0xFF2F5BFF), c(0xFF7A3CFF)), 0.85f),
                WallpaperPreset.Ribbon(floatArrayOf(-0.2f, 0.92f, 0.35f, 0.62f, 0.75f, 1.18f, 1.25f, 0.75f), 0.22f, intArrayOf(c(0xFFFFB36B), c(0xFFFF5E7A), c(0xFF8A4DFF)), 0.75f),
                WallpaperPreset.Ribbon(floatArrayOf(-0.1f, 0.18f, 0.40f, 0.02f, 0.70f, 0.40f, 1.2f, 0.12f), 0.16f, intArrayOf(c(0xFF9FE6FF), c(0xFF4C8DFF)), 0.55f),
            ),
        ),
        WallpaperPreset(
            "dawn", "Dawn",
            intArrayOf(c(0xFFFFB38A), c(0xFFFF6F91), c(0xFF6A3DE8)),
            listOf(
                WallpaperPreset.Blob(0.8f, 0.15f, 0.6f, c(0xFFFFE29A), 0.9f),
                WallpaperPreset.Blob(0.1f, 0.6f, 0.7f, c(0xFFFF4D8D), 0.6f),
                WallpaperPreset.Blob(0.6f, 1.0f, 0.8f, c(0xFF3B1FA8), 0.8f),
            ),
            listOf(
                WallpaperPreset.Ribbon(floatArrayOf(-0.2f, 0.45f, 0.3f, 0.2f, 0.7f, 0.8f, 1.2f, 0.5f), 0.26f, intArrayOf(c(0xFFFFF0C2), c(0xFFFF8FA3), c(0xFFB06BFF)), 0.7f),
            ),
        ),
        WallpaperPreset(
            "lagoon", "Lagoon",
            intArrayOf(c(0xFF003B3F), c(0xFF00796B), c(0xFF0B3D2E)),
            listOf(
                WallpaperPreset.Blob(0.2f, 0.2f, 0.6f, c(0xFF5EF2C2), 0.65f),
                WallpaperPreset.Blob(0.9f, 0.7f, 0.7f, c(0xFF00B3FF), 0.5f),
                WallpaperPreset.Blob(0.3f, 0.95f, 0.6f, c(0xFFC6FF6B), 0.35f),
            ),
            listOf(
                WallpaperPreset.Ribbon(floatArrayOf(-0.2f, 0.7f, 0.3f, 0.4f, 0.6f, 0.9f, 1.2f, 0.55f), 0.24f, intArrayOf(c(0xFF9CFFE4), c(0xFF19C3B0), c(0xFF0A7FA8)), 0.7f),
            ),
        ),
        WallpaperPreset(
            "graphite", "Graphite",
            intArrayOf(c(0xFF060608), c(0xFF15161C), c(0xFF050507)),
            listOf(
                WallpaperPreset.Blob(0.2f, 0.3f, 0.5f, c(0xFF2B3A67), 0.55f),
                WallpaperPreset.Blob(0.85f, 0.8f, 0.6f, c(0xFF3A2558), 0.45f),
            ),
            listOf(
                WallpaperPreset.Ribbon(floatArrayOf(-0.2f, 0.55f, 0.35f, 0.35f, 0.65f, 0.85f, 1.2f, 0.6f), 0.20f, intArrayOf(c(0xFF5A6A8F), c(0xFF2A2F45), c(0xFF6D5A9A)), 0.55f),
            ),
        ),
        WallpaperPreset(
            "cloud", "Cloud",
            intArrayOf(c(0xFFEAF2FF), c(0xFFD6E4FF), c(0xFFF7EEFF)),
            listOf(
                WallpaperPreset.Blob(0.15f, 0.2f, 0.6f, c(0xFFBFD8FF), 0.9f),
                WallpaperPreset.Blob(0.9f, 0.6f, 0.6f, c(0xFFFFD6E8), 0.8f),
                WallpaperPreset.Blob(0.4f, 1.0f, 0.6f, c(0xFFD9F7FF), 0.9f),
            ),
            listOf(
                WallpaperPreset.Ribbon(floatArrayOf(-0.2f, 0.6f, 0.3f, 0.35f, 0.7f, 0.85f, 1.2f, 0.5f), 0.25f, intArrayOf(c(0xFFFFFFFF), c(0xFFB8D4FF), c(0xFFF0C8FF)), 0.8f),
            ),
        ),
        WallpaperPreset(
            "bloom", "Bloom",
            intArrayOf(c(0xFF2A1458), c(0xFF6B2C91), c(0xFF1C2B6B)),
            listOf(
                WallpaperPreset.Blob(0.25f, 0.35f, 0.6f, c(0xFFFF7AC6), 0.65f),
                WallpaperPreset.Blob(0.85f, 0.15f, 0.5f, c(0xFF7AA8FF), 0.6f),
                WallpaperPreset.Blob(0.6f, 0.85f, 0.7f, c(0xFFB066FF), 0.6f),
            ),
        ),
        WallpaperPreset(
            "aurora", "Aurora",
            intArrayOf(c(0xFF020B1F), c(0xFF0B2E4F), c(0xFF061424)),
            listOf(
                WallpaperPreset.Blob(0.3f, 0.2f, 0.55f, c(0xFF2EE6C5), 0.55f),
                WallpaperPreset.Blob(0.75f, 0.45f, 0.6f, c(0xFF2F7BFF), 0.5f),
                WallpaperPreset.Blob(0.4f, 0.9f, 0.6f, c(0xFF8E5CFF), 0.45f),
            ),
            listOf(
                WallpaperPreset.Ribbon(floatArrayOf(-0.2f, 0.35f, 0.25f, 0.1f, 0.75f, 0.6f, 1.2f, 0.3f), 0.18f, intArrayOf(c(0xFF8BFFE8), c(0xFF35B6FF)), 0.5f),
            ),
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

object WallpaperRenderer {
    /** Renders at half resolution (the art is soft anyway), upscales, then adds full-res grain. */
    fun render(preset: WallpaperPreset, w: Int, h: Int): Bitmap {
        val half = renderRaw(preset, (w / 2).coerceAtLeast(1), (h / 2).coerceAtLeast(1))
        val full = Bitmap.createScaledBitmap(half, w, h, true)
        if (full !== half) half.recycle()
        addGrain(full)
        return full
    }

    private fun renderRaw(preset: WallpaperPreset, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), preset.base, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val dim = max(w, h).toFloat()
        for (b in preset.blobs) {
            val cx = b.x * w
            val cy = b.y * h
            val r = b.r * dim
            val col = b.color and 0x00FFFFFF
            val a = (b.alpha * 255).roundToInt().coerceIn(0, 255)
            p.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(col or (a shl 24), col or ((a * 0.45f).toInt() shl 24), col),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, p)
        }
        p.shader = null
        // Glass-like ribbons with a soft body and a crisp, light edge.
        for (rb in preset.ribbons) {
            val pts = rb.points
            val path = Path().apply {
                moveTo(pts[0] * w, pts[1] * h)
                cubicTo(pts[2] * w, pts[3] * h, pts[4] * w, pts[5] * h, pts[6] * w, pts[7] * h)
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = rb.width * w
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(0f, pts[1] * h, w.toFloat(), pts[7] * h, rb.colors, null, Shader.TileMode.CLAMP)
                alpha = (rb.alpha * 255).roundToInt()
                maskFilter = BlurMaskFilter(rb.width * w * 0.18f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawPath(path, body)
            // inner bright core
            body.strokeWidth = rb.width * w * 0.35f
            body.maskFilter = BlurMaskFilter(rb.width * w * 0.2f, BlurMaskFilter.Blur.NORMAL)
            body.alpha = (rb.alpha * 120).roundToInt()
            body.shader = null
            body.color = 0x66FFFFFF
            canvas.save()
            canvas.translate(-rb.width * w * 0.12f, -rb.width * w * 0.12f)
            canvas.drawPath(path, body)
            canvas.restore()
            // edge highlight
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w * 0.004f
                color = 0x55FFFFFF
                maskFilter = BlurMaskFilter(w * 0.003f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.save()
            canvas.translate(0f, -rb.width * w * 0.45f)
            canvas.drawPath(path, edge)
            canvas.restore()
        }
        return bmp
    }

    private fun addGrain(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        val rnd = Random(7)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val n = rnd.nextInt(-3, 4)
                val c = row[x]
                val r = ((c shr 16) and 0xFF) + n
                val g = ((c shr 8) and 0xFF) + n
                val b = (c and 0xFF) + n
                row[x] = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
    }
}

/** Downscaled luminance map, used for adaptive glass and status bar contrast. */
class LuminanceMap(bmp: Bitmap) {
    private val w = 36
    private val h: Int = max(1, (bmp.height.toFloat() / bmp.width * w).roundToInt())
    private val lum = FloatArray(w * h)

    init {
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = srgbToLinear(((c shr 16) and 0xFF) / 255f)
            val g = srgbToLinear(((c shr 8) and 0xFF) / 255f)
            val b = srgbToLinear((c and 0xFF) / 255f)
            lum[i] = (0.2126f * r + 0.7152f * g + 0.0722f * b).let { kotlin.math.sqrt(it) }
        }
        if (small !== bmp) small.recycle()
    }

    private fun srgbToLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    /** Average perceptual luminance of a normalised rect (0..1 coordinates). */
    fun average(l: Float, t: Float, r: Float, b: Float): Float {
        val x0 = (l * w).toInt().coerceIn(0, w - 1)
        val x1 = (r * w).toInt().coerceIn(x0, w - 1)
        val y0 = (t * h).toInt().coerceIn(0, h - 1)
        val y1 = (b * h).toInt().coerceIn(y0, h - 1)
        var sum = 0f
        var n = 0
        for (y in y0..y1) for (x in x0..x1) { sum += lum[y * w + x]; n++ }
        return if (n == 0) 0.5f else sum / n
    }
}

class WallpaperImage(
    val bitmap: ImageBitmap,
    val blurred: ImageBitmap,
    val luminance: LuminanceMap,
    val width: Int,
    val height: Int,
) {
    fun luminanceOf(rect: Rect, screenW: Float, screenH: Float): Float =
        luminance.average(rect.left / screenW, rect.top / screenH, rect.right / screenW, rect.bottom / screenH)
}

object WallpaperLoader {
    private const val CUSTOM = "custom_wallpaper.jpg"

    fun customFile(context: Context) = File(context.filesDir, CUSTOM)

    fun load(context: Context, id: String, w: Int, h: Int): WallpaperImage {
        val bmp: Bitmap = if (id == "custom" && customFile(context).exists()) {
            val raw = BitmapFactory.decodeFile(customFile(context).absolutePath)
            if (raw != null) centerCrop(raw, w, h) else renderCached(context, WallpaperPresets.all.first(), w, h)
        } else {
            renderCached(context, WallpaperPresets.byId(id), w, h)
        }
        return wrap(bmp)
    }

    fun wrap(bmp: Bitmap): WallpaperImage {
        val small = Bitmap.createScaledBitmap(bmp, (bmp.width / 10).coerceAtLeast(8), (bmp.height / 10).coerceAtLeast(8), true)
        val blurredSmall = boxBlur(boxBlur(small, 4), 4)
        val blurred = Bitmap.createScaledBitmap(blurredSmall, bmp.width / 2, bmp.height / 2, true)
        return WallpaperImage(bmp.asImageBitmap(), blurred.asImageBitmap(), LuminanceMap(bmp), bmp.width, bmp.height)
    }

    private fun renderCached(context: Context, preset: WallpaperPreset, w: Int, h: Int): Bitmap {
        val f = File(context.cacheDir, "wp_${preset.id}_${w}x${h}_v4.png")
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.let { if (it.width == w && it.height == h) return it }
        val bmp = WallpaperRenderer.render(preset, w, h)
        // write-then-rename so a process death mid-write never leaves a truncated image behind
        runCatching {
            val tmp = File(context.cacheDir, f.name + ".tmp")
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(f)
        }
        return bmp
    }

    fun renderThumb(preset: WallpaperPreset, w: Int, h: Int): Bitmap = WallpaperRenderer.render(preset, w, h)

    fun importCustom(context: Context, uri: Uri, w: Int, h: Int): Boolean = runCatching {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
        }
        val cropped = centerCrop(bmp, w, h)
        val tmp = File(context.filesDir, CUSTOM + ".tmp")
        FileOutputStream(tmp).use { cropped.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        tmp.renameTo(customFile(context))
    }.getOrDefault(false)

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        fun pass(input: IntArray, output: IntArray, horizontal: Boolean) {
            val len = if (horizontal) w else h
            val lines = if (horizontal) h else w
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var r = 0; var g = 0; var b = 0; var n = 0
                    for (k in -radius..radius) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val idx = if (horizontal) line * w + j else j * w + line
                        val c = input[idx]
                        r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                    }
                    val o = if (horizontal) line * w + i else i * w + line
                    output[o] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
        pass(px, tmp, true)
        pass(tmp, px, false)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
