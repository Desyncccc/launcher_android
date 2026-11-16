package com.liquid.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed interface HomeItem {
    val id: String
    val spanX: Int get() = 1
    val spanY: Int get() = 1
}

/** Placeholder that opens a gap while something is dragged over the grid (never persisted). */
data class GapItem(override val spanX: Int, override val spanY: Int) : HomeItem {
    override val id: String get() = GAP_ID
    companion object { const val GAP_ID = "__gap__" }
}

data class AppItem(override val id: String, val appKey: String) : HomeItem
data class FolderItem(override val id: String, val name: String, val apps: List<String>) : HomeItem
data class WidgetItem(
    override val id: String,
    val kind: String,
    override val spanX: Int,
    override val spanY: Int,
    val appWidgetId: Int = -1,
) : HomeItem

object WidgetKinds {
    const val CLOCK = "clock"
    const val CALENDAR = "calendar"
    const val BATTERY = "battery"
    const val DIGITAL_CLOCK = "digital_clock"
    const val SYSTEM = "system"
}

data class HomeLayout(val pages: List<List<HomeItem>>, val dock: List<HomeItem>) {

    fun allAppKeys(): Set<String> = buildSet {
        (pages.flatten() + dock).forEach {
            when (it) {
                is AppItem -> add(it.appKey)
                is FolderItem -> addAll(it.apps)
                else -> {}
            }
        }
    }

    fun findItem(id: String): HomeItem? = (pages.flatten() + dock).firstOrNull { it.id == id }

    fun toJson(): String = JSONObject().apply {
        put("pages", JSONArray().apply { pages.forEach { p -> put(JSONArray().apply { p.forEach { put(itemJson(it)) } }) } })
        put("dock", JSONArray().apply { dock.forEach { put(itemJson(it)) } })
    }.toString()

    companion object {
        fun newId(): String = UUID.randomUUID().toString().substring(0, 12)

        private fun itemJson(i: HomeItem): JSONObject = JSONObject().apply {
            put("id", i.id)
            when (i) {
                is AppItem -> { put("t", "app"); put("k", i.appKey) }
                is FolderItem -> { put("t", "folder"); put("n", i.name); put("a", JSONArray(i.apps)) }
                is WidgetItem -> { put("t", "widget"); put("kind", i.kind); put("sx", i.spanX); put("sy", i.spanY); put("wid", i.appWidgetId) }
                is GapItem -> put("t", "gap")
            }
        }

        private fun parseItem(o: JSONObject): HomeItem? = when (o.optString("t")) {
            "app" -> AppItem(o.getString("id"), o.getString("k"))
            "folder" -> FolderItem(o.getString("id"), o.optString("n", "Folder"), o.getJSONArray("a").let { a -> List(a.length()) { a.getString(it) } })
            "widget" -> WidgetItem(o.getString("id"), o.getString("kind"), o.optInt("sx", 2), o.optInt("sy", 2), o.optInt("wid", -1))
            else -> null
        }

        fun fromJson(s: String): HomeLayout? = runCatching {
            val o = JSONObject(s)
            val pages = o.getJSONArray("pages").let { arr ->
                List(arr.length()) { p -> arr.getJSONArray(p).let { pa -> List(pa.length()) { parseItem(pa.getJSONObject(it)) }.filterNotNull() } }
            }
            val dock = o.getJSONArray("dock").let { a -> List(a.length()) { parseItem(a.getJSONObject(it)) }.filterNotNull() }
            HomeLayout(pages, dock)
        }.getOrNull()
    }
}

/** iOS-style flow packing: items fill row-major, widgets claim whole blocks. */
object GridPacker {
    data class Cell(val x: Int, val y: Int)

    /** Returns the cell of each item, or null for items that overflow the page. */
    fun pack(items: List<HomeItem>, cols: Int, rows: Int): List<Cell?> {
        val occ = Array(rows) { BooleanArray(cols) }
        return items.map { item ->
            val sx = item.spanX.coerceAtMost(cols)
            val sy = item.spanY.coerceAtMost(rows)
            var found: Cell? = null
            loop@ for (y in 0..rows - sy) for (x in 0..cols - sx) {
                var free = true
                for (yy in y until y + sy) { for (xx in x until x + sx) if (occ[yy][xx]) { free = false; break }; if (!free) break }
                if (free) { found = Cell(x, y); break@loop }
            }
            found?.let { c -> for (yy in c.y until c.y + sy) for (xx in c.x until c.x + sx) occ[yy][xx] = true }
            found
        }
    }

    /** Moves overflowing items to the following pages so every page fits the grid. */
    fun normalize(layout: HomeLayout, cols: Int, rows: Int, keepEmpty: Boolean = false): HomeLayout {
        val result = mutableListOf<List<HomeItem>>()
        var carry = emptyList<HomeItem>()
        val src = layout.pages.toMutableList()
        var i = 0
        while (i < src.size || carry.isNotEmpty()) {
            val page = carry + (src.getOrNull(i) ?: emptyList())
            val cells = pack(page, cols, rows)
            val fits = page.filterIndexed { idx, _ -> cells[idx] != null }
            carry = page.filterIndexed { idx, _ -> cells[idx] == null }
            if (fits.isNotEmpty() || keepEmpty) result += fits
            i++
            if (i > src.size + 50) break
        }
        if (result.isEmpty()) result += emptyList<HomeItem>()
        return layout.copy(pages = result)
    }
}
