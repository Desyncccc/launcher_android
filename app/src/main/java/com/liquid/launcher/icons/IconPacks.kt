package com.liquid.launcher.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

/**
 * Support for the de-facto standard icon pack format (ADW / Nova / Lawnchair:
 * appfilter.xml mapping ComponentInfo{pkg/cls} → drawable). This is how the real
 * iOS / One UI / HyperOS icon artwork that users download gets into the launcher.
 */
class IconPackManager(private val context: Context) {
    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "app.lawnchair.icons.THEMED_ICON",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
    )

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        return actions.flatMap { a ->
            runCatching { pm.queryIntentActivities(Intent(a), 0) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().map { pkg ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            IconPackInfo(pkg, label)
        }.sortedBy { it.label.lowercase() }
    }

    private var loadedPkg: String? = null
    private var map: Map<String, String> = emptyMap()
    private var res: Resources? = null

    @Synchronized
    fun load(pkg: String) {
        if (pkg == loadedPkg) return
        loadedPkg = pkg
        map = emptyMap(); res = null
        if (pkg.isEmpty()) return
        runCatching {
            val r = context.packageManager.getResourcesForApplication(pkg)
            res = r
            map = parse(r, pkg)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun parse(r: Resources, pkg: String): Map<String, String> {
        val out = HashMap<String, String>()
        val parser: XmlPullParser? = run {
            val id = r.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) r.getXml(id) else runCatching {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(r.assets.open("appfilter.xml"), "utf-8")
                }
            }.getOrNull()
        }
        parser ?: return out
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val draw = parser.getAttributeValue(null, "drawable")
                if (comp != null && draw != null) {
                    val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                    out[inner] = draw
                }
            }
            ev = parser.next()
        }
        return out
    }

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
    @Synchronized
    fun drawableFor(component: ComponentName, densityDpi: Int): Drawable? {
        val r = res ?: return null
        val pkg = loadedPkg ?: return null
        val full = component.packageName + "/" + component.className
        val short = component.flattenToShortString()
        val name = map[full] ?: map[short] ?: return null
        val id = r.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { r.getDrawableForDensity(id, densityDpi, null) }.getOrNull()
    }
}
