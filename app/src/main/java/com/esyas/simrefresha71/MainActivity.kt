package com.esyas.simrefresha71

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var smartRefreshButton: Button
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        appendLog(if (granted) "READ_PHONE_STATE granted." else "READ_PHONE_STATE denied.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.txtLog)
        smartRefreshButton = findViewById(R.id.btnSmartRefresh)

        findViewById<Button>(R.id.btnRequestPermission).setOnClickListener {
            requestPhonePermission()
        }
        smartRefreshButton.setOnClickListener {
            smartSimRefresh()
        }
        findViewById<Button>(R.id.btnRestartModem).setOnClickListener {
            attemptRestartModem()
        }
        findViewById<Button>(R.id.btnOpenPhoneInfo).setOnClickListener {
            openPhoneInfo()
        }
        findViewById<Button>(R.id.btnOpenSimManager).setOnClickListener {
            openSimManager()
        }
        findViewById<Button>(R.id.btnDeviceInfo).setOnClickListener {
            readCurrentStatus()
        }
    }

    private fun requestPhonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            appendLog("READ_PHONE_STATE is already granted.")
        } else {
            permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    /**
     * Tries several non-destructive modem/SIM refresh paths. Android may block every
     * privileged operation; each result is logged so we can identify what this
     * Samsung firmware permits.
     */
    private fun smartSimRefresh() {
        smartRefreshButton.isEnabled = false
        logView.text = "SMART SIM REFRESH started.\nNo eSIM profile will be deleted."

        val baseManager = getSystemService(TelephonyManager::class.java)
        val subscriptionManager = getSystemService(SubscriptionManager::class.java)

        appendLog("Step 1/4: public rebootModem() API")
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                baseManager.rebootModem()
                appendLog("Public rebootModem request was accepted.")
            } else {
                appendLog("Public rebootModem is unavailable below Android 13.")
            }
        } catch (error: Throwable) {
            appendLog("Public rebootModem blocked: ${rootError(error)}")
        }

        appendLog("Step 2/4: hidden radio OFF/ON methods")
        val managers = mutableListOf<Pair<String, TelephonyManager>>()
        managers += "default" to baseManager

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                subscriptionManager.activeSubscriptionInfoList.orEmpty().forEach { info ->
                    managers += "subId=${info.subscriptionId},slot=${info.simSlotIndex}" to
                        baseManager.createForSubscriptionId(info.subscriptionId)
                }
            } catch (error: Throwable) {
                appendLog("Could not enumerate subscription managers: ${rootError(error)}")
            }
        }

        var offAccepted = false
        managers.distinctBy { it.first }.forEach { (label, manager) ->
            offAccepted = tryHiddenRadioPower(manager, label, false) || offAccepted
        }

        mainHandler.postDelayed({
            var onAccepted = false
            managers.distinctBy { it.first }.forEach { (label, manager) ->
                onAccepted = tryHiddenRadioPower(manager, label, true) || onAccepted
            }

            appendLog("Step 3/4: refresh current subscription snapshot")
            readCurrentStatus()

            appendLog("Step 4/4: result")
            if (offAccepted || onAccepted) {
                appendLog("At least one radio-control call was accepted. Wait 30-60 seconds and test calls/data on the newly selected eSIM profile.")
            } else {
                appendLog("All direct radio-control calls were blocked or unavailable on this firmware.")
                appendLog("Use Open Phone Information to try Mobile Radio Power manually; the app did not change any permanent network setting.")
            }
            smartRefreshButton.isEnabled = true
        }, 1800L)
    }

    private fun tryHiddenRadioPower(manager: TelephonyManager, label: String, enabled: Boolean): Boolean {
        val state = if (enabled) "ON" else "OFF"
        val booleanMethods = listOf("setRadioPower", "setRadio")

        booleanMethods.forEach { methodName ->
            try {
                val method = manager.javaClass.getMethod(methodName, Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(manager, enabled)
                appendLog("$label: $methodName($enabled) accepted [$state].")
                return true
            } catch (error: Throwable) {
                appendLog("$label: $methodName blocked/unavailable: ${rootError(error)}")
            }
        }

        if (!enabled) {
            try {
                val method = manager.javaClass.getMethod("toggleRadioOnOff")
                method.isAccessible = true
                method.invoke(manager)
                appendLog("$label: toggleRadioOnOff() accepted.")
                return true
            } catch (error: Throwable) {
                appendLog("$label: toggleRadioOnOff blocked/unavailable: ${rootError(error)}")
            }
        }

        return false
    }

    private fun rootError(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return "${current.javaClass.simpleName}: ${current.message ?: "no details"}"
    }

    private fun attemptRestartModem() {
        appendLog("Attempting TelephonyManager.rebootModem()...")
        try {
            val telephonyManager = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 33) {
                telephonyManager.rebootModem()
                appendLog("rebootModem() request was sent to Android.")
                appendLog("Wait up to 60 seconds and check whether the active SIM profile is recognized.")
            } else {
                appendLog("This Android version does not expose the public rebootModem() API.")
            }
        } catch (security: SecurityException) {
            appendLog("Blocked by Android: ${security.message}")
            appendLog("The app does not have the privileged MODIFY_PHONE_STATE permission or carrier privileges.")
        } catch (unsupported: UnsupportedOperationException) {
            appendLog("Not supported by this Samsung modem: ${unsupported.message}")
        } catch (error: Throwable) {
            appendLog("Restart attempt failed: ${rootError(error)}")
        }
    }

    private fun openPhoneInfo() {
        val candidates = listOf(
            Intent("android.intent.action.MAIN").apply {
                component = ComponentName("com.android.settings", "com.android.settings.RadioInfo")
            },
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        )

        val opened = candidates.any { intent ->
            try {
                startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }

        appendLog(if (opened) "Opened an available phone/network information screen." else "No compatible Phone Information activity was available.")
    }

    private fun openSimManager() {
        val candidates = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.app.telephonyui",
                    "com.samsung.android.app.telephonyui.netsettings.ui.simcardmanager.SimCardMgrActivity"
                )
            },
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )

        val opened = candidates.any { intent ->
            try {
                startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }

        appendLog(if (opened) "Opened SIM/network settings." else "SIM Manager could not be opened.")
    }

    private fun readCurrentStatus() {
        try {
            val telephonyManager = getSystemService(TelephonyManager::class.java)
            val subscriptionManager = getSystemService(SubscriptionManager::class.java)

            val permissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val info = buildString {
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Phone count: ${telephonyManager.activeModemCount}")
                appendLine("SIM state: ${telephonyManager.simState}")
                appendLine("Network operator: ${telephonyManager.networkOperatorName}")
                appendLine("Data state: ${telephonyManager.dataState}")
                appendLine("READ_PHONE_STATE granted: $permissionGranted")

                if (permissionGranted) {
                    val active = subscriptionManager.activeSubscriptionInfoList.orEmpty()
                    appendLine("Active subscriptions: ${active.size}")
                    active.forEachIndexed { index, item ->
                        appendLine("[$index] slot=${item.simSlotIndex}, carrier=${item.carrierName}, subscriptionId=${item.subscriptionId}")
                    }
                } else {
                    appendLine("Grant phone-state permission to list active subscriptions.")
                }
            }

            appendLog(info)
        } catch (error: Throwable) {
            appendLog("Could not read status: ${rootError(error)}")
        }
    }

    private fun appendLog(message: String) {
        val previous = logView.text?.toString().orEmpty()
        logView.text = if (previous == "Ready.") message else "$previous\n\n$message"
    }
}
