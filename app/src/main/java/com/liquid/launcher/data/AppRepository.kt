package com.liquid.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator

data class AppEntry(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val category: Int,
    val firstInstall: Long,
    val info: LauncherActivityInfo,
) {
    val packageName: String get() = component.packageName
}

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    var onPackagesChanged: ((Set<String>) -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(*packageNames)
    }

    private fun changed(vararg pkgs: String) {
        refresh()
        onPackagesChanged?.invoke(pkgs.toSet())
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun keyOf(component: ComponentName, user: UserHandle): String =
        component.flattenToShortString() + "#" + userManager.getSerialNumberForUser(user)

    fun refresh() {
        scope.launch {
            val collator = Collator.getInstance()
            val list = userManager.userProfiles.flatMap { user ->
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            }.filter { it.componentName.packageName != context.packageName || it.name.endsWith("SettingsActivity") }
                .map { info ->
                    val ai: ApplicationInfo = info.applicationInfo
                    AppEntry(
                        key = keyOf(info.componentName, info.user),
                        component = info.componentName,
                        user = info.user,
                        label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                        category = ai.category,
                        firstInstall = info.firstInstallTime,
                        info = info,
                    )
                }.sortedWith { a, b -> collator.compare(a.label, b.label) }
            _apps.value = list
            _loaded.value = true
        }
    }

    fun find(key: String): AppEntry? = _apps.value.firstOrNull { it.key == key }

    fun launch(app: AppEntry, bounds: Rect?, options: Bundle?) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, bounds, options) }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    val isMainUser: (UserHandle) -> Boolean = { it == Process.myUserHandle() }

    // App shortcuts (long-press quick actions). Only available to the default launcher.
    fun shortcuts(app: AppEntry): List<android.content.pm.ShortcutInfo> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        launcherApps.getShortcuts(q, app.user).orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
            .take(4)
    }.getOrDefault(emptyList())

    fun shortcutIcon(info: android.content.pm.ShortcutInfo) =
        runCatching { launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) }.getOrNull()

    fun startShortcut(info: android.content.pm.ShortcutInfo, bounds: Rect?) {
        runCatching { launcherApps.startShortcut(info, bounds, null) }
    }
}

/** App Library / drawer categories derived from ApplicationInfo.category. */
object AppCategories {
    fun name(category: Int, pkg: String): String = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO -> "Entertainment"
        ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
        ApplicationInfo.CATEGORY_IMAGE -> "Photo & Video"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "Information & Reading"
        ApplicationInfo.CATEGORY_MAPS -> "Travel"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity & Finance"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Utilities"
        else -> guess(pkg)
    }

    private fun guess(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            listOf("whatsapp", "telegram", "instagram", "facebook", "twitter", "snapchat", "discord", "messenger", "tiktok", "reddit", "signal", "mms", "messaging", "dialer", "contacts").any { it in p } -> "Social"
            listOf("music", "spotify", "youtube", "netflix", "video", "player", "podcast", "twitch", "tv").any { it in p } -> "Entertainment"
            listOf("camera", "gallery", "photo", "lightroom", "snapseed").any { it in p } -> "Photo & Video"
            listOf("maps", "uber", "lyft", "booking", "airbnb", "travel", "transit").any { it in p } -> "Travel"
            listOf("docs", "sheets", "office", "drive", "mail", "gmail", "calendar", "notes", "bank", "pay", "wallet", "finance", "keep", "outlook", "slack", "teams").any { it in p } -> "Productivity & Finance"
            listOf("shop", "amazon", "ebay", "store", "vending").any { it in p } -> "Shopping & Food"
            listOf("news", "reader", "kindle", "book", "weather").any { it in p } -> "Information & Reading"
            listOf("health", "fit", "sleep").any { it in p } -> "Health & Fitness"
            listOf("game", "games").any { it in p } -> "Games"
            else -> "Utilities"
        }
    }
}
