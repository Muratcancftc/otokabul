import 'dart:async' show unawaited;
import 'dart:convert';

import 'package:flutter/foundation.dart' show kDebugMode, debugPrint;
import 'package:http/http.dart' as http;
import 'package:oto_kabul/native_bridge.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// GitHub üzerinden lisans doğrulama ve yerel aktivasyon.
class LicenseManager {
  LicenseManager._();

  static const licensesUrl =
      'https://raw.githubusercontent.com/Muratcancftc/otokabul/main/licenses.json';

  /// Google Apps Script Web App — lisans aktivasyonu GitHub senkronu.
  static const activationScriptUrl =
      'https://script.google.com/macros/s/AKfycby2cxzATPxiKnMNnLRBfCFu_Gx-JH02HFQI7YSHZrnKh20T7JEiZTnGZj8eOrhDrMe4/exec';

  static const _keyCode = 'license_code';
  static const _keyDeviceId = 'license_device_id';
  static const _keyActivatedAt = 'license_activated_at';
  static const _keyExpiresAt = 'license_expires_at';

  static const licenseDurationDays = 7;
  static const expiringSoonDays = 2;

  static const msgNotFound = 'Kod bulunamadı';
  static const msgUsedOnOtherDevice = 'Bu kod başka cihazda kullanılıyor';
  static const msgExpired = 'Lisans süresi dolmuş';
  static const msgAlreadyUsed =
      'Bu kod zaten kullanılmış. Yeni kod alın.';
  static const msgDeactivated = 'Lisans devre dışı bırakıldı';
  static const msgRevoked = 'Lisansınız iptal edildi';
  static const msgNetwork = 'İnternet bağlantısı gerekli';

  /// Yerel lisans geçerli mi + GitHub doğrulaması (active=false → giriş yok).
  static Future<bool> checkLicense() async {
    final prefs = await SharedPreferences.getInstance();
    final code = prefs.getString(_keyCode);
    final localDeviceId = prefs.getString(_keyDeviceId);
    final localExpiresAt = prefs.getString(_keyExpiresAt);

    if (code == null || code.isEmpty || localDeviceId == null) {
      return false;
    }

    final ourId = await getDeviceId();
    if (ourId.isEmpty || localDeviceId != ourId) {
      await clearLicense();
      return false;
    }

    final remote = await _fetchLicenses();
    if (remote == null) {
      // İnternet yok — yalnızca yerel süre (panel pasife aldıysa çevrimiçi yakalanır)
      return !_isExpired(localExpiresAt);
    }

    final entry = _findByCode(remote, code);
    if (entry == null) {
      await clearLicense();
      return false;
    }

    if (!_isActive(entry)) {
      await clearLicense();
      return false;
    }

    final remoteDeviceId = _readString(entry, 'device_id');
    if (remoteDeviceId.isNotEmpty && remoteDeviceId != ourId) {
      await clearLicense();
      return false;
    }

    final remoteExpires = _readString(entry, 'expires_at');
    if (remoteExpires.isNotEmpty) {
      if (_isExpired(remoteExpires)) {
        await clearLicense();
        return false;
      }
      await prefs.setString(_keyExpiresAt, _toStoredIso(remoteExpires));
      final remoteActivated = _readString(entry, 'activated_at');
      if (remoteActivated.isNotEmpty) {
        await prefs.setString(_keyActivatedAt, _toStoredIso(remoteActivated));
      }
      return true;
    }

    return !_isExpired(localExpiresAt);
  }

  /// Aktivasyon — başarılıysa null, hata varsa Türkçe mesaj.
  static Future<String?> activateCode(String rawCode) async {
    final code = rawCode.trim().toUpperCase();
    if (code.isEmpty) return msgNotFound;

    final remote = await _fetchLicenses();
    if (remote == null) return msgNetwork;

    final entry = _findByCode(remote, code);
    if (entry == null) return msgNotFound;
    if (!_isActive(entry)) return msgDeactivated;

    final ourId = await getDeviceId();
    if (ourId.isEmpty) return msgNetwork;

    final remoteDeviceId = _readString(entry, 'device_id');
    final remoteActivated = _readString(entry, 'activated_at');
    final remoteExpires = _readString(entry, 'expires_at');

    if (_wasEverActivated(entry)) {
      final sameDevice = remoteDeviceId == ourId;
      final stillValid =
          remoteExpires.isNotEmpty && !_isExpired(remoteExpires);

      // Aynı telefon, uygulama silinip yeniden kuruldu — süre dolmamışsa geri yükle
      if (sameDevice && stillValid) {
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString(_keyCode, code);
        await prefs.setString(_keyDeviceId, ourId);
        if (remoteActivated.isNotEmpty) {
          await prefs.setString(
            _keyActivatedAt,
            _toStoredIso(remoteActivated),
          );
        }
        if (remoteExpires.isNotEmpty) {
          await prefs.setString(_keyExpiresAt, _toStoredIso(remoteExpires));
        }
        return null;
      }

      if (remoteDeviceId.isNotEmpty && remoteDeviceId != ourId) {
        return msgUsedOnOtherDevice;
      }
      return msgAlreadyUsed;
    }

    final now = DateTime.now().toUtc();
    final expiresAt = now.add(const Duration(days: licenseDurationDays));
    final activatedAtStr = _formatDateOnly(now);
    final expiresAtStr = _formatDateOnly(expiresAt);

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyCode, code);
    await prefs.setString(_keyDeviceId, ourId);
    await prefs.setString(_keyActivatedAt, now.toIso8601String());
    await prefs.setString(_keyExpiresAt, expiresAt.toUtc().toIso8601String());

    // Lokal kayıt tamam — GitHub senkronu arka planda; hata uygulamayı durdurmaz.
    unawaited(
      _syncActivationToRemote(
        code: code,
        deviceId: ourId,
        activatedAt: activatedAtStr,
        expiresAt: expiresAtStr,
      ),
    );

    return null;
  }

  /// Google Apps Script → GitHub licenses.json (GET; GAS POST redirect sorunlu).
  static Future<void> _syncActivationToRemote({
    required String code,
    required String deviceId,
    required String activatedAt,
    required String expiresAt,
  }) async {
    try {
      final uri = Uri.parse(activationScriptUrl).replace(
        queryParameters: {
          'action': 'activate',
          'code': code,
          'device_id': deviceId,
          'activated_at': activatedAt,
          'expires_at': expiresAt,
        },
      );

      final response = await http.get(uri).timeout(const Duration(seconds: 25));

      if (response.statusCode < 200 || response.statusCode >= 300) {
        if (kDebugMode) {
          debugPrint(
            '[LicenseManager] Script HTTP ${response.statusCode}: ${response.body}',
          );
        }
        return;
      }

      try {
        final decoded = jsonDecode(response.body);
        if (decoded is Map) {
          if (decoded['success'] == true) {
            if (kDebugMode) {
              debugPrint('[LicenseManager] GitHub senkron OK: $code');
            }
          } else if (kDebugMode) {
            debugPrint('[LicenseManager] Script hatası: $decoded');
          }
        }
      } catch (_) {
        if (kDebugMode) {
          debugPrint(
            '[LicenseManager] Script yanıtı JSON değil: ${response.body.length > 200 ? response.body.substring(0, 200) : response.body}',
          );
        }
      }
    } catch (e, st) {
      if (kDebugMode) {
        debugPrint('[LicenseManager] Script senkron hatası (lokal OK): $e\n$st');
      }
    }
  }

  static String _formatDateOnly(DateTime dt) {
    final y = dt.year.toString().padLeft(4, '0');
    final m = dt.month.toString().padLeft(2, '0');
    final d = dt.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }

  /// GitHub'taki YYYY-MM-DD veya ISO tarihini prefs için ISO'ya çevirir.
  static String _toStoredIso(String value) {
    final trimmed = value.trim();
    if (trimmed.contains('T')) return trimmed;
    final parts = trimmed.split('-');
    if (parts.length == 3) {
      final y = int.tryParse(parts[0]);
      final m = int.tryParse(parts[1]);
      final d = int.tryParse(parts[2]);
      if (y != null && m != null && d != null) {
        return DateTime.utc(y, m, d, 23, 59, 59).toIso8601String();
      }
    }
    return trimmed;
  }

  static Future<String> getDeviceId() async {
    try {
      final id = await NativeBridge.invoke<String>('getDeviceId');
      return id ?? '';
    } catch (_) {
      return '';
    }
  }

  static Future<int> getRemainingDays() async {
    final prefs = await SharedPreferences.getInstance();
    final expiresRaw = prefs.getString(_keyExpiresAt);
    if (expiresRaw == null || expiresRaw.isEmpty) return 0;

    final expires = DateTime.tryParse(expiresRaw);
    if (expires == null) return 0;

    final diff = expires.difference(DateTime.now().toUtc());
    if (diff.isNegative) return 0;
    return diff.inDays + (diff.inHours % 24 > 0 ? 1 : 0);
  }

  static Future<bool> isExpiringSoon() async {
    final days = await getRemainingDays();
    return days > 0 && days <= expiringSoonDays;
  }

  static Future<String?> getSavedCode() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyCode);
  }

  static Future<void> clearLicense() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyCode);
    await prefs.remove(_keyDeviceId);
    await prefs.remove(_keyActivatedAt);
    await prefs.remove(_keyExpiresAt);
  }

  static Future<List<Map<String, dynamic>>?> _fetchLicenses() async {
    try {
      final uri = Uri.parse(licensesUrl).replace(
        queryParameters: {
          '_': DateTime.now().millisecondsSinceEpoch.toString(),
        },
      );
      final response = await http
          .get(
            uri,
            headers: const {
              'Cache-Control': 'no-cache',
              'Pragma': 'no-cache',
            },
          )
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) return null;
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) return null;
      final list = decoded['licenses'];
      if (list is! List) return null;
      return list
          .whereType<Map>()
          .map((e) => Map<String, dynamic>.from(e))
          .toList();
    } catch (_) {
      return null;
    }
  }

  static Map<String, dynamic>? _findByCode(
    List<Map<String, dynamic>> licenses,
    String code,
  ) {
    for (final entry in licenses) {
      final c = _readString(entry, 'code').toUpperCase();
      if (c == code) return entry;
    }
    return null;
  }

  static bool _wasEverActivated(Map<String, dynamic> entry) {
    return _readString(entry, 'activated_at').isNotEmpty ||
        _readString(entry, 'device_id').isNotEmpty;
  }

  static bool _isActive(Map<String, dynamic> entry) {
    final active = entry['active'];
    if (active is bool) return active;
    if (active is String) return active.toLowerCase() == 'true';
    return false;
  }

  static String _readString(Map<String, dynamic> entry, String key) {
    final v = entry[key];
    if (v == null) return '';
    return v.toString().trim();
  }

  static bool _isExpired(String? iso) {
    if (iso == null || iso.isEmpty) return false;
    final dt = DateTime.tryParse(iso);
    if (dt == null) {
      // YYYY-MM-DD formatı
      final parts = iso.split('-');
      if (parts.length == 3) {
        final y = int.tryParse(parts[0]);
        final m = int.tryParse(parts[1]);
        final d = int.tryParse(parts[2]);
        if (y != null && m != null && d != null) {
          final parsed = DateTime.utc(y, m, d);
          return DateTime.now().toUtc().isAfter(parsed);
        }
      }
      return false;
    }
    return DateTime.now().toUtc().isAfter(dt.toUtc());
  }
}
