package com.liquid.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.liquid.launcher.data.AppItem
import com.liquid.launcher.data.FolderItem
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.data.WidgetItem
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.LocalGlassEnvironment
import com.liquid.launcher.glass.liquidGlass
import com.liquid.launcher.theme.FolderPanelStyle
import com.liquid.launcher.theme.LocalTheme
import com.liquid.launcher.theme.MenuStyle
import kotlin.math.ceil
import kotlin.math.min

/** Keeps the last non-null value around while an exit animation plays. */
@Composable
fun <T> rememberAnimatedPresence(value: T?, open: Animatable<Float, *>, dampingOpen: Float = 0.78f, stiffOpen: Float = 360f): T? {
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != null) {
            shown = value
            open.animateTo(1f, spring(dampingRatio = dampingOpen, stiffness = stiffOpen))
        } else {
            open.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            shown = null
        }
    }
    return shown
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

// ===================================================================== folders

@Composable
fun FolderOverlay(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val progress = remember { Animatable(0f) }
    val state = rememberAnimatedPresence(controller.folder, progress) ?: return
    val folder = controller.layout.findItem(state.folderId) as? FolderItem
    if (folder == null) { LaunchedEffect(Unit) { controller.folder = null }; return }
    val density = LocalDensity.current
    val p = progress.value
    val cols = spec.folderColumns
    val perPage = when (spec.folderPanel) { FolderPanelStyle.IOS, FolderPanelStyle.HYPER -> cols * 3; FolderPanelStyle.ONEUI -> cols * 4 }
    val rows = when (spec.folderPanel) {
        FolderPanelStyle.IOS, FolderPanelStyle.HYPER -> 3
        FolderPanelStyle.ONEUI -> ceil(min(folder.apps.size, perPage) / cols.toFloat()).toInt().coerceAtLeast(1)
    }
    val panelW = metrics.width * when (spec.folderPanel) { FolderPanelStyle.IOS -> 0.80f; FolderPanelStyle.ONEUI -> 0.88f; FolderPanelStyle.HYPER -> 0.84f }
    val cellW = (panelW - 24.dp) / cols
    val cellH = metrics.icon + 40.dp
    val header = if (spec.folderPanel == FolderPanelStyle.ONEUI) 58.dp else 0.dp
    val panelH = header + cellH * rows + 30.dp + if (folder.apps.size > perPage) 14.dp else 0.dp
    val panelLeft = (metrics.width - panelW) / 2
    val panelTop = (metrics.height - panelH) / 2 - 24.dp
    val anchor = state.anchor
    val aL = with(density) { anchor.left.toDp() }
    val aT = with(density) { anchor.top.toDp() }
    val aW = with(density) { anchor.width.toDp() }
    val l = lerp(aL, panelLeft, p)
    val t = lerp(aT, panelTop, p)
    val w = lerp(aW, panelW, p)
    val h = lerp(aW, panelH, p)
    val panelR = when (spec.folderPanel) { FolderPanelStyle.IOS -> 40.dp; FolderPanelStyle.ONEUI -> 30.dp; FolderPanelStyle.HYPER -> 36.dp }
    val r = lerp(aW * spec.folderShape.radiusFraction, panelR, p)
    val dark = LocalGlassEnvironment.current.dark
    val textColor = if (spec.folderPanel == FolderPanelStyle.ONEUI && !dark) Color.Black else Color.White

    Box(
        Modifier.fillMaxSize()
            .backdropBlur(controller.screenBackdrop, 30.dp, { progress.value }, Color.Black.copy(alpha = 0.16f))
            .pointerInput(Unit) { detectTapGestures { controller.folder = null } }
    ) {
        // title above the panel (iOS & HyperOS)
        if (spec.folderPanel != FolderPanelStyle.ONEUI) {
            FolderTitle(controller, folder, Modifier
                .offset(panelLeft + 4.dp, panelTop - 56.dp)
                .width(panelW)
                .graphicsLayer { alpha = p; translationY = (1f - p) * 30f }, 32.sp, Color.White, TextAlign.Start)
        }
        Box(
            Modifier.offset(l, t).size(w, h)
                .pointerInput(Unit) { detectTapGestures { } }
                .liquidGlass(
                    controller.screenBackdrop, GlassShape.rounded(r, 2.4f),
                    if (spec.folderPanel == FolderPanelStyle.ONEUI && !dark) spec.panelGlass.copy(frost = 0.5f) else spec.panelGlass,
                )
        ) {
            Column(Modifier.fillMaxSize().graphicsLayer {
                alpha = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                val s = lerpF(0.6f, 1f, p); scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
            }) {
                if (spec.folderPanel == FolderPanelStyle.ONEUI) {
                    FolderTitle(controller, folder, Modifier.fillMaxWidth().padding(top = 18.dp).height(40.dp), 20.sp, textColor, TextAlign.Center)
                }
                val pages = ceil(folder.apps.size / perPage.toFloat()).toInt().coerceAtLeast(1)
                val pager = rememberPagerState { pages }
                HorizontalPager(pager, Modifier.fillMaxWidth().height(cellH * rows + 30.dp)) { pg ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 15.dp)) {
                        folder.apps.drop(pg * perPage).take(perPage).forEachIndexed { i, key ->
                            val entry = controller.app(key)
                            Box(Modifier.offset(cellW * (i % cols), cellH * (i / cols)).size(cellW, cellH), contentAlignment = Alignment.TopCenter) {
                                FolderAppCell(controller, folder, key, entry, cellW, metrics, textColor)
                            }
                        }
                    }
                }
                if (pages > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(pages) { i ->
                            Box(Modifier.padding(3.dp).size(6.dp).clip(CircleShape).background(textColor.copy(alpha = if (i == pager.currentPage) 0.9f else 0.35f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTitle(controller: HomeController, folder: FolderItem, modifier: Modifier, size: androidx.compose.ui.unit.TextUnit, color: Color, align: TextAlign) {
    val spec = LocalTheme.current
    if (controller.editMode) {
        var text by remember(folder.id) { mutableStateOf(folder.name) }
        Box(modifier) {
            BasicTextField(
                text, { text = it; controller.renameFolder(folder.id, it) },
                singleLine = true,
                textStyle = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = spec.fontFamily, textAlign = align),
                cursorBrush = SolidColor(spec.accent),
                modifier = Modifier.fillMaxWidth().clip(GlassShape.rounded(14.dp)).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    } else {
        Box(modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { controller.enterEdit() }) }) {
            Txt(folder.name, size, color, FontWeight.Bold, Modifier.fillMaxWidth(), maxLines = 1, align = align)
        }
    }
}

@Composable
private fun FolderAppCell(controller: HomeController, folder: FolderItem, key: String, entry: com.liquid.launcher.data.AppEntry?, cellW: Dp, metrics: GridMetrics, textColor: Color) {
    val spec = LocalTheme.current
    val bounds = remember { LayoutRef() }
    val badges = LocalBadges.current
    Column(
        Modifier.width(cellW).itemGestures(
            key + folder.id, false,
            onTap = { if (entry != null) controller.launch(entry, bounds.rect()) },
            onLongPress = { controller.menu = MenuState(AppItem("f:$key", key), bounds.rect(), false, folder.id).also { controller.haptic() } },
            onDragStart = {},
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(metrics.icon).track(bounds).jiggle(controller.editMode, key)) {
            IconArt(rememberIconBundle(entry, metrics.icon), metrics.icon)
            if (LocalPrefs.current.badges) NotificationBadge(entry?.let { badges[it.packageName] } ?: 0, metrics.icon)
            if (controller.editMode && spec.style == ThemeStyle.IOS) DeleteBadge { controller.removeFromFolder(folder.id, key) }
        }
        Spacer(Modifier.height(6.dp))
        IconLabel(entry?.label ?: "", cellW, textColor)
    }
}

// ===================================================================== context menu

private data class MenuEntry(val title: String, val icon: ImageVector? = null, val destructive: Boolean = false, val bitmap: androidx.compose.ui.graphics.ImageBitmap? = null, val section: Int = 0, val run: () -> Unit)

@Composable
fun ContextMenuOverlay(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val progress = remember { Animatable(0f) }
    val state = rememberAnimatedPresence(controller.menu, progress, 0.72f, 420f) ?: return
    val density = LocalDensity.current
    val dark = LocalGlassEnvironment.current.dark
    val p = progress.value
    val entries = remember(state) { buildMenu(controller, state) }
    val textColor = if (dark) Color.White else Color.Black
    val menuW = when (spec.menuStyle) { MenuStyle.IOS -> 254.dp; MenuStyle.ONEUI -> 232.dp; MenuStyle.HYPER -> 228.dp }
    val a = state.anchor
    val aL = with(density) { a.left.toDp() }
    val aT = with(density) { a.top.toDp() }
    val aB = with(density) { a.bottom.toDp() }
    val aR = with(density) { a.right.toDp() }
    val rowH = if (spec.menuStyle == MenuStyle.IOS) 46.dp else 50.dp
    val estH = rowH * entries.size + 16.dp + if (spec.menuStyle == MenuStyle.ONEUI) 30.dp else 0.dp
    val below = aB + 12.dp + estH < metrics.height - metrics.navBottom - 10.dp
    val left = if ((aL + aR) / 2 < metrics.width / 2) aL.coerceAtLeast(16.dp) else (aR - menuW).coerceAtMost(metrics.width - menuW - 16.dp)
    val top = if (below) aB + 12.dp else aT - 12.dp - estH
    val originX = if ((aL + aR) / 2 < metrics.width / 2) 0.1f else 0.9f

    Box(
        Modifier.fillMaxSize()
            .backdropBlur(controller.screenBackdrop, 22.dp, { progress.value }, Color.Black.copy(alpha = 0.14f))
            .pointerInput(Unit) { detectTapGestures { controller.menu = null } }
    ) {
        // the lifted item
        Box(
            Modifier.offset(aL, aT).size(aR - aL, aB - aT)
                .graphicsLayer { val s = 1f + 0.07f * p; scaleX = s; scaleY = s }
        ) {
            when (val item = state.item) {
                is AppItem -> IconArt(rememberIconBundle(controller.app(item.appKey), aR - aL), aR - aL)
                is FolderItem -> FolderTile(item, aR - aL)
                is WidgetItem -> BuiltInWidget(item, Modifier.fillMaxSize())
                else -> {}
            }
        }
        Column(
            Modifier.offset(left, top).width(menuW).wrapContentHeight()
                .graphicsLayer {
                    val s = lerpF(0.35f, 1f, p)
                    scaleX = s; scaleY = s; alpha = p.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(originX, if (below) 0f else 1f)
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .liquidGlass(controller.screenBackdrop, spec.menuShape, spec.menuGlass)
                .padding(vertical = if (spec.menuStyle == MenuStyle.IOS) 6.dp else 8.dp)
        ) {
            val main = entries.filter { it.section < 9 }
            main.forEachIndexed { i, e ->
                if (i > 0 && main[i - 1].section != e.section && spec.menuStyle == MenuStyle.IOS) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).background(textColor.copy(alpha = 0.06f)))
                } else if (i > 0 && spec.menuStyle == MenuStyle.IOS) {
                    Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(0.5.dp).background(textColor.copy(alpha = 0.12f)))
                }
                MenuRow(e, rowH, textColor) { controller.menu = null; e.run() }
            }
            val bottom = entries.filter { it.section >= 9 }
            if (bottom.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).height(0.5.dp).background(textColor.copy(alpha = 0.15f)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    bottom.forEach { e ->
                        Column(
                            Modifier.pressable({ controller.menu = null; e.run() }).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            e.icon?.let { GlyphIcon(it, if (e.destructive) Color(0xFFFF453A) else textColor, 22.dp) }
                            Spacer(Modifier.height(4.dp))
                            Txt(e.title, 11.sp, textColor.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(e: MenuEntry, h: Dp, textColor: Color, onClick: () -> Unit) {
    val spec = LocalTheme.current
    val color = if (e.destructive) Color(0xFFFF3B30) else textColor
    Row(
        Modifier.fillMaxWidth().height(h).pressable(onClick, 0.98f).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconFirst = spec.menuStyle != MenuStyle.IOS
        if (iconFirst) {
            MenuIcon(e, color)
            Spacer(Modifier.width(14.dp))
        }
        Txt(e.title, 16.sp, color, FontWeight.Normal, Modifier.weight(1f), maxLines = 1)
        if (!iconFirst) MenuIcon(e, color)
    }
}

@Composable
private fun MenuIcon(e: MenuEntry, color: Color) {
    when {
        e.bitmap != null -> Image(e.bitmap, null, Modifier.size(22.dp).clip(GlassShape.rounded(6.dp)))
        e.icon != null -> GlyphIcon(e.icon, color, 21.dp)
    }
}

private fun buildMenu(controller: HomeController, state: MenuState): List<MenuEntry> {
    val spec = controller.spec
    val ios = spec.menuStyle == MenuStyle.IOS
    val one = spec.menuStyle == MenuStyle.ONEUI
    val out = mutableListOf<MenuEntry>()
    when (val item = state.item) {
        is AppItem -> {
            val e = controller.app(item.appKey)
            if (e != null) {
                controller.app.apps.shortcuts(e).forEach { s ->
                    val icon = controller.app.apps.shortcutIcon(s)?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                    out += MenuEntry(s.shortLabel?.toString() ?: "", bitmap = icon, section = 0) { controller.app.apps.startShortcut(s, null) }
                }
            }
            val inFolder = state.folderId
            if (ios) {
                out += MenuEntry("Edit Home Screen", Glyphs.Apps, section = 1) { controller.enterEdit() }
                if (e != null) out += MenuEntry("Share App", Glyphs.Share, section = 1) { controller.share(e) }
                if (inFolder == "__library__") {
                    if (e != null) out += MenuEntry("Delete App", Glyphs.Trash, destructive = true, section = 2) { controller.uninstall(e) }
                } else if (inFolder != null) {
                    out += MenuEntry("Remove from Folder", Glyphs.Folder, section = 2) { controller.removeFromFolder(inFolder, item.appKey) }
                } else {
                    out += MenuEntry("Remove App", Glyphs.MinusCircle, destructive = true, section = 2) { confirmRemove(controller, item) }
                }
            } else {
                val sec = if (one) 9 else 1
                if (e != null) out += MenuEntry("App info", Glyphs.Info, section = sec) { controller.app.apps.openAppInfo(e) }
                out += MenuEntry(if (one) "Select" else "Edit", Glyphs.Select, section = sec) { controller.enterEdit() }
                if (inFolder == "__library__") Unit
                else if (inFolder != null) out += MenuEntry("Remove", Glyphs.MinusCircle, section = sec) { controller.removeFromFolder(inFolder, item.appKey) }
                else out += MenuEntry("Remove", Glyphs.MinusCircle, section = sec) { controller.removeItem(item.id) }
                if (e != null && !one) out += MenuEntry("Uninstall", Glyphs.Trash, destructive = true, section = sec) { controller.uninstall(e) }
                if (e != null && one) out += MenuEntry("Uninstall", Glyphs.Trash, section = sec) { controller.uninstall(e) }
            }
        }
        is FolderItem -> {
            out += MenuEntry("Rename", Glyphs.Pencil, section = 0) { controller.enterEdit(); controller.openFolder(item.id, state.anchor) }
            out += MenuEntry(if (ios) "Edit Home Screen" else "Edit", Glyphs.Apps, section = 1) { controller.enterEdit() }
            out += MenuEntry("Remove Folder", Glyphs.MinusCircle, destructive = true, section = 2) { confirmRemove(controller, item) }
        }
        is WidgetItem -> {
            out += MenuEntry(if (ios) "Edit Home Screen" else "Edit", Glyphs.Apps, section = 0) { controller.enterEdit() }
            out += MenuEntry("Remove Widget", Glyphs.MinusCircle, destructive = true, section = 1) { confirmRemove(controller, item) }
        }
        else -> {}
    }
    return out
}

// ===================================================================== alerts

@Composable
fun AlertOverlay(controller: HomeController) {
    val spec = LocalTheme.current
    val progress = remember { Animatable(0f) }
    val state = rememberAnimatedPresence(controller.alert, progress, 0.7f, 520f) ?: return
    val dark = LocalGlassEnvironment.current.dark
    val p = progress.value
    val fg = if (dark) Color.White else Color.Black
    Box(
        Modifier.fillMaxSize()
            .backdropBlur(controller.screenBackdrop, 8.dp, { progress.value }, Color.Black.copy(alpha = 0.28f))
            .pointerInput(Unit) { detectTapGestures { controller.alert = null } },
        contentAlignment = if (spec.style == ThemeStyle.IOS) Alignment.Center else Alignment.BottomCenter,
    ) {
        val shape = when (spec.style) { ThemeStyle.IOS -> GlassShape.rounded(34.dp); ThemeStyle.ONEUI -> GlassShape.rounded(28.dp); ThemeStyle.HYPEROS -> GlassShape.rounded(30.dp) }
        Column(
            Modifier
                .padding(bottom = if (spec.style == ThemeStyle.IOS) 0.dp else 28.dp)
                .width(if (spec.style == ThemeStyle.IOS) 300.dp else 360.dp)
                .padding(horizontal = if (spec.style == ThemeStyle.IOS) 0.dp else 12.dp)
                .graphicsLayer {
                    val s = lerpF(1.12f, 1f, p); scaleX = s; scaleY = s; alpha = p.coerceIn(0f, 1f)
                    if (spec.style != ThemeStyle.IOS) translationY = (1f - p) * 120f
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .liquidGlass(controller.screenBackdrop, shape, spec.menuGlass.copy(frost = 0.45f))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = if (spec.style == ThemeStyle.IOS) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            val align = if (spec.style == ThemeStyle.IOS) TextAlign.Center else TextAlign.Start
            Txt(state.title, if (spec.style == ThemeStyle.IOS) 17.sp else 19.sp, fg, FontWeight.SemiBold, Modifier.fillMaxWidth(), align = align)
            Spacer(Modifier.height(6.dp))
            Txt(state.message, 14.sp, fg.copy(alpha = 0.75f), modifier = Modifier.fillMaxWidth(), align = align)
            Spacer(Modifier.height(18.dp))
            if (spec.style == ThemeStyle.IOS) {
                // iOS 26 alerts: stacked capsule buttons
                state.actions.forEach { a ->
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp)
                            .pressable({ controller.alert = null; a.run() }, 0.97f)
                            .clip(GlassShape.Capsule)
                            .background(if (a.cancel) fg.copy(alpha = 0.08f) else fg.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(a.title, 17.sp, if (a.destructive) Color(0xFFFF3B30) else fg, if (a.cancel) FontWeight.Medium else FontWeight.SemiBold)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.actions.sortedBy { if (it.cancel) 0 else 1 }.forEach { a ->
                        Box(
                            Modifier.weight(1f).height(46.dp)
                                .pressable({ controller.alert = null; a.run() }, 0.97f)
                                .clip(GlassShape.Capsule)
                                .background(
                                    when {
                                        a.cancel -> fg.copy(alpha = 0.08f)
                                        a.destructive && spec.style == ThemeStyle.HYPEROS -> Color(0xFFFF3B30)
                                        spec.style == ThemeStyle.HYPEROS -> spec.accent
                                        else -> Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val c = when {
                                spec.style == ThemeStyle.HYPEROS && !a.cancel -> Color.White
                                a.destructive -> Color(0xFFFF453A)
                                else -> if (a.cancel) fg else spec.accent
                            }
                            Txt(a.title, 15.sp, c, FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
