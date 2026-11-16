package com.liquid.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LayoutStore(private val context: Context, private val prefs: PrefsRepository) {
    private val file = File(context.filesDir, "home_layout.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _layout = MutableStateFlow<HomeLayout?>(null)
    val layout: StateFlow<HomeLayout?> = _layout.asStateFlow()

    init {
        if (file.exists()) _layout.value = HomeLayout.fromJson(file.readText())
    }

    fun update(transform: (HomeLayout) -> HomeLayout) {
        val cur = _layout.value ?: return
        val next = transform(cur)
        if (next == cur) return
        _layout.value = next
        persist(next)
    }

    private fun persist(l: HomeLayout) {
        scope.launch { runCatching { file.writeText(l.toJson()) } }
    }

    fun reset() {
        file.delete()
        prefs.setKnownApps(emptySet())
        _layout.value = null
    }

    /** Creates the first layout, adds newly installed apps and drops uninstalled ones. */
    fun reconcile(apps: List<AppEntry>, cols: Int, rows: Int) {
        if (apps.isEmpty()) return
        val installed = apps.associateBy { it.key }
        val cur = _layout.value
        val known = prefs.knownApps()
        if (cur == null) {
            val l = GridPacker.normalize(buildDefault(apps), cols, rows)
            _layout.value = l
            persist(l)
            prefs.setKnownApps(installed.keys)
            return
        }
        fun clean(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
            when (item) {
                is AppItem -> item.takeIf { it.appKey in installed }
                is FolderItem -> {
                    val a = item.apps.filter { it in installed }
                    when {
                        a.isEmpty() -> null
                        a.size == 1 -> AppItem(item.id, a.first())
                        else -> item.copy(apps = a)
                    }
                }
                is WidgetItem -> item
                is GapItem -> null
            }
        }
        var next = HomeLayout(cur.pages.map(::clean), clean(cur.dock))
        val present = next.allAppKeys()
        val newOnes = apps.filter { it.key !in known && it.key !in present }
        if (newOnes.isNotEmpty()) {
            val pages = next.pages.toMutableList()
            if (pages.isEmpty()) pages += emptyList<HomeItem>()
            pages[pages.lastIndex] = pages.last() + newOnes.map { AppItem(HomeLayout.newId(), it.key) }
            next = next.copy(pages = pages)
        }
        next = GridPacker.normalize(next, cols, rows)
        prefs.setKnownApps(known + installed.keys)
        if (next != cur) {
            _layout.value = next
            persist(next)
        }
    }

    private fun resolveKey(apps: List<AppEntry>, intent: Intent): String? {
        val pm = context.packageManager
        val pkgs = runCatching {
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        val def = runCatching { pm.resolveActivity(intent, 0)?.activityInfo?.packageName }.getOrNull()
        val ordered = listOfNotNull(def) + pkgs
        for (p in ordered) apps.firstOrNull { it.packageName == p }?.let { return it.key }
        return null
    }

    private fun byPackage(apps: List<AppEntry>, vararg pkgs: String): String? =
        pkgs.firstNotNullOfOrNull { p -> apps.firstOrNull { it.packageName == p }?.key }

    private fun buildDefault(apps: List<AppEntry>): HomeLayout {
        val used = mutableSetOf<String>()
        fun take(k: String?): String? = k?.takeIf { used.add(it) }

        val phone = take(resolveKey(apps, Intent(Intent.ACTION_DIAL)))
        val sms = take(Telephony.Sms.getDefaultSmsPackage(context)?.let { byPackage(apps, it) }
            ?: resolveKey(apps, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))))
        val browser = take(resolveKey(apps, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
        val music = take(resolveKey(apps, Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///x.mp3"), "audio/*"))
            ?: byPackage(apps, "com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music"))
        val camera = take(resolveKey(apps, Intent(MediaStore.ACTION_IMAGE_CAPTURE)))
        val gallery = take(resolveKey(apps, Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/*"))
            ?: byPackage(apps, "com.google.android.apps.photos"))
        val calendar = take(resolveKey(apps, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/0")))
            ?: byPackage(apps, "com.google.android.calendar", "com.samsung.android.calendar"))
        val clock = take(resolveKey(apps, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        val maps = take(resolveKey(apps, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=coffee"))))
        val mail = take(resolveKey(apps, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))))
        val settings = take(resolveKey(apps, Intent(Settings.ACTION_SETTINGS)))
        val store = take(byPackage(apps, "com.android.vending", "com.sec.android.app.samsungapps", "com.xiaomi.market"))
        val notes = take(byPackage(apps, "com.google.android.keep", "com.samsung.android.app.notes", "com.miui.notes"))
        val weather = take(byPackage(apps, "com.google.android.apps.weather", "com.sec.android.daemonapp", "com.miui.weather2"))
        val files = take(byPackage(apps, "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer", "com.android.documentsui"))
        val contacts = take(resolveKey(apps, Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people"))))
        val launcherSettings = take(apps.firstOrNull { it.packageName == context.packageName }?.key)

        val dock = listOfNotNull(phone, browser, sms, music ?: camera).map { AppItem(HomeLayout.newId(), it) }
        val firstPage = mutableListOf<HomeItem>(
            WidgetItem(HomeLayout.newId(), WidgetKinds.CALENDAR, 2, 2),
            WidgetItem(HomeLayout.newId(), WidgetKinds.CLOCK, 2, 2),
        )
        listOfNotNull(
            if (music != null) camera else null, gallery, calendar, clock, maps, weather, mail, notes,
            store, settings, files, contacts, launcherSettings,
        ).forEach { firstPage += AppItem(HomeLayout.newId(), it) }

        val rest = apps.filter { it.key !in used }.map { AppItem(HomeLayout.newId(), it.key) as HomeItem }
        // Group the leftovers into a few folders on page 2 like a fresh phone would, then the rest.
        val byCat = apps.filter { it.key !in used }.groupBy { AppCategories.name(it.category, it.packageName) }
        val folders = byCat.filter { it.value.size >= 3 && it.key == "Utilities" }.map { (name, list) ->
            FolderItem(HomeLayout.newId(), name, list.map { it.key })
        }
        val inFolders = folders.flatMap { it.apps }.toSet()
        val second = folders + rest.filter { (it as AppItem).appKey !in inFolders }
        return HomeLayout(listOf(firstPage, second), dock)
    }
}
