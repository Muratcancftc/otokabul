package com.otokabul

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Google Sheets trip log — lib/sheets_logger.dart ile aynı Apps Script endpoint.
 * Accessibility servisinden çağrılır (Flutter kapalı olsa da çalışır).
 */
object SheetsLogger {

    private const val SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycby2cxzATPxiKnMNnLRBfCFu_Gx-JH02HFQI7YSHZrnKh20T7JEiZTnGZj8eOrhDrMe4/exec"

    private val ioThread = HandlerThread("OtoKabulSheetsLog").apply { start() }
    private val ioHandler = Handler(ioThread.looper)

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale("tr", "TR"))
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))

    fun logTrip(
        context: Context,
        km: Double,
        accepted: Boolean,
        earningMin: Int? = null,
        earningMax: Int? = null,
    ) {
        val app = context.applicationContext
        val code = FlutterLicensePrefs.getCode(app) ?: return
        val deviceId = FlutterLicensePrefs.getDeviceId(app) ?: return
        if (deviceId.isEmpty()) return

        val now = Date()
        val date = dateFmt.format(now)
        val time = timeFmt.format(now)

        ioHandler.post {
            try {
                sendLog(
                    code = code,
                    deviceId = deviceId,
                    km = km,
                    earningMin = earningMin,
                    earningMax = earningMax,
                    accepted = accepted,
                    date = date,
                    time = time,
                )
            } catch (_: Exception) {
                // Sessizce geç
            }
        }
    }

    private fun sendLog(
        code: String,
        deviceId: String,
        km: Double,
        earningMin: Int?,
        earningMax: Int?,
        accepted: Boolean,
        date: String,
        time: String,
    ) {
        val params = linkedMapOf(
            "action" to "log",
            "code" to code.uppercase(),
            "device_id" to deviceId,
            "km" to km.toString(),
            "accepted" to if (accepted) "true" else "false",
            "date" to date,
            "time" to time,
        )
        if (earningMin != null) params["earning_min"] = earningMin.toString()
        if (earningMax != null) params["earning_max"] = earningMax.toString()

        val query = params.entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val url = URL("$SCRIPT_URL?$query")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
