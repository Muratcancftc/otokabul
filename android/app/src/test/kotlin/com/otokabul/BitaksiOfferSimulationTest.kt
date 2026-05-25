package com.otokabul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gerçek BiTaksi teklif kartı akışı simülasyonu (erişilebilirlik metinleri mock).
 * AutoAcceptAccessibilityService.handleOfferTexts ile aynı karar mantığı.
 */
class BitaksiOfferSimulationTest {

    data class OfferOutcome(
        val action: String,
        val km: Double?,
        val earningMin: Int?,
        val earningMax: Int?,
    )

    /** Servisin handleOfferTexts karar mantığı — saf fonksiyon. */
    private fun simulateOffer(allTexts: List<String>, minKm: Double): OfferOutcome {
        if (!OtoKabulLogic.isTripOfferScreen(allTexts)) {
            return OfferOutcome("ignore", null, null, null)
        }
        val earnings = OtoKabulLogic.earningsFromTexts(allTexts)
        val km = OtoKabulLogic.journeyKmFromTexts(allTexts)
            ?: return OfferOutcome(
                "no_km",
                null,
                earnings?.min,
                earnings?.max,
            )
        val action = if (OtoKabulLogic.shouldAccept(km, minKm)) "accept" else "skip"
        return OfferOutcome(action, km, earnings?.min, earnings?.max)
    }

    private val classicOffer = listOf(
        "Tümünü reddet (1)",
        "7 dk • 2,56 km",
        "Gaziler, 1705. Sk. No:18, Gebze",
        "8 dk • 2,24 km",
        "Hacıhalil, Körfez Cd., Gebze",
        "Kabul et",
        "Toplam kazanç",
        "₺170 - 215",
    )

    private val metersPickupOffer = listOf(
        "Tümünü reddet (1)",
        "1 dk • 509 m",
        "Osman Yılmaz Mah., Gebze",
        "7 dk • 3,33 km",
        "Arapçeşme Mah., Gebze",
        "Kabul et",
        "Toplam kazanç",
        "₺185 - 230",
    )

    @Test
    fun classicOffer_minKm2_acceptsWithEarnings() {
        val r = simulateOffer(classicOffer, minKm = 2.0)
        assertEquals("accept", r.action)
        assertEquals(2.24, r.km!!, 0.001)
        assertEquals(170, r.earningMin)
        assertEquals(215, r.earningMax)
    }

    @Test
    fun classicOffer_minKm5_skips() {
        val r = simulateOffer(classicOffer, minKm = 5.0)
        assertEquals("skip", r.action)
        assertEquals(2.24, r.km!!, 0.001)
        assertNotNull(r.earningMin)
    }

    @Test
    fun metersPickupOffer_minKm3_acceptsBottomKm() {
        val r = simulateOffer(metersPickupOffer, minKm = 3.0)
        assertEquals("accept", r.action)
        assertEquals(3.33, r.km!!, 0.001)
        assertEquals(185, r.earningMin)
        assertEquals(230, r.earningMax)
    }

    @Test
    fun metersPickupOffer_minKm4_skips() {
        val r = simulateOffer(metersPickupOffer, minKm = 4.0)
        assertEquals("skip", r.action)
        assertEquals(3.33, r.km!!, 0.001)
    }

    @Test
    fun longTrip_minKm5_accepts() {
        val texts = listOf(
            "Tümünü reddet (1)",
            "7 dk • 1,20 km",
            "12 dk • 7,30 km",
            "Kabul et",
            "₺300 - 400",
        )
        val r = simulateOffer(texts, minKm = 5.0)
        assertEquals("accept", r.action)
        assertEquals(7.30, r.km!!, 0.001)
        assertEquals(300, r.earningMin)
    }

    @Test
    fun settingsScreen_ignored() {
        val r = simulateOffer(listOf("Kabul et", "Ayarlar", "Profil"), minKm = 1.0)
        assertEquals("ignore", r.action)
        assertNull(r.km)
    }

    @Test
    fun incompleteCard_noKm() {
        val r = simulateOffer(
            listOf("Kabul et", "Toplam kazanç", "Tümünü reddet (1)"),
            minKm = 2.0,
        )
        assertEquals("no_km", r.action)
        assertNull(r.km)
    }

    @Test
    fun debounce_blocksRapidSecondScan() {
        val base = System.currentTimeMillis()
        assertFalse(OtoKabulLogic.shouldSkipEvent(base, 0L, false))
        assertTrue(OtoKabulLogic.shouldSkipEvent(base + 500, base, false))
        assertFalse(OtoKabulLogic.shouldSkipEvent(base + 3000, base, false))
    }

    @Test
    fun processingFlag_blocksScan() {
        val now = System.currentTimeMillis()
        assertTrue(OtoKabulLogic.shouldSkipEvent(now, 0L, isProcessing = true))
    }

    @Test
    fun splitAccessibilityNodes_fullFlow() {
        val texts = listOf(
            "Tümünü reddet (1)",
            "1 dk",
            "509 m",
            "adres",
            "7 dk",
            "3,33 km",
            "adres2",
            "Kabul et",
            "Toplam kazanç",
            "₺185 - 230",
        )
        val r = simulateOffer(texts, minKm = 3.0)
        assertEquals("accept", r.action)
        assertEquals(3.33, r.km!!, 0.001)
        assertEquals(185, r.earningMin)
    }

    @Test
    fun minKmChange_affectsNextDecision() {
        // min 2 → kabul, min 3 → atlama (aktifken slider 2'den 3'e çekildi)
        assertEquals("accept", simulateOffer(classicOffer, minKm = 2.0).action)
        assertEquals("skip", simulateOffer(classicOffer, minKm = 3.0).action)
    }

    @Test
    fun earningsMissing_stillAcceptsByKm() {
        val texts = listOf(
            "Tümünü reddet (1)",
            "7 dk • 2,56 km",
            "8 dk • 5,50 km",
            "Kabul et",
        )
        val r = simulateOffer(texts, minKm = 5.0)
        assertEquals("accept", r.action)
        assertEquals(5.50, r.km!!, 0.001)
        assertNull(r.earningMin)
        assertNull(r.earningMax)
    }
}
