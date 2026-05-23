import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:oto_kabul/test_runner.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// BiTaksi otomatik kabul arayüzü — tüm işlem telefonda lokal.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const _channel = MethodChannel('com.otokabul/service');
  static const _prefsKeyMinKm = 'min_km';
  static const _prefsKeyBatteryAsked = 'battery_opt_asked';

  double _minKm = 5;
  bool _serviceRunning = false;
  bool _accessibilityEnabled = false;
  final List<_LogEntry> _logs = [];
  Timer? _statusTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _channel.setMethodCallHandler(_onNativeCall);
    _loadSettings();
    _refreshStatus();
    _statusTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      _refreshStatus();
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _maybeRequestBatteryExemption();
      _syncRecentLogs();
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
      _refreshStatus();
      _syncRecentLogs();
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
    await _channel.invokeMethod('setMinKm', {'km': _minKm});
  }

  Future<void> _saveMinKm(double km) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_prefsKeyMinKm, km);
    await _channel.invokeMethod('setMinKm', {'km': km});
  }

  Future<void> _refreshStatus() async {
    try {
      final running =
          await _channel.invokeMethod<bool>('isServiceRunning') ?? false;
      final a11y = await _channel.invokeMethod<bool>('isAccessibilityEnabled') ??
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
    final prefs = await SharedPreferences.getInstance();
    if (prefs.getBool(_prefsKeyBatteryAsked) == true) return;

    try {
      final alreadyIgnored =
          await _channel.invokeMethod<bool>('isIgnoringBatteryOptimizations') ??
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
        await _channel.invokeMethod('requestIgnoreBatteryOptimizations');
      } catch (_) {}
    }
  }

  Future<void> _syncRecentLogs() async {
    try {
      final raw =
          await _channel.invokeMethod<List<dynamic>>('getRecentLogs') ?? [];
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
    if (call.method == 'onLog') {
      final args = call.arguments as Map<dynamic, dynamic>;
      _addLogEntry(
        _LogEntry(
          time: args['time'] as String? ?? '',
          km: (args['km'] as num?)?.toDouble() ?? 0,
          accepted: args['accepted'] as bool? ?? false,
          minKm: (args['minKm'] as num?)?.toDouble() ?? _minKm,
          reason: args['reason'] as String? ?? '',
        ),
      );
    }
  }

  Future<void> _toggleService() async {
    try {
      if (_serviceRunning) {
        await _channel.invokeMethod('stop');
      } else {
        await _channel.invokeMethod('start');
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
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
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

  _LogEntry({
    required this.time,
    required this.km,
    required this.accepted,
    required this.minKm,
    this.reason = '',
  });

  String format(String Function(double) fmtKm) {
    final kmStr = fmtKm(km);
    if (reason == 'click_failed') {
      return '⚠️ $time - $kmStr km - BUTONA BASILAMADI (min: ${minKm.toStringAsFixed(0)}km)';
    }
    if (reason == 'no_km') {
      return '⚠️ $time - KM OKUNAMADI — teklif kartı görüldü, erişilebilirlik metni eksik';
    }
    if (accepted) {
      return '✅ $time - $kmStr km - KABUL EDİLDİ';
    }
    return '❌ $time - $kmStr km - ATLANDI (min: ${minKm.toStringAsFixed(0)}km)';
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
