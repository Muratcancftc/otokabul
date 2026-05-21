# OtoKabul — Proje Dokümantasyonu

> BiTaksi sürücü uygulaması için otomatik yolculuk kabul aracı.  
> **Tamamen lokal** — proxy, API, sunucu yok. APK WhatsApp ile taksicilere dağıtılır.

---

## 0. Ne yapıldı? (geliştirme özeti)

### Proje oluşturuldu
- Flutter projesi `oto_kabul`, sadece **Android**, paket `com.otokabul`
- Boş iskelet → tam çalışan uygulama + release APK

### Çekirdek özellik (BiTaksi otomatik kabul)
- **AccessibilityService** ile `com.projectslender` popup izlenir
- Popup’taki km değerleri parse edilir; karar **2. km (index 1)** yolculuk mesafesine göre (kazanç)
- `tripKm >= minKm` → 200–500 ms sonra **"Kabul et"** tıklanır
- Flutter: slider 1–30 km (varsayılan 5), BAŞLAT/DURDUR, son 20 log satırı

### Eklenen yardımcı sistemler
| Özellik | Açıklama |
|---------|----------|
| **ForegroundService** | Arka planda canlı kalma, bildirim "OtoKabul Aktif 🚕" |
| **BootReceiver** | Telefon açılınca servis yeniden başlar |
| **30 sn watchdog** | Erişilebilirlik öldü mü kontrol + otomatik yeniden bağlanma |
| **ScreenWakeManager** | Ekran açık tutma (WakeLock) + kapalıyken uyarı |
| **AcceptSoundPlayer** | Kabul → ding, atlama → sessiz |
| **Self-test** | 10 test (`test_runner.dart` + `SelfTestNative.kt`) |
| **Method Channel** | `com.otokabul/service`, `com.otokabul/test` |

### Yapılan düzeltmeler / netleştirmeler
1. **Km seçimi:** `OtoKabulLogic.tripKmFromValues()` → **`getOrNull(1)`** (2. km, yolculuk mesafesi). 1. km (yolcuya uzaklık) kullanılmaz.
2. **packageNames:** `accessibility_service_config.xml` → sadece `com.projectslender` dinlenir.
3. **Self-test:** TEST 5/6, index 1 ve senaryolar (2.24 ATLA, 7.30/5.00 KABUL) güncellendi.

### Dağıtım
```bash
flutter build apk --release
# → build/app/outputs/flutter-apk/app-release.apk (~44 MB)
```

---

## 1. Genel mimari

```
┌─────────────────────────────────────────────────────────────┐
│  Flutter UI (lib/)                                          │
│  home_screen.dart  →  slider, başlat/durdur, log listesi    │
│  test_runner.dart  →  10 self-test                          │
└──────────────────────┬──────────────────────────────────────┘
                       │ Method Channel: com.otokabul/service
                       │ Method Channel: com.otokabul/test
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Android Native (kotlin/com/otokabul/)                      │
│  MainActivity          → channel + log broadcast            │
│  ForegroundService     → bildirim, watchdog 30sn, ekran     │
│  AutoAcceptAccessibilityService → BiTaksi popup izleme      │
│  BootReceiver          → telefon açılınca servis restart    │
└─────────────────────────────────────────────────────────────┘
```

| Özellik | Değer |
|---------|--------|
| Paket / applicationId | `com.otokabul` |
| BiTaksi sürücü paketi | `com.projectslender` |
| Min km varsayılan | 5 km |
| Kabul butonu metni | `Kabul et` (tam eşleşme) |

---

## 2. BiTaksi popup mantığı (kritik)

Popup’ta **iki km** değeri var (üstten alta, erişilebilirlik ağacı sırası):

| Sıra | Örnek | Anlam | Kullanım |
|------|--------|--------|----------|
| **1.** | `2,56 km` | Yolcuya uzaklık (alış) | **Kullanılmaz** |
| **2.** | `2,24 km` | Yolculuk mesafesi (kazanç) | **Karar buna göre (index 1)** |

- Regex: `(\d+)[,.](\d+)\s*km` → virgül noktaya çevrilir → `double`
- Tüm node’lardan km’ler toplanır → **`tripKmFromValues` = `getOrNull(1)`** = yolculuk mesafesi
- Karşılaştırma: `tripKm >= minKm` → kabul (eşit dahil)
- Kabul: rastgele **200–500 ms** bekle → `"Kabul et"` bul → `ACTION_CLICK`
- Atlama: sadece log, **ses yok**
- Kabul: **ding** sesi (`ToneGenerator.TONE_PROP_ACK`)
- Çift işlem koruması: **2500 ms** içinde ikinci event atlanır

---

## 3. Dosya yapısı

### Flutter (`lib/`)

| Dosya | Açıklama |
|-------|----------|
| `main.dart` | `OtoKabulApp`, MaterialApp, HomeScreen |
| `home_screen.dart` | UI: slider 1–30 km, BAŞLAT/DURDUR, log (son 20), servis durumu, ekran uyarısı, test butonu |
| `test_runner.dart` | 10 self-test ekranı, otomatik çalıştırma, özet |

### Android Kotlin (`android/app/src/main/kotlin/com/otokabul/`)

| Dosya | Açıklama |
|-------|----------|
| `MainActivity.kt` | Flutter embedding, Method Channel, log BroadcastReceiver |
| `AutoAcceptAccessibilityService.kt` | BiTaksi popup dinleme, km, tıklama, log broadcast |
| `ForegroundService.kt` | Ön plan bildirim, 30 sn watchdog, ekran izleme |
| `BootReceiver.kt` | `BOOT_COMPLETED` → servis yeniden başlat |
| `OtoKabulLogic.kt` | Km parse, shouldAccept, gecikme, debounce (tek kaynak) |
| `OtoKabulPrefs.kt` | SharedPreferences: `min_km`, `service_running` |
| `AccessibilityMonitor.kt` | Erişilebilirlik sağlık + otomatik yeniden bağlanma |
| `ScreenWakeManager.kt` | PowerManager WakeLock, ekran pulse |
| `AcceptSoundPlayer.kt` | Kabul ding sesi |
| `SelfTestNative.kt` | Native self-test implementasyonu |

### Android kaynaklar

| Dosya | Açıklama |
|-------|----------|
| `AndroidManifest.xml` | İzinler, servisler, receiver, BiTaksi package query |
| `res/xml/accessibility_service_config.xml` | Erişilebilirlik config, `packageNames=com.projectslender` |
| `res/values/strings.xml` | Bildirim ve uyarı metinleri |

---

## 4. Method Channel’lar

### `com.otokabul/service`

| Method | Yön | Açıklama |
|--------|-----|----------|
| `start` | Flutter→Native | ForegroundService başlat, prefs `service_running=true` |
| `stop` | Flutter→Native | Servis durdur |
| `setMinKm` | Flutter→Native | `{km: double}` → `otokabul_prefs` |
| `getMinKm` | Flutter→Native | Min km oku |
| `isServiceRunning` | Flutter→Native | Foreground çalışıyor mu |
| `isAccessibilityEnabled` | Flutter→Native | `AccessibilityMonitor.isHealthy()` (ayar+açık+bağlı) |
| `onLog` | Native→Flutter | `{km, accepted, minKm, time}` log satırı |

### `com.otokabul/test`

| Method | Dönüş |
|--------|--------|
| `testAccessibility` | bool |
| `testNotification` | bool |
| `testForegroundService` | bool |
| `testSharedPreferences` | bool |
| `testWakeLock` | bool |
| `testBitaksiInstalled` | bool |
| `testKmParse` | Map (4 parse + `trip_index_1`) |
| `testMinKmComparison` | Map (`2.24_atla`, `7.30_kabul`, `5.00_kabul`) |
| `testRandomDelay` | bool |
| `testDoubleTapProtection` | bool |

---

## 5. Broadcast’ler

| Action | Ne zaman |
|--------|----------|
| `com.otokabul.LOG` | Her teklif işlendiğinde (km, accepted, minKm, time) |
| `com.otokabul.ACCESSIBILITY_RECOVERED` | Erişilebilirlik yeniden bağlandı |
| `com.otokabul.ACCESSIBILITY_LOST` | Erişilebilirlik ayarlardan kapatıldı |

---

## 6. ForegroundService özellikleri

### Bildirim
- Kanal: `otokabul_foreground` — **"OtoKabul Aktif 🚕"**
- Uyarı kanalı: `otokabul_alert` — erişilebilirlik / ekran kapalı

### 30 saniye watchdog
1. `ScreenWakeManager.renewIfNeeded()` — WakeLock yenile
2. `checkScreenState()` — ekran kapalı mı
3. `checkAccessibilityAndRecover()` — erişilebilirlik öldü mü

### Erişilebilirlik kurtarma (`AccessibilityMonitor`)
- Ayarlarda kapalı → uyarı bildirimi (otomatik açılamaz)
- Ayarlarda açık ama `instance == null` → bileşen disable/enable ile yeniden bağlanma dene (max 60 sn arayla)

### Ekran açık tutma (`ScreenWakeManager`)
- `SCREEN_BRIGHT_WAKE_LOCK` + `ACQUIRE_CAUSES_WAKEUP` — servis açıkken
- `ACTION_SCREEN_OFF` → bildirim **"Ekranı açık tutun!"** + 4 sn pulse
- `MainActivity` açıkken: `FLAG_KEEP_SCREEN_ON`
- Ana ekranda sarı uyarı kutusu (servis çalışırken)

---

## 7. Ses

| Durum | Ses |
|-------|-----|
| Kabul edildi (`accepted=true`) | Ding (`AcceptSoundPlayer`, `STREAM_NOTIFICATION`) |
| Atlandı | Sessiz |

---

## 8. Self-test (10 test)

`lib/test_runner.dart` — açılınca veya butonla çalışır.

1. Accessibility Service izni  
2. Bildirim izni  
3. Foreground Service  
4. SharedPreferences (5.0 yaz/oku)  
5. Km parse: `2,56` / `2.56` / `10,5` / `abc` + **index 1** → `[2.56, 2.24]` = 2.24  
6. Min km (2. değer): 2.24 ATLA, 7.30 KABUL, 5.00 KABUL (eşit kabul)  
7. Rastgele gecikme 200–500 ms (10 deneme)  
8. WAKE_LOCK izni  
9. BiTaksi kurulu mu (uyarı sayılır, hata değil)  
10. Çift basma koruması 2500 ms  

Özet: `✅ 9/10 geçti, ⚠️ 1 uyarı` formatı.

---

## 9. Android izinleri

```xml
FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC
RECEIVE_BOOT_COMPLETED
WAKE_LOCK
POST_NOTIFICATIONS
BIND_ACCESSIBILITY_SERVICE (servis tanımında)
```

---

## 10. Log formatı (Flutter UI)

```
✅ 14:23 - 4,20 km - KABUL EDİLDİ
❌ 14:25 - 1,80 km - ATLANDI (min: 5km)
```

---

## 11. Kurulum / kullanım (taksici)

1. APK kur (`app-release.apk`)
2. **Ayarlar → Erişilebilirlik → OtoKabul** aç
3. Bildirim izni ver (Android 13+)
4. OtoKabul uygulaması → min yolculuk km ayarla → **BAŞLAT**
5. BiTaksi açık, **ekran açık** (veya WakeLock ile), telefon şarjda önerilir
6. Pil optimizasyonundan muaf tut (önerilir)

---

## 12. Build

```bash
cd oto_kabul
flutter pub get
flutter build apk --release
# Çıktı: build/app/outputs/flutter-apk/app-release.apk
```

---

## 13. Bilinen sınırlamalar

- Erişilebilirlik servisi kullanıcı kapatırsa uygulama **programatik açamaz** — sadece uyarı.
- Ekran kapalıyken erişilebilirlik ağacı okunamaz; WakeLock + uyarı var ama %100 garanti değil (OEM pil tasarrufu).
- Sadece `com.projectslender` dinlenir (`packageNames` config’de tanımlı).
- Sadece **Android** — iOS/web yok.

---

## 14. Bağımlılıklar

```yaml
flutter:
shared_preferences: ^2.5.3
cupertino_icons: ^1.0.8
```

Android: `androidx.core:core-ktx:1.15.0`

---

## 15. Örnek akış (kabul)

```
BiTaksi popup açılır
  → TYPE_WINDOW_CONTENT_CHANGED (com.projectslender)
  → findTripKm → 7.30 km (2. değer, yolculuk mesafesi)
  → minKm = 5.0 → shouldAccept ✓
  → 200–500 ms bekle
  → "Kabul et" tıkla
  → ding sesi
  → broadcast LOG → Flutter log listesi
```

## 16. Örnek akış (atlama)

```
tripKm = 2.24 < minKm 5.0
  → sendLog(accepted=false)
  → sessiz
  → Flutter: ❌ ATLANDI (min: 5km)
```

---

---

## 17. Claude’a kopyala — tek paragraf özet

OtoKabul, BiTaksi sürücü (`com.projectslender`) teklif popup’ını AccessibilityService ile okuyan, **2. km yolculuk mesafesini** (index 1, `getOrNull(1)`) slider’daki min km ile karşılaştıran ve uygunsa 200–500 ms sonra "Kabul et"e tıklayan tam lokal bir Flutter/Android uygulamasıdır. Sunucu yok. ForegroundService + 30 sn watchdog + ekran WakeLock + kabul ding sesi + 10 self-test + BootReceiver ile taksici telefonuna WhatsApp APK olarak kurulur.

---

*Son güncelleme: Mayıs 2026 — sürüm 1.0.0+1 — `tripKm` index 1, `packageNames=com.projectslender`*
