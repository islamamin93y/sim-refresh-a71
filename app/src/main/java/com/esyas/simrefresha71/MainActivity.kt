package com.esyas.simrefresha71

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        appendLog(if (granted) "READ_PHONE_STATE granted." else "READ_PHONE_STATE denied.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.txtLog)

        findViewById<Button>(R.id.btnRequestPermission).setOnClickListener {
            requestPhonePermission()
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

    private fun attemptRestartModem() {
        appendLog("Attempting TelephonyManager.rebootModem()...")
        try {
            val telephonyManager = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 33) {
                val success = telephonyManager.rebootModem()
                appendLog("rebootModem() returned: $success")
                if (!success) {
                    appendLog("Android accepted the call but the modem did not restart.")
                }
            } else {
                appendLog("This Android version does not expose the public rebootModem() API.")
            }
        } catch (security: SecurityException) {
            appendLog("Blocked by Android: ${security.message}")
            appendLog("The app does not have the privileged MODIFY_PHONE_STATE permission.")
        } catch (unsupported: UnsupportedOperationException) {
            appendLog("Not supported by this Samsung modem: ${unsupported.message}")
        } catch (error: Throwable) {
            appendLog("Restart attempt failed: ${error.javaClass.simpleName}: ${error.message}")
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
            appendLog("Could not read status: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun appendLog(message: String) {
        val previous = logView.text?.toString().orEmpty()
        logView.text = if (previous == "Ready.") message else "$previous\n\n$message"
    }
}
