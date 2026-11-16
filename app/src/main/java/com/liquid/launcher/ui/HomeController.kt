package com.liquid.launcher.ui

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntSize
import com.liquid.launcher.LauncherApp
import com.liquid.launcher.data.AppCategories
import com.liquid.launcher.data.AppEntry
import com.liquid.launcher.data.AppItem
import com.liquid.launcher.data.FolderItem
import com.liquid.launcher.data.GapItem
import com.liquid.launcher.data.GridPacker
import com.liquid.launcher.data.HomeItem
import com.liquid.launcher.data.HomeLayout
import com.liquid.launcher.data.WidgetItem
import com.liquid.launcher.glass.Backdrop
import com.liquid.launcher.theme.ThemeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

data class MenuState(val item: HomeItem, val anchor: Rect, val inDock: Boolean, val folderId: String? = null)
data class FolderOpenState(val folderId: String, val anchor: Rect)

sealed interface DropTarget {
    data class Page(val page: Int, val index: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
}

data class DragSource(val page: Int, val inDock: Boolean)

@Stable
class DragState(val item: HomeItem, val source: DragSource, val grab: Offset, val size: IntSize, start: Offset) {
    var pos by mutableStateOf(start)
    var target by mutableStateOf<DropTarget?>(null)
    var folderHover by mutableStateOf<String?>(null)
    var folderCandidate: String? = null
    var folderJob: Job? = null
    var edgeJob: Job? = null
    var edgeDir = 0
}

data class AlertAction(val title: String, val destructive: Boolean = false, val cancel: Boolean = false, val run: () -> Unit = {})
data class AlertState(val title: String, val message: String, val actions: List<AlertAction>)

@Stable
class HomeController(val app: LauncherApp, val scope: CoroutineScope) {
    var editMode by mutableStateOf(false)
    var menu by mutableStateOf<MenuState?>(null)
    var folder by mutableStateOf<FolderOpenState?>(null)
    var drag by mutableStateOf<DragState?>(null)
    var customize by mutableStateOf(false)
    var widgetPicker by mutableStateOf(false)
    var editMenu by mutableStateOf(false)
    var alert by mutableStateOf<AlertState?>(null)
    var libraryCategory by mutableStateOf<String?>(null)
    val spotlight = Animatable(0f)
    val drawer = Animatable(0f)

    val wallpaperBackdrop = Backdrop("wallpaper")
    val homeBackdrop = Backdrop("home")
    val screenBackdrop = Backdrop("screen")

    // refreshed on every composition of the root
    var spec: ThemeSpec by mutableStateOf(com.liquid.launcher.theme.Themes.IOS)
    var metrics: GridMetrics? = null
    var density: Float = 1f
    var layout: HomeLayout by mutableStateOf(HomeLayout(emptyList(), emptyList()))
    var appsByKey: Map<String, AppEntry> by mutableStateOf(emptyMap())
    var pager: PagerState? = null
    var homeOffset = 0
    var view: View? = null
    var haptics: HapticFeedback? = null
    var requestSystemWidget: ((android.appwidget.AppWidgetProviderInfo) -> Unit)? = null

    val anyOverlay: Boolean
        get() = menu != null || folder != null || customize || widgetPicker || editMenu || alert != null ||
            spotlight.targetValue > 0f || drawer.targetValue > 0f || libraryCategory != null

    fun app(key: String) = appsByKey[key]

    fun haptic(type: HapticFeedbackType = HapticFeedbackType.LongPress) = haptics?.performHapticFeedback(type)

    // ---------------------------------------------------------------- launching
    fun launch(entry: AppEntry, boundsInRoot: Rect?) {
        val v = view
        val bundle = if (v != null && boundsInRoot != null) {
            ActivityOptions.makeScaleUpAnimation(
                v, boundsInRoot.left.toInt(), boundsInRoot.top.toInt(),
                boundsInRoot.width.toInt().coerceAtLeast(1), boundsInRoot.height.toInt().coerceAtLeast(1)
            ).toBundle()
        } else null
        val rect = boundsInRoot?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        app.apps.launch(entry, rect, bundle)
        app.prefs.recordLaunch(entry.key)
        scope.launch {
            delay(350)
            closeAll(animated = false)
        }
    }

    fun uninstall(entry: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(i) }
    }

    fun share(entry: AppEntry) {
        val url = "https://play.google.com/store/apps/details?id=${entry.packageName}"
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        runCatching { app.startActivity(Intent.createChooser(i, entry.label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openSettings() {
        runCatching {
            app.startActivity(Intent(app, com.liquid.launcher.SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------------------------------------------------------------- overlays
    fun showMenu(item: HomeItem, anchor: Rect, inDock: Boolean) {
        haptic()
        menu = MenuState(item, anchor, inDock)
    }

    fun openFolder(id: String, anchor: Rect) {
        menu = null
        folder = FolderOpenState(id, anchor)
    }

    fun enterEdit() {
        if (!editMode) haptic()
        menu = null
        editMode = true
    }

    fun exitEdit() {
        editMode = false
        editMenu = false
        customize = false
        app.layout.update { GridPacker.normalize(it, spec.columns, spec.rows) }
    }

    fun closeAll(animated: Boolean = true) {
        menu = null
        folder = null
        customize = false
        widgetPicker = false
        editMenu = false
        alert = null
        libraryCategory = null
        scope.launch { if (animated) spotlight.animateTo(0f, spring(stiffness = 500f)) else spotlight.snapTo(0f) }
        scope.launch { if (animated) drawer.animateTo(0f, spring(stiffness = 500f)) else drawer.snapTo(0f) }
    }

    fun onBack(): Boolean {
        when {
            alert != null -> alert = null
            menu != null -> menu = null
            customize -> customize = false
            widgetPicker -> widgetPicker = false
            editMenu -> editMenu = false
            libraryCategory != null -> libraryCategory = null
            folder != null -> folder = null
            spotlight.targetValue > 0f -> scope.launch { spotlight.animateTo(0f) }
            drawer.targetValue > 0f -> scope.launch { drawer.animateTo(0f) }
            editMode -> exitEdit()
            else -> return false
        }
        return true
    }

    fun onHomePressed() {
        if (anyOverlay) { closeAll(); return }
        if (editMode) { exitEdit(); return }
        val p = pager ?: return
        if (p.currentPage != homeOffset) scope.launch { p.animateScrollToPage(homeOffset) }
    }

    // ---------------------------------------------------------------- editing
    fun removeItem(id: String) {
        val item = layout.findItem(id)
        if (item is WidgetItem && item.appWidgetId >= 0) app.widgets.delete(item.appWidgetId)
        app.layout.update { l ->
            HomeLayout(l.pages.map { p -> p.filterNot { it.id == id } }, l.dock.filterNot { it.id == id })
        }
    }

    fun removeFromFolder(folderId: String, appKey: String) {
        app.layout.update { l ->
            fun fix(items: List<HomeItem>) = items.mapNotNull { i ->
                if (i is FolderItem && i.id == folderId) {
                    val rest = i.apps - appKey
                    when (rest.size) {
                        0 -> null
                        1 -> AppItem(i.id, rest.first())
                        else -> i.copy(apps = rest)
                    }
                } else i
            }
            val pages = l.pages.map(::fix).toMutableList()
            val target = pages.indexOfFirst { p -> p.isEmpty() || p.size < spec.columns * spec.rows }.coerceAtLeast(0)
            if (pages.isEmpty()) pages += emptyList<HomeItem>()
            pages[target] = pages[target] + AppItem(HomeLayout.newId(), appKey)
            GridPacker.normalize(HomeLayout(pages, fix(l.dock)), spec.columns, spec.rows)
        }
        val f = layout.findItem(folderId)
        if (f !is FolderItem) folder = null
    }

    fun renameFolder(folderId: String, name: String) {
        app.layout.update { l ->
            fun fix(items: List<HomeItem>) = items.map { if (it is FolderItem && it.id == folderId) it.copy(name = name) else it }
            HomeLayout(l.pages.map(::fix), fix(l.dock))
        }
    }

    fun addWidget(item: WidgetItem) {
        val page = ((pager?.currentPage ?: homeOffset) - homeOffset).coerceIn(0, (layout.pages.size - 1).coerceAtLeast(0))
        app.layout.update { l ->
            val pages = l.pages.toMutableList()
            if (pages.isEmpty()) pages += emptyList<HomeItem>()
            pages[page] = listOf<HomeItem>(item) + pages[page]
            GridPacker.normalize(HomeLayout(pages, l.dock), spec.columns, spec.rows)
        }
        widgetPicker = false
    }

    // ---------------------------------------------------------------- drag & drop
    fun startDrag(item: HomeItem, source: DragSource, pointer: Offset, grab: Offset, size: IntSize) {
        menu = null
        if (!editMode) enterEdit()
        haptic(HapticFeedbackType.TextHandleMove)
        drag = DragState(item, source, grab, size, pointer).also { updateTarget(it) }
    }

    fun pagesForDisplay(): List<List<HomeItem>> =
        if (editMode) layout.pages + listOf(emptyList()) else layout.pages

    /** Items of a home page as they should currently be drawn (dragged item removed, gap inserted). */
    fun previewPage(page: Int): List<HomeItem> {
        val base = pagesForDisplay().getOrNull(page) ?: emptyList()
        val d = drag ?: return base
        val without = base.filterNot { it.id == d.item.id }
        val t = d.target
        return if (t is DropTarget.Page && t.page == page && d.folderHover == null) {
            without.toMutableList().apply { add(t.index.coerceIn(0, size), GapItem(d.item.spanX, d.item.spanY)) }
        } else without
    }

    fun previewDock(): List<HomeItem> {
        val d = drag ?: return layout.dock
        val without = layout.dock.filterNot { it.id == d.item.id }
        val t = d.target
        return if (t is DropTarget.Dock && d.folderHover == null) {
            without.toMutableList().apply { add(t.index.coerceIn(0, size), GapItem(1, 1)) }
        } else without
    }

    fun dragMove(p: Offset) {
        val d = drag ?: return
        d.pos = p
        updateTarget(d)
        edgeScroll(d)
    }

    private fun currentHomePage(): Int = ((pager?.currentPage ?: homeOffset) - homeOffset)

    private fun updateTarget(d: DragState) {
        val m = metrics ?: return
        val den = density
        val center = d.pos - d.grab + Offset(d.size.width / 2f, (m.icon.value * den) / 2f)
        val page = currentHomePage()
        val pages = pagesForDisplay()
        if (page !in pages.indices) { d.target = null; return }

        // ---- dock
        if (m.dockContains(d.pos, den) && d.item !is WidgetItem) {
            val dockItems = layout.dock.filterNot { it.id == d.item.id }
            val full = dockItems.size >= spec.dockSlots
            // folder hover over a dock icon?
            val shown = previewDock()
            val slotW = (m.dockWidth.value * den) / shown.size.coerceAtLeast(1)
            val left = m.dockLeft.value * den
            val idxShown = floor((d.pos.x - left) / slotW).toInt().coerceIn(0, (shown.size - 1).coerceAtLeast(0))
            val hovered = shown.getOrNull(idxShown)
            val slotCenter = left + slotW * (idxShown + 0.5f)
            if (hovered != null && hovered !is GapItem && d.item is AppItem && abs(center.x - slotCenter) < m.icon.value * den * 0.3f) {
                armFolder(d, hovered.id)
                return
            }
            disarmFolder(d)
            if (full) { d.target = null; return }
            val slots = dockItems.size + 1
            val w = (m.dockWidth.value * den) / slots
            val idx = floor((d.pos.x - left) / w).toInt().coerceIn(0, dockItems.size)
            d.target = DropTarget.Dock(idx)
            return
        }

        // ---- page grid
        val items = pages[page].filterNot { it.id == d.item.id }
        val cellW = m.cellW.value * den
        val cellH = m.cellH.value * den
        val gx = m.gridLeft.value * den
        val gy = m.gridTop.value * den
        val col = floor((center.x - gx) / cellW).toInt().coerceIn(0, spec.columns - 1)
        val row = floor((center.y - gy) / cellH).toInt().coerceIn(0, spec.rows - 1)

        // Folder creation: hovering the centre of an icon in the *current preview*
        if (d.item is AppItem) {
            val shown = previewPage(page)
            val shownCells = GridPacker.pack(shown, spec.columns, spec.rows)
            shown.forEachIndexed { i, it ->
                val c = shownCells[i] ?: return@forEachIndexed
                if (it is GapItem || it is WidgetItem || it.id == d.item.id) return@forEachIndexed
                val cx = gx + cellW * (c.x + 0.5f)
                val cy = gy + m.iconTop.value * den + m.icon.value * den / 2f
                val r = m.icon.value * den * 0.32f
                if (c.y == row && abs(center.x - cx) < r && abs(center.y - (cy + cellH * c.y)) < r * 1.4f) {
                    armFolder(d, it.id)
                    return
                }
            }
        }
        disarmFolder(d)

        val cells = GridPacker.pack(items, spec.columns, spec.rows)
        val pIndex = row * spec.columns + col
        var index = items.size
        for (i in items.indices) {
            val c = cells[i] ?: continue
            val it = items[i]
            val inside = col >= c.x && col < c.x + it.spanX && row >= c.y && row < c.y + it.spanY
            if (inside) {
                val midX = gx + cellW * (c.x + it.spanX / 2f)
                index = if (center.x > midX) i + 1 else i
                break
            }
            if (c.y * spec.columns + c.x > pIndex) { index = i; break }
        }
        d.target = DropTarget.Page(page, index)
    }

    private fun armFolder(d: DragState, id: String) {
        if (d.folderCandidate == id) return
        d.folderCandidate = id
        d.folderJob?.cancel()
        d.folderJob = scope.launch {
            delay(320)
            if (drag === d && d.folderCandidate == id) {
                d.folderHover = id
                haptic(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    private fun disarmFolder(d: DragState) {
        d.folderCandidate = null
        d.folderJob?.cancel()
        d.folderHover = null
    }

    private fun edgeScroll(d: DragState) {
        val m = metrics ?: return
        val edge = 22f * density
        val w = m.width.value * density
        val dir = when {
            d.pos.x < edge -> -1
            d.pos.x > w - edge -> 1
            else -> 0
        }
        if (dir == d.edgeDir) return
        d.edgeDir = dir
        d.edgeJob?.cancel()
        if (dir == 0) return
        d.edgeJob = scope.launch {
            while (drag === d && d.edgeDir == dir) {
                delay(650)
                val p = pager ?: break
                val homeCount = pagesForDisplay().size
                val next = (p.currentPage + dir).coerceIn(homeOffset, homeOffset + homeCount - 1)
                if (next != p.currentPage) {
                    p.animateScrollToPage(next)
                    updateTarget(d)
                } else break
            }
        }
    }

    fun dragEnd() {
        val d = drag ?: return
        d.folderJob?.cancel(); d.edgeJob?.cancel()
        val target = d.target
        val folderId = d.folderHover
        val item = d.item
        drag = null
        if (folderId == null && target == null) return
        app.layout.update { l ->
            val pages = (l.pages + if (editMode) listOf(emptyList()) else emptyList()).map { it.filterNot { x -> x.id == item.id } }.toMutableList()
            var dock = l.dock.filterNot { it.id == item.id }
            if (folderId != null && item is AppItem) {
                fun merge(list: List<HomeItem>): List<HomeItem> = list.map { t ->
                    when {
                        t.id != folderId -> t
                        t is FolderItem -> t.copy(apps = t.apps + item.appKey)
                        t is AppItem -> {
                            val e = appsByKey[t.appKey]
                            FolderItem(t.id, e?.let { AppCategories.name(it.category, it.packageName) } ?: "Folder", listOf(t.appKey, item.appKey))
                        }
                        else -> t
                    }
                }
                for (i in pages.indices) pages[i] = merge(pages[i])
                dock = merge(dock)
            } else when (target) {
                is DropTarget.Page -> {
                    while (pages.size <= target.page) pages += emptyList<HomeItem>()
                    pages[target.page] = pages[target.page].toMutableList().apply { add(target.index.coerceIn(0, size), item) }
                }
                is DropTarget.Dock -> dock = dock.toMutableList().apply { add(target.index.coerceIn(0, size), item) }
                null -> {}
            }
            GridPacker.normalize(HomeLayout(pages, dock), spec.columns, spec.rows)
        }
    }

    fun cancelDrag() {
        drag?.let { it.folderJob?.cancel(); it.edgeJob?.cancel() }
        drag = null
    }
}
