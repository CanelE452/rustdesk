package com.carriez.flutter_hbb

import android.app.Activity
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/**
 * Explicitly enabled Android hardware-input forwarding for a REMOTE session.
 *
 * Does not intercept HOME/SYSTEM keys or any normal typing keys. Those are
 * dispatched to Flutter/Android as usual. Function keys are intercepted only
 * while the user has turned on the controller mode.
 *
 * Captured mouse motion uses relative deltas as documented for
 * View.requestPointerCapture(). Pre-Android-17 OEM touchpads may not expose
 * those events, so "requested" must not be confused with "working".
 */
internal class RemoteHardwareInput(
    private val activity: Activity,
    messenger: BinaryMessenger,
) {
    companion object {
        const val CHANNEL = "rustdesk_remote_hardware_input"
    }

    private val channel = MethodChannel(messenger, CHANNEL)
    private var enabled = false
    private var pointerView: View? = null
    private var mouseButtons = 0

    init {
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "setEnabled" -> {
                    val shouldEnable = call.argument<Boolean>("enabled") == true
                    if (shouldEnable) {
                        result.success(enable())
                    } else {
                        disable("user")
                        result.success(mapOf("requested" to false, "captured" to false))
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun emit(kind: String, extras: Map<String, Any> = emptyMap()) {
        if (!enabled && kind != "released") return
        channel.invokeMethod("onEvent", mapOf("kind" to kind) + extras)
    }

    private fun enable(): Map<String, Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return mapOf("requested" to false, "captured" to false)
        }
        if (!activity.hasWindowFocus()) {
            return mapOf("requested" to false, "captured" to false)
        }
        if (enabled) {
            return mapOf(
                "requested" to true,
                "captured" to (pointerView?.hasPointerCapture() == true),
            )
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val target = content?.getChildAt(0)
            ?: return mapOf("requested" to false, "captured" to false)

        enabled = true
        mouseButtons = 0
        pointerView = target
        target.isFocusableInTouchMode = true
        target.requestFocus()
        target.setOnCapturedPointerListener { _, motion ->
            handleCapturedPointer(motion)
        }
        target.requestPointerCapture()
        return mapOf(
            "requested" to true,
            "captured" to target.hasPointerCapture(),
        )
    }

    fun disable(reason: String) {
        if (!enabled) return
        // The remote controller must release any held button on receipt.
        val held = mouseButtons
        enabled = false
        mouseButtons = 0
        val view = pointerView
        pointerView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view?.setOnCapturedPointerListener(null)
            view?.releasePointerCapture()
        }
        channel.invokeMethod(
            "onEvent",
            mapOf("kind" to "released", "reason" to reason, "buttons" to held),
        )
    }

    fun interceptFunctionKey(event: KeyEvent): Boolean {
        if (!enabled) return false
        val index = event.keyCode - KeyEvent.KEYCODE_F1 + 1
        if (index !in 1..12) {
            // In particular, never swallow HOME or other system shortcuts.
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> emit(
                "function_key", mapOf(
                    "index" to index,
                    "down" to true,
                    "repeat" to (event.repeatCount > 0),
                ),
            )
            KeyEvent.ACTION_UP -> emit(
                "function_key", mapOf(
                    "index" to index,
                    "down" to false,
                    "repeat" to false,
                ),
            )
            else -> return false
        }
        return true
    }

    private fun handleCapturedPointer(event: MotionEvent): Boolean {
        if (!enabled) return false
        val source = event.source
        val sourceIsMouse = (source and InputDevice.SOURCE_MOUSE) ==
            InputDevice.SOURCE_MOUSE ||
            (source and InputDevice.SOURCE_MOUSE_RELATIVE) ==
            InputDevice.SOURCE_MOUSE_RELATIVE
        if (!sourceIsMouse) return false

        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                // For captured mouse input getX/getY represent relative motion.
                // Some devices expose AXIS_RELATIVE_X/Y instead.
                val axisX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val axisY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                val dx = if (axisX != 0f || axisY != 0f) axisX else event.x
                val dy = if (axisX != 0f || axisY != 0f) axisY else event.y
                if (dx != 0f || dy != 0f) {
                    emit("move", mapOf(
                        "dx" to dx.toDouble(),
                        "dy" to dy.toDouble(),
                    ))
                }
            }
            MotionEvent.ACTION_SCROLL -> {
                emit("scroll", mapOf(
                    "x" to event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                    "y" to event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
                ))
            }
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                val buttons = when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (event.buttonState == 0) MotionEvent.BUTTON_PRIMARY
                        else event.buttonState
                    }
                    MotionEvent.ACTION_UP -> 0
                    MotionEvent.ACTION_BUTTON_PRESS ->
                        event.buttonState or event.actionButton
                    MotionEvent.ACTION_BUTTON_RELEASE ->
                        event.buttonState and event.actionButton.inv()
                    else -> event.buttonState
                }
                val supportedButtons = buttons and (
                    MotionEvent.BUTTON_PRIMARY or
                    MotionEvent.BUTTON_SECONDARY or
                    MotionEvent.BUTTON_TERTIARY
                )
                if (supportedButtons != mouseButtons) {
                    mouseButtons = supportedButtons
                    emit("buttons", mapOf("buttons" to supportedButtons))
                }
            }
        }
        return true
    }

    fun dispose() {
        disable("dispose")
        channel.setMethodCallHandler(null)
    }
}
