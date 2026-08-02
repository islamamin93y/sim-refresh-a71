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
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "SIM Refresh automation enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        refreshRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    fun runAssistedRadioRefresh() {
        if (refreshRunning) {
            Toast.makeText(this, "Refresh is already running", Toast.LENGTH_SHORT).show()
            return
        }
        refreshRunning = true

        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                "com.android.phone",
                "com.android.phone.settings.RadioInfo"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Throwable) {
            refreshRunning = false
            Toast.makeText(this, "Phone Information could not be opened", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Opening Phone Information… do not touch the screen", Toast.LENGTH_LONG).show()
        handler.postDelayed({ turnRadioOff() }, 2200L)
    }

    private fun turnRadioOff() {
        val toggle = findClickableForAnyText(
            listOf("Mobile Radio Power", "Mobile radio power")
        )
        val clicked = toggle?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!clicked) {
            finishWithMessage("Mobile Radio Power switch was not found")
            return
        }

        Toast.makeText(this, "Radio OFF — waiting 5 seconds", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ turnRadioOn() }, 5000L)
    }

    private fun turnRadioOn() {
        val toggle = findClickableForAnyText(
            listOf("Mobile Radio Power", "Mobile radio power")
        )
        val clicked = toggle?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        refreshRunning = false

        Toast.makeText(
            this,
            if (clicked) {
                "Radio ON — wait up to 60 seconds and test the new eSIM profile"
            } else {
                "Could not turn Mobile Radio Power back on"
            },
            Toast.LENGTH_LONG
        ).show()
    }

    private fun findClickableForAnyText(candidates: List<String>): AccessibilityNodeInfo? {
        for (text in candidates) {
            findClickableForText(text)?.let { return it }
        }
        return null
    }

    private fun findClickableForText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val matches = root.findAccessibilityNodeInfosByText(text)
        for (match in matches) {
            val visibleText = match.text?.toString().orEmpty()
            val description = match.contentDescription?.toString().orEmpty()
            if (!visibleText.contains(text, ignoreCase = true) &&
                !description.contains(text, ignoreCase = true)
            ) continue

            findClickableNode(match)?.let { return it }
            findClickableDescendant(match)?.let { return it }
            findClickableDescendant(match.parent)?.let { return it }
        }
        return null
    }

    private fun findClickableNode(start: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = start
        repeat(8) {
            val node = current ?: return null
            if (node.isClickable && node.isEnabled) return node
            current = node.parent
        }
        return null
    }

    private fun findClickableDescendant(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isClickable && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            findClickableDescendant(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun finishWithMessage(message: String) {
        refreshRunning = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        @Volatile
        private var instance: RadioRefreshAccessibilityService? = null

        fun requestRefresh(): Boolean {
            val service = instance ?: return false
            service.runAssistedRadioRefresh()
            return true
        }
    }
}
