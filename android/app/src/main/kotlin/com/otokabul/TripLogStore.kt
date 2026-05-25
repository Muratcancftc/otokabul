package com.otokabul

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Son teklif kayıtları — arka planda da saklanır, uygulama açılınca senkron edilir.
 */
object TripLogStore {
    private const val PREFS = "otokabul_trip_logs"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 20

    data class Entry(
        val time: String,
        val km: Double,
        val accepted: Boolean,
        val minKm: Double,
        val reason: String,
        val earningMin: Int? = null,
        val earningMax: Int? = null,
    )

    fun append(context: Context, entry: Entry) {
        val list = loadAll(context).toMutableList()
        list.add(0, entry)
        while (list.size > MAX_ENTRIES) list.removeAt(list.size - 1)
        saveAll(context, list)
    }

    fun loadAll(context: Context): List<Entry> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entry(
                            time = o.optString("time", ""),
                            km = o.optDouble("km", 0.0),
                            accepted = o.optBoolean("accepted", false),
                            minKm = o.optDouble("minKm", 5.0),
                            reason = o.optString("reason", ""),
                            earningMin = o.optInt("earningMin", -1).takeIf { it >= 0 },
                            earningMax = o.optInt("earningMax", -1).takeIf { it >= 0 },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject().apply {
                    put("time", e.time)
                    put("km", e.km)
                    put("accepted", e.accepted)
                    put("minKm", e.minKm)
                    put("reason", e.reason)
                    put("earningMin", e.earningMin ?: -1)
                    put("earningMax", e.earningMax ?: -1)
                },
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, arr.toString())
            .apply()
    }

    fun toMaps(list: List<Entry>): List<Map<String, Any?>> =
        list.map {
            mapOf(
                "time" to it.time,
                "km" to it.km,
                "accepted" to it.accepted,
                "minKm" to it.minKm,
                "reason" to it.reason,
                "earning_min" to it.earningMin,
                "earning_max" to it.earningMax,
            )
        }
}
