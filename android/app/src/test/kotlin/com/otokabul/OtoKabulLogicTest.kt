package com.otokabul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BiTaksi teklif kartı mantığı — gerçek ekran metinleriyle birim testleri.
 */
class OtoKabulLogicTest {

    /** Kullanıcının paylaştığı teklif kartı (screenshot). */
    private val realOfferTexts = listOf(
        "Tümünü reddet (1)",
        "7 dk • 2,56 km",
        "Gaziler, 1705. Sk. No:18, 41400 Gebze/Kocaeli, Türkiye",
        "8 dk • 2,24 km",
        "Hacıhalil, Körfez Cd. No:18, 41400 Gebze/Kocaeli, Türkiye",
        "Kabul et",
        "Toplam kazanç",
        "₺170 - 215",
    )

    @Test
    fun realOfferCard_isTripOfferScreen() {
        assertTrue(OtoKabulLogic.isTripOfferScreen(realOfferTexts))
    }

    @Test
    fun realOfferCard_journeyKm_isSecondRow_notPickup() {
        val journey = OtoKabulLogic.journeyKmFromTexts(realOfferTexts)
        assertEquals(2.24, journey!!, 0.001)
        assertFalse(journey == 2.56)
    }

    @Test
    fun realOfferCard_minKm5_shouldNotAccept() {
        val journey = OtoKabulLogic.journeyKmFromTexts(realOfferTexts)!!
        assertFalse(OtoKabulLogic.shouldAccept(journey, 5.0))
    }

    @Test
    fun realOfferCard_minKm2_shouldAccept() {
        val journey = OtoKabulLogic.journeyKmFromTexts(realOfferTexts)!!
        assertTrue(OtoKabulLogic.shouldAccept(journey, 2.0))
    }

    @Test
    fun realOfferCard_minKm224_exact_shouldAccept() {
        val journey = OtoKabulLogic.journeyKmFromTexts(realOfferTexts)!!
        assertTrue(OtoKabulLogic.shouldAccept(journey, 2.24))
    }

    @Test
    fun otherScreen_withKabulEt_isNotTripOfferScreen() {
        assertFalse(
            OtoKabulLogic.isTripOfferScreen(listOf("Kabul et", "Ayarlar", "Profil")),
        )
    }

    @Test
    fun otherScreen_kabulEtOnly_shouldNotTriggerClick() {
        val texts = listOf("Kabul et", "Yolculuk geçmişi")
        assertFalse(OtoKabulLogic.isTripOfferScreen(texts))
        assertNull(OtoKabulLogic.journeyKmFromTexts(texts))
    }

    @Test
    fun parseTripRowLine_8dk() {
        assertEquals(2.24, OtoKabulLogic.parseKmFromTripRowLine("8 dk • 2,24 km")!!, 0.001)
    }

    @Test
    fun parseTripRowLine_7dk_pickup_notUsedAsJourney() {
        val pickup = OtoKabulLogic.parseKmFromTripRowLine("7 dk • 2,56 km")!!
        assertEquals(2.56, pickup, 0.001)
        // Tek satır varsa journey null (2. satır yok)
        assertNull(
            OtoKabulLogic.journeyKmFromTexts(listOf("7 dk • 2,56 km", "Toplam kazanç", "Kabul et")),
        )
    }

    @Test
    fun highJourney_minKm5_shouldAccept() {
        val texts = listOf(
            "7 dk • 1,20 km",
            "12 dk • 7,30 km",
            "Kabul et",
            "Toplam kazanç",
        )
        assertTrue(OtoKabulLogic.isTripOfferScreen(texts))
        val journey = OtoKabulLogic.journeyKmFromTexts(texts)!!
        assertEquals(7.30, journey, 0.001)
        assertTrue(OtoKabulLogic.shouldAccept(journey, 5.0))
    }

    @Test
    fun offerWithoutToplamKazancText_stillDetected() {
        val texts = listOf(
            "Tümünü reddet (1)",
            "7 dk • 2,56 km",
            "8 dk • 2,24 km",
            "Kabul et",
            "₺170 - 215",
        )
        assertTrue(OtoKabulLogic.isTripOfferScreen(texts))
        assertEquals(2.24, OtoKabulLogic.journeyKmFromTexts(texts)!!, 0.001)
    }

    @Test
    fun splitNodes_kmInSeparateTexts() {
        val texts = listOf(
            "Tümünü reddet (1)",
            "7 dk",
            "2,56 km",
            "8 dk",
            "2,24 km",
            "Kabul et",
            "₺170 - 215",
        )
        assertTrue(OtoKabulLogic.isTripOfferScreen(texts))
        assertEquals(2.24, OtoKabulLogic.journeyKmFromTexts(texts)!!, 0.001)
    }

    @Test
    fun delayInRange() {
        val random = java.util.Random(42)
        repeat(20) {
            assertTrue(OtoKabulLogic.isDelayInRange(OtoKabulLogic.randomDelayMs(random)))
        }
    }

    @Test
    fun doubleTapProtection() {
        assertTrue(OtoKabulLogic.testDoubleTapProtection())
    }

    /** Tam akış: teklif kartı + km uygun → tıklanır; değilse tıklanmaz. */
    @Test
    fun fullDecisionFlow_realCard() {
        assertTrue(wouldClickAccept(realOfferTexts, minKm = 2.0))
        assertFalse(wouldClickAccept(realOfferTexts, minKm = 5.0))
    }

    @Test
    fun fullDecisionFlow_wrongScreen_neverClick() {
        assertFalse(wouldClickAccept(listOf("Kabul et", "Ayarlar"), minKm = 1.0))
    }

    private fun wouldClickAccept(allTexts: List<String>, minKm: Double): Boolean {
        if (!OtoKabulLogic.isTripOfferScreen(allTexts)) return false
        val journey = OtoKabulLogic.journeyKmFromTexts(allTexts) ?: return false
        return OtoKabulLogic.shouldAccept(journey, minKm)
    }
}
