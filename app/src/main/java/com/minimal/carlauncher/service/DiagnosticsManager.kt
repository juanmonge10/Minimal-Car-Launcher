package com.minimal.carlauncher.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.ContentValues
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class DiagnosticEventType {
    INCOMING_INTENT,
    OUTGOING_INTENT,
    SETTINGS_CHANGE,
    RADIO_LOG,
    SYSTEM_EVENT
}

data class DiagnosticEvent(
    val id: Long,
    val timestamp: String,
    val type: DiagnosticEventType,
    val action: String,
    val target: String?,
    val summary: String,
    val details: String
)

data class PackageDiagnosticInfo(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val versionName: String,
    val versionCode: Long,
    val receivers: List<String>,
    val services: List<String>
)

data class SystemDiagnosticInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val board: String,
    val hardware: String,
    val product: String,
    val androidVersion: String,
    val apiLevel: Int,
    val buildDisplay: String,
    val fingerprint: String,
    val screenMetrics: String
)

object DiagnosticsManager {
    private const val MAX_EVENTS = 60
    private val idCounter = AtomicLong(1)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val eventHistory = ArrayDeque<DiagnosticEvent>()
    private val eventLock = Any()

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    fun logIntent(intent: Intent, isIncoming: Boolean) {
        val action = intent.action ?: "(no action)"
        val component = intent.component?.flattenToShortString() ?: intent.`package`
        val time = timeFormat.format(Date())
        val type = if (isIncoming) DiagnosticEventType.INCOMING_INTENT else DiagnosticEventType.OUTGOING_INTENT

        val extras = intent.extras
        val keysCount = extras?.size() ?: 0
        val summary = if (keysCount > 0) "$keysCount extras" else "No extras"

        val details = buildString {
            append("Action: $action\n")
            if (component != null) append("Target: $component\n")
            val dataStr = intent.dataString
            if (dataStr != null) append("Data: $dataStr\n")
            val categories = intent.categories
            if (!categories.isNullOrEmpty()) append("Categories: ${categories.joinToString()}\n")
            append("Flags: 0x${Integer.toHexString(intent.flags)}\n")

            if (extras != null && !extras.isEmpty) {
                append("Extras ($keysCount):\n")
                formatBundle(extras, this, "  ")
            }
        }.trimEnd()

        addEvent(DiagnosticEvent(
            id = idCounter.getAndIncrement(),
            timestamp = time,
            type = type,
            action = action,
            target = component,
            summary = summary,
            details = details
        ))
    }

    fun logSettingsChange(uri: String, key: String? = null, value: String? = null) {
        val time = timeFormat.format(Date())
        val action = key ?: uri
        val summary = if (value != null) "= $value" else "URI changed"
        val details = "URI: $uri\nKey: ${key ?: "(all)"}\nValue: ${value ?: "(unknown or modified)"}"

        addEvent(DiagnosticEvent(
            id = idCounter.getAndIncrement(),
            timestamp = time,
            type = DiagnosticEventType.SETTINGS_CHANGE,
            action = action,
            target = "Settings.System/Global",
            summary = summary,
            details = details
        ))
    }

    fun logRadioAction(action: String, message: String) {
        val time = timeFormat.format(Date())
        addEvent(DiagnosticEvent(
            id = idCounter.getAndIncrement(),
            timestamp = time,
            type = DiagnosticEventType.RADIO_LOG,
            action = action,
            target = null,
            summary = message.take(40),
            details = message
        ))
    }

    fun logSystemEvent(action: String, message: String) {
        val time = timeFormat.format(Date())
        addEvent(DiagnosticEvent(
            id = idCounter.getAndIncrement(),
            timestamp = time,
            type = DiagnosticEventType.SYSTEM_EVENT,
            action = action,
            target = null,
            summary = message.take(40),
            details = message
        ))
    }

    private fun addEvent(event: DiagnosticEvent) {
        synchronized(eventLock) {
            if (eventHistory.size >= MAX_EVENTS) {
                eventHistory.removeFirst()
            }
            eventHistory.addLast(event)
            _events.value = eventHistory.toList().reversed()
        }
    }

    fun clearEvents() {
        synchronized(eventLock) {
            eventHistory.clear()
            _events.value = emptyList()
        }
    }

    private fun formatBundle(bundle: Bundle, sb: StringBuilder, indent: String) {
        val keys = try { bundle.keySet() } catch (e: Throwable) { return }
        for (k in keys) {
            try {
                val v = bundle.get(k)
                if (v == null) {
                    sb.append("$indent• $k: null\n")
                    continue
                }
                val typeName = v.javaClass.simpleName
                val repr = when (v) {
                    is ByteArray -> {
                        val hex = v.take(16).joinToString(" ") { "%02X".format(it) }
                        val ascii = try {
                            val str = String(v, Charsets.UTF_8).filter { it.isLetterOrDigit() || it in " .-" }
                            if (str.isNotBlank()) " | ascii: '$str'" else ""
                        } catch (e: Throwable) { "" }
                        "ByteArray[${v.size}] hex: [$hex]$ascii"
                    }
                    is IntArray -> "IntArray[${v.size}] values: ${v.take(16).joinToString(", ")}"
                    is LongArray -> "LongArray[${v.size}] values: ${v.take(16).joinToString(", ")}"
                    is Bundle -> {
                        sb.append("$indent• $k: (Bundle)\n")
                        formatBundle(v, sb, "$indent    ")
                        continue
                    }
                    else -> v.toString()
                }
                sb.append("$indent• $k: ($typeName) = $repr\n")
            } catch (e: Throwable) {
                sb.append("$indent• $k: error reading extra (${e.message})\n")
            }
        }
    }

    fun getSystemInfo(context: Context): SystemDiagnosticInfo {
        val dm = context.resources.displayMetrics
        val screen = "${dm.widthPixels}x${dm.heightPixels} (${dm.densityDpi} dpi, ${dm.density}x)"
        return SystemDiagnosticInfo(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            brand = Build.BRAND ?: "Unknown",
            board = Build.BOARD ?: "Unknown",
            hardware = Build.HARDWARE ?: "Unknown",
            product = Build.PRODUCT ?: "Unknown",
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            buildDisplay = Build.DISPLAY ?: "Unknown",
            fingerprint = Build.FINGERPRINT ?: "Unknown",
            screenMetrics = screen
        )
    }

    fun getInstalledRadioPackages(context: Context): List<PackageDiagnosticInfo> {
        val pm = context.packageManager
        val results = mutableListOf<PackageDiagnosticInfo>()

        val candidateKeywords = listOf("radio", "tuner", "nwd", "qf", "syu", "microntek", "allwinner", "carkit")
        val candidatePkgs = try {
            val installed = pm.getInstalledApplications(0)
            installed.filter { app ->
                val lower = app.packageName.lowercase()
                candidateKeywords.any { lower.contains(it) }
            }
        } catch (e: Throwable) {
            emptyList()
        }

        // Also check explicit known package names
        val explicitPackages = listOf(
            "com.nwd.radio",
            "com.nwd.link.radio",
            "com.qf.radio",
            "com.syu.radio",
            "com.microntek.radio",
            "com.allwinner.radio",
            "com.softwinner.radio",
            "com.ts.main.radio",
            "com.yecon.fmradio",
            "com.android.fmradio",
            "com.chaochuang.radio"
        )

        val uniquePkgs = (candidatePkgs.map { it.packageName } + explicitPackages).distinct()

        for (pkg in uniquePkgs) {
            try {
                val pkgInfo = pm.getPackageInfo(
                    pkg,
                    PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES
                )
                val appInfo = pkgInfo.applicationInfo ?: continue
                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (e: Throwable) { pkg }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val receivers = pkgInfo.receivers?.map { it.name.substringAfterLast('.') } ?: emptyList()
                val services = pkgInfo.services?.map { it.name.substringAfterLast('.') } ?: emptyList()
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }

                results.add(PackageDiagnosticInfo(
                    label = label,
                    packageName = pkg,
                    isSystem = isSystem,
                    versionName = pkgInfo.versionName ?: "N/A",
                    versionCode = vCode,
                    receivers = receivers,
                    services = services
                ))
            } catch (e: Throwable) {
                // Package not installed
            }
        }

        return results.sortedWith(compareByDescending<PackageDiagnosticInfo> { it.isSystem }.thenBy { it.packageName })
    }

    fun getAutomotiveSettings(context: Context): List<Pair<String, String>> {
        val resolver = context.contentResolver
        val keys = listOf(
            "nwd_radio_cur_freq",
            "radio_cur_freq",
            "cur_freq",
            "curr_freq",
            "curfreq",
            "current_freq",
            "radio_freq",
            "radio_station",
            "radio_band",
            "radio_channel",
            "radio_power",
            "radio_state",
            "radio_play",
            "radio_status",
            "qf_radio_freq",
            "allwinner_radio_freq",
            "mcu_radio_freq"
        )
        val list = mutableListOf<Pair<String, String>>()
        for (k in keys) {
            val sysVal = try { Settings.System.getString(resolver, k) } catch (e: Throwable) { null }
            if (!sysVal.isNullOrBlank()) {
                list.add("System.$k" to sysVal)
            }
            val globVal = try { Settings.Global.getString(resolver, k) } catch (e: Throwable) { null }
            if (!globVal.isNullOrBlank() && globVal != sysVal) {
                list.add("Global.$k" to globVal)
            }
        }
        return list
    }

    fun buildFullReport(
        context: Context,
        radioStation: String?,
        isRadioActive: Boolean,
        presets: List<String>
    ): String = buildString {
        appendLine("==================================================")
        appendLine("CAR LAUNCHER DIAGNOSTICS REPORT")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("==================================================")
        appendLine()

        val sys = getSystemInfo(context)
        appendLine("### 1. DEVICE & HEAD UNIT HARDWARE")
        appendLine("- Manufacturer: ${sys.manufacturer}")
        appendLine("- Model: ${sys.model}")
        appendLine("- Brand: ${sys.brand}")
        appendLine("- Board / Hardware: ${sys.board} / ${sys.hardware}")
        appendLine("- Product: ${sys.product}")
        appendLine("- Android Version: Android ${sys.androidVersion} (API ${sys.apiLevel})")
        appendLine("- Display Build ID: ${sys.buildDisplay}")
        appendLine("- Fingerprint: ${sys.fingerprint}")
        appendLine("- Screen Metrics: ${sys.screenMetrics}")
        appendLine()

        appendLine("### 2. LAUNCHER RADIO STATUS")
        appendLine("- Current Station: ${radioStation ?: "None / Inactive"}")
        appendLine("- Active State: ${if (isRadioActive) "Active" else "Idle"}")
        appendLine("- Saved Presets: ${presets.joinToString(", ")}")
        appendLine()

        appendLine("### 3. DETECTED AUTOMOTIVE SYSTEM SETTINGS")
        val settings = getAutomotiveSettings(context)
        if (settings.isEmpty()) {
            appendLine("(No known automotive radio keys currently found in Settings.System/Global)")
        } else {
            for ((k, v) in settings) {
                appendLine("- $k = $v")
            }
        }
        appendLine()

        appendLine("### 4. INSTALLED RADIO & VENDOR PACKAGES")
        val pkgs = getInstalledRadioPackages(context)
        if (pkgs.isEmpty()) {
            appendLine("(No vendor radio packages detected)")
        } else {
            for (p in pkgs) {
                appendLine("- **${p.label}** (`${p.packageName}`)")
                appendLine("  • System App: ${if (p.isSystem) "YES" else "NO"}")
                appendLine("  • Version: ${p.versionName} (${p.versionCode})")
                if (p.receivers.isNotEmpty()) {
                    appendLine("  • Receivers (${p.receivers.size}): ${p.receivers.joinToString(", ")}")
                }
                if (p.services.isNotEmpty()) {
                    appendLine("  • Services (${p.services.size}): ${p.services.joinToString(", ")}")
                }
            }
        }
        appendLine()

        appendLine("### 5. RECENT RADIO & INTENT EVENTS (${_events.value.size})")
        val currentEvents = _events.value
        if (currentEvents.isEmpty()) {
            appendLine("(No events captured yet)")
        } else {
            for (e in currentEvents) {
                appendLine("--------------------------------------------------")
                appendLine("[${e.timestamp}] [${e.type}] ${e.action}")
                if (e.target != null) appendLine("Target: ${e.target}")
                appendLine(e.details)
            }
        }
        appendLine("==================================================")
    }

    fun copyReportToClipboard(
        context: Context,
        radioStation: String?,
        isRadioActive: Boolean,
        presets: List<String>
    ): Boolean {
        return try {
            val report = buildFullReport(context, radioStation, isRadioActive, presets)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Car Launcher Diagnostics", report)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Diagnostics report copied to clipboard!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Throwable) {
            Toast.makeText(context, "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun reportFileName(): String =
        "informe-radio-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    /** Saves the report as a .txt in the public Downloads folder (Android 10+, no permission needed). */
    fun saveReportToDownloads(
        context: Context,
        radioStation: String?,
        isRadioActive: Boolean,
        presets: List<String>
    ) {
        try {
            val report = buildFullReport(context, radioStation, isRadioActive, presets)
            val name = reportFileName()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                context.contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
                Toast.makeText(context, "Informe guardado en Descargas/$name", Toast.LENGTH_LONG).show()
            } else {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = File(dir, name).apply { writeText(report) }
                Toast.makeText(context, "Informe guardado en ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Toast.makeText(context, "No se pudo guardar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the Android share sheet (Bluetooth, messaging apps…) with the report as a .txt file. */
    fun shareReport(
        context: Context,
        radioStation: String?,
        isRadioActive: Boolean,
        presets: List<String>
    ) {
        try {
            val report = buildFullReport(context, radioStation, isRadioActive, presets)
            val file = File(context.cacheDir, reportFileName()).apply { writeText(report) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Informe de radio - Car Launcher")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Compartir informe").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Throwable) {
            Toast.makeText(context, "No se pudo compartir: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
