package com.carriez.flutter_hbb

import android.app.Activity
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlin.math.abs

/**
 * Opt-in capture of physical keyboard and pointer input for an outgoing remote
 * session. No accessibility service, root permission or global key interception.
 *
 * The system can consume HOME/brightness shortcuts before they reach Activity;
 * those keys cannot be force-forwarded by this class.
 */
class PersonalRemoteInput(
    private val activity: Activity,
    messenger: BinaryMessenger,
) : EventChannel.StreamHandler {
    companion object {
        private const val CONTROL = "rustdesk/personal_remote_input/control"
        private const val EVENTS = "rustdesk/personal_remote_input/events"
    }

    private var sink: EventChannel.EventSink? = null
    private var enabled = false
    private var captured = false
    private var target: View? = null
    private var previousButtons = 0
    private var touchpadX: Float? = null
    private var touchpadY: Float? = null
    private var touchpadPointers = 0
    private var touchpadDevice = -1
    private val control = MethodChannel(messenger, CONTROL)
    private val events = EventChannel(messenger, EVENTS)

    init {
        events.setStreamHandler(this)
        control.setMethodCallHandler { call, result ->
            when (call.method) {
                "enable" -> result.success(enable())
                "disable" -> {
                    disable()
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink) {
        sink = eventSink
        emit(mapOf("type" to "mode", "enabled" to enabled))
        emit(mapOf("type" to "capture", "captured" to captured))
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    private fun emit(data: Map<String, Any>) {
        activity.runOnUiThread { sink?.success(data) }
    }

    private fun resetPointerSamples() {
        previousButtons = 0
        touchpadX = null
        touchpadY = null
        touchpadPointers = 0
        touchpadDevice = -1
    }

    private fun enable(): Boolean {
        if (enabled) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !activity.hasWindowFocus()) {
            return false
        }
        enabled = true
        resetPointerSamples()
        // FlutterView generally owns focus. Capture its event stream rather
        // than stealing keyboard focus for the decor view.
        val view = activity.window.decorView.findFocus() ?: activity.window.decorView
        target = view
        view.setOnCapturedPointerListener(View.OnCapturedPointerListener { _, event ->
            handleCapturedPointer(event)
        })
        view.requestPointerCapture()
        emit(mapOf("type" to "mode", "enabled" to true))
        view.postDelayed({
            if (enabled && target === view) {
                captured = view.hasPointerCapture()
                emit(mapOf("type" to "capture", "captured" to captured))
            }
        }, 180)
        return true
    }

    fun disable() {
        val view = target
        enabled = false
        captured = false
        target = null
        resetPointerSamples()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view?.releasePointerCapture()
            view?.setOnCapturedPointerListener(null)
        }
        emit(mapOf("type" to "capture", "captured" to false))
        emit(mapOf("type" to "mode", "enabled" to false))
    }

    fun dispose() {
        disable()
        control.setMethodCallHandler(null)
        events.setStreamHandler(null)
        sink = null
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus && enabled) disable()
    }

    fun onPointerCaptureChanged(hasCapture: Boolean) {
        if (!enabled) return
        captured = hasCapture
        if (!hasCapture) {
            resetPointerSamples()
        }
        emit(mapOf("type" to "capture", "captured" to hasCapture))
    }

    /**
     * Return true only for F1-F12 events actually delivered to this Activity.
     * Non-function keys retain RustDesk/Flutter's existing input behavior.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!enabled || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) {
            return false
        }
        val keyCode = event.keyCode
        if (keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            // Native dispatch intercepts these before Flutter, avoiding double
            // sends. A single press avoids a stuck key if key-up is lost.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                emit(mapOf("type" to "function", "number" to keyCode - KeyEvent.KEYCODE_F1 + 1))
            }
            return true
        }

        // Non-character keycodes only. Do not log text, passwords or devices.
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN ||
            keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_BRIGHTNESS_UP ||
            keyCode == KeyEvent.KEYCODE_BRIGHTNESS_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                emit(mapOf("type" to "key_observed", "keyCode" to keyCode,
                    "scanCode" to event.scanCode))
            }
        }
        return false
    }

    private fun hasSource(value: Int, source: Int): Boolean =
        value and source == source

    private fun handleCapturedPointer(event: MotionEvent): Boolean {
        if (!enabled) return false
        if (!captured) {
            captured = true
            emit(mapOf("type" to "capture", "captured" to true))
        }
        val isMouseRelative =
            hasSource(event.source, InputDevice.SOURCE_MOUSE_RELATIVE)
        // A physical touchpad can advertise SOURCE_TOUCHPAD even when Android
        // reports already-recognized relative mouse movements. In that case,
        // prefer the relative deltas rather than differencing those deltas.
        val isTouchpad = !isMouseRelative && (
            hasSource(event.source, InputDevice.SOURCE_TOUCHPAD) ||
                hasSource(event.device?.sources ?: 0, InputDevice.SOURCE_TOUCHPAD)
            )
        val isMouse = isMouseRelative ||
            hasSource(event.source, InputDevice.SOURCE_MOUSE)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isTouchpad) {
                    // Android <=16 generally reports raw finger positions on
                    // a captured trackpad. Two fingers are treated as scroll.
                    val count = event.pointerCount
                    if (count == 0) return true
                    val x = (0 until count).sumOf { event.getX(it).toDouble() }
                        .toFloat() / count
                    val y = (0 until count).sumOf { event.getY(it).toDouble() }
                        .toFloat() / count
                    if (touchpadDevice == event.deviceId &&
                        touchpadPointers == count &&
                        touchpadX != null && touchpadY != null) {
                        val dx = x - touchpadX!!
                        val dy = y - touchpadY!!
                        if (abs(dx) < 200 && abs(dy) < 200) {
                            if (count >= 2) {
                                // Finger drag down -> content scrolls down.
                                emit(mapOf("type" to "scroll", "dx" to (-dx / 36.0),
                                    "dy" to (-dy / 36.0)))
                            } else {
                                emitMove(dx.toDouble(), dy.toDouble())
                            }
                        }
                    }
                    touchpadX = x
                    touchpadY = y
                    touchpadPointers = count
                    touchpadDevice = event.deviceId
                } else if (isMouse) {
                    val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                        .takeIf { it != 0f } ?: event.x
                    val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                        .takeIf { it != 0f } ?: event.y
                    emitMove(dx.toDouble(), dy.toDouble())
                }
            }
            MotionEvent.ACTION_SCROLL -> {
                emit(mapOf("type" to "scroll",
                    "dx" to event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                    "dy" to event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()))
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                touchpadX = null
                touchpadY = null
                touchpadPointers = 0
            }
            MotionEvent.ACTION_CANCEL -> resetPointerSamples()
        }
        if (isMouse || isTouchpad) forwardButtons(event)
        return true
    }

    private fun emitMove(dx: Double, dy: Double) {
        if (dx.isFinite() && dy.isFinite() && (dx != 0.0 || dy != 0.0)) {
            emit(mapOf("type" to "move", "dx" to dx, "dy" to dy))
        }
    }

    private fun forwardButtons(event: MotionEvent) {
        val state = event.buttonState
        val changed = previousButtons xor state
        for ((mask, button) in arrayOf(
            MotionEvent.BUTTON_PRIMARY to "left",
            MotionEvent.BUTTON_SECONDARY to "right",
            MotionEvent.BUTTON_TERTIARY to "wheel"
        )) {
            if (changed and mask != 0) {
                emit(mapOf("type" to "button", "button" to button,
                    "down" to (state and mask != 0)))
            }
        }
        previousButtons = state
    }
}
