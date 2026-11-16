package com.liquid.launcher.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquid.launcher.data.AppCategories
import com.liquid.launcher.data.AppEntry
import com.liquid.launcher.data.AppItem
import com.liquid.launcher.data.ThemeStyle
import com.liquid.launcher.data.WidgetItem
import com.liquid.launcher.data.WidgetKinds
import com.liquid.launcher.glass.GlassGlow
import com.liquid.launcher.glass.GlassShape
import com.liquid.launcher.glass.LocalGlassEnvironment
import com.liquid.launcher.glass.liquidGlass
import com.liquid.launcher.theme.LocalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun sortedApps(controller: HomeController): List<AppEntry> =
    controller.appsByKey.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

private fun suggested(controller: HomeController, n: Int): List<AppEntry> {
    val counts = controller.app.prefs.launchCounts()
    val all = sortedApps(controller)
    val ranked = all.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] ?: 0 }
    return (ranked + all.filter { it !in ranked }).take(n)
}

@Composable
private fun SearchCapsule(controller: HomeController, placeholder: String, modifier: Modifier = Modifier, height: Dp = 40.dp, onClick: () -> Unit) {
    val glow = remember { GlassGlow() }
    val spec = LocalTheme.current
    Row(
        modifier.fillMaxWidth().height(height)
            .glassPress(glow, onClick, 1.03f)
            .liquidGlass(controller.wallpaperBackdrop, GlassShape.Capsule, spec.pillGlass.copy(blur = 8.dp), glow)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (spec.style == ThemeStyle.IOS) Arrangement.Center else Arrangement.Start,
    ) {
        GlyphIcon(Glyphs.Search, Color.White.copy(alpha = 0.85f), 17.dp)
        Spacer(Modifier.width(6.dp))
        Txt(placeholder, 16.sp, Color.White.copy(alpha = 0.8f), FontWeight.Medium)
    }
}

@Composable
private fun AppTile(controller: HomeController, entry: AppEntry, iconSize: Dp, cellW: Dp, label: Boolean = true, textColor: Color = Color.White) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val badges = LocalBadges.current
    Column(
        Modifier.width(cellW).itemGestures(
            entry.key, false,
            onTap = { controller.launch(entry, bounds) },
            onLongPress = { controller.menu = MenuState(AppItem("lib:" + entry.key, entry.key), bounds, false, "__library__").also { controller.haptic() } },
            onDragStart = {},
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(iconSize).onGloballyPositioned { bounds = it.boundsInRoot() }) {
            IconArt(rememberIconBundle(entry, iconSize), iconSize)
            if (LocalPrefs.current.badges) NotificationBadge(badges[entry.packageName] ?: 0, iconSize)
        }
        if (label) {
            Spacer(Modifier.height(5.dp))
            IconLabel(entry.label, cellW, textColor)
        }
    }
}

// ===================================================================== Spotlight (iOS 26)

@Composable
fun SpotlightOverlay(controller: HomeController, metrics: GridMetrics) {
    val p = controller.spotlight.value
    val spec = LocalTheme.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val target = controller.spotlight.targetValue
    LaunchedEffect(target) {
        if (target >= 1f) { delay(60); runCatching { focus.requestFocus() } } else { kb?.hide(); query = "" }
    }
    if (p <= 0.001f) return
    val results = remember(query, controller.appsByKey) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList() else sortedApps(controller).filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }
    val suggestions = remember(controller.appsByKey) { suggested(controller, 8) }
    val cellW = (metrics.width - 32.dp) / 4
    Box(
        Modifier.fillMaxSize()
            .backdropBlur(controller.screenBackdrop, 44.dp, { controller.spotlight.value }, Color.Black.copy(alpha = 0.22f))
            .pointerInput(Unit) { detectTapGestures { controller.scope.launch { controller.spotlight.animateTo(0f) } } }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .graphicsLayer { alpha = p.coerceIn(0f, 1f); translationY = (1f - p) * -60f }
        ) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                if (query.isBlank()) {
                    item { Txt("Suggestions", 15.sp, Color.White.copy(alpha = 0.75f), FontWeight.SemiBold, Modifier.padding(start = 4.dp, bottom = 10.dp)) }
                    item {
                        Box(
                            Modifier.fillMaxWidth().liquidGlass(controller.screenBackdrop, GlassShape.rounded(28.dp), spec.menuGlass.copy(frost = 0f, blur = 10.dp))
                                .padding(vertical = 14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                suggestions.chunked(4).forEach { row ->
                                    Row { row.forEach { AppTile(controller, it, metrics.icon, cellW) } }
                                }
                            }
                        }
                    }
                } else {
                    if (results.isNotEmpty()) {
                        item { Txt("Top Hit", 15.sp, Color.White.copy(alpha = 0.75f), FontWeight.SemiBold, Modifier.padding(start = 4.dp, bottom = 8.dp)) }
                        item {
                            ResultRow(controller, results.first(), true)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    if (results.size > 1) {
                        item { Txt("Apps", 15.sp, Color.White.copy(alpha = 0.75f), FontWeight.SemiBold, Modifier.padding(start = 4.dp, bottom = 8.dp)) }
                        item {
                            Column(Modifier.fillMaxWidth().liquidGlass(controller.screenBackdrop, GlassShape.rounded(26.dp), spec.menuGlass.copy(frost = 0.05f, blur = 10.dp))) {
                                results.drop(1).take(12).forEach { ResultRow(controller, it, false) }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().liquidGlass(controller.screenBackdrop, GlassShape.rounded(26.dp), spec.menuGlass.copy(frost = 0.05f, blur = 10.dp))) {
                            WebRow("Search Web", query, Glyphs.Globe) { webSearch(controller, query) }
                            WebRow("Search in Store", query, Glyphs.Bag) { storeSearch(controller, query) }
                        }
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(50.dp)
                    .liquidGlass(controller.screenBackdrop, GlassShape.Capsule, spec.pillGlass.copy(blur = 6.dp, refraction = 14.dp, bevel = 18.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyphs.Search, Color.White.copy(alpha = 0.85f), 19.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Search", 17.sp, Color.White.copy(alpha = 0.6f))
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = spec.fontFamily),
                        cursorBrush = SolidColor(spec.accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                            results.firstOrNull()?.let { controller.launch(it, null) } ?: webSearch(controller, query)
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                GlyphIcon(Glyphs.Mic, Color.White.copy(alpha = 0.85f), 19.dp)
            }
        }
    }
}

@Composable
private fun ResultRow(controller: HomeController, e: AppEntry, top: Boolean) {
    val spec = LocalTheme.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val mod = if (top) Modifier.liquidGlass(controller.screenBackdrop, GlassShape.rounded(26.dp), spec.menuGlass.copy(frost = 0.05f, blur = 10.dp)) else Modifier
    Row(
        Modifier.fillMaxWidth().then(mod).pressable({ controller.launch(e, bounds) }, 0.98f).padding(horizontal = 14.dp, vertical = if (top) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val s = if (top) 52.dp else 40.dp
        Box(Modifier.size(s).onGloballyPositioned { bounds = it.boundsInRoot() }) { IconArt(rememberIconBundle(e, s), s) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(e.label, if (top) 18.sp else 16.sp, Color.White, FontWeight.Medium, maxLines = 1)
            if (top) Txt(AppCategories.name(e.category, e.packageName), 13.sp, Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        if (top) Box(
            Modifier.clip(GlassShape.Capsule).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 16.dp, vertical = 6.dp)
        ) { Txt("Open", 15.sp, Color.White, FontWeight.SemiBold) }
    }
}

@Composable
private fun WebRow(title: String, q: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick, 0.98f).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(icon, Color.White.copy(alpha = 0.85f), 22.dp)
        Spacer(Modifier.width(12.dp))
        Txt("$title  “$q”", 16.sp, Color.White, maxLines = 1)
    }
}

private fun webSearch(controller: HomeController, q: String) {
    val ctx = controller.app
    val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }.onFailure {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    controller.closeAll(false)
}

private fun storeSearch(controller: HomeController, q: String) {
    runCatching {
        controller.app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    controller.closeAll(false)
}

// ===================================================================== App Library (iOS)

private data class Category(val name: String, val apps: List<AppEntry>)

private fun categories(controller: HomeController): List<Category> {
    val all = sortedApps(controller)
    val recent = all.sortedByDescending { it.firstInstall }.take(8)
    val groups = all.groupBy { AppCategories.name(it.category, it.packageName) }
        .map { Category(it.key, it.value) }
        .sortedByDescending { it.apps.size }
    return listOf(Category("Suggestions", suggested(controller, 8)), Category("Recently Added", recent)) + groups
}

@Composable
fun AppLibraryPage(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val cats = remember(controller.appsByKey) { categories(controller) }
    val side = 20.dp
    val gap = 18.dp
    val tile = (metrics.width - side * 2 - gap) / 2
    Column(Modifier.fillMaxSize().padding(top = metrics.statusTop + 10.dp)) {
        if (!searching) {
            SearchCapsule(controller, "App Library", Modifier.padding(horizontal = side)) { searching = true }
            LazyVerticalGrid(
                GridCells.Fixed(2),
                contentPadding = PaddingValues(start = side, end = side, top = 18.dp, bottom = metrics.navBottom + 30.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cats, key = { it.name }) { cat -> CategoryTile(controller, cat, tile) }
            }
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(50); runCatching { focus.requestFocus() } }
            Row(Modifier.padding(horizontal = side).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(40.dp)
                        .liquidGlass(controller.wallpaperBackdrop, GlassShape.Capsule, spec.pillGlass.copy(blur = 8.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyphs.Search, Color.White.copy(alpha = 0.85f), 17.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Txt("App Library", 16.sp, Color.White.copy(alpha = 0.6f))
                        BasicTextField(query, { query = it }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = spec.fontFamily),
                            cursorBrush = SolidColor(spec.accent), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.pressable({ searching = false; query = "" })) { Txt("Cancel", 16.sp, Color.White, FontWeight.Medium) }
            }
            val list = remember(query, controller.appsByKey) {
                sortedApps(controller).filter { query.isBlank() || it.label.contains(query.trim(), true) }
            }
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = side, end = side, top = 14.dp, bottom = 40.dp)) {
                var last = ' '
                list.forEach { e ->
                    val c = e.label.firstOrNull()?.uppercaseChar() ?: '#'
                    if (c != last && query.isBlank()) {
                        last = c
                        item(key = "h$c") { Txt(c.toString(), 15.sp, Color.White.copy(alpha = 0.6f), FontWeight.Bold, Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)) }
                    }
                    item(key = e.key) {
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        Row(Modifier.fillMaxWidth().pressable({ controller.launch(e, bounds) }, 0.98f).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).onGloballyPositioned { bounds = it.boundsInRoot() }) { IconArt(rememberIconBundle(e, 40.dp), 40.dp) }
                            Spacer(Modifier.width(14.dp))
                            Txt(e.label, 17.sp, Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(controller: HomeController, cat: Category, tile: Dp) {
    val spec = LocalTheme.current
    val big = tile * 0.375f
    val pad = tile * 0.085f
    val small = (big - 6.dp) / 2
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(tile).liquidGlass(controller.wallpaperBackdrop, GlassShape.rounded(28.dp), spec.folderTileGlass.copy(blur = 10.dp, refraction = 12.dp, bevel = 18.dp))
        ) {
            val showCluster = cat.apps.size > 4
            val bigApps = if (showCluster) cat.apps.take(3) else cat.apps.take(4)
            val positions = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(0f, 1f), Offset(1f, 1f))
            bigApps.forEachIndexed { i, e ->
                val o = positions[i]
                Box(Modifier.offset(pad + (big + tile - pad * 2 - big * 2) * o.x, pad + (big + tile - pad * 2 - big * 2) * o.y)) {
                    AppTile(controller, e, big, big, label = false)
                }
            }
            if (showCluster) {
                val o = tile - pad - big
                Box(
                    Modifier.offset(o, o).size(big).pressable({ controller.libraryCategory = cat.name })
                ) {
                    cat.apps.drop(3).take(4).forEachIndexed { i, e ->
                        Box(Modifier.offset((small + 6.dp) * (i % 2), (small + 6.dp) * (i / 2))) { MiniIcon(e, small) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Txt(cat.name, 12.5.sp, Color.White, FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun LibraryCategoryOverlay(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val progress = remember { Animatable(0f) }
    val name = rememberAnimatedPresence(controller.libraryCategory, progress) ?: return
    val cat = remember(name, controller.appsByKey) { categories(controller).firstOrNull { it.name == name } } ?: return
    val p = progress.value
    val panelW = metrics.width * 0.86f
    val cellW = (panelW - 24.dp) / 4
    Box(
        Modifier.fillMaxSize()
            .backdropBlur(controller.screenBackdrop, 30.dp, { progress.value }, Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) { detectTapGestures { controller.libraryCategory = null } },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(panelW).graphicsLayer { val s = 0.7f + 0.3f * p; scaleX = s; scaleY = s; alpha = p.coerceIn(0f, 1f) }) {
            Txt(cat.name, 30.sp, Color.White, FontWeight.Bold, Modifier.padding(start = 6.dp, bottom = 14.dp))
            Box(
                Modifier.fillMaxWidth().height(minOf(metrics.height * 0.6f, (metrics.icon + 44.dp) * ((cat.apps.size + 3) / 4) + 30.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .liquidGlass(controller.screenBackdrop, spec.panelShape, spec.panelGlass)
            ) {
                LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(cat.apps, key = { it.key }) { e -> AppTile(controller, e, metrics.icon, cellW) }
                }
            }
        }
    }
}

// ===================================================================== Today View (iOS)

@Composable
fun TodayView(controller: HomeController, metrics: GridMetrics) {
    val w2 = metrics.cellW * 2 - (metrics.cellW - metrics.icon)
    val h2 = metrics.cellH + metrics.icon
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = metrics.gridLeft + (metrics.cellW - metrics.icon) / 2, end = metrics.gridLeft + (metrics.cellW - metrics.icon) / 2, top = metrics.statusTop + 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { SearchCapsule(controller, "Search") { controller.scope.launch { controller.spotlight.animateTo(1f) } } }
        item {
            val date = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
            Txt(date, 22.sp, Color.White, FontWeight.Bold, Modifier.padding(start = 4.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BuiltInWidget(WidgetItem("t1", WidgetKinds.CALENDAR, 2, 2), Modifier.size(w2, h2))
                BuiltInWidget(WidgetItem("t2", WidgetKinds.CLOCK, 2, 2), Modifier.size(w2, h2))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BuiltInWidget(WidgetItem("t3", WidgetKinds.BATTERY, 2, 2), Modifier.size(w2, h2))
                SuggestionsWidget(controller, Modifier.size(w2, h2))
            }
        }
    }
}

@Composable
private fun SuggestionsWidget(controller: HomeController, modifier: Modifier) {
    val apps = remember(controller.appsByKey) { suggested(controller, 4) }
    WidgetSurface(modifier) { _, _ ->
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            apps.forEachIndexed { i, e ->
                val s = 52.dp
                Box(Modifier.align(listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)[i])) {
                    AppTile(controller, e, s, s, label = false)
                }
            }
        }
    }
}

// ===================================================================== App drawer (One UI / HyperOS)

@Composable
fun AppDrawerOverlay(controller: HomeController, metrics: GridMetrics) {
    val spec = LocalTheme.current
    val anim = controller.drawer
    val p = anim.value
    if (p <= 0.001f || spec.hasAppLibrary) return
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf("All") }
    val fg = Color.White
    val apps = remember(controller.appsByKey, query, chip) {
        sortedApps(controller).filter { (query.isBlank() || it.label.contains(query.trim(), true)) && (chip == "All" || AppCategories.name(it.category, it.packageName) == chip) }
    }
    val range = metrics.height.value * 2f
    val nested = remember(anim) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { anim.snapTo((anim.value - available.y / range).coerceIn(0f, 1f)) }
                    return available
                }
                return Offset.Zero
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (anim.value < 1f && available.y < 0f) {
                    scope.launch { anim.snapTo((anim.value - available.y / range).coerceIn(0f, 1f)) }
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (anim.value < 1f) {
                    anim.animateTo(if (anim.value > 0.75f && available.y <= 0f) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 380f))
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    Box(
        Modifier.fillMaxSize()
            .backdropBlur(controller.screenBackdrop, 40.dp, { anim.value }, Color.Black.copy(alpha = if (spec.style == ThemeStyle.ONEUI) 0.40f else 0.28f))
            .nestedScroll(nested)
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .graphicsLayer { alpha = p.coerceIn(0f, 1f); translationY = (1f - p) * 160.dp.toPx() }
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth().height(46.dp)
                    .liquidGlass(controller.screenBackdrop, GlassShape.Capsule, spec.pillGlass.copy(frost = 0.1f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyphs.Search, fg.copy(alpha = 0.8f), 18.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Search", 16.sp, fg.copy(alpha = 0.6f))
                    BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = fg, fontSize = 16.sp, fontFamily = spec.fontFamily),
                        cursorBrush = SolidColor(spec.accent), modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.pressable({ controller.openSettings() })) { GlyphIcon(Glyphs.More, fg.copy(alpha = 0.85f), 20.dp) }
            }
            val cols = spec.drawerColumns
            val cellW = (metrics.width - 24.dp) / cols
            if (spec.drawerPaged && query.isBlank()) {
                val perPage = cols * 6
                val pages = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
                val pager = rememberPagerState { pages }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { pg ->
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        apps.drop(pg * perPage).take(perPage).chunked(cols).forEach { row ->
                            Row { row.forEach { AppTile(controller, it, metrics.icon * 0.92f, cellW) } }
                        }
                        repeat(6 - ((apps.drop(pg * perPage).take(perPage).size + cols - 1) / cols)) { Spacer(Modifier.height(metrics.icon)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = metrics.navBottom + 16.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages) { i ->
                        Box(Modifier.padding(4.dp).size(6.dp).clip(CircleShape).background(fg.copy(alpha = if (i == pager.currentPage) 0.95f else 0.4f)))
                    }
                }
            } else {
                if (spec.style == ThemeStyle.HYPEROS && query.isBlank()) {
                    val chips = remember(controller.appsByKey) {
                        listOf("All") + sortedApps(controller).map { AppCategories.name(it.category, it.packageName) }.distinct().sorted()
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chips) { c ->
                            Box(
                                Modifier.clip(GlassShape.Capsule)
                                    .background(if (c == chip) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f))
                                    .pressable({ chip = c }).padding(horizontal = 14.dp, vertical = 7.dp)
                            ) { Txt(c, 13.sp, if (c == chip) Color.Black else fg, FontWeight.Medium) }
                        }
                    }
                }
                LazyVerticalGrid(
                    GridCells.Fixed(cols), Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = metrics.navBottom + 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (query.isBlank() && spec.style == ThemeStyle.HYPEROS && chip == "All") {
                        item(span = { GridItemSpan(cols) }) { Txt("Frequently used", 13.sp, fg.copy(alpha = 0.6f), FontWeight.Medium, Modifier.padding(start = 8.dp)) }
                        items(suggested(controller, cols), key = { "s" + it.key }) { AppTile(controller, it, metrics.icon * 0.92f, cellW) }
                        item(span = { GridItemSpan(cols) }) { Spacer(Modifier.height(4.dp)) }
                    }
                    items(apps, key = { it.key }) { AppTile(controller, it, metrics.icon * 0.92f, cellW) }
                }
            }
        }
    }
}
