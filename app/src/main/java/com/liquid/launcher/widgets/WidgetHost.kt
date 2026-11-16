package com.liquid.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** Hosts real Android app widgets alongside the built-in iOS-style ones. */
class WidgetHost(context: Context) {
    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host = AppWidgetHost(appContext, HOST_ID)

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }

    fun providers(): List<AppWidgetProviderInfo> = runCatching { manager.installedProviders }.getOrDefault(emptyList())
        .sortedBy { it.loadLabel(appContext.packageManager)?.lowercase() }

    fun allocate(): Int = host.allocateAppWidgetId()
    fun delete(id: Int) = runCatching { host.deleteAppWidgetId(id) }
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    companion object { const val HOST_ID = 2611 }
}
