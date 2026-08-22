// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Bible';

  @override
  String get settings => 'Settings';

  @override
  String get back => 'Back';

  @override
  String get close => 'Close';

  @override
  String get save => 'Save';

  @override
  String get delete => 'Delete';

  @override
  String get clear => 'Clear';

  @override
  String get cancel => 'Cancel';

  @override
  String get options => 'Options';

  @override
  String get retry => 'Retry';

  @override
  String get search => 'Search';

  @override
  String get bibleAi => 'Bible AI';

  @override
  String get bibleAiConversation => 'Bible AI chat';

  @override
  String get clearConversation => 'Clear conversation';

  @override
  String get clearConversationQuestion => 'Clear conversation?';

  @override
  String get clearConversationBody =>
      'All Bible AI conversation history will be removed from this device.';

  @override
  String get noConversation => 'No conversation yet';

  @override
  String get answerIncomplete => 'The answer stopped or is incomplete';

  @override
  String get askAi => 'Ask AI';

  @override
  String get explainScripture => 'Explain scripture';

  @override
  String get copyScripture => 'Copy scripture';

  @override
  String get copyAnswer => 'Copy answer';

  @override
  String get removeScriptureAttachment => 'Remove scripture attachment';

  @override
  String get regenerate => 'Regenerate';

  @override
  String get goToLatest => 'Go to latest answer';

  @override
  String get thinking => 'Thinking';

  @override
  String get thinkingContent => 'Thinking content';

  @override
  String get expandThinking => 'Expand thinking content';

  @override
  String get collapseThinking => 'Collapse thinking content';

  @override
  String get questionHint => 'Enter your question';

  @override
  String followUpHint(String reference) {
    return 'Ask about $reference';
  }

  @override
  String get send => 'Send';

  @override
  String get stop => 'Stop generating';

  @override
  String get aiInitializingFailed =>
      'Bible AI could not initialize. Please retry.';

  @override
  String get answerFailed =>
      'The answer could not be completed. Your text is preserved; please retry.';

  @override
  String get regenerateFailed =>
      'The answer could not be regenerated. Please retry.';

  @override
  String get openRouterNotConnected => 'OpenRouter is not connected';

  @override
  String get openRouterConnectBody => 'Sign in to use Bible AI.';

  @override
  String get login => 'Sign in';

  @override
  String get logout => 'Sign out';

  @override
  String get connectedSecurely => 'Securely connected';

  @override
  String get notSignedIn => 'Not signed in';

  @override
  String get openRouterConnection => 'OpenRouter connection';

  @override
  String get modelId => 'Model ID';

  @override
  String get modelHelper =>
      'Uses Free Models Router by default. You can enter another OpenRouter model ID.';

  @override
  String get saveModel => 'Save model';

  @override
  String get appearance => 'Appearance';

  @override
  String get theme => 'Theme';

  @override
  String get light => 'Light';

  @override
  String get dark => 'Dark';

  @override
  String get appLanguage => 'App language';

  @override
  String get chinese => 'Chinese';

  @override
  String get english => 'English';

  @override
  String get bilingual => 'Bilingual';

  @override
  String get selectBook => 'Select book';

  @override
  String get selectChapter => 'Select chapter';

  @override
  String selectChapterCurrent(int chapter) {
    return 'Select chapter, currently chapter $chapter';
  }

  @override
  String chapterNumber(int chapter) {
    return 'Chapter $chapter';
  }

  @override
  String chapterCount(int count) {
    return '$count chapters';
  }

  @override
  String get readingLanguage => 'Reading language';

  @override
  String get currentlyReading => 'Currently reading';

  @override
  String get previousChapter => 'Previous chapter';

  @override
  String get nextChapter => 'Next chapter';

  @override
  String get scriptureLoadFailed => 'Scripture could not be loaded';

  @override
  String get closeSearch => 'Close search';

  @override
  String get closeChapterPicker => 'Close chapter picker';

  @override
  String get closeLibrary => 'Close book library';

  @override
  String get closeScriptureActions => 'Close scripture actions';

  @override
  String explainScripturePrompt(String reference) {
    return 'Explain $reference, including its context, main message, and how it can be understood today.';
  }

  @override
  String get searchWholeBible => 'Search the whole Bible';

  @override
  String get searchHintBody => 'Enter words, a person, event, or topic';

  @override
  String get traditionalSearch => 'Text search';

  @override
  String get aiSearch => 'AI search';

  @override
  String get aiOverview => 'AI Overview';

  @override
  String get aiScriptureResults => 'AI scripture results';

  @override
  String get searchStatus => 'Search status';

  @override
  String get searchingOverview => 'Preparing overview';

  @override
  String get searchingScripture => 'Matching scripture';

  @override
  String get noResults => 'No matching scripture found';

  @override
  String get searchFailed => 'Search could not be completed. Please retry.';

  @override
  String get overviewFailed => 'AI Overview could not be completed.';

  @override
  String get referencesFailed => 'AI scripture search could not be completed.';

  @override
  String get verseResultsFailed => 'Scripture results could not be loaded.';

  @override
  String get loginToSearch =>
      'Sign in to OpenRouter to continue AI search. Text search works without signing in.';

  @override
  String traditionalResultCount(String count) {
    return 'Text results · $count';
  }

  @override
  String aiResultCount(int count) {
    return 'AI scripture results · $count';
  }

  @override
  String get oldTestamentCount => 'Old Testament · 39';

  @override
  String get newTestamentCount => 'New Testament · 27';

  @override
  String get returnToSearchOrigin => 'Return to previous reading position';

  @override
  String get aiSettings => 'Settings';

  @override
  String get openRouterLoginFailed => 'OpenRouter sign-in could not be opened.';

  @override
  String get tabBible => 'Bible';

  @override
  String get tabAsk => 'Ask';

  @override
  String get tabDevotion => 'Devotions';

  @override
  String get navbarStyle => 'Navigation bar style';

  @override
  String get navbarStyleLiquidGlass => 'Liquid Glass';

  @override
  String get navbarStyleSolid => 'Solid';

  @override
  String get navbarStyleBlur => 'Blur';

  @override
  String get liquidGlassUnsupportedHint =>
      'Liquid Glass isn\'t supported on this device (requires Android 13 or later); using the blur style instead.';

  @override
  String get glassPerfFallbackNotice =>
      'Switched to the blur style automatically to keep things smooth. You can re-enable Liquid Glass in Settings.';

  @override
  String get devotionLoading => 'Loading devotion…';

  @override
  String get devotionLoadFailed => 'Devotion could not be loaded';

  @override
  String get devotionRetry => 'Retry';

  @override
  String get devotionRefresh => 'Refresh';

  @override
  String get devotionOpenInBrowser => 'Open in browser';

  @override
  String get devotionOpenWebReader => 'Web reader';

  @override
  String get devotionWebLoadFailed => 'Page failed to load';

  @override
  String get devotionOpenEmbed => 'Open embedded audio';

  @override
  String get devotionWatchVideo => 'Play video';

  @override
  String get layoutSection => 'Layout';

  @override
  String get navbarVisibility => 'Navigation bar';

  @override
  String get devotionVisibility => 'Devotions';

  @override
  String get optionShow => 'Show';

  @override
  String get optionHide => 'Hide';

  @override
  String get appVersion => 'Version';
}
