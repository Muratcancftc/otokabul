package com.otokabul

import java.util.Random
import java.util.regex.Pattern

/**
 * Km parse, kabul kararı, gecikme ve çift basma koruması — tek kaynak.
 */
object OtoKabulLogic {
    val KM_REGEX: Pattern =
        Pattern.compile("""(\d+)[,.](\d+)\s*km""", Pattern.CASE_INSENSITIVE)

    /** "7 dk • 2,56 km" — ayraç BiTaksi sürümüne göre değişebilir */
    val TRIP_ROW_KM_REGEX: Pattern = Pattern.compile(
        """\d+\s*dk\s*[•·\-–—|]?\s*(\d+)[,.](\d+)\s*km""",
        Pattern.CASE_INSENSITIVE,
    )

    const val DEBOUNCE_MS = 2500L
    const val DELAY_MIN_MS = 200
    const val DELAY_MAX_MS = 500
    const val ACCEPT_BUTTON_TEXT = "Kabul et"
    const val TRIP_OFFER_EARNINGS_MARKER = "Toplam kazanç"

    fun parseKm(text: String): Double? {
        val matcher = KM_REGEX.matcher(text)
        if (!matcher.find()) return null
        val whole = matcher.group(1) ?: return null
        val fraction = matcher.group(2) ?: return null
        return "$whole.$fraction".toDoubleOrNull()
    }

    fun parseAllKmInText(text: String): List<Double> {
        val result = mutableListOf<Double>()
        val matcher = KM_REGEX.matcher(text)
        while (matcher.find()) {
            val whole = matcher.group(1) ?: continue
            val fraction = matcher.group(2) ?: continue
            val km = "$whole.$fraction".toDoubleOrNull() ?: continue
            result.add(km)
        }
        return result
    }

    fun parseKmFromTripRowLine(text: String): Double? {
        val matcher = TRIP_ROW_KM_REGEX.matcher(text)
        if (!matcher.find()) return null
        val whole = matcher.group(1) ?: return null
        val fraction = matcher.group(2) ?: return null
        return "$whole.$fraction".toDoubleOrNull()
    }

    fun parseAllKmFromTripRowLines(text: String): List<Double> {
        val result = mutableListOf<Double>()
        val matcher = TRIP_ROW_KM_REGEX.matcher(text)
        while (matcher.find()) {
            val whole = matcher.group(1) ?: continue
            val fraction = matcher.group(2) ?: continue
            val km = "$whole.$fraction".toDoubleOrNull() ?: continue
            result.add(km)
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

    /**
     * Yolculuk km — önce "8 dk … km" satırının 2.si, yoksa ağaçtaki 2. km.
     * BiTaksi bazen metni böler ("8 dk" / "2,24 km" ayrı node).
     */
    fun journeyKmFromTexts(allTexts: List<String>): Double? {
        val rowKms = mutableListOf<Double>()
        for (text in allTexts) {
            rowKms.addAll(parseAllKmFromTripRowLines(text))
        }
        rowKms.getOrNull(1)?.let { return it }

        val allKm = allKmFromTexts(allTexts)
        return allKm.getOrNull(1)
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
     * Teklif kartı: Kabul et + (reddet / ₺ / kazanç / en az 2 km).
     * "Toplam kazanç" erişilebilirlikte olmayabilir — zorunlu değil.
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
