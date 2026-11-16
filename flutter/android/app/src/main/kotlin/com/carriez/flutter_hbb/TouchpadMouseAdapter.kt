package com.carriez.flutter_hbb

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Android reports keyboard-cover touchpad pointers with TOOL_TYPE_FINGER, and Flutter picks the
 * pointer kind from the tool type alone, so the remote page sees them as touches: a plain glide
 * (hover) is ignored and a two-finger swipe turns into a one-finger drag. Relabelled as mouse
 * pointers, Flutter's mouse handling applies instead: hover moves the remote cursor, button
 * presses click, and a button-less down on a mouse source becomes a trackpad pan (scrolling).
 */
internal class TouchpadMouseAdapter {
    // Pinches and three/four-finger swipes keep their finger events for the whole gesture.
    private var passThroughGesture = false

    /** Returns a mouse copy of [event] that the caller must recycle, or null to keep [event]. */
    fun toMouseEvent(event: MotionEvent): MotionEvent? {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE) &&
            !event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
        ) {
            return null
        }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            passThroughGesture = isMultiFingerGesture(event)
        }
        val passThrough = passThroughGesture
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            passThroughGesture = false
        }
        if (passThrough) {
            return null
        }

        val count = event.pointerCount
        val properties = Array(count) { MotionEvent.PointerProperties() }
        val coords = Array(count) { MotionEvent.PointerCoords() }
        var fingers = 0
        for (i in 0 until count) {
            event.getPointerProperties(i, properties[i])
            event.getPointerCoords(i, coords[i])
            if (properties[i].toolType == MotionEvent.TOOL_TYPE_FINGER) {
                properties[i].toolType = MotionEvent.TOOL_TYPE_MOUSE
                fingers++
            }
        }
        if (fingers == 0) {
            return null
        }
        return MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            event.action,
            count,
            properties,
            coords,
            event.metaState,
            event.buttonState,
            event.xPrecision,
            event.yPrecision,
            event.deviceId,
            event.edgeFlags,
            InputDevice.SOURCE_MOUSE,
            event.flags
        )
    }

    private fun isMultiFingerGesture(event: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }
        val classification = event.classification
        return classification == MotionEvent.CLASSIFICATION_PINCH ||
            classification == MotionEvent.CLASSIFICATION_MULTI_FINGER_SWIPE
    }
}
