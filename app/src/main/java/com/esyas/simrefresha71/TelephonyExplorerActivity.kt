package com.esyas.simrefresha71

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class TelephonyExplorerActivity : AppCompatActivity() {

    private lateinit var reportView: TextView
    private var lastReport: String = ""

    private val keywords = listOf(
        "sim", "uicc", "euicc", "esim", "telephony", "subscription",
        "radio", "ril", "carrier", "network", "phone"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telephony_explorer)

        reportView = findViewById(R.id.txtExplorerReport)

        findViewById<Button>(R.id.btnScanComponents).setOnClickListener {
            scanComponents()
        }
        findViewById<Button>(R.id.btnCopyReport).setOnClickListener {
            copyReport()
        }
        findViewById<Button>(R.id.btnTryKnownScreens).setOnClickListener {
            tryKnownScreens()
        }
    }

    private fun scanComponents() {
        reportView.text = "Scanning installed telephony components..."

        Thread {
            val report = buildReport()
            runOnUiThread {
                lastReport = report
                reportView.text = report
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun buildReport(): String {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_DISABLED_COMPONENTS or
            PackageManager.GET_META_DATA

        val packages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                packageManager.getInstalledPackages(flags)
            }
        } catch (error: Throwable) {
            return "Package scan failed: ${error.javaClass.simpleName}: ${error.message}"
        }

        val matches = mutableListOf<String>()
        var componentCount = 0

        packages.sortedBy { it.packageName }.forEach { pkg ->
            val packageName = pkg.packageName
            val packageRelevant = isRelevant(packageName)
            val lines = mutableListOf<String>()

            pkg.activities.orEmpty().forEach { info ->
                if (packageRelevant || isRelevant(info.name)) {
                    componentCount++
                    lines += "  ACTIVITY exported=${info.exported} enabled=${info.enabled} ${info.name}"
                }
            }
            pkg.services.orEmpty().forEach { info ->
                if (packageRelevant || isRelevant(info.name)) {
                    componentCount++
                    lines += "  SERVICE exported=${info.exported} enabled=${info.enabled} permission=${info.permission ?: "none"} ${info.name}"
                }
            }
            pkg.receivers.orEmpty().forEach { info ->
                if (packageRelevant || isRelevant(info.name)) {
                    componentCount++
                    lines += "  RECEIVER exported=${info.exported} enabled=${info.enabled} permission=${info.permission ?: "none"} ${info.name}"
                }
            }
            pkg.providers.orEmpty().forEach { info ->
                if (packageRelevant || isRelevant(info.name) || isRelevant(info.authority.orEmpty())) {
                    componentCount++
                    lines += "  PROVIDER exported=${info.exported} enabled=${info.enabled} authority=${info.authority ?: "none"} ${info.name}"
                }
            }

            if (lines.isNotEmpty()) {
                matches += "\nPACKAGE $packageName"
                matches += lines
            }
        }

        return buildString {
            appendLine("Samsung A71 Telephony Explorer")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Packages scanned: ${packages.size}")
            appendLine("Relevant components: $componentCount")
            appendLine("Only metadata is read. Nothing is changed.")
            matches.forEach { appendLine(it) }
        }
    }

    private fun isRelevant(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return keywords.any { lower.contains(it) }
    }

    private fun copyReport() {
        val text = lastReport.ifBlank { reportView.text?.toString().orEmpty() }
        if (text.isBlank()) {
            Toast.makeText(this, "Run the scan first", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Telephony Explorer report", text))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun tryKnownScreens() {
        val candidates = listOf(
            ComponentName(
                "com.samsung.android.app.telephonyui",
                "com.samsung.android.app.telephonyui.netsettings.ui.simcardmanager.SimCardMgrActivity"
            ),
            ComponentName("com.android.settings", "com.android.settings.RadioInfo"),
            ComponentName("com.android.settings", "com.android.settings.Settings\$MobileNetworkListActivity"),
            ComponentName("com.android.settings", "com.android.settings.Settings\$NetworkDashboardActivity")
        )

        val results = mutableListOf<String>()
        for (component in candidates) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                this.component = component
            }
            try {
                startActivity(intent)
                results += "OPENED: ${component.flattenToShortString()}"
                lastReport += "\n${results.last()}"
                reportView.text = lastReport
                return
            } catch (error: Throwable) {
                results += "BLOCKED: ${component.flattenToShortString()} -> ${rootError(error)}"
            }
        }

        val text = results.joinToString("\n")
        lastReport = if (lastReport.isBlank()) text else "$lastReport\n\nKNOWN SCREEN TEST\n$text"
        reportView.text = lastReport
    }

    private fun rootError(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return "${current.javaClass.simpleName}: ${current.message ?: "no details"}"
    }
}
