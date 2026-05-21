import 'package:flutter_test/flutter_test.dart';
import 'package:oto_kabul/main.dart';

void main() {
  testWidgets('OtoKabul uygulaması açılır', (WidgetTester tester) async {
    await tester.pumpWidget(const OtoKabulApp());
    expect(find.text('OtoKabul 🚕'), findsOneWidget);
  });
}
