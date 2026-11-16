package com.liquid.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.liquid.launcher.LauncherApp
import com.liquid.launcher.data.AppearanceTone
import com.liquid.launcher.data.HomeLayout
import com.liquid.launcher.data.IconAppearance
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.data.WidgetItem
import com.liquid.launcher.data.WidgetKinds
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.LocalGlassEnvironment
import com.liquid.launcher.glass.liquidGlass
import com.liquid.launcher.theme.LocalTheme

/** iOS 26 jiggle-mode chrome: "Edit" menu on the left, "Done" on the right. */
@Composable
fun IosEditTopBar(controller: HomeController, metrics: GridMetrics) {
    val appear = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 420f)) }
    Row(
        Modifier.fillMaxWidth().padding(top = metrics.statusTop + 4.dp, start = 16.dp, end = 16.dp)
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * -24.dp.toPx() },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassCapsuleButton("Edit", controller.screenBackdrop, height = 36.dp) { controller.editMenu = !controller.editMenu }
        GlassCapsuleButton("Done", controller.screenBackdrop, height = 36.dp) { controller.exitEdit() }
    }
}

@Composable
fun EditMenuOverlay(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val progress = remember { Animatable(0f) }
    rememberAnimatedPresence(if (controller.editMenu) true else null, progress, 0.72f, 450f) ?: return
    val dark = LocalGlassEnvironment.current.dark
    val fg = if (dark) Color.White else Color.Black
    val p = progress.value
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controller.editMenu = false } }) {
        Column(
            Modifier.offset(16.dp, metrics.statusTop + 48.dp).width(240.dp)
                .graphicsLayer {
                    val s = 0.4f + 0.6f * p; scaleX = s; scaleY = s; alpha = p.coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
                .liquidGlass(controller.screenBackdrop, spec.menuShape, spec.menuGlass)
                .padding(vertical = 6.dp)
        ) {
            listOf(
                Triple("Add Widget", Glyphs.Widgets) { controller.editMenu = false; controller.widgetPicker = true },
                Triple("Customize", Glyphs.Palette) { controller.editMenu = false; controller.customize = true },
                Triple("Launcher Settings", Glyphs.Gear) { controller.editMenu = false; controller.openSettings() },
            ).forEachIndexed { i, (t, ic, run) ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(0.5.dp).background(fg.copy(alpha = 0.12f)))
                Row(Modifier.fillMaxWidth().height(46.dp).pressable(run, 0.98f).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(t, 16.sp, fg, modifier = Modifier.weight(1f))
                    GlyphIcon(ic, fg, 20.dp)
                }
            }
        }
    }
}

/** One UI / HyperOS edit mode: pages shrink and a toolbar appears at the bottom. */
@Composable
fun OneUiEditBar(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val p by animateFloatAsState(if (controller.editMode) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 420f), label = "edbar")
    if (p <= 0.01f) return
    val items: List<Triple<String, ImageVector, () -> Unit>> = listOf(
        Triple("Wallpaper", Glyphs.Image) { controller.openSettings() },
        Triple(if (spec.style == ThemeStyle.ONEUI) "Themes" else "Icons", Glyphs.Palette) { controller.customize = true },
        Triple("Widgets", Glyphs.Widgets) { controller.widgetPicker = true },
        Triple("Settings", Glyphs.Gear) { controller.openSettings() },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.navigationBarsPadding().padding(bottom = 18.dp, start = 18.dp, end = 18.dp).fillMaxWidth().height(84.dp)
                .graphicsLayer { alpha = p; translationY = (1f - p) * 140.dp.toPx() }
                .liquidGlass(controller.screenBackdrop, GlassShape.rounded(30.dp), spec.panelGlass.copy(frost = 0.05f)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (t, ic, run) ->
                Column(Modifier.pressable(run).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphIcon(ic, Color.White, 24.dp)
                    Spacer(Modifier.height(6.dp))
                    Txt(t, 12.sp, Color.White, FontWeight.Medium)
                }
            }
        }
        // tap-to-exit hint like One UI's page editor
        Box(
            Modifier.fillMaxWidth().padding(top = metrics.statusTop + 10.dp).align(Alignment.TopCenter)
                .graphicsLayer { alpha = p },
            contentAlignment = Alignment.Center,
        ) {
            GlassCapsuleButton("Done", controller.screenBackdrop, height = 34.dp) { controller.exitEdit() }
        }
    }
}

/** iOS 26 "Customize" sheet: Default / Dark / Clear / Tinted, Small / Large, light/dark and tint. */
@Composable
fun CustomizeSheet(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val progress = remember { Animatable(0f) }
    rememberAnimatedPresence(if (controller.customize) true else null, progress, 0.82f, 380f) ?: return
    val prefs = LocalPrefs.current
    val app = LauncherApp.instance
    val dark = LocalGlassEnvironment.current.dark
    val fg = if (dark) Color.White else Color.Black
    val p = progress.value
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controller.customize = false } }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.navigationBarsPadding().padding(10.dp).fillMaxWidth()
                .graphicsLayer { translationY = (1f - p) * with(density) { 420.dp.toPx() } }
                .pointerInput(Unit) { detectTapGestures { } }
                .liquidGlass(controller.screenBackdrop, GlassShape.rounded(38.dp), spec.menuGlass)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Segmented(listOf("Small", "Large"), if (prefs.largeIcons) 1 else 0, { i -> app.prefs.update { it.copy(largeIcons = i == 1, showLabels = i == 0) } }, dark, Modifier.width(150.dp))
                Spacer(Modifier.weight(1f))
                val toneIcon = when (prefs.tone) { AppearanceTone.AUTO -> Glyphs.Auto; AppearanceTone.LIGHT -> Glyphs.Sun; AppearanceTone.DARK -> Glyphs.Moon }
                Box(
                    Modifier.size(38.dp).clip(GlassShape.Capsule).background(fg.copy(alpha = 0.1f))
                        .pressable({
                            app.prefs.update {
                                it.copy(tone = when (it.tone) { AppearanceTone.AUTO -> AppearanceTone.LIGHT; AppearanceTone.LIGHT -> AppearanceTone.DARK; AppearanceTone.DARK -> AppearanceTone.AUTO })
                            }
                        }),
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(toneIcon, fg, 20.dp) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconAppearance.entries.forEach { a ->
                    val sel = prefs.appearance == a
                    Column(
                        Modifier.weight(1f).padding(horizontal = 4.dp).clip(GlassShape.rounded(20.dp))
                            .background(if (sel) fg.copy(alpha = 0.12f) else Color.Transparent)
                            .pressable({ app.prefs.update { it.copy(appearance = a) } })
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val sample = controller.appsByKey.values.firstOrNull()
                        IconArt(rememberIconBundle(sample, 48.dp), 48.dp, appearanceOverride = a)
                        Spacer(Modifier.height(8.dp))
                        Txt(a.title, 13.sp, fg, if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            if (prefs.appearance == IconAppearance.TINTED) {
                Spacer(Modifier.height(16.dp))
                val hues = (0..12).map { Color.hsv(it * 30f % 360f, 0.85f, 1f) }
                GradientSlider(prefs.tintHue / 360f, { v -> app.prefs.update { it.copy(tintHue = v * 360f) } }, hues)
                Spacer(Modifier.height(10.dp))
                GradientSlider(prefs.tintSaturation, { v -> app.prefs.update { it.copy(tintSaturation = v) } },
                    listOf(Color.White, Color.hsv(prefs.tintHue, 1f, 1f)))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Liquid Glass", 15.sp, fg, FontWeight.Medium, Modifier.weight(1f))
                Segmented(listOf("Clear", "Tinted"), if (prefs.glassTinted) 1 else 0, { i -> app.prefs.update { it.copy(glassTinted = i == 1) } }, dark, Modifier.width(160.dp))
            }
        }
    }
}

@Composable
fun WidgetPickerSheet(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val progress = remember { Animatable(0f) }
    rememberAnimatedPresence(if (controller.widgetPicker) true else null, progress, 0.86f, 360f) ?: return
    val dark = LocalGlassEnvironment.current.dark
    val fg = if (dark) Color.White else Color.Black
    val p = progress.value
    val density = LocalDensity.current
    val context = LocalContext.current
    val providers = remember { LauncherApp.instance.widgets.providers() }
    val w2 = metrics.cellW * 2 - (metrics.cellW - metrics.icon)
    val h2 = metrics.cellH + metrics.icon
    Box(
        Modifier.fillMaxSize()
            .backdropBlur(controller.screenBackdrop, 20.dp, { progress.value }, Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) { detectTapGestures { controller.widgetPicker = false } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.86f)
                .graphicsLayer { translationY = (1f - p) * with(density) { 700.dp.toPx() } }
                .pointerInput(Unit) { detectTapGestures { } }
                .liquidGlass(controller.screenBackdrop, GlassShape.rounded(38.dp), spec.menuGlass.copy(frost = 0.4f))
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(36.dp, 5.dp).clip(GlassShape.Capsule).background(fg.copy(alpha = 0.25f)))
            Txt("Widgets", 26.sp, fg, FontWeight.Bold, Modifier.padding(start = 22.dp, top = 12.dp, bottom = 6.dp))
            LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = metrics.navBottom + 20.dp)) {
                item {
                    val builtIns = buildList {
                        add(Triple("Calendar", WidgetKinds.CALENDAR, 2 to 2))
                        add(Triple("Clock", WidgetKinds.CLOCK, 2 to 2))
                        add(Triple("Batteries", WidgetKinds.BATTERY, 2 to 2))
                        add(Triple("Digital Clock", WidgetKinds.DIGITAL_CLOCK, 4 to 2))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        builtIns.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { (name, kind, span) ->
                                    Column(
                                        Modifier.pressable({ controller.addWidget(WidgetItem(HomeLayout.newId(), kind, span.first.coerceAtMost(spec.columns), span.second)) }),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(Modifier.size(if (span.first == 4) w2 * 1.2f else w2 * 0.8f, h2 * 0.8f).clip(GlassShape.rounded(spec.widgetRadius)).background(Color.Black.copy(alpha = 0.35f))) {
                                            BuiltInWidget(WidgetItem("preview$kind", kind, span.first, span.second), Modifier.fillMaxSize())
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Txt(name, 13.sp, fg, FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (providers.isNotEmpty()) Txt("App Widgets", 18.sp, fg, FontWeight.SemiBold, Modifier.padding(start = 4.dp, bottom = 6.dp))
                }
                items(providers, key = { it.provider.flattenToString() }) { info ->
                    val label = remember(info) { info.loadLabel(context.packageManager) ?: "" }
                    val icon = remember(info) { runCatching { info.loadIcon(context, 0)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                    val appLabel = remember(info) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault("") }
                    Row(
                        Modifier.fillMaxWidth().pressable({ controller.widgetPicker = false; controller.requestSystemWidget?.invoke(info) }, 0.98f).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(GlassShape.rounded(10.dp))) else Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(label, 16.sp, fg, maxLines = 1)
                            Txt(appLabel, 13.sp, fg.copy(alpha = 0.55f), maxLines = 1)
                        }
                        GlyphIcon(Glyphs.Plus, spec.accent, 22.dp)
                    }
                }
            }
        }
    }
}
