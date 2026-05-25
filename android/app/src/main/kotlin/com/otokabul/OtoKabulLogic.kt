package com.otokabul

import java.util.Random
import java.util.regex.Pattern

/**
 * Km parse, kabul kararı, gecikme ve çift basma koruması — tek kaynak.
 * Yolculuk km: karttaki alt satır (son km birimli satır). Üst satır m veya km — yok sayılır.
 */
object OtoKabulLogic {
    /** "2,24 km", "2 km", "2.24km" */
    val KM_REGEX: Pattern =
        Pattern.compile("""(\d+)(?:[,.](\d+))?\s*km""", Pattern.CASE_INSENSITIVE)

    /** "7 dk • 2,56 km" — ayraç BiTaksi sürümüne göre değişebilir */
    val TRIP_ROW_KM_REGEX: Pattern = Pattern.compile(
        """\d+\s*dk\s*[•·\-–—|]?\s*(\d+)(?:[,.](\d+))?\s*km""",
        Pattern.CASE_INSENSITIVE,
    )

    /** "1 dk • 509 m" veya "7 dk • 3,33 km" */
    val TRIP_ROW_REGEX: Pattern = Pattern.compile(
        """\d+\s*dk\s*[•·\-–—|]?\s*(\d+)(?:[,.](\d+))?\s*(m|km)\b""",
        Pattern.CASE_INSENSITIVE,
    )

    val METER_IN_TEXT_REGEX: Pattern =
        Pattern.compile("""(\d+)(?:[,.](\d+))?\s*m\b""", Pattern.CASE_INSENSITIVE)

    val DK_ONLY_REGEX: Pattern =
        Pattern.compile("""\d+\s*dk\b""", Pattern.CASE_INSENSITIVE)

    enum class DistanceUnit { M, KM }

    data class TripRowDistance(val value: Double, val unit: DistanceUnit)

    const val DEBOUNCE_MS = 2500L
    const val DELAY_MIN_MS = 200
    const val DELAY_MAX_MS = 800
    const val ACCEPT_BUTTON_TEXT = "Kabul et"
    const val TRIP_OFFER_EARNINGS_MARKER = "Toplam kazanç"

    /** "₺170 - 215" */
    val EARNING_REGEX: Pattern =
        Pattern.compile("""₺(\d+)\s*[-–—]\s*(\d+)""")

    data class EarningRange(val min: Int, val max: Int)

    private fun kmFromGroups(whole: String, fraction: String?): Double? {
        if (fraction != null) {
            return "$whole.$fraction".toDoubleOrNull()
        }
        return whole.toDoubleOrNull()
    }

    fun parseKm(text: String): Double? {
        val matcher = KM_REGEX.matcher(text)
        if (!matcher.find()) return null
        val whole = matcher.group(1) ?: return null
        return kmFromGroups(whole, matcher.group(2))
    }

    fun parseAllKmInText(text: String): List<Double> {
        val result = mutableListOf<Double>()
        val matcher = KM_REGEX.matcher(text)
        while (matcher.find()) {
            val whole = matcher.group(1) ?: continue
            val km = kmFromGroups(whole, matcher.group(2)) ?: continue
            result.add(km)
        }
        return result
    }

    fun parseKmFromTripRowLine(text: String): Double? {
        val matcher = TRIP_ROW_KM_REGEX.matcher(text)
        if (!matcher.find()) return null
        val whole = matcher.group(1) ?: return null
        return kmFromGroups(whole, matcher.group(2))
    }

    fun parseAllKmFromTripRowLines(text: String): List<Double> {
        val result = mutableListOf<Double>()
        val matcher = TRIP_ROW_KM_REGEX.matcher(text)
        while (matcher.find()) {
            val whole = matcher.group(1) ?: continue
            val km = kmFromGroups(whole, matcher.group(2)) ?: continue
            result.add(km)
        }
        return result
    }

    fun allTripRowsInText(text: String): List<TripRowDistance> {
        val result = mutableListOf<TripRowDistance>()
        val matcher = TRIP_ROW_REGEX.matcher(text)
        while (matcher.find()) {
            val whole = matcher.group(1) ?: continue
            val value = kmFromGroups(whole, matcher.group(2)) ?: continue
            val unitStr = matcher.group(3)?.lowercase() ?: continue
            val unit = if (unitStr == "km") DistanceUnit.KM else DistanceUnit.M
            result.add(TripRowDistance(value, unit))
        }
        return result
    }

    /** Ağaç sırasına göre tüm dk satırları (m ve km). */
    fun allTripRowsFromTexts(allTexts: List<String>): List<TripRowDistance> {
        val result = mutableListOf<TripRowDistance>()
        for (text in allTexts) {
            result.addAll(allTripRowsInText(text))
        }
        return result
    }

    /** Ağaç sırasına göre tüm km değerleri (dk satırı olmasa da). */
    fun allKmFromTexts(allTexts: List<String>): List<Double> {
        val result = mutableListOf<Double>()
        for (text in allTexts) {
            result.addAll(parseAllKmInText(text))
        }
        return result
    }

    private fun isMeterOnlyText(text: String): Boolean =
        METER_IN_TEXT_REGEX.matcher(text).find() && parseKm(text) == null

    /** "X dk" satırından sonra gelen km (metre satırları atlanır). */
    fun kmFromDkPairs(allTexts: List<String>): List<Double> {
        val result = mutableListOf<Double>()
        for (i in allTexts.indices) {
            if (!DK_ONLY_REGEX.matcher(allTexts[i]).find()) continue
            for (j in i + 1 until minOf(i + 5, allTexts.size)) {
                val next = allTexts[j]
                if (isMeterOnlyText(next)) break
                parseKm(next)?.let { km ->
                    result.add(km)
                    break
                }
            }
        }
        return result
    }

    /** Alt yolculuk satırı = son km birimli dk satırı. */
    private fun journeyKmFromTripRows(rows: List<TripRowDistance>): Double? {
        val kmRows = rows.filter { it.unit == DistanceUnit.KM }
        if (kmRows.isEmpty()) return null
        if (kmRows.size >= 2) return kmRows.last().value
        // Tek km + üstte m var → alt yolculuk km'si
        if (rows.any { it.unit == DistanceUnit.M }) return kmRows.last().value
        // Tek km, üst m yok — kart henüz tam yüklenmemiş olabilir
        if (rows.size == 1) return null
        return kmRows.last().value
    }

    /**
     * Yolculuk km — karttaki alt satır (son km).
     * Üst satır m veya km olsun yok sayılır.
     */
    fun journeyKmFromTexts(allTexts: List<String>): Double? {
        val treeRows = allTripRowsFromTexts(allTexts)
        journeyKmFromTripRows(treeRows)?.let { return it }

        val joinedRows = allTripRowsInText(allTexts.joinToString(" "))
        journeyKmFromTripRows(joinedRows)?.let { return it }

        kmFromDkPairs(allTexts).lastOrNull()?.let { return it }

        val allKm = allKmFromTexts(allTexts)
        if (allKm.size >= 2) return allKm.last()
        if (allKm.size == 1 && joinedRows.any { it.unit == DistanceUnit.M }) {
            return allKm.last()
        }
        return null
    }

    /** "₺170 - 215" → min: 170, max: 215 */
    fun parseEarnings(text: String): EarningRange? {
        val matcher = EARNING_REGEX.matcher(text)
        if (!matcher.find()) return null
        val min = matcher.group(1)?.toIntOrNull() ?: return null
        val max = matcher.group(2)?.toIntOrNull() ?: return null
        return EarningRange(min, max)
    }

    /** Teklif kartındaki kazanç tahmini (son eşleşme). */
    fun earningsFromTexts(allTexts: List<String>): EarningRange? {
        var last: EarningRange? = null
        for (text in allTexts) {
            parseEarnings(text)?.let { last = it }
        }
        if (last != null) return last
        return parseEarnings(allTexts.joinToString(" "))
    }

    fun hasAcceptButton(allTexts: List<String>): Boolean =
        allTexts.any { isAcceptButtonText(it) }

    fun isAcceptButtonText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        return t.equals(ACCEPT_BUTTON_TEXT, ignoreCase = true) ||
            t.contains(ACCEPT_BUTTON_TEXT, ignoreCase = true)
    }

    /**
     * Teklif kartı: Kabul et + (reddet / ₺ / kazanç / dk+km).
     */
    fun isTripOfferScreen(allTexts: List<String>): Boolean {
        if (!hasAcceptButton(allTexts)) return false

        if (allTexts.any { it.contains(TRIP_OFFER_EARNINGS_MARKER, ignoreCase = true) }) {
            return true
        }
        if (allTexts.any { it.contains("kazanç", ignoreCase = true) }) return true
        if (allTexts.any { it.contains("₺") }) return true
        if (allTexts.any { it.contains("reddet", ignoreCase = true) }) return true
        if (allTexts.any { it.contains("Tümünü", ignoreCase = true) }) return true
        if (allTexts.any { it.contains(" dk", ignoreCase = true) } &&
            (allKmFromTexts(allTexts).isNotEmpty() ||
                allTripRowsFromTexts(allTexts).any { it.unit == DistanceUnit.KM })
        ) {
            return true
        }

        return allKmFromTexts(allTexts).size >= 2
    }

    fun shouldAccept(tripKm: Double, minKm: Double): Boolean = tripKm >= minKm

    fun randomDelayMs(random: Random = Random()): Int =
        DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS + 1)

    fun isDelayInRange(ms: Int): Boolean = ms in DELAY_MIN_MS..DELAY_MAX_MS

    fun shouldSkipEvent(now: Long, lastHandledAt: Long, isProcessing: Boolean): Boolean =
        isProcessing || (lastHandledAt > 0L && now - lastHandledAt < DEBOUNCE_MS)

    fun testDoubleTapProtection(): Boolean {
        val base = 1_000_000L
        val firstSkipped = shouldSkipEvent(base, 0L, false)
        val secondSkipped = shouldSkipEvent(base + 1000, base, false)
        return !firstSkipped && secondSkipped
    }
}
