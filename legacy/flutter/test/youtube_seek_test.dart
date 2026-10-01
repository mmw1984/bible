import 'package:bible/devotion_youtube_player.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('clampSeekTarget', () {
    test('seeks forward within bounds', () {
      expect(
        clampSeekTarget(current: 30, total: 300, delta: 10),
        40,
      );
    });

    test('seeks backward within bounds', () {
      expect(
        clampSeekTarget(current: 30, total: 300, delta: -10),
        20,
      );
    });

    test('clamps at the end of the video', () {
      expect(
        clampSeekTarget(current: 295, total: 300, delta: 10),
        300,
      );
    });

    test('clamps at the start of the video', () {
      expect(
        clampSeekTarget(current: 5, total: 300, delta: -10),
        0,
      );
    });

    test('stacked double-taps accumulate then clamp', () {
      var position = 290.0;
      for (var i = 0; i < 3; i++) {
        position = clampSeekTarget(
          current: position,
          total: 300,
          delta: 10,
        );
      }
      expect(position, 300);
    });

    test('unknown duration yields 0 instead of NaN', () {
      expect(clampSeekTarget(current: 10, total: 0, delta: 10), 0);
    });
  });
}
