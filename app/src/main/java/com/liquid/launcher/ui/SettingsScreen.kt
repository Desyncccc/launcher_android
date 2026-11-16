package com.liquid.launcher.ui

import android.app.role.RoleManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.liquid.launcher.LauncherApp
import com.liquid.launcher.data.AppearanceTone
import com.liquid.launcher.data.BadgeStore
import com.liquid.launcher.data.IconAppearance
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.continuousPath
import com.liquid.launcher.theme.LocalTheme
import com.liquid.launcher.theme.Themes
import com.liquid.launcher.wallpaper.WallpaperLoader
import com.liquid.launcher.wallpaper.WallpaperPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class SettingsColors(dark: Boolean, style: ThemeStyle) {
    val bg = if (dark) Color.Black else when (style) { ThemeStyle.IOS -> Color(0xFFF2F2F7); ThemeStyle.ONEUI -> Color(0xFFF4F4F4); ThemeStyle.HYPEROS -> Color(0xFFF7F7F7) }
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val fg = if (dark) Color.White else Color.Black
    val sub = if (dark) Color(0xFF8E8E93) else Color(0xFF6D6D72)
    val sep = if (dark) Color(0xFF38383A) else Color(0xFFE0E0E5)
}

@Composable
fun SettingsScreen(
    app: LauncherApp,
    onPickPhoto: () -> Unit,
    onSetDefault: () -> Unit,
    onBadgeAccess: () -> Unit,
    onGetIconPacks: () -> Unit,
    onClose: () -> Unit,
) {
    val prefs by app.prefs.state.collectAsState()
    val spec = specFor(prefs)
    val dark = isDark(prefs)
    val c = remember(dark, spec.style) { SettingsColors(dark, spec.style) }
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(false) }
    var badgeAccess by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        else context.packageManager.resolveActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == context.packageName
        badgeAccess = BadgeStore.hasAccess(context)
    }
    val packs = remember { app.iconPacks.installedPacks() }
    var confirmReset by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalTheme provides spec, LocalPrefs provides prefs) {
        LazyColumn(
            Modifier.fillMaxSize().background(c.bg),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                    if (spec.style == ThemeStyle.ONEUI) Spacer(Modifier.height(110.dp)) else Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("Liquid Launcher", if (spec.style == ThemeStyle.ONEUI) 32.sp else 34.sp, c.fg, FontWeight.Bold, Modifier.weight(1f))
                        Box(Modifier.size(36.dp).clip(GlassShape.Capsule).background(c.fg.copy(alpha = 0.08f)).pressable(onClose), contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyphs.Close, c.fg, 18.dp)
                        }
                    }
                    if (spec.style == ThemeStyle.ONEUI) Spacer(Modifier.height(40.dp)) else Spacer(Modifier.height(14.dp))
                }
            }
            if (!isDefault) item {
                Card(c) {
                    Row(Modifier.fillMaxWidth().pressable(onSetDefault, 0.98f).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Glyphs.Home, Color(0xFF0A84FF))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt("Set as Home app", 17.sp, c.fg, FontWeight.SemiBold)
                            Txt("Make Liquid Launcher your default launcher", 13.sp, c.sub)
                        }
                        GlyphIcon(Glyphs.ChevronRight, c.sub, 16.dp)
                    }
                }
            }
            item { Header("Style", c) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ThemeStyle.entries) { st ->
                        ThemeCard(st, st == prefs.theme, c) {
                            app.prefs.update { p ->
                                val oldDefault = Themes.of(p.theme).defaultWallpaper
                                p.copy(
                                    theme = st, columns = 0, rows = 0,
                                    wallpaperId = if (p.wallpaperId == oldDefault) Themes.of(st).defaultWallpaper else p.wallpaperId,
                                )
                            }
                            app.icons.clear()
                        }
                    }
                }
            }
            item { Header("Home Screen", c) }
            item {
                Card(c) {
                    Column(Modifier.padding(16.dp)) {
                        Txt("Icon appearance", 15.sp, c.fg, FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Segmented(IconAppearance.entries.map { it.title }, prefs.appearance.ordinal, { i -> app.prefs.update { it.copy(appearance = IconAppearance.entries[i]) } }, dark, Modifier.fillMaxWidth())
                        if (prefs.appearance == IconAppearance.CLEAR || prefs.appearance == IconAppearance.TINTED) {
                            Spacer(Modifier.height(12.dp))
                            Segmented(listOf("Automatic", "Light", "Dark"), prefs.tone.ordinal, { i -> app.prefs.update { it.copy(tone = AppearanceTone.entries[i]) } }, dark, Modifier.fillMaxWidth())
                        }
                        if (prefs.appearance == IconAppearance.TINTED) {
                            Spacer(Modifier.height(14.dp))
                            GradientSlider(prefs.tintHue / 360f, { v -> app.prefs.update { it.copy(tintHue = v * 360f) } }, (0..12).map { Color.hsv(it * 30f % 360f, 0.85f, 1f) })
                        }
                    }
                    Sep(c)
                    ToggleRow("Large icons", "Hides app names, like iOS", prefs.largeIcons, c, Glyphs.Apps, Color(0xFF5E5CE6)) { v -> app.prefs.update { it.copy(largeIcons = v, showLabels = !v) } }
                    Sep(c)
                    ToggleRow("Show app names", null, prefs.showLabels, c, Glyphs.Pencil, Color(0xFF8E8E93)) { v -> app.prefs.update { it.copy(showLabels = v) } }
                    if (spec.style != ThemeStyle.IOS) {
                        Sep(c)
                        StepperRow("Grid columns", spec.columns, 3..6, c) { v -> app.prefs.update { it.copy(columns = v) } }
                        Sep(c)
                        StepperRow("Grid rows", spec.rows, 4..7, c) { v -> app.prefs.update { it.copy(rows = v) } }
                    }
                }
            }
            item { Header("Liquid Glass", c) }
            item {
                Card(c) {
                    Column(Modifier.padding(16.dp)) {
                        Txt("Choose your preferred look for Liquid Glass.", 13.sp, c.sub)
                        Spacer(Modifier.height(10.dp))
                        Segmented(listOf("Clear", "Tinted"), if (prefs.glassTinted) 1 else 0, { i -> app.prefs.update { it.copy(glassTinted = i == 1) } }, dark, Modifier.fillMaxWidth())
                    }
                    Sep(c)
                    ToggleRow("Motion lighting", "Specular highlights follow the gyroscope", prefs.gyroLighting, c, Glyphs.Sun, Color(0xFFFF9F0A)) { v -> app.prefs.update { it.copy(gyroLighting = v) } }
                    Sep(c)
                    ToggleRow("Reduce transparency", null, prefs.reduceTransparency, c, Glyphs.Stack, Color(0xFF30B0C7)) { v -> app.prefs.update { it.copy(reduceTransparency = v) } }
                    Sep(c)
                    Column(Modifier.padding(16.dp)) {
                        Txt("Appearance", 15.sp, c.fg, FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Segmented(listOf("System", "Light", "Dark"), prefs.darkMode, { i -> app.prefs.update { it.copy(darkMode = i) } }, dark, Modifier.fillMaxWidth())
                    }
                    if (Build.VERSION.SDK_INT < 33) {
                        Sep(c)
                        Txt("Real-time refraction needs Android 13+. This device uses the frosted fallback.", 13.sp, c.sub, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            item { Header("Wallpaper", c) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(WallpaperPresets.all) { preset ->
                        val thumb by produceState<ImageBitmap?>(null, preset.id) {
                            value = withContext(Dispatchers.Default) { WallpaperLoader.renderThumb(preset, 180, 390).asImageBitmap() }
                        }
                        val sel = prefs.wallpaperId == preset.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(78.dp, 168.dp).clip(GlassShape.rounded(16.dp))
                                    .then(if (sel) Modifier.border(3.dp, spec.accent, GlassShape.rounded(16.dp)) else Modifier)
                                    .background(Color.Gray)
                                    .pressable({ app.prefs.update { it.copy(wallpaperId = preset.id) } })
                            ) { thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                            Spacer(Modifier.height(6.dp))
                            Txt(preset.name, 12.sp, c.sub)
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(78.dp, 168.dp).clip(GlassShape.rounded(16.dp))
                                    .then(if (prefs.wallpaperId == "custom") Modifier.border(3.dp, spec.accent, GlassShape.rounded(16.dp)) else Modifier)
                                    .background(c.card).pressable(onPickPhoto),
                                contentAlignment = Alignment.Center,
                            ) { GlyphIcon(Glyphs.Image, spec.accent, 28.dp) }
                            Spacer(Modifier.height(6.dp))
                            Txt("Photos", 12.sp, c.sub)
                        }
                    }
                }
            }
            item { Header("Icons", c) }
            item {
                Card(c) {
                    PackRow("Original app icons", "Real icons from each installed app, shaped for ${spec.style.title}", prefs.iconPack.isEmpty(), c) {
                        app.prefs.update { it.copy(iconPack = "") }; app.icons.clear()
                    }
                    packs.forEach { pk ->
                        Sep(c)
                        PackRow(pk.label, "Icon pack", prefs.iconPack == pk.packageName, c) {
                            app.prefs.update { it.copy(iconPack = pk.packageName) }; app.icons.clear()
                        }
                    }
                    Sep(c)
                    Row(Modifier.fillMaxWidth().pressable(onGetIconPacks, 0.98f).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Get icon packs…", 16.sp, spec.accent, modifier = Modifier.weight(1f))
                        GlyphIcon(Glyphs.ChevronRight, c.sub, 16.dp)
                    }
                }
                Txt(
                    "Any ADW/Nova-compatible icon pack works – install an iOS, One UI or HyperOS style pack and pick it here.",
                    12.sp, c.sub, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp)
                )
            }
            item { Header("Notifications", c) }
            item {
                Card(c) {
                    ToggleRow("Notification badges", if (badgeAccess) "Access granted" else "Requires notification access", prefs.badges && badgeAccess, c, Glyphs.Info, Color(0xFFFF3B30)) { v ->
                        if (v && !badgeAccess) onBadgeAccess()
                        app.prefs.update { it.copy(badges = v) }
                    }
                }
            }
            item { Header("Layout", c) }
            item {
                Card(c) {
                    Row(Modifier.fillMaxWidth().pressable({ confirmReset = true }, 0.98f).padding(16.dp)) {
                        Txt(if (confirmReset) "Tap again to reset Home Screen" else "Reset Home Screen Layout", 16.sp, Color(0xFFFF3B30))
                    }
                }
                LaunchedEffect(confirmReset) {
                    if (confirmReset) {
                        kotlinx.coroutines.delay(2500); confirmReset = false
                    }
                }
                if (confirmReset) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Box(Modifier.clip(GlassShape.Capsule).background(Color(0xFFFF3B30)).pressable({ app.layout.reset(); confirmReset = false }).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Txt("Reset", 15.sp, Color.White, FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Txt(
                    "Liquid Glass is rendered in real time with an AGSL lens shader (Android 13+). Typeface: Inter, SIL Open Font License.",
                    12.sp, c.sub, modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp).navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun Header(text: String, c: SettingsColors) {
    val spec = LocalTheme.current
    Txt(
        if (spec.style == ThemeStyle.ONEUI) text else text,
        if (spec.style == ThemeStyle.IOS) 13.sp else 14.sp,
        if (spec.style == ThemeStyle.ONEUI) spec.accent else c.sub,
        if (spec.style == ThemeStyle.IOS) FontWeight.Normal else FontWeight.SemiBold,
        Modifier.padding(start = 36.dp, top = 22.dp, bottom = 7.dp),
    )
}

@Composable
private fun Card(c: SettingsColors, content: @Composable () -> Unit) {
    val spec = LocalTheme.current
    val r = when (spec.style) { ThemeStyle.IOS -> 26.dp; ThemeStyle.ONEUI -> 26.dp; ThemeStyle.HYPEROS -> 20.dp }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(GlassShape.rounded(r)).background(c.card)) { content() }
}

@Composable
private fun Sep(c: SettingsColors) {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.6.dp).background(c.sep))
}

@Composable
private fun RowIcon(icon: ImageVector, color: Color) {
    val spec = LocalTheme.current
    if (spec.style != ThemeStyle.IOS) return
    Box(Modifier.size(30.dp).clip(GlassShape.icon(0.28f, 2.4f)).background(color), contentAlignment = Alignment.Center) {
        GlyphIcon(icon, Color.White, 18.dp)
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, c: SettingsColors, icon: ImageVector, iconColor: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon, iconColor)
        if (LocalTheme.current.style == ThemeStyle.IOS) Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, 16.sp, c.fg)
            if (sub != null) Txt(sub, 13.sp, c.sub)
        }
        ThemedSwitch(checked, onChange)
    }
}

@Composable
private fun StepperRow(title: String, value: Int, range: IntRange, c: SettingsColors, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, 16.sp, c.fg, modifier = Modifier.weight(1f))
        Row(Modifier.clip(GlassShape.Capsule).background(c.fg.copy(alpha = 0.07f)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp, 34.dp).pressable({ if (value > range.first) onChange(value - 1) }), contentAlignment = Alignment.Center) { GlyphIcon(Glyphs.Minus, c.fg, 16.dp) }
            Txt(value.toString(), 16.sp, c.fg, FontWeight.SemiBold)
            Box(Modifier.size(44.dp, 34.dp).pressable({ if (value < range.last) onChange(value + 1) }), contentAlignment = Alignment.Center) { GlyphIcon(Glyphs.Plus, c.fg, 16.dp) }
        }
    }
}

@Composable
private fun PackRow(title: String, sub: String, selected: Boolean, c: SettingsColors, onClick: () -> Unit) {
    val spec = LocalTheme.current
    Row(Modifier.fillMaxWidth().pressable(onClick, 0.98f).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, 16.sp, c.fg)
            Txt(sub, 13.sp, c.sub)
        }
        if (selected) GlyphIcon(Glyphs.Check, spec.accent, 20.dp)
    }
}

/** Little phone mock-up that previews each style's grid, icon shape and dock. */
@Composable
private fun ThemeCard(style: ThemeStyle, selected: Boolean, c: SettingsColors, onClick: () -> Unit) {
    val s = Themes.of(style)
    val accent = LocalTheme.current.accent
    val thumb by produceState<ImageBitmap?>(null, style) {
        value = withContext(Dispatchers.Default) { WallpaperLoader.renderThumb(WallpaperPresets.byId(s.defaultWallpaper), 180, 390).asImageBitmap() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(104.dp, 212.dp).clip(GlassShape.rounded(22.dp))
                .then(if (selected) Modifier.border(3.dp, accent, GlassShape.rounded(22.dp)) else Modifier)
                .background(Color.DarkGray)
                .pressable(onClick)
        ) {
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Canvas(Modifier.fillMaxSize()) {
                val cols = s.columns
                val icon = size.width * s.iconFraction
                val cellW = size.width * 0.93f / cols
                val left = size.width * 0.035f
                val top = size.height * 0.1f
                val cellH = size.height * 0.11f
                for (r in 0 until s.rows.coerceAtMost(5)) for (col in 0 until cols) {
                    val x = left + cellW * col + (cellW - icon) / 2
                    val y = top + cellH * r
                    val p = continuousPath(icon, icon, icon * s.iconShape.radiusFraction, s.iconShape.exponent, x, y)
                    drawPath(p, Color.White.copy(alpha = 0.78f))
                }
                val dockH = icon + size.height * 0.035f
                val dockTop = size.height - dockH - size.height * 0.02f
                if (s.dockPlatter) {
                    val dp = continuousPath(size.width * 0.94f, dockH, dockH * 0.42f, 2.4f, size.width * 0.03f, dockTop)
                    drawPath(dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.18f)), startY = dockTop, endY = dockTop + dockH))
                    val pillW = size.width * 0.26f
                    drawPath(continuousPath(pillW, 7.dp.toPx(), 99f, 2f, (size.width - pillW) / 2, dockTop - 11.dp.toPx()), Color.White.copy(alpha = 0.45f))
                } else {
                    for (i in 0 until 3) drawCircle(Color.White.copy(alpha = if (i == 0) 1f else 0.5f), 1.6.dp.toPx(), Offset(size.width / 2 + (i - 1) * 7.dp.toPx(), dockTop - 6.dp.toPx()))
                }
                val n = s.dockSlots
                for (i in 0 until n) {
                    val slot = size.width * 0.94f / n
                    val x = size.width * 0.03f + slot * i + (slot - icon) / 2
                    drawPath(continuousPath(icon, icon, icon * s.iconShape.radiusFraction, s.iconShape.exponent, x, dockTop + (dockH - icon) / 2), Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(style.title, 14.sp, c.fg, FontWeight.SemiBold)
        Txt(style.subtitle, 12.sp, c.sub)
    }
}
