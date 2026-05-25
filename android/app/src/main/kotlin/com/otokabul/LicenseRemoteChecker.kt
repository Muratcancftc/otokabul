package com.otokabul

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * GitHub licenses.json — uzaktan active / süre kontrolü (ForegroundService watchdog).
 */
object LicenseRemoteChecker {

    const val LICENSES_URL =
        "https://raw.githubusercontent.com/Muratcancftc/otokabul/main/licenses.json"

    enum class Status {
        /** Kayıtlı kod yok veya cihaz eşleşmiyor — kontrol atlanır */
        SKIP,
        /** GitHub: active + süre OK */
        VALID,
        /** Panel: Pasife Al */
        INACTIVE,
        /** 7 gün doldu */
        EXPIRED,
        /** Kod listede yok */
        NOT_FOUND,
        /** Ağ / parse hatası — servis durdurulmaz */
        NETWORK_ERROR,
    }

    data class Result(
        val status: Status,
        val code: String = "",
        val active: Boolean? = null,
        val message: String = "",
    )

    fun check(context: Context): Result {
        val code = FlutterLicensePrefs.getCode(context)?.uppercase()
            ?: return Result(Status.SKIP, message = "no_local_code")

        val localDeviceId = FlutterLicensePrefs.getDeviceId(context)
        val ourId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: ""
        if (localDeviceId.isNullOrEmpty() || ourId.isEmpty() || localDeviceId != ourId) {
            return Result(Status.SKIP, code = code, message = "device_mismatch")
        }

        val fetch = fetchEntryForCode(code)
        when (fetch) {
            is FetchResult.Error -> return Result(
                Status.NETWORK_ERROR,
                code = code,
                message = "fetch_failed",
            )
            is FetchResult.NotFound -> return Result(
                Status.NOT_FOUND,
                code = code,
                active = false,
                message = "not_found",
            )
            is FetchResult.Found -> {
                val entry = fetch.entry
                val active = parseActive(entry)
                if (!active) {
                    return Result(
                        Status.INACTIVE,
                        code = code,
                        active = false,
                        message = "inactive",
                    )
                }

                val expiresAt = entry.optString("expires_at", "").trim()
                if (expiresAt.isNotEmpty() && isExpiredDate(expiresAt)) {
                    return Result(
                        Status.EXPIRED,
                        code = code,
                        active = true,
                        message = "expired",
                    )
                }

                val remoteDevice = entry.optString("device_id", "").trim()
                if (remoteDevice.isNotEmpty() && remoteDevice != ourId) {
                    return Result(
                        Status.NOT_FOUND,
                        code = code,
                        active = active,
                        message = "other_device",
                    )
                }

                return Result(
                    Status.VALID,
                    code = code,
                    active = true,
                    message = "ok",
                )
            }
        }
    }

    /** Self-test — yalnızca GitHub active alanını okur. */
    fun testRemoteActive(context: Context): Map<String, Any> {
        val code = FlutterLicensePrefs.getCode(context)?.uppercase() ?: ""
        if (code.isEmpty()) {
            return mapOf(
                "allPass" to false,
                "code" to "",
                "active" to false,
                "status" to "no_local_code",
                "message" to "Yerel lisans kodu yok",
            )
        }

        val fetch = fetchEntryForCode(code)
        if (fetch is FetchResult.Error) {
            return mapOf(
                "allPass" to false,
                "code" to code,
                "active" to false,
                "status" to "network_error",
                "message" to "GitHub okunamadı",
            )
        }
        if (fetch is FetchResult.NotFound) {
            return mapOf(
                "allPass" to false,
                "code" to code,
                "active" to false,
                "status" to "not_found",
                "message" to "Kod GitHub listesinde yok",
            )
        }

        val entry = (fetch as FetchResult.Found).entry
        val active = parseActive(entry)
        val full = check(context)
        val ok = full.status == Status.VALID

        return mapOf(
            "allPass" to ok,
            "code" to code,
            "active" to active,
            "status" to full.status.name.lowercase(),
            "message" to when (full.status) {
                Status.VALID -> "active=true, süre geçerli"
                Status.INACTIVE -> "active=false (panel pasif)"
                Status.EXPIRED -> "Süre dolmuş"
                else -> full.message
            },
        )
    }

    private sealed class FetchResult {
        data class Found(val entry: JSONObject) : FetchResult()
        object NotFound : FetchResult()
        object Error : FetchResult()
    }

    private fun fetchEntryForCode(code: String): FetchResult {
        return try {
            val url = URL(
                "$LICENSES_URL?_=${System.currentTimeMillis()}",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return FetchResult.Error
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = JSONObject(body)
            val licenses = root.getJSONArray("licenses")
            for (i in 0 until licenses.length()) {
                val item = licenses.getJSONObject(i)
                if (item.optString("code", "").trim().uppercase() == code) {
                    return FetchResult.Found(item)
                }
            }
            FetchResult.NotFound
        } catch (_: Exception) {
            FetchResult.Error
        }
    }

    private fun parseActive(entry: JSONObject): Boolean {
        if (!entry.has("active")) return false
        return when (val v = entry.get("active")) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun isExpiredDate(value: String): Boolean {
        return try {
            if (value.contains("T")) {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = fmt.parse(value.substringBefore(".").substringBefore("Z"))
                    ?: return false
                System.currentTimeMillis() > parsed.time
            } else {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = fmt.parse(value) ?: return false
                val endOfDay = parsed.time + 24 * 60 * 60 * 1000 - 1
                System.currentTimeMillis() > endOfDay
            }
        } catch (_: Exception) {
            false
        }
    }
}
