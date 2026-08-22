import 'package:bible/app_theme.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('bible/android');

  setUp(() {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
  });

  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('loads each Android screen corner independently', () async {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getDeviceRoundedCorners');
      return <String, Object>{
        'available': true,
        'topLeftDp': 40.0,
        'topRightDp': 36.0,
        'bottomLeftDp': 16.0,
        'bottomRightDp': 0.0,
      };
    });

    final radii = await AppRadii.load();

    expect(radii.topLeft, 40);
    expect(radii.topRight, 36);
    expect(radii.bottomLeft, 16);
    expect(radii.bottomRight, 0);
    expect(radii.screen, 40);
    expect(radii.compact, closeTo(9.6, .001));
    expect(radii.control, closeTo(13.6, .001));
    expect(radii.surface, closeTo(19.2, .001));
  });

  test('falls back when rounded-corner geometry is unavailable', () async {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (_) async => <String, Object>{
        'available': false,
        'topLeftDp': 0.0,
        'topRightDp': 0.0,
        'bottomLeftDp': 0.0,
        'bottomRightDp': 0.0,
      },
    );

    final radii = await AppRadii.load();

    expect(radii.screen, AppRadii.fallback.screen);
    expect(radii.topLeft, AppRadii.fallback.topLeft);
    expect(radii.topRight, AppRadii.fallback.topRight);
    expect(radii.bottomLeft, AppRadii.fallback.bottomLeft);
    expect(radii.bottomRight, AppRadii.fallback.bottomRight);
  });

  test('supports the former single-radius native response', () async {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (_) async => <String, Object>{'available': true, 'radiusDp': 32.0},
    );

    final radii = await AppRadii.load();

    expect(radii.screen, 32);
    expect(radii.topLeft, 32);
    expect(radii.topRight, 32);
    expect(radii.bottomLeft, 32);
    expect(radii.bottomRight, 32);
  });
}
