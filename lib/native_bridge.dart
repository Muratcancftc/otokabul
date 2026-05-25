import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';

/// Android MethodChannel — web önizlemede stub döner.
class NativeBridge {
  NativeBridge._();

  static const channel = MethodChannel('com.otokabul/service');

  static Future<T?> invoke<T>(String method, [dynamic arguments]) async {
    if (kIsWeb) return _webStub<T>(method, arguments);
    return channel.invokeMethod<T>(method, arguments);
  }

  static T? _webStub<T>(String method, dynamic arguments) {
    switch (method) {
      case 'isServiceRunning':
      case 'isAccessibilityEnabled':
        return false as T?;
      case 'isIgnoringBatteryOptimizations':
        return true as T?;
      case 'getRecentLogs':
        return <dynamic>[] as T?;
      case 'getDeviceId':
        return 'web-onizleme' as T?;
      case 'setMinKm':
      case 'start':
      case 'stop':
      case 'requestIgnoreBatteryOptimizations':
        return null;
      default:
        return null;
    }
  }
}
