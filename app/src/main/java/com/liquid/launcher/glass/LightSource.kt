package com.liquid.launcher.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The environment light that produces Liquid Glass specular highlights.
 * Apple: "in some cases the lighting responds to device motion" – on the Home
 * Screen "based on gyro input, you can see light moving on the edge of the icon".
 */
@Stable
class LightSource {
    /** Unit vector pointing from the surface *towards* the light, screen space (y down). */
    var direction by mutableStateOf(Offset(-0.62f, -0.78f))
        internal set
    val angle: Float get() = atan2(direction.y, direction.x)
}

val LocalLightSource = staticCompositionLocalOf { LightSource() }

private const val BASE_ANGLE = -2.24f // ≈ -128°, top-left

@Composable
fun rememberTiltLight(enabled: Boolean): LightSource {
    val light = remember { LightSource() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, lifecycle) {
        if (!enabled) {
            light.direction = Offset(cos(BASE_ANGLE), sin(BASE_ANGLE))
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var gx = 0f
        var gy = 9.81f * 0.7f
        var refGy = Float.NaN
        var lastAngle = BASE_ANGLE
        var lastUpdate = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                gx += (e.values[0] - gx) * 0.08f
                gy += (e.values[1] - gy) * 0.08f
                if (refGy.isNaN()) refGy = gy
                // slowly re-centre so the "rest" position follows how the phone is held
                refGy += (gy - refGy) * 0.004f
                val roll = (-gx / 9.81f).coerceIn(-1f, 1f)
                val pitch = ((gy - refGy) / 9.81f).coerceIn(-1f, 1f)
                val a = BASE_ANGLE + roll * 1.35f + pitch * 0.8f
                // Every light change re-renders all glass, so only react to a real tilt (~3°)
                // and never more than ~12 times a second.
                val now = e.timestamp
                if (abs(a - lastAngle) > 0.055f && now - lastUpdate > 80_000_000L) {
                    lastAngle = a
                    lastUpdate = now
                    light.direction = Offset(cos(a), sin(a))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                Lifecycle.Event.ON_PAUSE -> sm.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }
    return light
}
