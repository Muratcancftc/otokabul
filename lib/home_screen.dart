import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:oto_kabul/activation_screen.dart';
import 'package:oto_kabul/license_manager.dart';
import 'package:oto_kabul/native_bridge.dart';
import 'package:oto_kabul/test_runner.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// BiTaksi otomatik kabul arayüzü — tüm işlem telefonda lokal.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const _channel = NativeBridge.channel;
  static const _prefsKeyMinKm = 'min_km';
  static const _prefsKeyBatteryAsked = 'battery_opt_asked';

  double _minKm = 5;
  bool _serviceRunning = false;
  bool _accessibilityEnabled = false;
  int _licenseDaysLeft = 0;
  bool _licenseExpiringSoon = false;
  final List<_LogEntry> _logs = [];
  Timer? _statusTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _channel.setMethodCallHandler(_onNativeCall);
    _loadSettings();
    _refreshStatus();
    _statusTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      if (WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        _refreshStatus();
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _maybeRequestBatteryExemption();
      _syncRecentLogs();
      _loadLicenseInfo();
    });
  }

  Future<void> _verifyLicenseStillValid() async {
    final ok = await LicenseManager.checkLicense();
    if (!mounted || ok) return;
    _goToActivationRevoked();
  }

  Future<void> _goToActivationRevoked() async {
    await LicenseManager.clearLicense();
    if (!mounted) return;
    await Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(
        builder: (_) => const ActivationScreen(
          revokedMessage: LicenseManager.msgRevoked,
        ),
      ),
      (_) => false,
    );
  }

  Future<void> _loadLicenseInfo() async {
    final days = await LicenseManager.getRemainingDays();
    final soon = await LicenseManager.isExpiringSoon();
    if (!mounted) return;
    setState(() {
      _licenseDaysLeft = days;
      _licenseExpiringSoon = soon;
    });
  }

  @override
  void dispose() {
    _statusTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _verifyLicenseStillValid();
      _refreshStatus();
      _syncRecentLogs();
      _loadLicenseInfo();
    }
    // Uygulama tamamen kapatıldıysa native taraf servisi durdurur; UI senkronu
    if (state == AppLifecycleState.detached) {
      _refreshStatus();
    }
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getDouble(_prefsKeyMinKm) ?? 5.0;
    setState(() => _minKm = saved.clamp(1, 30));
    await NativeBridge.invoke('setMinKm', {'km': _minKm});
  }

  Future<void> _saveMinKm(double km) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_prefsKeyMinKm, km);
    await NativeBridge.invoke('setMinKm', {'km': km});
  }

  Future<void> _refreshStatus() async {
    try {
      final running =
          await NativeBridge.invoke<bool>('isServiceRunning') ?? false;
      final a11y = await NativeBridge.invoke<bool>('isAccessibilityEnabled') ??
          false;
      if (mounted) {
        setState(() {
          _serviceRunning = running;
          _accessibilityEnabled = a11y;
        });
      }
    } catch (_) {}
  }

  Future<void> _maybeRequestBatteryExemption() async {
    if (kIsWeb) return;
    final prefs = await SharedPreferences.getInstance();
    if (prefs.getBool(_prefsKeyBatteryAsked) == true) return;

    try {
      final alreadyIgnored =
          await NativeBridge.invoke<bool>('isIgnoringBatteryOptimizations') ??
              false;
      if (alreadyIgnored) {
        await prefs.setBool(_prefsKeyBatteryAsked, true);
        return;
      }
    } catch (_) {}

    if (!mounted) return;

    final allow = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Text('Pil optimizasyonu'),
        content: const Text(
          'Arka planda çalışmak için pil optimizasyonunu '
          'kapatmamıza izin verin',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Sonra'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('İzin ver'),
          ),
        ],
      ),
    );

    await prefs.setBool(_prefsKeyBatteryAsked, true);

    if (allow == true && mounted) {
      try {
        await NativeBridge.invoke('requestIgnoreBatteryOptimizations');
      } catch (_) {}
    }
  }

  Future<void> _syncRecentLogs() async {
    try {
      final raw =
          await NativeBridge.invoke<List<dynamic>>('getRecentLogs') ?? [];
      if (!mounted) return;
      setState(() {
        _logs
          ..clear()
          ..addAll(
            raw.map((e) {
              final m = e as Map<dynamic, dynamic>;
              return _LogEntry(
                time: m['time'] as String? ?? '',
                km: (m['km'] as num?)?.toDouble() ?? 0,
                accepted: m['accepted'] as bool? ?? false,
                minKm: (m['minKm'] as num?)?.toDouble() ?? _minKm,
                reason: m['reason'] as String? ?? '',
                earningMin: (m['earning_min'] as num?)?.toInt(),
                earningMax: (m['earning_max'] as num?)?.toInt(),
              );
            }),
          );
      });
    } catch (_) {}
  }

  void _addLogEntry(_LogEntry entry) {
    if (!mounted) return;
    setState(() {
      final dup = _logs.any(
        (l) => l.time == entry.time && l.km == entry.km && l.reason == entry.reason,
      );
      if (dup) return;
      _logs.insert(0, entry);
      if (_logs.length > 20) _logs.removeLast();
    });
  }

  Future<dynamic> _onNativeCall(MethodCall call) async {
    if (call.method == 'onLicenseRevoked') {
      _goToActivationRevoked();
      return;
    }
    if (call.method == 'onLog') {
      final args = call.arguments as Map<dynamic, dynamic>;
      _addLogEntry(
        _LogEntry(
          time: args['time'] as String? ?? '',
          km: (args['km'] as num?)?.toDouble() ?? 0,
          accepted: args['accepted'] as bool? ?? false,
          minKm: (args['minKm'] as num?)?.toDouble() ?? _minKm,
          reason: args['reason'] as String? ?? '',
          earningMin: (args['earning_min'] as num?)?.toInt(),
          earningMax: (args['earning_max'] as num?)?.toInt(),
        ),
      );
    }
  }

  Future<void> _toggleService() async {
    try {
      if (_serviceRunning) {
        await NativeBridge.invoke('stop');
      } else {
        await NativeBridge.invoke('start');
      }
      await _refreshStatus();
    } on PlatformException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Hata: ${e.message}')),
      );
    }
  }

  String _formatKm(double km) {
    return km.toStringAsFixed(2).replaceAll('.', ',');
  }

  @override
  Widget build(BuildContext context) {
    final active = _serviceRunning && _accessibilityEnabled;

    return Scaffold(
      appBar: AppBar(
        title: const Text('OtoKabul 🚕'),
        centerTitle: true,
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(
              child: Text(
                '🔑 $_licenseDaysLeft gün kaldı',
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
            ),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (kIsWeb) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.blue.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.blue.shade300),
                ),
                child: Text(
                  'Web önizleme — otomatik kabul yalnızca Android\'de çalışır.',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Colors.blue.shade900,
                        fontWeight: FontWeight.w600,
                      ),
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: 12),
            ],
            if (_licenseExpiringSoon) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.amber.shade100,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.amber.shade700),
                ),
                child: Text(
                  '⚠️ Lisansınız $_licenseDaysLeft gün sonra doluyor!',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Colors.amber.shade900,
                        fontWeight: FontWeight.w600,
                      ),
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: 12),
            ],
            _StatusCard(
              serviceRunning: _serviceRunning,
              accessibilityEnabled: _accessibilityEnabled,
              active: active,
            ),
            const SizedBox(height: 20),
            Text(
              'Minimum yolculuk mesafesi: ${_minKm.toStringAsFixed(0)} km',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            Text(
              'Karar: "8 dk • … km" satırı ≥ min km. İlk test için min km\'yi 2–3 yapın '
              '(5 km çoğu teklifi bilerek atlar). Loglar arka planda da kaydedilir.',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Colors.grey.shade600,
                  ),
            ),
            Slider(
              value: _minKm,
              min: 1,
              max: 30,
              divisions: 29,
              label: '${_minKm.toStringAsFixed(0)} km',
              onChanged: (v) {
                setState(() => _minKm = v);
                _saveMinKm(v);
              },
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const TestRunnerScreen(runOnOpen: true),
                  ),
                );
              },
              icon: const Text('🧪'),
              label: const Text('Testleri Çalıştır'),
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 64,
              child: FilledButton(
                onPressed: _toggleService,
                style: FilledButton.styleFrom(
                  backgroundColor:
                      _serviceRunning ? Colors.red.shade700 : Colors.green.shade700,
                  foregroundColor: Colors.white,
                  textStyle: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                child: Text(_serviceRunning ? 'DURDUR' : 'BAŞLAT'),
              ),
            ),
            if (!_accessibilityEnabled) ...[
              const SizedBox(height: 12),
              Text(
                'Ayarlar → Erişilebilirlik → OtoKabul servisini açın',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Colors.orange.shade800,
                    ),
                textAlign: TextAlign.center,
              ),
            ],
            if (_serviceRunning) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.blue.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.blue.shade300),
                ),
                child: Text(
                  'Arka planda çalışır (Ana ekran / BiTaksi). '
                  'OtoKabul\'ü görev listesinden silseniz bile servis çalışır. '
                  'Durdurmak için DURDUR.',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Colors.blue.shade900,
                      ),
                ),
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.amber.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.amber.shade700),
                ),
                child: Row(
                  children: [
                    Icon(Icons.light_mode, color: Colors.amber.shade900),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'Ekranı açık tutun! Telefon cebine girince veya ekran kapanınca teklifler okunamaz.',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Colors.amber.shade900,
                              fontWeight: FontWeight.w500,
                            ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 16),
            Text(
              'Son işlemler',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            const SizedBox(height: 8),
            Expanded(
              child: _logs.isEmpty
                  ? Center(
                      child: Text(
                        'Henüz kayıt yok',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Colors.grey,
                            ),
                      ),
                    )
                  : ListView.builder(
                      itemCount: _logs.length,
                      itemBuilder: (context, index) {
                        final log = _logs[index];
                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Text(
                            log.format(_formatKm),
                            style: const TextStyle(fontSize: 14),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LogEntry {
  final String time;
  final double km;
  final bool accepted;
  final double minKm;
  final String reason;
  final int? earningMin;
  final int? earningMax;

  _LogEntry({
    required this.time,
    required this.km,
    required this.accepted,
    required this.minKm,
    this.reason = '',
    this.earningMin,
    this.earningMax,
  });

  String _earningLabel() {
    if (earningMin != null && earningMax != null) {
      return '₺$earningMin-$earningMax';
    }
    return '';
  }

  String format(String Function(double) fmtKm) {
    final kmStr = fmtKm(km);
    final earning = _earningLabel();
    final earningPart = earning.isNotEmpty ? ' - $earning' : '';
    if (reason == 'click_failed') {
      return '⚠️ $time - $kmStr km$earningPart - BUTONA BASILAMADI (min: ${minKm.toStringAsFixed(0)}km)';
    }
    if (reason == 'no_km') {
      return '⚠️ $time - KM OKUNAMADI — teklif kartı görüldü, erişilebilirlik metni eksik';
    }
    if (accepted) {
      return '✅ $time - $kmStr km$earningPart - KABUL EDİLDİ';
    }
    if (reason == 'skipped') {
      return '⏭️ $time - $kmStr km$earningPart - ATLANDI (min: ${minKm.toStringAsFixed(0)}km) — kısa tik sesi';
    }
    return '❌ $time - $kmStr km$earningPart - ATLANDI (min: ${minKm.toStringAsFixed(0)}km)';
  }
}

class _StatusCard extends StatelessWidget {
  final bool serviceRunning;
  final bool accessibilityEnabled;
  final bool active;

  const _StatusCard({
    required this.serviceRunning,
    required this.accessibilityEnabled,
    required this.active,
  });

  @override
  Widget build(BuildContext context) {
    final accent = active ? Colors.green : Colors.grey;
    final iconColor = active ? Colors.green.shade700 : Colors.grey.shade600;
    final statusText = active
        ? 'Aktif — teklifler izleniyor'
        : serviceRunning && !accessibilityEnabled
            ? 'Servis çalışıyor — erişilebilirlik kapalı'
            : 'Pasif';

    return Card(
      color: accent.withValues(alpha: 0.12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Icon(
              active ? Icons.check_circle : Icons.pause_circle_outline,
              color: iconColor,
              size: 36,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Servis durumu',
                    style: Theme.of(context).textTheme.labelMedium,
                  ),
                  Text(
                    statusText,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
