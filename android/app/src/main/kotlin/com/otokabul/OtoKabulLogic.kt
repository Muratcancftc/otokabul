package com.otokabul

import java.util.Random
import java.util.regex.Pattern

/**
 * Km parse, kabul kararı, gecikme ve çift basma koruması — tek kaynak.
 */
object OtoKabulLogic {
    val KM_REGEX: Pattern =
        Pattern.compile("""(\d+)[,.](\d+)\s*km""", Pattern.CASE_INSENSITIVE)

    /** BiTaksi teklif kartı satırı: "7 dk • 2,56 km" / "8 dk • 2,24 km" */
    val TRIP_ROW_KM_REGEX: Pattern = Pattern.compile(
        """\d+\s*dk\s*•\s*(\d+)[,.](\d+)\s*km""",
        Pattern.CASE_INSENSITIVE,
    )

    const val DEBOUNCE_MS = 2500L
    const val DELAY_MIN_MS = 200
    const val DELAY_MAX_MS = 500
    const val ACCEPT_BUTTON_TEXT = "Kabul et"
    /** Yolculuk teklif kartındaki kazanç etiketi — başka ekranlardaki "Kabul et" ayırt edilir. */
    const val TRIP_OFFER_EARNINGS_MARKER = "Toplam kazanç"

    /** Metindeki ilk km değerini parse eder. */
    fun parseKm(text: String): Double? {
        val matcher = KM_REGEX.matcher(text)
        if (!matcher.find()) return null
        val whole = matcher.group(1) ?: return null
        val fraction = matcher.group(2) ?: return null
        return "$whole.$fraction".toDoubleOrNull()
    }

    /** Metindeki tüm km değerlerini sırayla döndürür. */
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

    /** "8 dk • 2,24 km" gibi tek satırdaki km. */
    fun parseKmFromTripRowLine(text: String): Double? {
        val matcher = TRIP_ROW_KM_REGEX.matcher(text)
        if (!matcher.find()) return null
        val whole = matcher.group(1) ?: return null
        val fraction = matcher.group(2) ?: return null
        return "$whole.$fraction".toDoubleOrNull()
    }

    /** Bir metinde birden fazla "X dk • Y km" satırı olabilir. */
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

    /**
     * Alttaki yolculuk satırındaki km — "8 dk • 2,24 km" (2. "dk • km" satırı).
     * Üstteki "7 dk • 2,56 km" (alış) kullanılmaz.
     */
    fun journeyKmFromTexts(allTexts: List<String>): Double? {
        val rowKms = mutableListOf<Double>()
        for (text in allTexts) {
            rowKms.addAll(parseAllKmFromTripRowLines(text))
        }
        return rowKms.getOrNull(1)
    }

    /**
     * BiTaksi yolculuk teklif kartı mı? Km sayısına bakılmaz.
     * Başka ekranlardaki "Kabul et" butonları bu kontrolden geçemez.
     */
    fun isTripOfferScreen(allTexts: List<String>): Boolean {
        if (!allTexts.any { it.contains(TRIP_OFFER_EARNINGS_MARKER, ignoreCase = true) }) {
            return false
        }
        return allTexts.any { it.trim() == ACCEPT_BUTTON_TEXT }
    }

    /** Min km ve üzeri → kabul (eşit dahil). Karar yolculuk mesafesine göre. */
    fun shouldAccept(tripKm: Double, minKm: Double): Boolean = tripKm >= minKm

    /** 200–500 ms arası rastgele gecikme (hızlı tıklama). */
    fun randomDelayMs(random: Random = Random()): Int =
        DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS + 1)

    fun isDelayInRange(ms: Int): Boolean = ms in DELAY_MIN_MS..DELAY_MAX_MS

    /** true → event atlanmalı (çift basma / debounce). */
    fun shouldSkipEvent(now: Long, lastHandledAt: Long, isProcessing: Boolean): Boolean =
        isProcessing || (lastHandledAt > 0L && now - lastHandledAt < DEBOUNCE_MS)

    /** İlk event işlenir, 2500 ms içindeki ikinci event atlanır. */
    fun testDoubleTapProtection(): Boolean {
        val base = 1_000_000L
        val firstSkipped = shouldSkipEvent(base, 0L, false)
        val secondSkipped = shouldSkipEvent(base + 1000, base, false)
        return !firstSkipped && secondSkipped
    }
}
