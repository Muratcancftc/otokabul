# OtoKabul 🚕

BiTaksi sürücü uygulaması (`com.projectslender`) için **tamamen lokal** otomatik yolculuk kabul aracı. Sunucu, proxy veya API yok — tüm işlem telefonda çalışır.

## Ne yapar?

- BiTaksi teklif popup'ını izler (Erişilebilirlik servisi)
- **2. km** (yolculuk mesafesi) değerini okur
- Slider'daki minimum km ile karşılaştırır (`tripKm >= minKm` → kabul)
- Uygun teklifte **200–500 ms** sonra **"Kabul et"** butonuna tıklar
- Kabul → ding sesi | Atlama → sessiz

## Kurulum (taksici)

1. Release APK kur (veya `flutter build apk --release`)
2. **Ayarlar → Erişilebilirlik → OtoKabul** servisini aç
3. Bildirim izni ver (Android 13+)
4. Pil optimizasyonundan muaf tut (uygulama ilk açılışta sorar)
5. Minimum yolculuk km ayarla → **BAŞLAT**
6. BiTaksi açık, ekran açık (veya WakeLock) — telefon şarjda önerilir

## Geliştirme

```bash
cd oto_kabul
flutter pub get
flutter run
```

Release APK (macOS — Android SDK + Java 17 gerekir):

```bash
# Proje kökündeyken (oto_kabul içinde; tekrar cd oto_kabul yapma)
./scripts/build_apk.sh
# Çıktı: build/app/outputs/flutter-apk/app-release.apk
```

İlk kurulum (bir kez):

```bash
brew install --cask android-commandlinetools openjdk@17
flutter config --android-sdk /opt/homebrew/share/android-commandlinetools
```

`JAVA_HOME` Android Studio'ya işaret ediyorsa (`No Android SDK` / Gradle hatası): terminalde `unset JAVA_HOME` veya `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.

GitHub Actions her `main` push'ta APK üretir (Artifacts).

## Self-test

Uygulama içinde **🧪 Testleri Çalıştır** — 10 kontrol (erişilebilirlik, servis, km parse, pil, vb.)

## Teknik özet

| Özellik | Değer |
|---------|--------|
| Paket | `com.otokabul` |
| Hedef uygulama | `com.projectslender` |
| Min km | 1–30 (varsayılan 5) |
| Platform | Android only |

Detaylı dokümantasyon: [PROJE_DOKUMANTASYON.md](PROJE_DOKUMANTASYON.md)

## Lisans

Özel proje — Muratcancftc/otokabul
