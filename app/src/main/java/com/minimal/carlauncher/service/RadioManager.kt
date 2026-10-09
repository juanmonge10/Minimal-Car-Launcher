package com.minimal.carlauncher.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.view.KeyEvent
import com.minimal.carlauncher.data.AppRepository
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.roundToInt

/**
 * RadioManager tracks the active radio station and frequency on Android automotive head units,
 * with deep optimization for Allwinner K2401 / QF (QuickFish / K706 / ROCO) and NWD (Nowada) platforms.
 *
 * Implements strict frequency validation and token blacklisting to completely eliminate false detections
 * (such as system airplane mode network settings "cell,bluetooth,wifi,nfc").
 */
class RadioManager(private val context: Context) {

    private val repository = AppRepository(context)

    private val _radioStation = MutableStateFlow<String?>(null)
    val radioStation: StateFlow<String?> = _radioStation.asStateFlow()

    private val _isRadioActive = MutableStateFlow(false)
    val isRadioActive: StateFlow<Boolean> = _isRadioActive.asStateFlow()

    private val _savedStations = MutableStateFlow<List<String>>(repository.getSavedRadioStations())
    val savedStations: StateFlow<List<String>> = _savedStations.asStateFlow()

    private val _presetNames = MutableStateFlow<List<String>>(repository.getRadioPresetNames())
    val presetNames: StateFlow<List<String>> = _presetNames.asStateFlow()

    private var isMonitoring = false
    private var pollingJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val radioPackages = listOf(
        "com.nwd.radio",
        "com.nwd.link.radio",
        "com.allwinner.radio",
        "com.allwinner.radio.app",
        "com.qf.radio",
        "com.android.fmradio",
        "com.mediatek.fmradio",
        "com.ts.radio",
        "com.syu.radio"
    )

    // Comprehensive QF, Allwinner, NWD, and generic automotive Settings keys
    private val observedSettingsKeys = listOf(
        // QuickFish (QF / K2401 / K706 / ROCO)
        "qf_radio_cur_freq",
        "qf_radio_freq",
        "qf_radio_current_freq",
        "qf_radio_frequency",
        "qf_cur_freq",
        "qf_curfreq",
        "qf_freq",
        "qf_radio_station",
        "qf_radio_name",
        "qf_radio_band",
        "com.qf.radio.freq",
        "com.qf.radio.frequency",
        "com.qf.radio.cur_freq",
        "com.qf.radio.station",
        "com.qf.radio.name",

        // Allwinner / Softwinner
        "allwinner_radio_cur_freq",
        "allwinner_radio_freq",
        "allwinner_freq",
        "allwinner_radio_station",
        "allwinner_radio_name",
        "softwinner_radio_cur_freq",
        "softwinner_radio_freq",
        "softwinner_freq",
        "com.allwinner.radio.freq",
        "com.allwinner.radio.cur_freq",
        "com.allwinner.radio.station",

        // Nowada (NWD / K2401P / Allwinner T507 / A133)
        "nwd_radio_current_freq",
        "nwd_radio_freq",
        "nwd_radio_name",
        "nwd_radio_station",
        "nwd_radio_band",
        "nwd_radio_channel",
        "nwd_freq",
        "nwd_cur_freq",
        "nwd_station",
        "Radio_Freq",
        "Radio_Current_Freq",
        "Radio_Band",
        "Radio_Channel",
        "Radio_Name",
        "Radio_Status",
        "RADIO_FREQ",
        "RADIO_CURRENT_FREQ",
        "RADIO_BAND",
        "RADIO_CHANNEL",
        "RADIO_NAME",

        // Generic Automotive & MCU
        "radio_cur_freq",
        "radio_freq",
        "cur_freq",
        "curfreq",
        "cur_frequency",
        "radio_current_freq",
        "radio_frequency",
        "radio_station",
        "radio_station_name",
        "radio_name",
        "radio_band",
        "radio_channel",
        "radio_play_freq",
        "radio_play_frequency",
        "fm_freq",
        "fm_frequency",
        "fm_station",
        "curRadioFreq",
        "currentRadioFreq",
        "current_radio_freq",
        "mcu_radio_freq",
        "mcu_radio_cur_freq",
        "mcu_freq",
        "radio_last_freq",
        "last_radio_freq",
        "radio_ps",
        "radio_rds",
        "radio_rt",
        "sys.radio.freq",
        "persist.sys.radio.freq",

        // TopWay / TS / FYT / Syu / MTK
        "ts_radio_freq",
        "fyt_radio_freq",
        "syu_radio_freq",
        "tuner_freq",
        "hw_radio_freq"
    )

    private val systemPropertyKeys = listOf(
        "persist.nwd.radio.freq",
        "persist.nwd.cur_freq",
        "persist.nwd.radio.band",
        "persist.nwd.radio.name",
        "nwd.radio.freq",
        "nwd.radio.cur_freq",
        "sys.nwd.radio.freq",
        "sys.nwd.freq",
        "persist.sys.radio.freq",
        "persist.sys.radio.cur_freq",
        "persist.radio.freq",
        "persist.radio.cur_freq",
        "sys.radio.freq",
        "sys.radio.cur_freq",
        "ro.radio.freq",
        "qf.radio.freq",
        "qf.radio.cur_freq",
        "persist.qf.radio.freq",
        "persist.qf.cur_freq",
        "allwinner.radio.freq",
        "persist.allwinner.radio.freq",
        "radio.freq",
        "hw.radio.freq"
    )

    private val bgThread = HandlerThread("RadioSettingsObserver").apply { start() }
    private val bgHandler = Handler(bgThread.looper)

    private val settingsObserver = object : ContentObserver(bgHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            DiagnosticsManager.logSettingsChange(uri?.toString() ?: "content://settings/system")
            readCurrentSettingsFrequency()
        }
    }

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val isLauncher = intent.getBooleanExtra("is_launcher_source", false)
            DiagnosticsManager.logIntent(intent, isIncoming = !isLauncher)
            if (isLauncher) return
            extractFromIntent(intent)
        }
    }

    init {
        activeInstance = WeakReference(this)
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        activeInstance = WeakReference(this)

        // 1. Warm up dynamic target components in background (0ms button tap latency)
        warmUpDynamicTargets()

        // 2. Initial read from System Settings and Properties on background thread
        CoroutineScope(Dispatchers.IO).launch {
            readCurrentSettingsFrequency()
        }

        // 3. Register ContentObservers for real-time changes
        registerSettingsObservers()

        // 4. Register BroadcastReceiver for vendor events
        registerBroadcastReceiver()

        // 5. Request initial radio info broadcast from MCU daemon
        requestRadioInfoPing()

        // 6. Background polling loop every 3s for continuous sync
        pollingJob?.cancel()
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isMonitoring) {
                delay(3000L)
                readCurrentSettingsFrequency()
                requestRadioInfoPing()
            }
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        pollingJob?.cancel()
        pollingJob = null

        try {
            context.contentResolver.unregisterContentObserver(settingsObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            context.unregisterReceiver(radioReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            bgThread.quitSafely()
        } catch (e: Exception) {}
    }

    fun readCurrentSettingsFrequency() {
        try {
            // First sanity check: purge any stale blacklisted values from current state
            val currentStation = _radioStation.value
            if (currentStation != null && BLACKLISTED_TOKENS.any { currentStation.lowercase().contains(it) }) {
                _radioStation.value = null
                _isRadioActive.value = false
            }

            val resolver = context.contentResolver
            var freqVal: String? = null
            var nameVal: String? = null
            var bandVal: String? = null

            // Check if radio power is explicitly reported off (never check ambiguous state keys like "state" or "radio_state" where 0 = FM1)
            val powerKeys = listOf("qf_radio_power", "radio_power", "nwd_radio_power")
            for (pk in powerKeys) {
                val p = Settings.System.getString(resolver, pk)
                if (p.equals("false", ignoreCase = true) || p.equals("off", ignoreCase = true)) {
                    _isRadioActive.value = false
                    _radioStation.value = null
                    return
                }
            }

            // 1. Check known Settings.System keys
            for (key in observedSettingsKeys) {
                try {
                    val v = Settings.System.getString(resolver, key)
                    if (!v.isNullOrBlank() && v != "0" && v != "-1" && !v.equals("null", ignoreCase = true)) {
                        if (formatFrequency(v, null, null) != null) {
                            freqVal = v
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // 2. Fallback: Check Settings.Global
            if (freqVal.isNullOrBlank()) {
                for (key in observedSettingsKeys) {
                    try {
                        val v = Settings.Global.getString(resolver, key)
                        if (!v.isNullOrBlank() && v != "0" && v != "-1" && !v.equals("null", ignoreCase = true)) {
                            if (formatFrequency(v, null, null) != null) {
                                freqVal = v
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // 3. Fallback: Check Settings.Secure
            if (freqVal.isNullOrBlank()) {
                for (key in observedSettingsKeys) {
                    try {
                        val v = Settings.Secure.getString(resolver, key)
                        if (!v.isNullOrBlank() && v != "0" && v != "-1" && !v.equals("null", ignoreCase = true)) {
                            if (formatFrequency(v, null, null) != null) {
                                freqVal = v
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // 4. Fallback: Query system settings table cursor directly (with strict filters)
            if (freqVal.isNullOrBlank()) {
                val cursorResult = scanSystemSettingsCursor()
                if (cursorResult != null) {
                    freqVal = cursorResult.first
                    if (nameVal.isNullOrBlank()) nameVal = cursorResult.second
                    if (bandVal.isNullOrBlank()) bandVal = cursorResult.third
                }
            }

            // 5. Fallback: Check SystemProperties via reflection
            if (freqVal.isNullOrBlank()) {
                for (prop in systemPropertyKeys) {
                    val pVal = readSystemProperty(prop)
                    if (!pVal.isNullOrBlank() && pVal != "0" && pVal != "-1") {
                        if (formatFrequency(pVal, null, null) != null) {
                            freqVal = pVal
                            break
                        }
                    }
                }
            }

            // 6. Fallback: Query automotive radio ContentProviders (e.g. com.nwd.radio)
            if (freqVal.isNullOrBlank()) {
                val providerResult = scanContentProviders()
                if (providerResult != null) {
                    freqVal = providerResult.first
                    if (nameVal.isNullOrBlank()) nameVal = providerResult.second
                    if (bandVal.isNullOrBlank()) bandVal = providerResult.third
                }
            }

            // Read Station Name
            if (nameVal.isNullOrBlank()) {
                val nameKeys = listOf(
                    "nwd_radio_name", "nwd_radio_station", "nwd_station",
                    "Radio_Name", "RADIO_NAME",
                    "qf_radio_name", "qf_radio_station", "qf_station", "qf_name",
                    "allwinner_radio_station", "allwinner_radio_name",
                    "radio_name", "radio_station", "radio_station_name",
                    "radio_ps", "radio_rds", "radio_rt"
                )
                for (nk in nameKeys) {
                    val nv = Settings.System.getString(resolver, nk)
                        ?: Settings.Global.getString(resolver, nk)
                    if (!nv.isNullOrBlank() && !nv.equals("null", ignoreCase = true) && nv != "0" && nv != "-1") {
                        if (nv.length in 2..40 && BLACKLISTED_TOKENS.none { nv.lowercase().contains(it) }) {
                            nameVal = nv.trim()
                            break
                        }
                    }
                }
            }

            // Read Band
            if (bandVal.isNullOrBlank()) {
                val bandKeys = listOf("nwd_radio_band", "Radio_Band", "RADIO_BAND", "qf_radio_band", "radio_band", "qf_band")
                for (bk in bandKeys) {
                    val bv = Settings.System.getString(resolver, bk)
                        ?: Settings.Global.getString(resolver, bk)
                    if (!bv.isNullOrBlank() && !bv.equals("null", ignoreCase = true)) {
                        bandVal = bv.trim()
                        break
                    }
                }
            }

            if (!freqVal.isNullOrBlank()) {
                val formatted = formatFrequency(freqVal, bandVal, nameVal)
                if (formatted != null) {
                    _radioStation.value = formatted
                    _isRadioActive.value = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanSystemSettingsCursor(): Triple<String, String?, String?>? {
        val uris = listOf(
            Settings.System.CONTENT_URI,
            Settings.Global.CONTENT_URI
        )

        for (uri in uris) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf("name", "value"),
                    null, null, null
                ) ?: continue

                var foundFreq: String? = null
                var foundName: String? = null
                var foundBand: String? = null

                cursor.use { c ->
                    val nameCol = c.getColumnIndex("name")
                    val valCol = c.getColumnIndex("value")
                    if (nameCol != -1 && valCol != -1) {
                        while (c.moveToNext()) {
                            val name = c.getString(nameCol) ?: continue
                            val value = c.getString(valCol) ?: continue
                            val lowerName = name.lowercase()

                            // 1. Immediately discard blacklisted system settings (airplane mode, wifi, bluetooth, etc.)
                            if (BLACKLISTED_TOKENS.any { lowerName.contains(it) }) continue

                            // 2. Discard empty / invalid values
                            if (value.isBlank() || value == "0" || value == "-1" || value.equals("null", ignoreCase = true)) continue

                            // 3. Scan candidate frequency settings (strictly require frequency / channel keywords, NEVER generic prefixes)
                            val isFreqCandidate = lowerName.contains("freq") ||
                                lowerName.contains("frequency") ||
                                lowerName.contains("curfreq") ||
                                (lowerName.contains("radio") && (lowerName.contains("chan") || lowerName.contains("channel") || lowerName.contains("station") || lowerName.contains("play")))

                            if (foundFreq == null && isFreqCandidate) {
                                val formatted = formatFrequency(value, null, null)
                                if (formatted != null) {
                                    foundFreq = value
                                }
                            }

                            // 4. Scan candidate station name settings
                            val isNameCandidate = lowerName.contains("radio_name") ||
                                lowerName.contains("radio_station") ||
                                lowerName.contains("radio_ps") ||
                                lowerName.contains("radio_rds") ||
                                lowerName.contains("radio_rt") ||
                                lowerName.contains("qf_radio_name") ||
                                lowerName.contains("qf_radio_station") ||
                                lowerName.contains("qf_station") ||
                                lowerName.contains("qf_name") ||
                                lowerName.contains("nwd_radio_name") ||
                                lowerName.contains("allwinner_radio_station")

                            if (foundName == null && isNameCandidate) {
                                if (value.length in 2..40 && BLACKLISTED_TOKENS.none { value.lowercase().contains(it) }) {
                                    foundName = value.trim()
                                }
                            }

                            // 5. Scan candidate band settings
                            val isBandCandidate = lowerName.contains("radio_band") ||
                                lowerName.contains("qf_radio_band") ||
                                lowerName.contains("nwd_radio_band")

                            if (foundBand == null && isBandCandidate) {
                                foundBand = value.trim()
                            }
                        }
                    }
                }

                val finalFreq = foundFreq
                if (finalFreq != null) {
                    return Triple(finalFreq, foundName, foundBand)
                }
            } catch (e: Throwable) {
                // Ignore security exceptions on restricted tables
            }
        }
        return null
    }

    private fun readSystemProperty(key: String): String? {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val v = get.invoke(null, key) as? String
            if (!v.isNullOrBlank()) v else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun scanContentProviders(): Triple<String, String?, String?>? {
        val candidateUris = listOf(
            "content://com.nwd.radio/info",
            "content://com.nwd.radio.provider/status",
            "content://com.nwd.radio/status",
            "content://com.nwd.radio/cur_freq",
            "content://com.qf.radio/info",
            "content://com.allwinner.radio/info"
        )
        for (uriStr in candidateUris) {
            try {
                val uri = Uri.parse(uriStr)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        var foundFreq: String? = null
                        var foundName: String? = null
                        var foundBand: String? = null
                        for (i in 0 until cursor.columnCount) {
                            val colName = cursor.getColumnName(i).lowercase()
                            val colVal = cursor.getString(i) ?: continue
                            if (colVal.isBlank() || BLACKLISTED_TOKENS.any { colVal.lowercase().contains(it) }) continue

                            if (foundFreq == null && (colName.contains("freq") || colName.contains("channel"))) {
                                if (formatFrequency(colVal, null, null) != null) {
                                    foundFreq = colVal
                                }
                            }
                            if (foundName == null && (colName.contains("name") || colName.contains("station") || colName.contains("ps"))) {
                                if (colVal.length in 2..40) {
                                    foundName = colVal.trim()
                                }
                            }
                            if (foundBand == null && colName.contains("band")) {
                                foundBand = colVal.trim()
                            }
                        }
                        if (foundFreq != null) {
                            return Triple(foundFreq, foundName, foundBand)
                        }
                    }
                }
            } catch (e: Throwable) {
                // Ignore providers not present or lacking permissions
            }
        }
        return null
    }

    /**
     * Actively broadcasts a status request to NWD, QF, Allwinner and MCU daemons to prompt
     * an immediate state broadcast update back to the launcher.
     */
    fun requestRadioInfoPing() {
        val pingActions = listOf(
            "com.nwd.action.ACTION_SEND_RADIO_INFO",
            "com.nwd.radio.req_info",
            "com.nwd.action.REQ_RADIO_INFO",
            "com.nwd.radio.update_action",
            "com.nwd.radio.status",
            "com.qf.action.RADIO_INFO",
            "com.qf.radio.action",
            "com.allwinner.radio.REQ_INFO",
            "com.allwinner.action.RADIO_INFO",
            "com.microntek.sync"
        )
        for (act in pingActions) {
            try {
                context.sendBroadcast(Intent(act))
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    /**
     * Broadcasts tune / seek previous commands across NWD, QF, Allwinner and automotive HAL daemons.
     */
    fun savePreset(index: Int, station: String) {
        // Station text looks like "95.2 FM • RDS NAME"; store frequency and name separately
        val parts = station.split("•").map { it.trim() }
        val clean = parts.first().replace(" FM", "").replace(" AM", "").trim()
        val name = parts.getOrNull(1).orEmpty()
        repository.setSavedRadioStation(index, clean, name)
        _savedStations.value = repository.getSavedRadioStations()
        _presetNames.value = repository.getRadioPresetNames()
    }

    private val cachedDynamicReceivers = mutableListOf<ComponentName>()
    @Volatile
    private var hasCachedTargets = false

    private fun warmUpDynamicTargets() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pm = context.packageManager
                val installed = pm.getInstalledPackages(android.content.pm.PackageManager.GET_RECEIVERS)
                val recs = mutableListOf<ComponentName>()
                for (pkg in installed) {
                    val pName = pkg.packageName.lowercase()
                    // Strictly isolate to genuine radio tuner packages; NEVER match general system / carkit / settings
                    val isRadioPackage = (pName.contains("radio") || pName.contains("fmradio") || pName.contains("tuner")) &&
                            !pName.contains("setting") && !pName.contains("carkit") && !pName.contains("can") &&
                            !pName.contains("mcu") && !pName.contains("camera") && !pName.contains("dvr") &&
                            !pName.contains("bluetooth") && !pName.contains("bt") && !pName.contains("wifi") &&
                            !pName.contains("system")
                    if (isRadioPackage) {
                        pkg.receivers?.forEach { r ->
                            val rName = r.name.lowercase()
                            if (!rName.contains("carkit") && !rName.contains("setting") && !rName.contains("mcu") && !rName.contains("can")) {
                                recs.add(ComponentName(pkg.packageName, r.name))
                            }
                        }
                    }
                }
                synchronized(cachedDynamicReceivers) {
                    cachedDynamicReceivers.clear()
                    cachedDynamicReceivers.addAll(recs)
                    hasCachedTargets = true
                }
            } catch (e: Throwable) {}
        }
    }

    fun getRadioDiagnosticInfo(): String {
        val recs = getDynamicRadioReceivers()
        val pkgs = recs.map { it.packageName }.distinct()
        val freq = _radioStation.value ?: "None"
        return "Radio: $freq | Detected: ${if (pkgs.isEmpty()) "None" else pkgs.joinToString()} (${recs.size} receivers)"
    }

    private fun getDynamicRadioReceivers(): List<ComponentName> {
        synchronized(cachedDynamicReceivers) {
            if (hasCachedTargets) {
                return cachedDynamicReceivers.toList()
            }
        }
        warmUpDynamicTargets()
        return emptyList()
    }

    /**
     * Broadcasts tune / seek previous commands across NWD, QF, Allwinner and automotive HAL daemons.
     */
    fun tunePreviousStation() {
        dispatchRadioCommand(isNext = false)
    }

    /**
     * Broadcasts tune / seek next commands across NWD, QF, Allwinner and automotive HAL daemons.
     */
    fun tuneNextStation() {
        dispatchRadioCommand(isNext = true)
    }

    /**
     * Comprehensive multi-tier command dispatcher for NWD K2401P, QF, Allwinner and generic automotive units.
     * Combines Shell input key injection, AudioManager media/channel key events, explicit component broadcasts,
     * explicit service start commands, and multi-format (Int & String) NWD IPC commands.
     */
    private fun dispatchRadioCommand(isNext: Boolean) {
        val primaryKeyCode = if (isNext) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS
        val channelKeyCode = if (isNext) KeyEvent.KEYCODE_CHANNEL_UP else KeyEvent.KEYCODE_CHANNEL_DOWN
        val stepKeyCode = if (isNext) KeyEvent.KEYCODE_MEDIA_STEP_FORWARD else KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD
        val skipKeyCode = if (isNext) KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD else KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD

        val keyCodes = listOf(primaryKeyCode, channelKeyCode, stepKeyCode, skipKeyCode)

        // 1. Direct Shell Input Key Injection (Global on Android automotive ROMs)
        for (code in listOf(primaryKeyCode, channelKeyCode)) {
            try {
                Runtime.getRuntime().exec(arrayOf("input", "keyevent", code.toString()))
            } catch (e: Throwable) {}
        }

        // 2. Dispatch Media & Channel Key Events to Audio System
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            for (code in keyCodes) {
                audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        } catch (e: Throwable) {}

        // 3. ACTION_MEDIA_BUTTON Broadcasts (Both ordered global and package-targeted)
        for (code in keyCodes) {
            try {
                val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, code))
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                context.sendOrderedBroadcast(downIntent, null)
                context.sendOrderedBroadcast(upIntent, null)
                for (pkg in radioPackages) {
                    try {
                        context.sendBroadcast(Intent(downIntent).apply { setPackage(pkg) })
                        context.sendBroadcast(Intent(upIntent).apply { setPackage(pkg) })
                    } catch (e: Throwable) {}
                }
            } catch (e: Throwable) {}
        }

        // 4. Discover Dynamic Radio Receivers from PackageManager (strictly radio only)
        val dynamicReceivers = getDynamicRadioReceivers()
        val allTargetPackages = (radioPackages + dynamicReceivers.map { it.packageName })
            .filter { pkg ->
                val p = pkg.lowercase()
                (p.contains("radio") || p.contains("fm")) && !p.contains("setting") && !p.contains("carkit") && !p.contains("can") && !p.contains("mcu")
            }
            .distinct()

        // 5. Build Comprehensive Action Lists & Intent Bundles
        val stringCommands = if (isNext) {
            listOf("next", "seek_next", "seek_up", "tune_up", "step_up", "channel_up", "up", "search_up", "forward")
        } else {
            listOf("prev", "seek_prev", "seek_down", "tune_down", "step_down", "channel_down", "down", "search_down", "backward")
        }

        val specificActions = if (isNext) {
            listOf(
                "com.nwd.radio.next",
                "com.nwd.radio.tune_up",
                "com.nwd.radio.seek_up",
                "com.nwd.radio.step_up",
                "com.nwd.link.radio.next",
                "com.qf.action.RADIO_NEXT",
                "com.qf.action.KEY_NEXT",
                "com.allwinner.radio.next",
                "com.allwinner.radio.ACTION_NEXT",
                "android.intent.action.NEXT"
            )
        } else {
            listOf(
                "com.nwd.radio.prev",
                "com.nwd.radio.tune_down",
                "com.nwd.radio.seek_down",
                "com.nwd.radio.step_down",
                "com.nwd.link.radio.prev",
                "com.qf.action.RADIO_PREV",
                "com.qf.action.KEY_PREV",
                "com.allwinner.radio.prev",
                "com.allwinner.radio.ACTION_PREV",
                "android.intent.action.PREV"
            )
        }

        val commonActions = listOf(
            "com.nwd.action.ACTION_SEND_RADIO_COMMAND",
            "com.nwd.action.ACTION_RADIO_CMD",
            "com.nwd.radio.action",
            "com.nwd.radio.command",
            "com.nwd.radio.cmd",
            "com.qf.radio.action",
            "com.qf.action.RADIO_COMMAND",
            "com.allwinner.radio.ACTION_COMMAND",
            "com.allwinner.radio.command",
            "com.allwinner.radio.action",
            "com.microntek.sync",
            "com.syu.radio"
        )

        val intentsToSend = mutableListOf<Intent>()

        // 5a. Specific Actions
        for (act in specificActions) {
            intentsToSend.add(Intent(act).apply {
                putExtra("keyCode", primaryKeyCode)
                putExtra("key", if (isNext) "next" else "prev")
            })
        }

        // 5b. Common Actions with String Commands
        for (act in commonActions) {
            for (sc in stringCommands) {
                intentsToSend.add(Intent(act).apply {
                    putExtra("command", sc)
                    putExtra("extra_command", sc)
                    putExtra("cmd", sc)
                    putExtra("action", sc)
                    putExtra("key", sc)
                    putExtra("keyCode", primaryKeyCode)
                })
            }
        }

        // 5c. NWD / MCU Specific Radio Opcode (3 = PREV, 4 = NEXT)
        val nwdCmd = if (isNext) 4 else 3
        intentsToSend.add(Intent("com.nwd.action.ACTION_SEND_RADIO_COMMAND").apply {
            putExtra("command", nwdCmd)
            putExtra("cmd", nwdCmd)
            putExtra("extra_cmd", nwdCmd)
            putExtra("nwd_cmd", nwdCmd)
            putExtra("keyCode", primaryKeyCode)
        })
        intentsToSend.add(Intent("com.nwd.radio.action").apply {
            putExtra("action", if (isNext) "next" else "prev")
            putExtra("command", nwdCmd)
        })

        // 5d. Radio Key Code Actions (Standard Android & NWD key injection)
        intentsToSend.add(Intent("com.nwd.action.ACTION_SEND_KEY_CODE").apply {
            putExtra("keyCode", primaryKeyCode)
            putExtra("key_code", primaryKeyCode)
        })
        intentsToSend.add(Intent("com.nwd.action.ACTION_SEND_KEY_CODE").apply {
            putExtra("keyCode", channelKeyCode)
            putExtra("key_code", channelKeyCode)
        })
        intentsToSend.add(Intent("com.nwd.action.ACTION_KEY").apply {
            putExtra("key", if (isNext) "next" else "prev")
            putExtra("keyCode", primaryKeyCode)
        })

        // 6. Execute Dispatch (Global, Package-Targeted, Component-Targeted Broadcasts on background thread)
        DiagnosticsManager.logRadioAction("DISPATCH_COMMAND", "Sent ${if (isNext) "NEXT" else "PREVIOUS"} to ${intentsToSend.size} actions")
        CoroutineScope(Dispatchers.IO).launch {
            for (baseIntent in intentsToSend) {
                baseIntent.putExtra("is_launcher_source", true)
                baseIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

                // 6a. Implicit Broadcast
                try {
                    context.sendBroadcast(baseIntent)
                } catch (e: Throwable) {}

                // 6b. Package-targeted Broadcasts
                for (pkg in allTargetPackages) {
                    try {
                        context.sendBroadcast(Intent(baseIntent).apply { setPackage(pkg) })
                    } catch (e: Throwable) {}
                }

                // 6c. Explicit Component Broadcasts
                for (rec in dynamicReceivers) {
                    try {
                        context.sendBroadcast(Intent(baseIntent).apply { component = rec })
                    } catch (e: Throwable) {}
                }
            }

            delay(350L)
            requestRadioInfoPing()
            readCurrentSettingsFrequency()
        }
    }

    /**
     * Directly tunes to a specific frequency or preset index from the user's collected stations.
     */
    fun tuneToStation(frequencyStr: String, presetIndex: Int) {
        val numStr = frequencyStr.replace(Regex("[^0-9.]"), "")
        val freqDouble = numStr.toDoubleOrNull()
        val freqKHz = if (freqDouble != null) {
            if (freqDouble < 200.0) (freqDouble * 1000.0).roundToInt() else freqDouble.roundToInt()
        } else null
        val freq10KHz = if (freqDouble != null) {
            if (freqDouble < 200.0) (freqDouble * 100.0).roundToInt() else (freqDouble / 10.0).roundToInt()
        } else null

        val intents = mutableListOf<Intent>()

        // 1. Direct Frequency Tune Intents
        if (numStr.isNotBlank()) {
            intents.add(Intent("com.nwd.action.ACTION_SEND_RADIO_COMMAND").apply {
                putExtra("command", "set_freq")
                putExtra("cmd", "set_freq")
                putExtra("freq", numStr)
                if (freqDouble != null) putExtra("frequency", freqDouble)
                if (freqKHz != null) putExtra("freq_khz", freqKHz)
                if (freq10KHz != null) putExtra("extra_freq", freq10KHz)
            })
            intents.add(Intent("com.nwd.action.ACTION_SEND_RADIO_COMMAND").apply {
                putExtra("command", 14) // SET_FREQ opcode in NWD MCU
                putExtra("cmd", 14)
                putExtra("freq", numStr)
                if (freqDouble != null) putExtra("frequency", freqDouble)
                if (freqKHz != null) putExtra("freq_khz", freqKHz)
                if (freq10KHz != null) putExtra("extra_freq", freq10KHz)
            })
            intents.add(Intent("com.nwd.radio.set_freq").apply {
                putExtra("freq", numStr)
                if (freqKHz != null) putExtra("freq_khz", freqKHz)
            })
            intents.add(Intent("com.nwd.action.ACTION_SET_FREQ").apply {
                putExtra("freq", numStr)
                putExtra("frequency", numStr)
            })
            intents.add(Intent("com.qf.radio.action").apply {
                putExtra("action", "set_freq")
                putExtra("freq", numStr)
                if (freqKHz != null) putExtra("freq_khz", freqKHz)
            })
            intents.add(Intent("com.allwinner.radio.set_freq").apply {
                putExtra("freq", numStr)
            })
            // Deep links
            intents.add(Intent(Intent.ACTION_VIEW, Uri.parse("radio://$numStr")))
            intents.add(Intent(Intent.ACTION_VIEW, Uri.parse("radio://tune?freq=$numStr")))
        }

        // 2. Preset / Collect Index Selection Intents
        if (presetIndex >= 0) {
            val pNum = presetIndex + 1
            intents.add(Intent("com.nwd.action.ACTION_SEND_RADIO_COMMAND").apply {
                putExtra("command", "preset")
                putExtra("cmd", "preset")
                putExtra("preset", pNum)
                putExtra("collect", pNum)
                putExtra("index", presetIndex)
            })
            intents.add(Intent("com.nwd.action.ACTION_SEND_RADIO_COMMAND").apply {
                putExtra("command", "collect")
                putExtra("cmd", "collect")
                putExtra("index", presetIndex)
                putExtra("preset", pNum)
            })
            intents.add(Intent("com.nwd.action.ACTION_SEND_RADIO_COMMAND").apply {
                putExtra("command", 13) // COLLECT opcode
                putExtra("cmd", 13)
                putExtra("preset", pNum)
                putExtra("collect", pNum)
                putExtra("index", presetIndex)
            })
            intents.add(Intent("com.nwd.radio.preset").apply {
                putExtra("index", presetIndex)
                putExtra("preset", pNum)
            })
            intents.add(Intent("com.qf.radio.action").apply {
                putExtra("action", "select_preset")
                putExtra("index", presetIndex)
            })
        }

        val dynamicReceivers = getDynamicRadioReceivers()
        val allTargetPackages = (radioPackages + dynamicReceivers.map { it.packageName })
            .filter { pkg ->
                val p = pkg.lowercase()
                (p.contains("radio") || p.contains("fm")) && !p.contains("setting") && !p.contains("carkit") && !p.contains("can") && !p.contains("mcu")
            }
            .distinct()

        DiagnosticsManager.logRadioAction("TUNE_STATION", "Tuning to $frequencyStr (preset $presetIndex) - dispatched ${intents.size} intents")
        CoroutineScope(Dispatchers.IO).launch {
            for (intent in intents) {
                intent.putExtra("is_launcher_source", true)
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                try {
                    context.sendBroadcast(intent)
                } catch (e: Throwable) {}
                for (pkg in allTargetPackages) {
                    try {
                        context.sendBroadcast(Intent(intent).apply { setPackage(pkg) })
                    } catch (e: Throwable) {}
                }
                for (rec in dynamicReceivers) {
                    try {
                        context.sendBroadcast(Intent(intent).apply { component = rec })
                    } catch (e: Throwable) {}
                }
            }

            delay(350L)
            requestRadioInfoPing()
            readCurrentSettingsFrequency()
        }
    }

    private fun registerSettingsObservers() {
        val resolver = context.contentResolver

        // 1. Observe entire Settings.System for any modification
        try {
            resolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                settingsObserver
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Observe specific key URIs as explicit backup
        for (key in observedSettingsKeys) {
            try {
                val uri = Settings.System.getUriFor(key)
                if (uri != null) {
                    resolver.registerContentObserver(uri, false, settingsObserver)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            // Nowada (NWD / K2401P / Allwinner T507 / A133)
            addAction("com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW")
            addAction("ACTION_SEND_RADIO_FREQUENCE_NEW")
            addAction("com.nwd.radio.freq_action")
            addAction("com.nwd.radio.change")
            addAction("com.nwd.radio.status")
            addAction("com.nwd.radio.update_action")
            addAction("com.nwd.action.ACTION_RADIO_INFO")
            addAction("com.nwd.action.ACTION_RADIO_STATE")
            addAction("com.nwd.action.RADIO_STATUS_CHANGED")
            addAction("com.nwd.action.ACTION_SEND_RADIO_INFO")
            addAction("com.nwd.action.RADIO_INFO")
            addAction("com.nwd.action.RADIO_FREQUENCE")
            addAction("com.nwd.action.RADIO_STATUS")
            addAction("com.nwd.radio.REPORT")
            addAction("com.nwd.radio")
            addAction("com.nwd.link.radio.freq")
            addAction("com.nwd.link.radio")
            addAction("com.nwd.radio.broadcast")
            addAction("com.nwd.radio.CURRENT_FREQ")
            addAction("nwd.radio.cur_freq")
            addAction("com.nwd.ACTION_CHANGE_SOURCE")
            addAction("com.nwd.mcu.radio")
            addAction("com.nwd.kernel.radio")
            addAction("com.nwd.broadcast.RADIO")
            addAction("com.nwd.radio.ACTION_SEND_RADIO_INFO")
            addAction("com.nwd.radio.ACTION_RADIO_FREQ_CHANGED")
            addAction("com.nwd.radio.FREQ_CHANGED")
            addAction("com.nwd.radio.action.STATION_CHANGED")
            addAction("com.nwd.radio.station_changed")
            addAction("com.android.radio.freq")
            addAction("com.android.radio.frequence")

            // QuickFish (QF / K2401 / K706 / ROCO)
            addAction("com.qf.radio.update_action")
            addAction("com.qf.action.RADIO")
            addAction("com.qf.action.RADIO_INFO")
            addAction("com.qf.action.RADIO_STATE")
            addAction("com.qf.radio")
            addAction("com.qf.fmradio")
            addAction("com.qf.radio.REPORT")
            addAction("com.qf.radio.action")
            addAction("com.qf.action.ACC_ON")

            // Allwinner / Softwinner
            addAction("com.allwinner.radio.station_changed")
            addAction("com.allwinner.radio.REPORT")
            addAction("com.allwinner.radio")
            addAction("com.allwinner.action.RADIO_INFO")
            addAction("com.softwinner.radio.REPORT")
            addAction("com.softwinner.radio.station_changed")
            addAction("com.softwinner.radio")

            // Standard Android Media & Music
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackcomplete")
            addAction("com.android.music.queuechanged")
            addAction("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION")

            // Microntek (MTC)
            addAction("com.microntek.radiostate")
            addAction("com.microntek.radio.report")
            addAction("com.microntek.sync")

            // FYT / Syu / Joying
            addAction("com.syu.radio")
            addAction("com.syu.radio.freq")
            addAction("com.syu.ms.radio")
            addAction("com.syu.radio.station")

            // Topway / TS / XYAuto
            addAction("com.ts.radio.broadcast")
            addAction("com.ts.radio")
            addAction("com.xyauto.radio")
            addAction("com.forfan.radio")

            // NavRadio / Navimods
            addAction("com.navimods.radio.status")
            addAction("com.navimods.radio.station")
            addAction("com.navimods.radio")

            // AOSP / Generic Automotive
            addAction("android.intent.action.RADIO_STATE_CHANGED")
            addAction("android.hardware.radio.action.STATION_CHANGED")
            addAction("com.android.fmradio.FM_ENABLED")
            addAction("com.android.fmradio.FM_FREQUENCY_CHANGED")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(radioReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(radioReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkIntentRadioOff(intent: Intent): Boolean {
        val extras = intent.extras ?: return false
        val powerKeys = listOf("radio_power", "qf_radio_power", "nwd_radio_power", "is_power_on")
        for (k in powerKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k)
                if (v == false || v == "false" || v == "off") {
                    return true
                }
            }
        }
        return false
    }

    fun extractFromIntent(intent: Intent) {
        if (intent.getBooleanExtra("is_launcher_source", false)) return
        val extras = intent.extras

        // Support intent.data or dataString if URI encodes frequency (e.g. radio://... or content://...)
        val dataStr = intent.dataString
        if (dataStr != null) {
            val formattedData = formatFrequency(dataStr, null, null)
            if (formattedData != null) {
                _radioStation.value = formattedData
                _isRadioActive.value = true
                return
            }
        }

        if (extras == null) {
            readCurrentSettingsFrequency()
            return
        }

        // Check if intent explicitly signals radio turned off
        if (checkIntentRadioOff(intent)) {
            _isRadioActive.value = false
            _radioStation.value = null
            return
        }

        var detectedFreq: Any? = null
        var detectedName: String? = null
        var detectedBand: String? = null

        // Collect primary extras bundle and any nested bundles
        val candidateBundles = mutableListOf(extras)
        for (k in extras.keySet()) {
            try {
                val nested = extras.getBundle(k)
                if (nested != null) candidateBundles.add(nested)
            } catch (e: Throwable) {
                // Ignore
            }
        }

        // 1. Check known frequency extra keys across bundles
        val freqKeys = listOf(
            "extra_radio_frequence", "extra_radio_freq", "extra_freq", "cur_freq",
            "curfreq", "cur_frequency", "current_freq", "curRadioFreq", "currentRadioFreq",
            "nwd_freq", "nwd_radio_freq", "frequency", "freq", "channel",
            "radio:freq", "radio_freq", "qf_freq", "qf_radio_freq", "frequence",
            "radio_frequence", "mFreq", "station_freq", "play_freq", "tuner_freq",
            "RadioFreq", "RADIO_FREQ", "Radio_Freq", "freq_kHz", "freq_MHz",
            "cur_freq_khz", "freq_int", "EXTRA_RADIO_FREQUENCY", "EXTRA_FREQUENCY",
            "FREQ", "CUR_FREQ"
        )
        for (b in candidateBundles) {
            for (k in freqKeys) {
                if (b.containsKey(k)) {
                    val v = b.get(k) ?: continue
                    val formatted = formatFrequency(v, null, null)
                    if (formatted != null) {
                        detectedFreq = v
                        break
                    }
                }
            }
            if (detectedFreq != null) break
        }

        // 2. Check known station name extra keys across bundles
        val nameKeys = listOf(
            "extra_radio_name", "name", "station", "ps", "radio:name", "rds",
            "station_name", "title", "track", "label", "qf_station", "qf_name",
            "rds_ps", "rds_name", "radio_ps", "radio_name", "ps_name", "cur_station",
            "nwd_station", "program_service", "EXTRA_STATION_NAME", "STATION_NAME", "RADIO_NAME"
        )
        for (b in candidateBundles) {
            for (k in nameKeys) {
                if (b.containsKey(k)) {
                    val v = b.get(k)?.toString()?.trim()
                    if (!v.isNullOrBlank() && !v.equals("null", ignoreCase = true) && v.length in 2..40) {
                        if (BLACKLISTED_TOKENS.none { v.lowercase().contains(it) }) {
                            detectedName = v
                            break
                        }
                    }
                }
            }
            if (detectedName != null) break
        }

        // 3. Check known band extra keys across bundles
        val bandKeys = listOf("extra_radio_band", "band", "radio:band", "type", "cur_band", "nwd_band", "qf_band", "EXTRA_RADIO_BAND", "BAND", "RADIO_BAND")
        for (b in candidateBundles) {
            for (k in bandKeys) {
                if (b.containsKey(k)) {
                    val v = b.get(k)?.toString()?.trim()
                    if (!v.isNullOrBlank()) {
                        detectedBand = v
                        break
                    }
                }
            }
            if (detectedBand != null) break
        }

        // 4. Fallback: Scan all keys in the Bundle for any valid radio frequency value or array
        if (detectedFreq == null) {
            for (b in candidateBundles) {
                for (key in b.keySet()) {
                    val lowerKey = key.lowercase()
                    if (BLACKLISTED_TOKENS.any { lowerKey.contains(it) }) continue

                    val v = b.get(key) ?: continue

                    // Check IntArray or LongArray (common in MCU payloads)
                    if (v is IntArray) {
                        for (intVal in v) {
                            if (formatFrequency(intVal, detectedBand, null) != null) {
                                detectedFreq = intVal
                                break
                            }
                        }
                        if (detectedFreq != null) break
                    } else if (v is LongArray) {
                        for (longVal in v) {
                            if (formatFrequency(longVal, detectedBand, null) != null) {
                                detectedFreq = longVal
                                break
                            }
                        }
                        if (detectedFreq != null) break
                    } else if (v is ShortArray) {
                        for (shortVal in v) {
                            if (formatFrequency(shortVal, detectedBand, null) != null) {
                                detectedFreq = shortVal
                                break
                            }
                        }
                        if (detectedFreq != null) break
                    } else if (v is ByteArray) {
                        if (formatFrequency(v, detectedBand, null) != null) {
                            detectedFreq = v
                            break
                        }
                    } else {
                        val formatted = formatFrequency(v, detectedBand, null)
                        if (formatted != null) {
                            detectedFreq = v
                            break
                        }
                    }
                }
                if (detectedFreq != null) break
            }
        }

        if (detectedFreq != null) {
            val formatted = formatFrequency(detectedFreq, detectedBand, detectedName)
            if (formatted != null) {
                DiagnosticsManager.logRadioAction("FREQUENCY_UPDATE", "Parsed $formatted from action ${intent.action}")
                _radioStation.value = formatted
                _isRadioActive.value = true
                return
            }
        }

        // If intent had no recognizable frequency, query system settings
        readCurrentSettingsFrequency()
    }

    companion object {
        private var activeInstance: WeakReference<RadioManager>? = null

        /**
         * Global blacklist of tokens that belong to Android OS system settings or connectivity features,
         * NEVER to broadcast radio.
         */
        val BLACKLISTED_TOKENS = listOf(
            "airplane", "toggleable", "cell", "wifi", "bluetooth", "nfc", "telephony",
            "network", "mobile", "carrier", "sim", "gps", "device_name", "volume",
            "mute", "gain", "switch", "package", "service", "provider", "version",
            "data_stall", "mode_radios", "audio_output", "com.android", "com.google",
            "bright", "brightness", "backlight", "screen", "display", "touch", "dsp",
            "sound", "audio", "sensor", "light", "temp", "battery", "level", "mode"
        )

        fun onGlobalBroadcast(intent: Intent) {
            activeInstance?.get()?.extractFromIntent(intent)
        }

        /**
         * Strictly validates and formats raw radio frequency values into clean user-facing strings (e.g. "98.5 FM", "1050 AM • Rock Radio").
         * Rejects any non-radio string or out-of-band number by returning null.
         */
        fun formatFrequency(rawFreq: Any?, rawBand: String? = null, rawName: String? = null): String? {
            if (rawFreq == null) return null

            val str = when (rawFreq) {
                is Double -> if (rawFreq % 1.0 == 0.0) rawFreq.toLong().toString() else rawFreq.toString()
                is Float -> if (rawFreq % 1.0f == 0.0f) rawFreq.toLong().toString() else rawFreq.toString()
                is Number -> rawFreq.toLong().toString()
                is ByteArray -> {
                    // Try parsing as UTF-8 string first
                    val text = try { String(rawFreq, Charsets.UTF_8).trim() } catch (e: Throwable) { null }
                    if (text != null && text.length in 2..12 && text.any { it.isDigit() }) {
                        text
                    } else if (rawFreq.size >= 2) {
                        // Try 16-bit LE / BE integers (standard MCU FF01 frequency registers)
                        val le = (rawFreq[0].toInt() and 0xFF) or ((rawFreq[1].toInt() and 0xFF) shl 8)
                        val be = ((rawFreq[0].toInt() and 0xFF) shl 8) or (rawFreq[1].toInt() and 0xFF)
                        if (le in 8700..10850 || le in 520..1750 || le in 87000..108500) {
                            le.toString()
                        } else if (be in 8700..10850 || be in 520..1750 || be in 87000..108500) {
                            be.toString()
                        } else {
                            rawFreq.toString()
                        }
                    } else {
                        rawFreq.toString()
                    }
                }
                else -> rawFreq.toString().trim()
            }

            if (str.isBlank() || str == "0" || str == "-1" || str.equals("null", ignoreCase = true)) return null

            val lower = str.lowercase()
            if (BLACKLISTED_TOKENS.any { lower.contains(it) }) return null

            var formattedFreq: String? = null
            val isExplicitAm = str.contains("AM", ignoreCase = true) ||
                rawBand?.contains("AM", ignoreCase = true) == true ||
                rawBand == "3" || rawBand == "4"
            val isExplicitFm = str.contains("FM", ignoreCase = true) ||
                rawBand?.contains("FM", ignoreCase = true) == true ||
                rawBand == "0" || rawBand == "1" || rawBand == "2"

            val directNum = str.toDoubleOrNull()
            if (directNum != null) {
                when {
                    // FM in Hz (87 MHz - 108.5 MHz): 87,000,000 to 108,500,000 Hz
                    directNum in 87_000_000.0..108_500_000.0 -> {
                        val mhz = directNum / 1_000_000.0
                        formattedFreq = String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // AM in Hz (500 kHz - 1750 kHz): 500,000 to 1,750,000 Hz
                    directNum in 500_000.0..1_750_000.0 -> {
                        val khz = (directNum / 1000.0).roundToInt()
                        formattedFreq = "$khz AM"
                    }
                    // FM in kHz (87,000 - 108,500 kHz, e.g. 98500)
                    directNum in 87_000.0..108_500.0 -> {
                        val mhz = directNum / 1000.0
                        formattedFreq = String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // AM in 100 Hz / 10x kHz (5,200 - 17,500, e.g. 10500 -> 1050 AM)
                    isExplicitAm && directNum in 5200.0..17500.0 -> {
                        val khz = (directNum / 10.0).roundToInt()
                        formattedFreq = "$khz AM"
                    }
                    // FM in 10 kHz (8,700 - 10,850, e.g. 9850 -> 98.5 FM, standard Chinese car stereos Allwinner/QF/NWD)
                    directNum in 8700.0..10850.0 -> {
                        val mhz = directNum / 100.0
                        formattedFreq = String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // AM in kHz (520 - 1750 kHz) OR FM in 100 kHz (875 - 1080)
                    directNum in 520.0..1750.0 -> {
                        formattedFreq = if ((isExplicitFm || !isExplicitAm) && directNum in 875.0..1080.0) {
                            String.format(Locale.US, "%.1f FM", directNum / 10.0)
                        } else {
                            "${directNum.roundToInt()} AM"
                        }
                    }
                    // FM in MHz (Standard European / US / Worldwide Band: 87.0 - 108.5 MHz, e.g. 98.5)
                    directNum in 87.0..108.5 -> {
                        formattedFreq = String.format(Locale.US, "%.1f FM", directNum)
                    }
                    // Extended FM (e.g. 65.0 - 87.0) ONLY accepted if explicit decimal point or explicit FM band flag, NEVER a raw integer like 77
                    isExplicitFm && directNum in 65.0..87.0 && (str.contains(".") || rawBand?.contains("OIRT", ignoreCase = true) == true) -> {
                        formattedFreq = String.format(Locale.US, "%.1f FM", directNum)
                    }
                    // LW in kHz (140 - 300 kHz)
                    directNum in 140.0..300.0 -> {
                        formattedFreq = "${directNum.roundToInt()} LW"
                    }
                    else -> {
                        // Out of all valid broadcast radio frequency ranges
                        return null
                    }
                }
            } else {
                // String with text or units, e.g. "FM 98.5", "98.50 MHz", "1050 kHz AM"
                val match = Regex("""(\d{2,8}(?:\.\d{1,2})?)""").find(str)
                if (match != null) {
                    val extractedNum = match.value.toDoubleOrNull()
                    if (extractedNum != null) {
                        when {
                            extractedNum in 87_000_000.0..108_500_000.0 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum / 1_000_000.0)
                            }
                            extractedNum in 500_000.0..1_750_000.0 -> {
                                formattedFreq = "${(extractedNum / 1000.0).roundToInt()} AM"
                            }
                            extractedNum in 87_000.0..108_500.0 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum / 1000.0)
                            }
                            isExplicitAm && extractedNum in 5200.0..17500.0 -> {
                                formattedFreq = "${(extractedNum / 10.0).roundToInt()} AM"
                            }
                            extractedNum in 8700.0..10850.0 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum / 100.0)
                            }
                            extractedNum in 520.0..1750.0 -> {
                                formattedFreq = if ((isExplicitFm || !isExplicitAm) && extractedNum in 875.0..1080.0) {
                                    String.format(Locale.US, "%.1f FM", extractedNum / 10.0)
                                } else {
                                    "${extractedNum.roundToInt()} AM"
                                }
                            }
                            extractedNum in 87.0..108.5 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum)
                            }
                        }
                    }
                }
            }

            if (formattedFreq == null) return null

            // Clean station name (RDS PS text) if provided
            val cleanName = rawName?.trim()?.takeIf { candidate ->
                candidate.isNotBlank() &&
                    !candidate.equals("null", ignoreCase = true) &&
                    !candidate.equals("0", ignoreCase = true) &&
                    !candidate.equals("-1", ignoreCase = true) &&
                    !candidate.equals(str, ignoreCase = true) &&
                    !candidate.equals(formattedFreq, ignoreCase = true) &&
                    candidate.length in 2..40 &&
                    BLACKLISTED_TOKENS.none { candidate.lowercase().contains(it) }
            }

            return if (cleanName != null) {
                "$formattedFreq • $cleanName"
            } else {
                formattedFreq
            }
        }
    }
}
