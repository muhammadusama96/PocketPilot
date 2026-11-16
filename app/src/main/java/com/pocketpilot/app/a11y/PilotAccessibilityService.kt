package com.pocketpilot.app.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketpilot.core.screen.ScreenState
import com.pocketpilot.core.screen.UiNode

/**
 * The hands + eyes. An AccessibilityService — no root, no ADB driving.
 * Reads the foreground app's UI tree, performs taps/swipes/typing, and
 * takes screenshots (API 30+). One instance; the ScreenController below
 * is the synchronous API the agent loop calls.
 */
class PilotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PilotAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ScreenController.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive observation only; the loop polls the tree on demand.
        event?.let { ScreenController.onEvent(it) }
    }

    override fun onInterrupt() {
        // Nothing to clean up; gestures are short-lived.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
            ScreenController.detach()
        }
    }
}

/** Synchronous screen-driving API used by the agent loop and tool handlers. */
object ScreenController {
    private var service: PilotAccessibilityService? = null
    private var lastPackage: String = ""
    private var lastActivity: String? = null

    fun attach(service: PilotAccessibilityService) {
        this.service = service
    }

    fun detach() {
        service = null
    }

    fun onEvent(event: AccessibilityEvent) {
        event.packageName?.toString()?.let { lastPackage = it }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastActivity = event.className?.toString()
        }
    }

    /** Dump the current screen as a ScreenState (normalised 0-1000 bounds). */
    fun dumpScreen(): ScreenState {
        val svc = service ?: return ScreenState(lastPackage, lastActivity, emptyList())
        val root = svc.rootInActiveWindow ?: return ScreenState(lastPackage, lastActivity, emptyList())
        val nodes = mutableListOf<UiNode>()
        val metrics = svc.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        var counter = 0
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            if (text != null || desc != null || node.isClickable || node.isEditable) {
                counter++
                nodes += UiNode(
                    id = "e$counter",
                    text = text,
                    contentDescription = desc,
                    className = node.className?.toString(),
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    left = rect.left * 1000 / width,
                    top = rect.top * 1000 / height,
                    right = rect.right * 1000 / width,
                    bottom = rect.bottom * 1000 / height,
                )
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return ScreenState(lastPackage, lastActivity, nodes)
    }

    /** Tap normalised 0-1000 coordinates. Returns false when no service. */
    fun tap(x: Int, y: Int): Boolean {
        val svc = service ?: return false
        val metrics = svc.resources.displayMetrics
        val px = x * metrics.widthPixels / 1000f
        val py = y * metrics.heightPixels / 1000f
        val path = Path().apply { moveTo(px, py) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return svc.dispatchGesture(gesture, null, null)
    }

    fun swipe(direction: String, distance: Int = 600): Boolean {
        val svc = service ?: return false
        val metrics = svc.resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        val cy = metrics.heightPixels / 2f
        val (sx, sy, ex, ey) = when (direction.lowercase()) {
            "up" -> arrayOf(cx, cy + distance / 2f, cx, cy - distance / 2f)
            "down" -> arrayOf(cx, cy - distance / 2f, cx, cy + distance / 2f)
            "left" -> arrayOf(cx + distance / 2f, cy, cx - distance / 2f, cy)
            else -> arrayOf(cx - distance / 2f, cy, cx + distance / 2f, cy)
        }
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return svc.dispatchGesture(gesture, null, null)
    }

    /** Type into the currently focused editable node. */
    fun typeText(text: String): Boolean {
        val svc = service ?: return false
        val focused = svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Screenshot via the accessibility API (API 30+). Null when unavailable. */
    fun takeScreenshot(callback: (ByteArray?) -> Unit) {
        val svc = service
        if (svc == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        svc.takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            screenshotExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = result.hardwareBuffer
                    val bitmap = if (hardwareBuffer != null) {
                        android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                            .also { hardwareBuffer.close() }
                    } else {
                        null
                    }
                    if (bitmap == null) {
                        callback(null)
                        return
                    }
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                    callback(stream.toByteArray())
                }

                override fun onFailure(errorCode: Int) {
                    callback(null)
                }
            },
        )
    }

    private val screenshotExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** System global actions: back, home, recents. Returns false when no service. */
    fun globalAction(action: Int): Boolean {
        val svc = service ?: return false
        return svc.performGlobalAction(action)
    }
}
