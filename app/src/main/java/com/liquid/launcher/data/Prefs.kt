package com.liquid.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeStyle(val title: String, val subtitle: String) {
    IOS("iOS 26", "Liquid Glass"),
    ONEUI("One UI 8", "Samsung"),
    HYPEROS("HyperOS", "Xiaomi · MIUI"),
}

/** iOS 26 Home Screen "Customize" icon appearances. */
enum class IconAppearance(val title: String) { DEFAULT("Default"), DARK("Dark"), CLEAR("Clear"), TINTED("Tinted") }

/** Light/dark flavour of Clear and Tinted icons. */
enum class AppearanceTone { AUTO, LIGHT, DARK }

data class LauncherPrefs(
    val theme: ThemeStyle = ThemeStyle.IOS,
    val appearance: IconAppearance = IconAppearance.DEFAULT,
    val tone: AppearanceTone = AppearanceTone.AUTO,
    val tintHue: Float = 210f,
    val tintSaturation: Float = 0.75f,
    /** iOS 26.1: Settings › Display › Liquid Glass › Clear / Tinted. */
    val glassTinted: Boolean = false,
    val largeIcons: Boolean = false,
    val showLabels: Boolean = true,
    val wallpaperId: String = "tahoe",
    val iconPack: String = "",
    val gyroLighting: Boolean = true,
    val reduceTransparency: Boolean = false,
    val badges: Boolean = true,
    val columns: Int = 0,
    val rows: Int = 0,
    val darkMode: Int = 0, // 0 = system, 1 = light, 2 = dark
    val wallpaperVersion: Int = 0,
)

class PrefsRepository(context: Context) {
    private val sp = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<LauncherPrefs> = _state.asStateFlow()

    private fun read(): LauncherPrefs {
        val d = LauncherPrefs()
        return LauncherPrefs(
            theme = runCatching { ThemeStyle.valueOf(sp.getString("theme", d.theme.name)!!) }.getOrDefault(d.theme),
            appearance = runCatching { IconAppearance.valueOf(sp.getString("appearance", d.appearance.name)!!) }.getOrDefault(d.appearance),
            tone = runCatching { AppearanceTone.valueOf(sp.getString("tone", d.tone.name)!!) }.getOrDefault(d.tone),
            tintHue = sp.getFloat("tintHue", d.tintHue),
            tintSaturation = sp.getFloat("tintSat", d.tintSaturation),
            glassTinted = sp.getBoolean("glassTinted", d.glassTinted),
            largeIcons = sp.getBoolean("largeIcons", d.largeIcons),
            showLabels = sp.getBoolean("showLabels", d.showLabels),
            wallpaperId = sp.getString("wallpaper", d.wallpaperId)!!,
            iconPack = sp.getString("iconPack", d.iconPack)!!,
            gyroLighting = sp.getBoolean("gyro", d.gyroLighting),
            reduceTransparency = sp.getBoolean("reduceTransparency", d.reduceTransparency),
            badges = sp.getBoolean("badges", d.badges),
            columns = sp.getInt("columns", d.columns),
            rows = sp.getInt("rows", d.rows),
            darkMode = sp.getInt("darkMode", d.darkMode),
            wallpaperVersion = sp.getInt("wallpaperVersion", 0),
        )
    }

    fun update(block: (LauncherPrefs) -> LauncherPrefs) {
        val n = block(_state.value)
        _state.value = n
        sp.edit()
            .putString("theme", n.theme.name)
            .putString("appearance", n.appearance.name)
            .putString("tone", n.tone.name)
            .putFloat("tintHue", n.tintHue)
            .putFloat("tintSat", n.tintSaturation)
            .putBoolean("glassTinted", n.glassTinted)
            .putBoolean("largeIcons", n.largeIcons)
            .putBoolean("showLabels", n.showLabels)
            .putString("wallpaper", n.wallpaperId)
            .putString("iconPack", n.iconPack)
            .putBoolean("gyro", n.gyroLighting)
            .putBoolean("reduceTransparency", n.reduceTransparency)
            .putBoolean("badges", n.badges)
            .putInt("columns", n.columns)
            .putInt("rows", n.rows)
            .putInt("darkMode", n.darkMode)
            .putInt("wallpaperVersion", n.wallpaperVersion)
            .apply()
    }

    fun knownApps(): Set<String> = sp.getStringSet("knownApps", null) ?: emptySet()
    fun setKnownApps(s: Set<String>) = sp.edit().putStringSet("knownApps", HashSet(s)).apply()
    fun launchCounts(): Map<String, Int> = sp.getString("launchCounts", "")!!
        .split('\n').filter { it.contains('\t') }
        .associate { it.substringBefore('\t') to (it.substringAfter('\t').toIntOrNull() ?: 0) }

    fun recordLaunch(key: String) {
        val m = launchCounts().toMutableMap()
        m[key] = (m[key] ?: 0) + 1
        sp.edit().putString("launchCounts", m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }
}
