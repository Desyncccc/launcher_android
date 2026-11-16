package com.liquid.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.lifecycleScope
import com.liquid.launcher.data.HomeLayout
import com.liquid.launcher.data.WidgetItem
import com.liquid.launcher.data.WidgetKinds
import com.liquid.launcher.ui.HomeController
import com.liquid.launcher.ui.LauncherRoot
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    private lateinit var app: LauncherApp
    private lateinit var controller: HomeController
    private var pendingWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        app = application as LauncherApp
        controller = HomeController(app, lifecycleScope)
        controller.requestSystemWidget = ::bindWidget
        setContent {
            controller.view = LocalView.current
            controller.haptics = LocalHapticFeedback.current
            BackHandler(enabled = true) { controller.onBack() }
            LauncherRoot(app, controller)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            controller.onHomePressed()
        }
    }

    override fun onStart() {
        super.onStart()
        app.widgets.start()
    }

    override fun onStop() {
        super.onStop()
        app.widgets.stop()
    }

    // ------------------------------------------------------------- app widgets
    private fun bindWidget(info: AppWidgetProviderInfo) {
        val id = app.widgets.allocate()
        pendingWidgetId = id
        if (app.widgets.bindIfAllowed(id, info)) {
            configureOrAdd(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureOrAdd(id: Int) {
        val info = app.widgets.info(id) ?: return
        if (info.configure != null) {
            runCatching {
                app.widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            }.onFailure { addWidget(id) }
        } else addWidget(id)
    }

    private fun addWidget(id: Int) {
        val info = app.widgets.info(id) ?: return
        val m = controller.metrics ?: return
        val spec = controller.spec
        val dm = resources.displayMetrics
        val (needX, needY) = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) {
            info.targetCellWidth to info.targetCellHeight
        } else {
            ceil(info.minWidth / dm.density / m.cellW.value).toInt() to ceil(info.minHeight / dm.density / m.cellH.value).toInt()
        }
        val sx = if (needX <= 2) 2 else spec.columns
        val sy = needY.coerceIn(1, spec.rows).let { if (spec.style == com.liquid.launcher.data.ThemeStyle.IOS && it % 2 == 1) it + 1 else it }.coerceAtMost(spec.rows)
        controller.addWidget(WidgetItem(HomeLayout.newId(), WidgetKinds.SYSTEM, sx, sy, id))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) configureOrAdd(id) else app.widgets.delete(id)
            REQ_CONFIGURE -> if (resultCode == RESULT_OK) addWidget(id) else app.widgets.delete(id)
        }
    }

    companion object {
        private const val REQ_BIND = 41
        private const val REQ_CONFIGURE = 42
    }
}
