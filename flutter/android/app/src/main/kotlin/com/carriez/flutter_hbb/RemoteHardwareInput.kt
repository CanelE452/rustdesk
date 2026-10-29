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
    private var captureConfirmed = false
    // Android <=16: captured touchpad finger locations are absolute, not mouse deltas.
    private var touchpadFingerId = -1
    private var touchpadLastX = 0f
    private var touchpadLastY = 0f

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
        val target = activity.currentFocus?.takeIf { it.isAttachedToWindow }
            ?: content?.getChildAt(0)
            ?: return mapOf("requested" to false, "captured" to false)

        enabled = true
        mouseButtons = 0
        captureConfirmed = false
        touchpadFingerId = -1
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
        captureConfirmed = false
        touchpadFingerId = -1
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

    /// Called by MainActivity after Android actually grants or loses capture.
    /// requestPointerCapture() is asynchronous, so "requested" is not "captured".
    fun onPointerCaptureStateChanged(captured: Boolean) {
        if (!enabled) return
        captureConfirmed = captured
        emit("capture_state", mapOf("captured" to captured))
        if (!captured) {
            // Never leave a remotely held button pressed on capture loss.
            disable("capture_lost")
        }
    }

    private fun resetTouchpadFinger() {
        touchpadFingerId = -1
    }

    /// Pre-Android-17 captured touchpads use SOURCE_TOUCHPAD with raw finger
    /// positions and AXIS_RELATIVE_X/Y. Finger-down is *not* a mouse click.
    private fun handleCapturedTouchpad(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                resetTouchpadFinger()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                // Never join two different fingers into one movement delta.
                resetTouchpadFinger()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                resetTouchpadFinger()
                if (event.pointerCount == 1) {
                    touchpadFingerId = event.getPointerId(0)
                    touchpadLastX = event.getX(0)
                    touchpadLastY = event.getY(0)
                }
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                emit("scroll", mapOf(
                    "x" to event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                    "y" to event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
                ))
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                return handleCapturedButtons(event)
            }
            MotionEvent.ACTION_MOVE -> {
                // Android 14+ two-finger gestures expose scroll distances.
                // Avoid interpreting multi-finger pan as cursor movement.
                if (event.pointerCount > 1) {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val scrollX = event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE)
                        val scrollY = event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE)
                        if (scrollX != 0f || scrollY != 0f) {
                            // Approximate 40 screen pixels per wheel unit;
                            // physical direction/sensitivity require device verification.
                            emit("scroll", mapOf(
                                "x" to (scrollX / 40f).toDouble(),
                                "y" to (scrollY / 40f).toDouble(),
                            ))
                        }
                    }
                    resetTouchpadFinger()
                    return true
                }
                if (event.pointerCount != 1) return true
                val pointerId = event.getPointerId(0)
                val currentX = event.getX(0)
                val currentY = event.getY(0)
                val axisX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val axisY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                if (touchpadFingerId != pointerId) {
                    touchpadFingerId = pointerId
                    touchpadLastX = currentX
                    touchpadLastY = currentY
                    return true
                }
                val dx = if (axisX != 0f || axisY != 0f) axisX
                         else currentX - touchpadLastX
                val dy = if (axisX != 0f || axisY != 0f) axisY
                         else currentY - touchpadLastY
                touchpadLastX = currentX
                touchpadLastY = currentY
                if (dx != 0f || dy != 0f) {
                    emit("move", mapOf(
                        "dx" to dx.toDouble(),
                        "dy" to dy.toDouble(),
                    ))
                }
                return true
            }
        }
        return true
    }

    private fun handleCapturedButtons(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val buttons = when (action) {
            MotionEvent.ACTION_DOWN ->
                if (event.buttonState == 0) MotionEvent.BUTTON_PRIMARY
                else event.buttonState
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
        return true
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
        if (!enabled || !captureConfirmed) return false
        val source = event.source
        val isTouchpad = (source and InputDevice.SOURCE_TOUCHPAD) ==
            InputDevice.SOURCE_TOUCHPAD
        if (isTouchpad) {
            return handleCapturedTouchpad(event)
        }
        val isMouse = (source and InputDevice.SOURCE_MOUSE) ==
            InputDevice.SOURCE_MOUSE ||
            (source and InputDevice.SOURCE_MOUSE_RELATIVE) ==
            InputDevice.SOURCE_MOUSE_RELATIVE
        if (!isMouse) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                // Captured mouse coordinates represent relative motion.
                val dx = event.getX(0)
                val dy = event.getY(0)
                if (dx != 0f || dy != 0f) {
                    emit("move", mapOf(
                        "dx" to dx.toDouble(),
                        "dy" to dy.toDouble(),
                    ))
                }
            }
            MotionEvent.ACTION_SCROLL -> emit("scroll", mapOf(
                "x" to event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                "y" to event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
            ))
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> return handleCapturedButtons(event)
        }
        return true
    }

    fun dispose() {
        disable("dispose")
        channel.setMethodCallHandler(null)
    }
}
