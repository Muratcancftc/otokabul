import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// OtoKabul self-test ekranı — tüm kontroller telefonda lokal çalışır.
class TestRunnerScreen extends StatefulWidget {
  const TestRunnerScreen({super.key, this.runOnOpen = true});

  /// true ise ekran açılınca testler otomatik başlar.
  final bool runOnOpen;

  @override
  State<TestRunnerScreen> createState() => _TestRunnerScreenState();
}

enum _TestStatus { pending, running, passed, failed, warning }

class _TestItem {
  _TestItem({
    required this.id,
    required this.title,
  });

  final int id;
  final String title;
  _TestStatus status = _TestStatus.pending;
  String detail = '';
}

class _TestRunnerScreenState extends State<TestRunnerScreen> {
  static const _testChannel = MethodChannel('com.otokabul/test');

  final List<_TestItem> _tests = [
    _TestItem(id: 1, title: 'TEST 1: Accessibility Service izni'),
    _TestItem(id: 2, title: 'TEST 2: Bildirim izni'),
    _TestItem(id: 3, title: 'TEST 3: Foreground Service'),
    _TestItem(id: 4, title: 'TEST 4: SharedPreferences'),
    _TestItem(
      id: 5,
      title: 'TEST 5: Km parse (4 senaryo + index 1)',
    ),
    _TestItem(
      id: 6,
      title: 'TEST 6: Min km (2. değer, yolculuk mesafesi)',
    ),
    _TestItem(id: 7, title: 'TEST 7: Rastgele gecikme (200–500 ms)'),
    _TestItem(id: 8, title: 'TEST 8: Wake Lock izni'),
    _TestItem(id: 9, title: 'TEST 9: BiTaksi kurulu mu?'),
    _TestItem(id: 10, title: 'TEST 10: Çift basma koruması'),
    _TestItem(
      id: 11,
      title: 'TEST 11: Lisans uzaktan iptal kontrolü',
    ),
  ];

  bool _isRunning = false;
  String? _summary;

  @override
  void initState() {
    super.initState();
    if (widget.runOnOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _runAllTests());
    }
  }

  Future<void> _runAllTests() async {
    if (_isRunning) return;
    setState(() {
      _isRunning = true;
      _summary = null;
      for (final t in _tests) {
        t.status = _TestStatus.pending;
        t.detail = '';
      }
    });

    await _runTest(0, _test1Accessibility);
    await _runTest(1, _test2Notification);
    await _runTest(2, _test3ForegroundService);
    await _runTest(3, _test4SharedPreferences);
    await _runTest(4, _test5KmParse);
    await _runTest(5, _test6MinKm);
    await _runTest(6, _test7RandomDelay);
    await _runTest(7, _test8WakeLock);
    await _runTest(8, _test9Bitaksi);
    await _runTest(9, _test10DoubleTap);
    await _runTest(10, _test11LicenseRemote);

    if (!mounted) return;
    final passed = _tests.where((t) => t.status == _TestStatus.passed).length;
    final warnings = _tests.where((t) => t.status == _TestStatus.warning).length;
    final failed = _tests.where((t) => t.status == _TestStatus.failed).length;

    setState(() {
      _isRunning = false;
      if (failed == 0 && warnings == 0) {
        _summary = '✅ $passed/${_tests.length} test geçti';
      } else if (failed == 0) {
        _summary = '✅ $passed/${_tests.length} test geçti, ⚠️ $warnings uyarı';
      } else {
        _summary =
            '✅ $passed/${_tests.length} geçti, ❌ $failed başarısız${warnings > 0 ? ', ⚠️ $warnings uyarı' : ''}';
      }
    });
  }

  Future<void> _runTest(int index, Future<void> Function() testFn) async {
    setState(() {
      _tests[index].status = _TestStatus.running;
      _tests[index].detail = '⏳ Test çalışıyor...';
    });
    await Future<void>.delayed(const Duration(milliseconds: 120));
    await testFn();
    if (mounted) setState(() {});
  }

  Future<bool> _invokeBool(String method) async {
    final result = await _testChannel.invokeMethod<bool>(method);
    return result ?? false;
  }

  void _pass(int index, String detail) {
    _tests[index].status = _TestStatus.passed;
    _tests[index].detail = detail;
  }

  void _fail(int index, String detail) {
    _tests[index].status = _TestStatus.failed;
    _tests[index].detail = detail;
  }

  void _warn(int index, String detail) {
    _tests[index].status = _TestStatus.warning;
    _tests[index].detail = detail;
  }

  Future<void> _test1Accessibility() async {
    try {
      final ok = await _invokeBool('testAccessibility');
      if (ok) {
        _pass(0, '✅ AKTİF');
      } else {
        _fail(0, '❌ İZİN YOK');
      }
    } catch (e) {
      _fail(0, '❌ HATA: $e');
    }
  }

  Future<void> _test2Notification() async {
    try {
      final ok = await _invokeBool('testNotification');
      if (ok) {
        _pass(1, '✅ VAR');
      } else {
        _fail(1, '❌ YOK');
      }
    } catch (e) {
      _fail(1, '❌ HATA: $e');
    }
  }

  Future<void> _test3ForegroundService() async {
    try {
      final ok = await _invokeBool('testForegroundService');
      if (ok) {
        _pass(2, '✅ ÇALIŞIYOR');
      } else {
        _fail(2, '❌ DURDU (BAŞLAT ile servisi açın)');
      }
    } catch (e) {
      _fail(2, '❌ HATA: $e');
    }
  }

  Future<void> _test4SharedPreferences() async {
    try {
      final ok = await _invokeBool('testSharedPreferences');
      if (ok) {
        _pass(3, '✅ ÇALIŞIYOR (5.0 yaz/oku OK)');
      } else {
        _fail(3, '❌ HATA');
      }
    } catch (e) {
      _fail(3, '❌ HATA: $e');
    }
  }

  Future<void> _test5KmParse() async {
    try {
      final raw = await _testChannel.invokeMethod<Map<dynamic, dynamic>>('testKmParse');
      final map = raw ?? {};
      final minKm = (map['minKm'] as num?)?.toDouble() ?? 0;
      final allOk = map['allPass'] == true;
      final lines = <String>[
        'Mevcut min km: ${_formatKmSetting(minKm)}',
        '"8 dk • … km" satırından yolculuk km alınıyor',
        '',
      ];

      void check(String input, double? expected) {
        final ok = map[input] == true;
        final exp = expected == null ? 'null' : expected.toString();
        lines.add('"$input" → $exp ${ok ? '✅' : '❌'}');
      }

      check('2,56 km', 2.56);
      check('2.56 km', 2.56);
      check('10,5 km', 10.5);
      check('abc km', null);

      final journeyOk = map['journey_row_km'] == true;
      final ignoresPickup = map['ignores_pickup_km'] == true;
      lines.add(
        '8 dk satırı → 2,24 km ${journeyOk ? '✅' : '❌'}',
      );
      lines.add(
        '7 dk satırı (2,56) yok sayılıyor ${ignoresPickup ? '✅' : '❌'}',
      );

      final offerOk = map['trip_offer_screen'] == true;
      final otherRejected = map['other_screen_rejected'] == true;
      lines.add('Teklif kartı tanıma ${offerOk ? '✅' : '❌'}');
      lines.add('Diğer ekran reddi ${otherRejected ? '✅' : '❌'}');

      final detail = lines.join('\n');
      if (allOk) {
        _pass(4, detail);
      } else {
        _fail(4, detail);
      }
    } catch (e) {
      _fail(4, '❌ HATA: $e');
    }
  }

  Future<void> _test6MinKm() async {
    try {
      final raw =
          await _testChannel.invokeMethod<Map<dynamic, dynamic>>('testMinKmComparison');
      final map = raw ?? {};
      final minKm = (map['minKm'] as num?)?.toDouble() ?? 0;
      final allOk = map['allPass'] == true;
      final atlaKm = (map['atla_km'] as num?)?.toDouble() ?? 0;
      final kabulKm = (map['kabul_km'] as num?)?.toDouble() ?? 0;
      final esitKm = (map['esit_km'] as num?)?.toDouble() ?? minKm;

      final lines = <String>[
        'Mevcut ayar: ${_formatKmSetting(minKm)}',
        '${_formatKmSetting(atlaKm)} geldi (min-1) → ATLA ${map['atla_ok'] == true ? '✅' : '❌'}',
        '${_formatKmSetting(kabulKm)} geldi (min+2) → KABUL ${map['kabul_plus_ok'] == true ? '✅' : '❌'}',
        '${_formatKmSetting(esitKm)} geldi (eşit) → KABUL ${map['esit_ok'] == true ? '✅' : '❌'}',
      ];

      final detail = lines.join('\n');
      if (allOk) {
        _pass(5, detail);
      } else {
        _fail(5, detail);
      }
    } catch (e) {
      _fail(5, '❌ HATA: $e');
    }
  }

  String _formatKmSetting(double km) {
    final s = km.toStringAsFixed(2).replaceAll('.', ',');
    if (s.endsWith(',00')) return '${km.toStringAsFixed(0)}km';
    return '${s}km';
  }

  Future<void> _test7RandomDelay() async {
    try {
      final ok = await _invokeBool('testRandomDelay');
      if (ok) {
        _pass(6, '✅ DOĞRU (10/10 değer 200–500 ms aralığında)');
      } else {
        _fail(6, '❌ ARALIK DIŞI');
      }
    } catch (e) {
      _fail(6, '❌ HATA: $e');
    }
  }

  Future<void> _test8WakeLock() async {
    try {
      final ok = await _invokeBool('testWakeLock');
      if (ok) {
        _pass(7, '✅ VAR');
      } else {
        _fail(7, '❌ YOK');
      }
    } catch (e) {
      _fail(7, '❌ HATA: $e');
    }
  }

  Future<void> _test9Bitaksi() async {
    try {
      final ok = await _invokeBool('testBitaksiInstalled');
      if (ok) {
        _pass(8, '✅ KURULU (com.projectslender)');
      } else {
        _warn(8, '⚠️ KURULU DEĞİL (test ortamı)');
      }
    } catch (e) {
      _fail(8, '❌ HATA: $e');
    }
  }

  Future<void> _test10DoubleTap() async {
    try {
      final ok = await _invokeBool('testDoubleTapProtection');
      if (ok) {
        _pass(9, '✅ KORUMALI (2500 ms içinde 2. event atlanır)');
      } else {
        _fail(9, '❌ ÇİFT BASMA RİSKİ');
      }
    } catch (e) {
      _fail(9, '❌ HATA: $e');
    }
  }

  Future<void> _test11LicenseRemote() async {
    try {
      final raw = await _testChannel.invokeMethod<Map<dynamic, dynamic>>(
        'testLicenseRemoteRevoke',
      );
      final map = raw ?? {};
      final code = map['code']?.toString() ?? '';
      final active = map['active'] == true;
      final status = map['status']?.toString() ?? '';
      final message = map['message']?.toString() ?? '';
      final allOk = map['allPass'] == true;

      final lines = <String>[
        if (code.isEmpty) 'Yerel kod yok — önce aktivasyon yapın' else 'Kod: $code',
        'GitHub active: ${active ? 'true' : 'false'}',
        'Durum: $status',
        message,
      ];

      final detail = lines.join('\n');
      if (code.isEmpty) {
        _warn(10, detail);
      } else if (allOk) {
        _pass(10, detail);
      } else {
        _fail(10, detail);
      }
    } catch (e) {
      _fail(10, '❌ HATA: $e');
    }
  }

  Color _statusColor(_TestStatus status) {
    switch (status) {
      case _TestStatus.passed:
        return Colors.green.shade800;
      case _TestStatus.failed:
        return Colors.red.shade800;
      case _TestStatus.warning:
        return Colors.orange.shade800;
      case _TestStatus.running:
        return Colors.blue.shade800;
      case _TestStatus.pending:
        return Colors.grey.shade600;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Self-Test 🧪'),
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
      ),
      body: Column(
        children: [
          if (_summary != null)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: Text(
                _summary!,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                textAlign: TextAlign.center,
              ),
            ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: _tests.length,
              itemBuilder: (context, index) {
                final test = _tests[index];
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          test.title,
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            color: _statusColor(test.status),
                          ),
                        ),
                        if (test.detail.isNotEmpty) ...[
                          const SizedBox(height: 6),
                          Text(
                            test.detail,
                            style: TextStyle(
                              fontSize: 13,
                              color: _statusColor(test.status),
                              height: 1.35,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton.icon(
                  onPressed: _isRunning ? null : _runAllTests,
                  icon: const Icon(Icons.refresh),
                  label: Text(
                    _isRunning ? 'Testler çalışıyor...' : 'Testleri Tekrar Çalıştır',
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
