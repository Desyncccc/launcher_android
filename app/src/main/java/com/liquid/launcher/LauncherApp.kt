package com.liquid.launcher

import android.app.Application
import com.liquid.launcher.data.AppRepository
import com.liquid.launcher.data.LayoutStore
import com.liquid.launcher.data.PrefsRepository
import com.liquid.launcher.icons.IconPackManager
import com.liquid.launcher.icons.IconProvider
import com.liquid.launcher.widgets.WidgetHost

class LauncherApp : Application() {
    lateinit var prefs: PrefsRepository
    lateinit var apps: AppRepository
    lateinit var layout: LayoutStore
    lateinit var iconPacks: IconPackManager
    lateinit var icons: IconProvider
    lateinit var widgets: WidgetHost

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PrefsRepository(this)
        apps = AppRepository(this)
        layout = LayoutStore(this, prefs)
        iconPacks = IconPackManager(this)
        icons = IconProvider(this, iconPacks)
        widgets = WidgetHost(this)
    }

    companion object {
        lateinit var instance: LauncherApp
            private set
    }
}
