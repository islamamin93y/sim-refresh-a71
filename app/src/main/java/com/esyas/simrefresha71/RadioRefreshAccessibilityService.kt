package com.esyas.simrefresha71

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class RadioRefreshAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunning = false

    override fun onServiceConnected() {
        instance = this
        Toast.makeText(this, "SIM Refresh automation enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        refreshRunning = false
    }

    fun runAssistedRadioRefresh() {
        if (refreshRunning) {
            Toast.makeText(this, "Refresh is already running", Toast.LENGTH_SHORT).show()
            return
        }
        refreshRunning = true

        val intent = Intent("android.intent.action.MAIN").apply {
            component = ComponentName("com.android.settings", "com.android.settings.RadioInfo")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (error: Throwable) {
            refreshRunning = false
            Toast.makeText(this, "Phone Information could not be opened", Toast.LENGTH_LONG).show()
            return
        }

        handler.postDelayed({ toggleRadioOffThenOn() }, 1800L)
    }

    private fun toggleRadioOffThenOn() {
        val firstToggle = findClickableForText("Mobile Radio Power")
        if (firstToggle == null || !firstToggle.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            refreshRunning = false
            Toast.makeText(
                this,
                "Mobile Radio Power was not found. Open Phone Information and try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(this, "Radio OFF — waiting...", Toast.LENGTH_SHORT).show()

        handler.postDelayed({
            val secondToggle = findClickableForText("Mobile Radio Power")
            val clicked = secondToggle?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            refreshRunning = false
            Toast.makeText(
                this,
                if (clicked) "Radio ON — wait up to 60 seconds" else "Could not turn the radio back on",
                Toast.LENGTH_LONG
            ).show()
        }, 3500L)
    }

    private fun findClickableForText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val matches = root.findAccessibilityNodeInfosByText(text)
        for (match in matches) {
            findClickableNode(match)?.let { return it }
            findClickableDescendant(match)?.let { return it }
            match.parent?.let { parent ->
                findClickableDescendant(parent)?.let { return it }
            }
        }
        return null
    }

    private fun findClickableNode(start: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = start
        repeat(5) {
            if (current?.isClickable == true && current.isEnabled) return current
            current = current?.parent
        }
        return null
    }

    private fun findClickableDescendant(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isClickable && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            findClickableDescendant(node.getChild(index))?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: RadioRefreshAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        fun requestRefresh(): Boolean {
            val service = instance ?: return false
            service.runAssistedRadioRefresh()
            return true
        }
    }
}
