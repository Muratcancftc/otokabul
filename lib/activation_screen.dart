import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:oto_kabul/home_screen.dart';
import 'package:oto_kabul/license_manager.dart';

/// İlk açılış / lisans geçersiz — aktivasyon kodu girişi.
class ActivationScreen extends StatefulWidget {
  const ActivationScreen({super.key, this.revokedMessage});

  /// Panelden pasife alınınca veya uzaktan iptal edilince gösterilir.
  final String? revokedMessage;

  @override
  State<ActivationScreen> createState() => _ActivationScreenState();
}

class _ActivationScreenState extends State<ActivationScreen> {
  final _controller = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _activate() async {
    final code = _controller.text.trim();
    if (code.isEmpty) {
      setState(() => _error = LicenseManager.msgNotFound);
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    final err = await LicenseManager.activateCode(code);

    if (!mounted) return;

    if (err != null) {
      setState(() {
        _loading = false;
        _error = err;
      });
      return;
    }

    Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(builder: (_) => const HomeScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 32),
              Text(
                'OtoKabul 🚕',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 8),
              Text(
                'Devam etmek için lisans kodunuzu girin',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Colors.grey.shade600,
                    ),
              ),
              const SizedBox(height: 40),
              if (widget.revokedMessage != null) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.red.shade50,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.red.shade300),
                  ),
                  child: Text(
                    widget.revokedMessage!,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Colors.red.shade900,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                ),
                const SizedBox(height: 20),
              ],
              TextField(
                controller: _controller,
                textCapitalization: TextCapitalization.characters,
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9]')),
                  _UpperCaseFormatter(),
                ],
                decoration: InputDecoration(
                  labelText: 'Lisans kodu',
                  hintText: 'TKTEST01',
                  border: const OutlineInputBorder(),
                  errorText: _error,
                ),
                enabled: !_loading,
                onSubmitted: (_) => _activate(),
              ),
              const SizedBox(height: 24),
              SizedBox(
                height: 52,
                child: FilledButton(
                  onPressed: _loading ? null : _activate,
                  child: _loading
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text(
                          'Aktive Et',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
              ),
              const Spacer(),
              Text(
                'Kod GitHub üzerinden doğrulanır.\n'
                'İnternet bağlantısı gereklidir.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Colors.grey.shade500,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UpperCaseFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    return TextEditingValue(
      text: newValue.text.toUpperCase(),
      selection: newValue.selection,
    );
  }
}
