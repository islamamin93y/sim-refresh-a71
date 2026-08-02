package com.esyas.simrefresha71

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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

        if (!openSimManager()) {
            refreshRunning = false
            Toast.makeText(this, "SIM Manager could not be opened", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Opening SIM Manager… do not touch the screen", Toast.LENGTH_LONG).show()
        handler.postDelayed({ chooseMobileDataSim2() }, 2500L)
    }

    private fun openSimManager(): Boolean {
        val candidates = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.app.telephonyui",
                    "com.samsung.android.app.telephonyui.netsettings.ui.simcardmanager.SimCardMgrActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in candidates) {
            try {
                startActivity(intent)
                return true
            } catch (_: Throwable) {
                // Try the next available Samsung/settings screen.
            }
        }
        return false
    }

    private fun chooseMobileDataSim2() {
        if (!clickFirstText(listOf("Mobile data", "Mobile data SIM", "Preferred SIM for mobile data"))) {
            finishWithMessage("Mobile data option was not found in SIM Manager")
            return
        }

        handler.postDelayed({
            val selected = clickFirstText(listOf("WE", "We", "SIM 2", "SIM2"))
            if (!selected) {
                finishWithMessage("SIM 2 / WE option was not found")
                return@postDelayed
            }
            clickConfirmationIfPresent()
            Toast.makeText(this, "Temporarily switched data to SIM 2", Toast.LENGTH_SHORT).show()
            handler.postDelayed({ chooseMobileDataSim1() }, 5000L)
        }, 1200L)
    }

    private fun chooseMobileDataSim1() {
        if (!clickFirstText(listOf("Mobile data", "Mobile data SIM", "Preferred SIM for mobile data"))) {
            finishWithMessage("Could not reopen Mobile data selection")
            return
        }

        handler.postDelayed({
            val selected = clickFirstText(listOf("esim.me", "eSIM.me", "ESIM.ME", "SIM 1", "SIM1"))
            if (!selected) {
                finishWithMessage("SIM 1 / eSIM.me option was not found")
                return@postDelayed
            }
            clickConfirmationIfPresent()
            handler.postDelayed({
                refreshRunning = false
                Toast.makeText(
                    this,
                    "Data returned to SIM 1. Wait up to 60 seconds and test the new eSIM profile.",
                    Toast.LENGTH_LONG
                ).show()
            }, 1500L)
        }, 1200L)
    }

    private fun clickConfirmationIfPresent() {
        handler.postDelayed({
            clickFirstText(listOf("OK", "Done", "Confirm", "Switch"))
        }, 500L)
    }

    private fun clickFirstText(candidates: List<String>): Boolean {
        for (text in candidates) {
            val node = findClickableForText(text)
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
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
        repeat(6) {
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
