import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppLocale { zhHant, en }

enum AppNavBarStyle { material, materialBlur }

extension AppLocaleValue on AppLocale {
  Locale get locale => switch (this) {
    AppLocale.zhHant => const Locale.fromSubtags(
      languageCode: 'zh',
      scriptCode: 'Hant',
    ),
    AppLocale.en => const Locale('en'),
  };

  String get storageValue => switch (this) {
    AppLocale.zhHant => 'zh-Hant',
    AppLocale.en => 'en',
  };

  String get aiLanguage => switch (this) {
    AppLocale.zhHant => 'natural Traditional Chinese',
    AppLocale.en => 'natural English',
  };
}

class AppSettingsState {
  const AppSettingsState({
    required this.themeMode,
    required this.locale,
    this.navbarStyle = AppNavBarStyle.materialBlur,
    this.glassPerfBlocked = false,
    this.showNavbar = true,
    this.showDevotion = true,
  });

  final ThemeMode themeMode;
  final AppLocale locale;
  final AppNavBarStyle navbarStyle;

  /// Set when the liquid-glass capture pipeline measured sustained jank and
  /// the runtime fell back to a lighter style. The user can force the glass
  /// style back on by picking it again in Settings.
  final bool glassPerfBlocked;

  /// Whether the floating bottom navigation bar is visible. When hidden the
  /// reader top bar exposes Ask/Devotion shortcuts instead.
  final bool showNavbar;

  /// Whether the 靈修默想 (devotion) tab is offered at all.
  final bool showDevotion;
}

class AppSettingsController extends ChangeNotifier {
  AppSettingsController();

  static final instance = AppSettingsController();
  static const _themeKey = 'app_theme_mode';
  static const _localeKey = 'app_locale';
  static const _navbarStyleKey = 'navbar_style';
  static const _glassBlockedKey = 'glass_perf_blocked';
  static const _showNavbarKey = 'show_navbar';
  static const _showDevotionKey = 'show_devotion';

  AppSettingsState state = const AppSettingsState(
    themeMode: ThemeMode.light,
    locale: AppLocale.zhHant,
  );
  bool initialized = false;

  Future<void> initialize() async {
    if (initialized) return;
    await reload(notify: false);
    initialized = true;
  }

  Future<void> reload({bool notify = true}) async {
    final preferences = await SharedPreferences.getInstance();
    final storedTheme = preferences.getString(_themeKey);
    final legacyDark = preferences.getBool('appearance_dark');
    final themeMode = switch (storedTheme) {
      'dark' => ThemeMode.dark,
      'light' => ThemeMode.light,
      _ =>
        (legacyDark ??
                PlatformDispatcher.instance.platformBrightness ==
                    Brightness.dark)
            ? ThemeMode.dark
            : ThemeMode.light,
    };
    final locale = switch (preferences.getString(_localeKey)) {
      'en' => AppLocale.en,
      _ => AppLocale.zhHant,
    };
    final navbarStyle = switch (preferences.getString(_navbarStyleKey)) {
      'material' => AppNavBarStyle.material,
      _ => AppNavBarStyle.materialBlur,
    };
    final next = AppSettingsState(
      themeMode: themeMode,
      locale: locale,
      navbarStyle: navbarStyle,
      glassPerfBlocked: preferences.getBool(_glassBlockedKey) ?? false,
      showNavbar: preferences.getBool(_showNavbarKey) ?? true,
      showDevotion: preferences.getBool(_showDevotionKey) ?? true,
    );
    final changed = state.themeMode != next.themeMode ||
        state.locale != next.locale ||
        state.navbarStyle != next.navbarStyle ||
        state.glassPerfBlocked != next.glassPerfBlocked ||
        state.showNavbar != next.showNavbar ||
        state.showDevotion != next.showDevotion;
    state = next;
    if (notify && changed) notifyListeners();
  }

  Future<void> setThemeMode(ThemeMode value) async {
    state = _copy(themeMode: value);
    notifyListeners();
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(_themeKey, value.name);
    await preferences.setBool('appearance_dark', value == ThemeMode.dark);
  }

  Future<void> setLocale(AppLocale value) async {
    state = _copy(locale: value);
    notifyListeners();
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(_localeKey, value.storageValue);
  }

  Future<void> setNavbarStyle(AppNavBarStyle value) async {
    state = _copy(navbarStyle: value, glassPerfBlocked: false);
    notifyListeners();
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(_navbarStyleKey, value.name);
    await preferences.setBool(_glassBlockedKey, false);
  }

  Future<void> setGlassPerfBlocked(bool value) async {
    if (state.glassPerfBlocked == value) return;
    state = _copy(glassPerfBlocked: value);
    notifyListeners();
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool(_glassBlockedKey, value);
  }

  Future<void> setShowNavbar(bool value) async {
    state = _copy(showNavbar: value);
    notifyListeners();
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool(_showNavbarKey, value);
  }

  Future<void> setShowDevotion(bool value) async {
    state = _copy(showDevotion: value);
    notifyListeners();
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool(_showDevotionKey, value);
  }

  AppSettingsState _copy({
    ThemeMode? themeMode,
    AppLocale? locale,
    AppNavBarStyle? navbarStyle,
    bool? glassPerfBlocked,
    bool? showNavbar,
    bool? showDevotion,
  }) => AppSettingsState(
    themeMode: themeMode ?? state.themeMode,
    locale: locale ?? state.locale,
    navbarStyle: navbarStyle ?? state.navbarStyle,
    glassPerfBlocked: glassPerfBlocked ?? state.glassPerfBlocked,
    showNavbar: showNavbar ?? state.showNavbar,
    showDevotion: showDevotion ?? state.showDevotion,
  );
}

class AppSettingsScope extends InheritedNotifier<AppSettingsController> {
  const AppSettingsScope({
    super.key,
    required AppSettingsController controller,
    required super.child,
  }) : super(notifier: controller);

  AppSettingsState get state => notifier!.state;

  static AppSettingsScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppSettingsScope>();

  static AppSettingsScope of(BuildContext context) {
    final scope = maybeOf(context);
    assert(scope != null, 'No AppSettingsScope found in context.');
    return scope!;
  }
}
