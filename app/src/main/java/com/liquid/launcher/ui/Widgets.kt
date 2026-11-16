package com.liquid.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.liquid.launcher.LauncherApp
import com.liquid.launcher.data.IconAppearance
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.data.WidgetItem
import com.liquid.launcher.data.WidgetKinds
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.GlassVariant
import com.liquid.launcher.glass.LocalGlassEnvironment
import com.liquid.launcher.glass.liquidGlass
import com.liquid.launcher.theme.LocalTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun rememberNowState(tickMs: Long): androidx.compose.runtime.State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMs) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(tickMs - (now.longValue % tickMs))
        }
    }
    return now
}

@Composable
private fun rememberNow(tickMs: Long): Long = rememberNowState(tickMs).value

/** Widget chrome: solid in Default/Dark appearance, Liquid Glass in Clear/Tinted (as on iOS 26). */
@Composable
fun WidgetSurface(modifier: Modifier = Modifier, lightBg: Color = Color.White, content: @Composable BoxScope.(glass: Boolean, dark: Boolean) -> Unit) {
    val spec = LocalTheme.current
    val prefs = LocalPrefs.current
    val env = LocalGlassEnvironment.current
    val home = LocalHome.current
    val shape = GlassShape.rounded(spec.widgetRadius, 2.4f)
    val glass = prefs.appearance == IconAppearance.CLEAR || prefs.appearance == IconAppearance.TINTED || spec.style != ThemeStyle.IOS
    val dark = env.dark || prefs.appearance == IconAppearance.DARK
    val m = if (glass) {
        val st = when {
            prefs.appearance == IconAppearance.TINTED -> spec.iconGlass.copy(tintColor = if (darkTone()) Color(0xFF0B0B0F) else tintColor(), tintStrength = 0.5f, refraction = 14.dp, bevel = 20.dp)
            spec.style == ThemeStyle.IOS -> spec.iconGlass.copy(refraction = 14.dp, bevel = 22.dp, blur = 4.dp)
            else -> spec.folderTileGlass.copy(variant = GlassVariant.CLEAR, frost = 0.12f)
        }
        modifier.liquidGlass(home.wallpaperBackdrop, shape, st)
    } else {
        modifier.clip(shape).background(if (dark) Color(0xFF1C1C1E) else lightBg)
    }
    Box(m) { content(glass, dark && !glass) }
}

@Composable
fun AnalogClockWidget(modifier: Modifier) {
    // read in the draw phase only: ticking redraws the hands without recomposing anything
    val nowState = rememberNowState(1000)
    WidgetSurface(modifier) { glass, dark ->
        val face = if (glass) Color.White else if (dark) Color.White else Color.Black
        val accent = Color(0xFFFF9500)
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val r = size.minDimension / 2f
            val c = center
            if (!glass) drawCircle(if (dark) Color.Black else Color.White, r, c)
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0).toFloat()
                val major = i % 5 == 0
                val len = if (major) r * 0.12f else r * 0.05f
                val o = r * 0.94f
                drawLine(
                    face.copy(alpha = if (major) 0.9f else 0.35f),
                    Offset(c.x + sin(a) * o, c.y - cos(a) * o),
                    Offset(c.x + sin(a) * (o - len), c.y - cos(a) * (o - len)),
                    strokeWidth = if (major) 2.2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            val cal = Calendar.getInstance().apply { timeInMillis = nowState.value }
            val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
            val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
            val s = cal.get(Calendar.SECOND).toFloat()
            rotate(h * 30f, c) { drawLine(face, c, Offset(c.x, c.y - r * 0.5f), 5.dp.toPx(), StrokeCap.Round) }
            rotate(m * 6f, c) { drawLine(face, c, Offset(c.x, c.y - r * 0.78f), 3.5.dp.toPx(), StrokeCap.Round) }
            rotate(s * 6f, c) {
                drawLine(accent, Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r * 0.86f), 1.4.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(accent, 3.dp.toPx(), c)
            drawCircle(if (dark || glass) Color.Black else Color.White, 1.3.dp.toPx(), c)
        }
    }
}

@Composable
fun CalendarMonthWidget(modifier: Modifier) {
    val now = rememberNow(60_000)
    WidgetSurface(modifier) { glass, dark ->
        val fg = if (glass || dark) Color.White else Color.Black
        val red = Color(0xFFFF3B30)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(now)).uppercase()
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val firstDow = (first.get(Calendar.DAY_OF_WEEK) - first.firstDayOfWeek + 7) % 7
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Txt(month, 11.sp, if (glass) Color.White else red, FontWeight.Bold, letter = 0.3.sp)
            Spacer(Modifier.height(4.dp))
            val letters = run {
                val f = SimpleDateFormat("EEEEE", Locale.getDefault())
                val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
                List(7) { f.format(c.time).also { c.add(Calendar.DAY_OF_MONTH, 1) } }
            }
            Row(Modifier.fillMaxWidth()) {
                letters.forEach { Txt(it, 8.5.sp, fg.copy(alpha = 0.55f), FontWeight.SemiBold, Modifier.weight(1f), align = TextAlign.Center) }
            }
            val weeks = (firstDow + days + 6) / 7
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (d in 0 until 7) {
                            val day = w * 7 + d - firstDow + 1
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (day in 1..days) {
                                    if (day == today) Box(Modifier.size(15.dp).clip(CircleShape).background(if (glass) Color.White else red))
                                    Txt(
                                        day.toString(), 8.5.sp,
                                        if (day == today) (if (glass) Color.Black else Color.White) else fg,
                                        if (day == today) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryWidget(modifier: Modifier) {
    val context = LocalContext.current
    var level by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                level = if (l >= 0) l * 100 / s else -1
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(r) }
    }
    WidgetSurface(modifier) { glass, dark ->
        val fg = if (glass || dark) Color.White else Color.Black
        val ring = when {
            level in 0..20 && !charging -> Color(0xFFFF3B30)
            else -> Color(0xFF34C759)
        }
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(78.dp)) {
                    val sw = 7.dp.toPx()
                    drawCircle(fg.copy(alpha = 0.16f), size.minDimension / 2 - sw / 2, style = Stroke(sw))
                    drawArc(ring, -90f, 360f * (level.coerceAtLeast(0) / 100f), false,
                        topLeft = Offset(sw / 2, sw / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                        style = Stroke(sw, cap = StrokeCap.Round))
                }
                GlyphIcon(if (charging) Glyphs.Bolt else Glyphs.Phone, fg, 26.dp)
            }
            Txt(if (level >= 0) "$level%" else "—", 20.sp, fg, FontWeight.SemiBold)
        }
    }
}

@Composable
fun DigitalClockWidget(modifier: Modifier) {
    val now = rememberNow(1000)
    val spec = LocalTheme.current
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(Date(now))
    Box(modifier.padding(horizontal = 6.dp), contentAlignment = if (spec.style == ThemeStyle.HYPEROS) Alignment.CenterStart else Alignment.Center) {
        Column(horizontalAlignment = if (spec.style == ThemeStyle.HYPEROS) Alignment.Start else Alignment.CenterHorizontally) {
            Txt(time, if (spec.style == ThemeStyle.HYPEROS) 64.sp else 58.sp, Color.White,
                if (spec.style == ThemeStyle.HYPEROS) FontWeight.Light else FontWeight.Normal, letter = (-1).sp)
            Txt(date, 15.sp, Color.White.copy(alpha = 0.9f), FontWeight.Medium)
        }
    }
}

@Composable
fun SystemWidget(item: WidgetItem, modifier: Modifier, onLongPress: () -> Unit) {
    val spec = LocalTheme.current
    val app = LauncherApp.instance
    Box(modifier.clip(GlassShape.rounded(spec.widgetRadius))) {
        AndroidView(
            factory = { ctx ->
                app.widgets.createView(ctx, item.appWidgetId)?.apply {
                    setOnLongClickListener { onLongPress(); true }
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                } ?: FrameLayout(ctx)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun BuiltInWidget(item: WidgetItem, modifier: Modifier, onLongPress: () -> Unit = {}) {
    when (item.kind) {
        WidgetKinds.CLOCK -> AnalogClockWidget(modifier)
        WidgetKinds.CALENDAR -> CalendarMonthWidget(modifier)
        WidgetKinds.BATTERY -> BatteryWidget(modifier)
        WidgetKinds.DIGITAL_CLOCK -> DigitalClockWidget(modifier)
        WidgetKinds.SYSTEM -> SystemWidget(item, modifier, onLongPress)
        else -> Box(modifier)
    }
}
