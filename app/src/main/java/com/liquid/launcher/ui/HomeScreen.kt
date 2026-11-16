package com.liquid.launcher.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import com.liquid.launcher.data.AppItem
import com.liquid.launcher.data.FolderItem
import com.liquid.launcher.data.GapItem
import com.liquid.launcher.data.GridPacker
import com.liquid.launcher.data.HomeItem
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.data.WidgetItem
import com.liquid.launcher.glass.GlassGlow
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.backdropSource
import com.liquid.launcher.glass.liquidGlass
import com.liquid.launcher.theme.LocalTheme
import com.liquid.launcher.theme.PageIndicatorStyle
import com.liquid.launcher.theme.SwipeAction
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

@Composable
fun HomeScreen(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val homePages = controller.pagesForDisplay()
    val todayOffset = if (spec.hasTodayView) 1 else 0
    controller.homeOffset = todayOffset
    val libraryCount = if (spec.hasAppLibrary) 1 else 0
    val pageCount = todayOffset + homePages.size + libraryCount
    val pager = rememberPagerState(initialPage = todayOffset) { pageCount }
    controller.pager = pager
    val lastHome = todayOffset + homePages.size - 1
    LaunchedEffect(spec.style) { pager.scrollToPage(todayOffset) }

    val jiggle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(controller.editMode) {
        if (controller.editMode) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { jiggle.floatValue = (it - start) / 1e9f }
        }
    }
    val side by remember(todayOffset, lastHome, libraryCount) {
        derivedStateOf {
            val pos = pager.currentPage + pager.currentPageOffsetFraction
            val t = if (todayOffset == 1) (1f - pos).coerceIn(0f, 1f) else 0f
            val l = if (libraryCount == 1) (pos - lastHome).coerceIn(0f, 1f) else 0f
            max(t, l)
        }
    }
    val oneUiEditScale by animateFloatAsState(
        if (controller.editMode && spec.style != ThemeStyle.IOS) 0.86f else 1f,
        spring(dampingRatio = 0.85f, stiffness = 420f), label = "editscale"
    )

    CompositionLocalProvider(LocalJiggleClock provides jiggle) {
        Box(
            Modifier.fillMaxSize()
                .dragTracker(controller)
                .homeSwipes(controller) { !controller.editMode && controller.drag == null && controller.menu == null && controller.folder == null && side < 0.01f }
        ) {
            Box(Modifier.fillMaxSize().backdropSource(controller.screenBackdrop)) {
                Box(Modifier.fillMaxSize().backdropSource(controller.homeBackdrop)) {
                    Wallpaper(Modifier.fillMaxSize().backdropSource(controller.wallpaperBackdrop))
                    Box(Modifier.fillMaxSize().backdropBlur(controller.wallpaperBackdrop, 34.dp, { side }, Color.Black.copy(alpha = 0.18f)))
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = oneUiEditScale; scaleY = oneUiEditScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.42f)
                        },
                        beyondViewportPageCount = 0,
                        key = { p ->
                            when {
                                p < todayOffset -> "today"
                                p > lastHome -> "library"
                                else -> "page${p - todayOffset}"
                            }
                        },
                    ) { p ->
                        when {
                            p < todayOffset -> TodayView(controller, metrics)
                            p > lastHome -> AppLibraryPage(controller, metrics)
                            else -> HomePage(controller, p - todayOffset, metrics)
                        }
                    }
                }
                val chromeAlpha = 1f - side
                PageIndicator(controller, pager, homePages.size, todayOffset, metrics, chromeAlpha)
                Dock(controller, metrics, chromeAlpha)
            }
            if (controller.editMode && spec.style == ThemeStyle.IOS) IosEditTopBar(controller, metrics)
            if (spec.style != ThemeStyle.IOS) OneUiEditBar(controller, metrics)
            DragOverlay(controller, metrics)
            FolderOverlay(controller, metrics)
            ContextMenuOverlay(controller, metrics)
            SpotlightOverlay(controller, metrics)
            AppDrawerOverlay(controller, metrics)
            LibraryCategoryOverlay(controller, metrics)
            EditMenuOverlay(controller, metrics)
            CustomizeSheet(controller, metrics)
            WidgetPickerSheet(controller, metrics)
            AlertOverlay(controller)
        }
    }
}

@Composable
fun Wallpaper(modifier: Modifier) {
    val wp = LocalWallpaper.current
    Box(modifier.background(Color.Black)) {
        if (wp != null) Image(wp.bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** While a drag is active this sees every pointer event first and moves the dragged item. */
private fun Modifier.dragTracker(controller: HomeController): Modifier = pointerInput(controller) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (controller.drag == null) continue
            val c = ev.changes.firstOrNull() ?: continue
            if (c.pressed) controller.dragMove(c.position) else controller.dragEnd()
            c.consume()
        }
    }
}

fun expandNotifications(context: Context) {
    runCatching {
        @Suppress("WrongConstant")
        val sb = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    }
}

/** Swipe down: Spotlight (iOS) / notification shade (One UI, HyperOS). Swipe up: app drawer. */
private fun Modifier.homeSwipes(controller: HomeController, enabled: () -> Boolean): Modifier = this.then(
    Modifier.pointerInput(controller.spec.style) {
        val spec = controller.spec
        val downRange = 260.dp.toPx()
        val upRange = size.height * 0.55f
        var total = 0f
        var dir = 0
        var fired = false
        var active = false
        detectVerticalDragGestures(
            onDragStart = {
                total = 0f; dir = 0; fired = false
                active = enabled() && controller.spotlight.value == 0f && controller.drawer.value == 0f
            },
            onDragEnd = {
                if (!active) return@detectVerticalDragGestures
                controller.scope.launch {
                    val s = controller.spotlight
                    if (s.value > 0f) s.animateTo(if (s.value > 0.28f) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 380f))
                }
                controller.scope.launch {
                    val d = controller.drawer
                    if (d.value > 0f) d.animateTo(if (d.value > 0.25f) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 360f))
                }
            },
            onDragCancel = {
                if (active) {
                    controller.scope.launch { controller.spotlight.animateTo(0f) }
                    controller.scope.launch { controller.drawer.animateTo(0f) }
                }
            },
        ) { change, dy ->
            if (!active) return@detectVerticalDragGestures
            total += dy
            if (dir == 0 && abs(total) > 6f) dir = if (total > 0) 1 else -1
            val action = if (dir > 0) spec.swipeDown else if (dir < 0) spec.swipeUp else SwipeAction.NONE
            when (action) {
                SwipeAction.SPOTLIGHT -> controller.scope.launch {
                    controller.spotlight.snapTo((controller.spotlight.value + dy / downRange).coerceIn(0f, 1f))
                }
                SwipeAction.NOTIFICATIONS -> if (!fired && total > 48.dp.toPx()) {
                    fired = true
                    expandNotifications(controller.app)
                }
                SwipeAction.DRAWER -> controller.scope.launch {
                    controller.drawer.snapTo((controller.drawer.value - dy / upRange).coerceIn(0f, 1f))
                }
                SwipeAction.NONE -> {}
            }
            change.consume()
        }
    }
)

@Composable
fun HomePage(controller: HomeController, page: Int, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val items = controller.previewPage(page)
    val cells = remember(items, spec.columns, spec.rows) { GridPacker.pack(items, spec.columns, spec.rows) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize().pointerInput(controller.editMode) {
            detectTapGestures(
                onTap = { if (controller.editMode) controller.exitEdit() },
                onLongPress = { if (!controller.editMode) controller.enterEdit() },
            )
        }
    ) {
        items.forEachIndexed { i, item ->
            val cell = cells[i] ?: return@forEachIndexed
            key(item.id) {
                val target = with(density) {
                    IntOffset(
                        (metrics.gridLeft + metrics.cellW * cell.x).roundToPx(),
                        (metrics.gridTop + metrics.cellH * cell.y).roundToPx(),
                    )
                }
                val off by animateIntOffsetAsState(target, spring(dampingRatio = 0.8f, stiffness = 360f), label = "cell")
                Box(Modifier.offset { off }.size(metrics.cellW * item.spanX, metrics.cellH * item.spanY)) {
                    HomeItemView(controller, item, page, false, metrics)
                }
            }
        }
    }
}

@Composable
fun HomeItemView(controller: HomeController, item: HomeItem, page: Int, inDock: Boolean, metrics: GridMetrics) {
    when (item) {
        is GapItem -> Spacer(Modifier.fillMaxSize())
        is AppItem -> AppCell(controller, item, page, inDock, metrics)
        is FolderItem -> FolderCell(controller, item, page, inDock, metrics)
        is WidgetItem -> WidgetCell(controller, item, page, metrics)
    }
}

@Composable
private fun cellTopSpacing(inDock: Boolean, metrics: GridMetrics): Dp =
    if (inDock) (metrics.dockHeight - metrics.icon) / 2 else metrics.iconTop

@Composable
fun AppCell(controller: HomeController, item: AppItem, page: Int, inDock: Boolean, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val prefs = LocalPrefs.current
    val entry = controller.app(item.appKey)
    val badges = LocalBadges.current
    val cellPos = remember { LayoutRef() }
    val bounds = remember { LayoutRef() }
    val hover = controller.drag?.folderHover == item.id
    val edit = controller.editMode
    val iconPx = with(LocalDensity.current) { metrics.icon.roundToPx() }
    Column(
        Modifier.fillMaxWidth()
            .track(cellPos)
            .itemGestures(
                item.id, edit,
                onTap = { if (!edit && entry != null) controller.launch(entry, bounds.rect()) },
                onLongPress = { controller.showMenu(item, bounds.rect(), inDock) },
                onDragStart = { local ->
                    val pointer = cellPos.pos() + local
                    controller.startDrag(item, DragSource(page, inDock), pointer, pointer - bounds.rect().topLeft, IntSize(iconPx, iconPx))
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(cellTopSpacing(inDock, metrics)))
        Box(
            Modifier.size(metrics.icon)
                .track(bounds)
                .jiggle(edit, item.id)
        ) {
            if (hover) {
                Box(Modifier.matchParentSize().graphicsLayer { scaleX = 1.18f; scaleY = 1.18f }
                    .liquidGlass(controller.wallpaperBackdrop, spec.folderShape, spec.folderTileGlass))
            }
            val s by animateFloatAsState(if (hover) 0.72f else 1f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "hover")
            IconArt(rememberIconBundle(entry, metrics.icon), metrics.icon, Modifier.graphicsLayer { scaleX = s; scaleY = s })
            if (prefs.badges && !edit) NotificationBadge(entry?.let { badges[it.packageName] } ?: 0, metrics.icon)
            if (edit && spec.style == ThemeStyle.IOS && !hover) DeleteBadge { confirmRemove(controller, item) }
        }
        if (prefs.showLabels && !inDock) {
            Spacer(Modifier.height(metrics.labelGap))
            IconLabel(entry?.label ?: "", metrics.cellW)
        }
    }
}

@Composable
fun FolderCell(controller: HomeController, item: FolderItem, page: Int, inDock: Boolean, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val prefs = LocalPrefs.current
    val badges = LocalBadges.current
    val cellPos = remember { LayoutRef() }
    val bounds = remember { LayoutRef() }
    val hover = controller.drag?.folderHover == item.id
    val edit = controller.editMode
    val iconPx = with(LocalDensity.current) { metrics.icon.roundToPx() }
    Column(
        Modifier.fillMaxWidth()
            .track(cellPos)
            .itemGestures(
                item.id, edit,
                onTap = { controller.openFolder(item.id, bounds.rect()) },
                onLongPress = { controller.showMenu(item, bounds.rect(), inDock) },
                onDragStart = { local ->
                    val pointer = cellPos.pos() + local
                    controller.startDrag(item, DragSource(page, inDock), pointer, pointer - bounds.rect().topLeft, IntSize(iconPx, iconPx))
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(cellTopSpacing(inDock, metrics)))
        Box(Modifier.size(metrics.icon).track(bounds).jiggle(edit, item.id)) {
            FolderTile(item, metrics.icon, hover)
            val count = item.apps.sumOf { k -> controller.app(k)?.let { badges[it.packageName] } ?: 0 }
            if (prefs.badges && !edit) NotificationBadge(count, metrics.icon)
            if (edit && spec.style == ThemeStyle.IOS) DeleteBadge { confirmRemove(controller, item) }
        }
        if (prefs.showLabels && !inDock) {
            Spacer(Modifier.height(metrics.labelGap))
            IconLabel(item.name, metrics.cellW)
        }
    }
}

@Composable
fun WidgetCell(controller: HomeController, item: WidgetItem, page: Int, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val prefs = LocalPrefs.current
    val cellPos = remember { LayoutRef() }
    val bounds = remember { LayoutRef() }
    val edit = controller.editMode
    val hPad = (metrics.cellW - metrics.icon) / 2
    val bottomPad = metrics.cellH - metrics.iconTop - metrics.icon
    Box(
        Modifier.fillMaxSize()
            .track(cellPos)
            .itemGestures(
                item.id, edit,
                onTap = {},
                onLongPress = { controller.showMenu(item, bounds.rect(), false) },
                onDragStart = { local ->
                    val pointer = cellPos.pos() + local
                    bounds.rect().let { r -> controller.startDrag(item, DragSource(page, false), pointer, pointer - r.topLeft, IntSize(r.width.toInt(), r.height.toInt())) }
                },
            )
    ) {
        Box(
            Modifier.padding(start = hPad, end = hPad, top = metrics.iconTop, bottom = bottomPad)
                .fillMaxSize()
                .track(bounds)
                .jiggle(edit, item.id)
        ) {
            BuiltInWidget(item, Modifier.fillMaxSize()) { controller.showMenu(item, bounds.rect(), false) }
            if (edit) {
                Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                if (spec.style == ThemeStyle.IOS) DeleteBadge { controller.removeItem(item.id) }
            }
        }
        if (prefs.showLabels && spec.style == ThemeStyle.IOS && item.kind != com.liquid.launcher.data.WidgetKinds.DIGITAL_CLOCK) {
            val label = when (item.kind) {
                com.liquid.launcher.data.WidgetKinds.CLOCK -> "Clock"
                com.liquid.launcher.data.WidgetKinds.CALENDAR -> "Calendar"
                com.liquid.launcher.data.WidgetKinds.BATTERY -> "Batteries"
                else -> com.liquid.launcher.LauncherApp.instance.widgets.info(item.appWidgetId)?.loadLabel(LocalContext.current.packageManager) ?: ""
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = (bottomPad - 18.dp).coerceAtLeast(0.dp))) {
                IconLabel(label, metrics.cellW * item.spanX)
            }
        }
    }
}

fun confirmRemove(controller: HomeController, item: HomeItem) {
    val spec = controller.spec
    when (item) {
        is AppItem -> {
            val e = controller.app(item.appKey)
            val name = e?.label ?: "App"
            controller.alert = AlertState(
                if (spec.style == ThemeStyle.IOS) "Remove “$name”?" else "Remove $name?",
                if (spec.style == ThemeStyle.IOS) "Removing from Home Screen will keep the app in your App Library." else "You can still find it in Apps.",
                listOfNotNull(
                    e?.let { AlertAction(if (spec.style == ThemeStyle.IOS) "Delete App" else "Uninstall", destructive = true) { controller.uninstall(it) } },
                    AlertAction(if (spec.style == ThemeStyle.IOS) "Remove from Home Screen" else "Remove from Home") { controller.removeItem(item.id) },
                    AlertAction("Cancel", cancel = true),
                )
            )
        }
        is FolderItem -> controller.alert = AlertState(
            "Remove “${item.name}” Folder?",
            "The apps in this folder will be moved to the App Library.",
            listOf(AlertAction("Remove Folder", destructive = true) { controller.removeItem(item.id) }, AlertAction("Cancel", cancel = true))
        )
        is WidgetItem -> controller.alert = AlertState(
            "Remove Widget?", "Removing this widget will not delete any data.",
            listOf(AlertAction("Remove", destructive = true) { controller.removeItem(item.id) }, AlertAction("Cancel", cancel = true))
        )
        else -> {}
    }
}

@Composable
fun Dock(controller: HomeController, metrics: GridMetrics, alpha: Float) {
    val spec = LocalTheme.current
    val items = controller.previewDock()
    val density = LocalDensity.current
    if (alpha <= 0.01f) return
    val dockMod = Modifier
        .offset(metrics.dockLeft, metrics.dockTop)
        .size(metrics.dockWidth, metrics.dockHeight)
        .graphicsLayer {
            this.alpha = alpha.coerceIn(0f, 1f)
            translationY = (1f - alpha) * 40.dp.toPx()
        }
    Box(
        if (spec.dockPlatter) dockMod.liquidGlass(controller.homeBackdrop, spec.dockShape, spec.dockGlass) else dockMod
    ) {
        val n = if (spec.style == ThemeStyle.IOS) items.size.coerceAtLeast(1) else max(spec.dockSlots, items.size)
        val slotW = metrics.dockWidth / n
        val cellW = if (slotW < metrics.cellW) slotW else metrics.cellW
        items.forEachIndexed { i, item ->
            key(item.id) {
                val target = with(density) { IntOffset((slotW * i + (slotW - cellW) / 2).roundToPx(), 0) }
                val off by animateIntOffsetAsState(target, spring(dampingRatio = 0.8f, stiffness = 380f), label = "dock")
                Box(Modifier.offset { off }.size(cellW, metrics.dockHeight)) {
                    HomeItemView(controller, item, -1, true, metrics)
                }
            }
        }
    }
}

@Composable
fun PageIndicator(controller: HomeController, pager: PagerState, pages: Int, offset: Int, metrics: GridMetrics, alpha: Float) {
    val spec = LocalTheme.current
    if (alpha <= 0.01f) return
    val current = (pager.currentPage - offset).coerceIn(0, (pages - 1).coerceAtLeast(0))
    Box(
        Modifier.fillMaxWidth().offset(y = metrics.indicatorTop).height(metrics.indicatorHeight)
            .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center,
    ) {
        when (spec.indicator) {
            PageIndicatorStyle.SEARCH_PILL -> SearchPill(controller, pager, pages, current)
            PageIndicatorStyle.DOTS -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(pages) { i ->
                    if (i == 0) {
                        GlyphIcon(Glyphs.Home, Color.White.copy(alpha = if (i == current) 1f else 0.5f), 10.dp)
                    } else {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == current) 1f else 0.45f)))
                    }
                }
            }
            PageIndicatorStyle.PILL_DOTS -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(pages) { i ->
                    val w by animateDpAsState(if (i == current) 14.dp else 5.dp, spring(dampingRatio = 0.8f, stiffness = 500f), label = "dotw")
                    Box(Modifier.size(w, 5.dp).clip(GlassShape.Capsule).background(Color.White.copy(alpha = if (i == current) 0.95f else 0.45f)))
                }
            }
        }
    }
}

/** iOS: the "Search" glass pill above the dock morphs into page dots while you swipe. */
@Composable
private fun SearchPill(controller: HomeController, pager: PagerState, pages: Int, current: Int) {
    val spec = LocalTheme.current
    val showDots = (pager.isScrollInProgress || controller.editMode) && pages > 1
    val dotsW = (pages * 14 + 22).dp
    val w by animateDpAsState(if (showDots) dotsW else 92.dp, spring(dampingRatio = 0.68f, stiffness = 420f), label = "pillw")
    val glow = remember { GlassGlow() }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(w, 30.dp)
            .glassPress(glow, { scope.launch { controller.spotlight.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f)) } })
            .liquidGlass(controller.homeBackdrop, GlassShape.Capsule, spec.pillGlass, glow),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(showDots, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pill") { dots ->
            if (dots) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(pages) { i ->
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == current) 1f else 0.38f)))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyphs.Search, Color.White, 13.dp)
                    Spacer(Modifier.width(4.dp))
                    Txt("Search", 13.5.sp, Color.White, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun DragOverlay(controller: HomeController, metrics: GridMetrics) {
    val d = controller.drag ?: return
    val spec = LocalTheme.current
    val lift by animateFloatAsState(1.12f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
    val density = LocalDensity.current
    Box(
        Modifier.offset { (d.pos - d.grab).round() }
            .graphicsLayer { scaleX = lift; scaleY = lift; alpha = 0.96f }
    ) {
        when (val item = d.item) {
            is AppItem -> Box(Modifier.shadow(10.dp, spec.iconShape, clip = false)) {
                IconArt(rememberIconBundle(controller.app(item.appKey), metrics.icon), metrics.icon)
            }
            is FolderItem -> FolderTile(item, metrics.icon)
            is WidgetItem -> with(density) {
                BuiltInWidget(item, Modifier.size(d.size.width.toDp(), d.size.height.toDp()))
            }
            else -> {}
        }
    }
}

