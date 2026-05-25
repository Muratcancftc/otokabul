import 'package:flutter/material.dart';
import 'package:oto_kabul/activation_screen.dart';
import 'package:oto_kabul/home_screen.dart';
import 'package:oto_kabul/license_manager.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const OtoKabulApp());
}

class OtoKabulApp extends StatelessWidget {
  const OtoKabulApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'OtoKabul',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1B8F4E),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: const _LicenseGate(),
    );
  }
}

/// Açılışta lisans kontrolü — geçerliyse Home, değilse Activation.
class _LicenseGate extends StatefulWidget {
  const _LicenseGate();

  @override
  State<_LicenseGate> createState() => _LicenseGateState();
}

class _LicenseGateState extends State<_LicenseGate> with WidgetsBindingObserver {
  bool _checking = true;
  bool _licensed = false;
  String? _revokedMessage;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _runCheck();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _runCheck(silent: _licensed);
    }
  }

  Future<void> _runCheck({bool silent = false}) async {
    if (!silent) {
      setState(() => _checking = true);
    }
    final wasLicensed = _licensed;
    final ok = await LicenseManager.checkLicense();
    if (!mounted) return;
    setState(() {
      _licensed = ok;
      _checking = false;
      if (!ok && wasLicensed) {
        _revokedMessage = LicenseManager.msgRevoked;
      } else if (ok) {
        _revokedMessage = null;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_checking) {
      return const Scaffold(
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Lisans kontrol ediliyor…'),
            ],
          ),
        ),
      );
    }

    return _licensed
        ? const HomeScreen()
        : ActivationScreen(revokedMessage: _revokedMessage);
  }
}
