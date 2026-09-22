import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('zh'),
    Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hant'),
  ];

  /// No description provided for @appTitle.
  ///
  /// In zh_Hant, this message translates to:
  /// **'聖經'**
  String get appTitle;

  /// No description provided for @settings.
  ///
  /// In zh_Hant, this message translates to:
  /// **'設定'**
  String get settings;

  /// No description provided for @back.
  ///
  /// In zh_Hant, this message translates to:
  /// **'返去'**
  String get back;

  /// No description provided for @close.
  ///
  /// In zh_Hant, this message translates to:
  /// **'閂'**
  String get close;

  /// No description provided for @save.
  ///
  /// In zh_Hant, this message translates to:
  /// **'儲存'**
  String get save;

  /// No description provided for @delete.
  ///
  /// In zh_Hant, this message translates to:
  /// **'刪除'**
  String get delete;

  /// No description provided for @clear.
  ///
  /// In zh_Hant, this message translates to:
  /// **'清除'**
  String get clear;

  /// No description provided for @cancel.
  ///
  /// In zh_Hant, this message translates to:
  /// **'取消'**
  String get cancel;

  /// No description provided for @options.
  ///
  /// In zh_Hant, this message translates to:
  /// **'選項'**
  String get options;

  /// No description provided for @retry.
  ///
  /// In zh_Hant, this message translates to:
  /// **'再試'**
  String get retry;

  /// No description provided for @search.
  ///
  /// In zh_Hant, this message translates to:
  /// **'搵'**
  String get search;

  /// No description provided for @bibleAi.
  ///
  /// In zh_Hant, this message translates to:
  /// **'Bible AI'**
  String get bibleAi;

  /// No description provided for @bibleAiConversation.
  ///
  /// In zh_Hant, this message translates to:
  /// **'Bible AI 傾偈'**
  String get bibleAiConversation;

  /// No description provided for @clearConversation.
  ///
  /// In zh_Hant, this message translates to:
  /// **'清除傾偈'**
  String get clearConversation;

  /// No description provided for @clearConversationQuestion.
  ///
  /// In zh_Hant, this message translates to:
  /// **'清除傾偈？'**
  String get clearConversationQuestion;

  /// No description provided for @clearConversationBody.
  ///
  /// In zh_Hant, this message translates to:
  /// **'所有 Bible AI 傾偈記錄都會喺呢部機度刪走。'**
  String get clearConversationBody;

  /// No description provided for @noConversation.
  ///
  /// In zh_Hant, this message translates to:
  /// **'重未有傾偈'**
  String get noConversation;

  /// No description provided for @answerIncomplete.
  ///
  /// In zh_Hant, this message translates to:
  /// **'回覆停咗或者未完整'**
  String get answerIncomplete;

  /// No description provided for @askAi.
  ///
  /// In zh_Hant, this message translates to:
  /// **'問 AI'**
  String get askAi;

  /// No description provided for @explainScripture.
  ///
  /// In zh_Hant, this message translates to:
  /// **'解經'**
  String get explainScripture;

  /// No description provided for @copyScripture.
  ///
  /// In zh_Hant, this message translates to:
  /// **'複製經文'**
  String get copyScripture;

  /// No description provided for @copyAnswer.
  ///
  /// In zh_Hant, this message translates to:
  /// **'複製回覆'**
  String get copyAnswer;

  /// No description provided for @removeScriptureAttachment.
  ///
  /// In zh_Hant, this message translates to:
  /// **'移除經文附件'**
  String get removeScriptureAttachment;

  /// No description provided for @regenerate.
  ///
  /// In zh_Hant, this message translates to:
  /// **'再生成'**
  String get regenerate;

  /// No description provided for @goToLatest.
  ///
  /// In zh_Hant, this message translates to:
  /// **'跳去最新回覆'**
  String get goToLatest;

  /// No description provided for @thinking.
  ///
  /// In zh_Hant, this message translates to:
  /// **'諗緊…'**
  String get thinking;

  /// No description provided for @thinkingContent.
  ///
  /// In zh_Hant, this message translates to:
  /// **'思考過程'**
  String get thinkingContent;

  /// No description provided for @expandThinking.
  ///
  /// In zh_Hant, this message translates to:
  /// **'睇下點諗'**
  String get expandThinking;

  /// No description provided for @collapseThinking.
  ///
  /// In zh_Hant, this message translates to:
  /// **'收埋諗法'**
  String get collapseThinking;

  /// No description provided for @questionHint.
  ///
  /// In zh_Hant, this message translates to:
  /// **'輸入你嘅問題'**
  String get questionHint;

  /// No description provided for @followUpHint.
  ///
  /// In zh_Hant, this message translates to:
  /// **'追問 {reference}'**
  String followUpHint(String reference);

  /// No description provided for @send.
  ///
  /// In zh_Hant, this message translates to:
  /// **'送出'**
  String get send;

  /// No description provided for @stop.
  ///
  /// In zh_Hant, this message translates to:
  /// **'停'**
  String get stop;

  /// No description provided for @aiInitializingFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'Bible AI 開唔到，請再試過。'**
  String get aiInitializingFailed;

  /// No description provided for @answerFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'回覆完成唔到，內容留番喺度，遲啲再試。'**
  String get answerFailed;

  /// No description provided for @regenerateFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'再生成唔到，遲啲再試。'**
  String get regenerateFailed;

  /// No description provided for @openRouterNotConnected.
  ///
  /// In zh_Hant, this message translates to:
  /// **'OpenRouter 未連線'**
  String get openRouterNotConnected;

  /// No description provided for @openRouterConnectBody.
  ///
  /// In zh_Hant, this message translates to:
  /// **'登入咗就可以用 Bible AI。'**
  String get openRouterConnectBody;

  /// No description provided for @login.
  ///
  /// In zh_Hant, this message translates to:
  /// **'登入'**
  String get login;

  /// No description provided for @logout.
  ///
  /// In zh_Hant, this message translates to:
  /// **'登出'**
  String get logout;

  /// No description provided for @connectedSecurely.
  ///
  /// In zh_Hant, this message translates to:
  /// **'已經安全連線'**
  String get connectedSecurely;

  /// No description provided for @notSignedIn.
  ///
  /// In zh_Hant, this message translates to:
  /// **'重未登入'**
  String get notSignedIn;

  /// No description provided for @openRouterConnection.
  ///
  /// In zh_Hant, this message translates to:
  /// **'OpenRouter 連線'**
  String get openRouterConnection;

  /// No description provided for @modelId.
  ///
  /// In zh_Hant, this message translates to:
  /// **'模型 ID'**
  String get modelId;

  /// No description provided for @modelHelper.
  ///
  /// In zh_Hant, this message translates to:
  /// **'預設用 Free Models Router，亦可以入第二個 OpenRouter model ID。'**
  String get modelHelper;

  /// No description provided for @saveModel.
  ///
  /// In zh_Hant, this message translates to:
  /// **'儲存模型'**
  String get saveModel;

  /// No description provided for @appearance.
  ///
  /// In zh_Hant, this message translates to:
  /// **'外觀'**
  String get appearance;

  /// No description provided for @theme.
  ///
  /// In zh_Hant, this message translates to:
  /// **'主題'**
  String get theme;

  /// No description provided for @light.
  ///
  /// In zh_Hant, this message translates to:
  /// **'淺色'**
  String get light;

  /// No description provided for @dark.
  ///
  /// In zh_Hant, this message translates to:
  /// **'深色'**
  String get dark;

  /// No description provided for @appLanguage.
  ///
  /// In zh_Hant, this message translates to:
  /// **'App 語言'**
  String get appLanguage;

  /// No description provided for @chinese.
  ///
  /// In zh_Hant, this message translates to:
  /// **'廣東話'**
  String get chinese;

  /// No description provided for @english.
  ///
  /// In zh_Hant, this message translates to:
  /// **'英文'**
  String get english;

  /// No description provided for @bilingual.
  ///
  /// In zh_Hant, this message translates to:
  /// **'中英對照'**
  String get bilingual;

  /// No description provided for @selectBook.
  ///
  /// In zh_Hant, this message translates to:
  /// **'揀書卷'**
  String get selectBook;

  /// No description provided for @selectChapter.
  ///
  /// In zh_Hant, this message translates to:
  /// **'揀章'**
  String get selectChapter;

  /// No description provided for @selectChapterCurrent.
  ///
  /// In zh_Hant, this message translates to:
  /// **'揀章，而家係第 {chapter} 章'**
  String selectChapterCurrent(int chapter);

  /// No description provided for @chapterNumber.
  ///
  /// In zh_Hant, this message translates to:
  /// **'第 {chapter} 章'**
  String chapterNumber(int chapter);

  /// No description provided for @chapterCount.
  ///
  /// In zh_Hant, this message translates to:
  /// **'{count} 章'**
  String chapterCount(int count);

  /// No description provided for @readingLanguage.
  ///
  /// In zh_Hant, this message translates to:
  /// **'閱讀文字'**
  String get readingLanguage;

  /// No description provided for @currentlyReading.
  ///
  /// In zh_Hant, this message translates to:
  /// **'而家睇緊'**
  String get currentlyReading;

  /// No description provided for @previousChapter.
  ///
  /// In zh_Hant, this message translates to:
  /// **'上一章'**
  String get previousChapter;

  /// No description provided for @nextChapter.
  ///
  /// In zh_Hant, this message translates to:
  /// **'下一章'**
  String get nextChapter;

  /// No description provided for @scriptureLoadFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'經文暫時載入唔到'**
  String get scriptureLoadFailed;

  /// No description provided for @closeSearch.
  ///
  /// In zh_Hant, this message translates to:
  /// **'閂搵嘢'**
  String get closeSearch;

  /// No description provided for @closeChapterPicker.
  ///
  /// In zh_Hant, this message translates to:
  /// **'閂揀章'**
  String get closeChapterPicker;

  /// No description provided for @closeLibrary.
  ///
  /// In zh_Hant, this message translates to:
  /// **'閂書卷'**
  String get closeLibrary;

  /// No description provided for @closeScriptureActions.
  ///
  /// In zh_Hant, this message translates to:
  /// **'閂經文選項'**
  String get closeScriptureActions;

  /// No description provided for @explainScripturePrompt.
  ///
  /// In zh_Hant, this message translates to:
  /// **'請解釋 {reference}，並說明上下文、主旨及今日可以點樣理解。請用廣東話（繁體）回答。'**
  String explainScripturePrompt(String reference);

  /// No description provided for @searchWholeBible.
  ///
  /// In zh_Hant, this message translates to:
  /// **'搵遍全本聖經'**
  String get searchWholeBible;

  /// No description provided for @searchHintBody.
  ///
  /// In zh_Hant, this message translates to:
  /// **'打低字詞、人物、事件或者主題'**
  String get searchHintBody;

  /// No description provided for @traditionalSearch.
  ///
  /// In zh_Hant, this message translates to:
  /// **'全文直搵'**
  String get traditionalSearch;

  /// No description provided for @aiSearch.
  ///
  /// In zh_Hant, this message translates to:
  /// **'AI 搵'**
  String get aiSearch;

  /// No description provided for @aiOverview.
  ///
  /// In zh_Hant, this message translates to:
  /// **'AI 總覽'**
  String get aiOverview;

  /// No description provided for @aiScriptureResults.
  ///
  /// In zh_Hant, this message translates to:
  /// **'AI 搵到嘅經文'**
  String get aiScriptureResults;

  /// No description provided for @searchStatus.
  ///
  /// In zh_Hant, this message translates to:
  /// **'搵嘢進度'**
  String get searchStatus;

  /// No description provided for @searchingOverview.
  ///
  /// In zh_Hant, this message translates to:
  /// **'整理緊總覽'**
  String get searchingOverview;

  /// No description provided for @searchingScripture.
  ///
  /// In zh_Hant, this message translates to:
  /// **'對緊經文'**
  String get searchingScripture;

  /// No description provided for @noResults.
  ///
  /// In zh_Hant, this message translates to:
  /// **'搵唔到相關經文'**
  String get noResults;

  /// No description provided for @searchFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'搵唔到嘢，請再試過。'**
  String get searchFailed;

  /// No description provided for @overviewFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'AI 總覽整唔到。'**
  String get overviewFailed;

  /// No description provided for @referencesFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'AI 搵經文整唔到。'**
  String get referencesFailed;

  /// No description provided for @verseResultsFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'經文結果載入唔到。'**
  String get verseResultsFailed;

  /// No description provided for @loginToSearch.
  ///
  /// In zh_Hant, this message translates to:
  /// **'登入咗 OpenRouter 就會自動繼續幫你 AI 搵；全文直搵唔使登入。'**
  String get loginToSearch;

  /// No description provided for @traditionalResultCount.
  ///
  /// In zh_Hant, this message translates to:
  /// **'全文結果 · {count}'**
  String traditionalResultCount(String count);

  /// No description provided for @aiResultCount.
  ///
  /// In zh_Hant, this message translates to:
  /// **'AI 結果 · {count}'**
  String aiResultCount(int count);

  /// No description provided for @oldTestamentCount.
  ///
  /// In zh_Hant, this message translates to:
  /// **'舊約 · 39'**
  String get oldTestamentCount;

  /// No description provided for @newTestamentCount.
  ///
  /// In zh_Hant, this message translates to:
  /// **'新約 · 27'**
  String get newTestamentCount;

  /// No description provided for @returnToSearchOrigin.
  ///
  /// In zh_Hant, this message translates to:
  /// **'返去啱先嗰版'**
  String get returnToSearchOrigin;

  /// No description provided for @aiSettings.
  ///
  /// In zh_Hant, this message translates to:
  /// **'設定'**
  String get aiSettings;

  /// No description provided for @openRouterLoginFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'開唔到 OpenRouter 登入。'**
  String get openRouterLoginFailed;

  /// No description provided for @tabBible.
  ///
  /// In zh_Hant, this message translates to:
  /// **'聖經'**
  String get tabBible;

  /// No description provided for @tabAsk.
  ///
  /// In zh_Hant, this message translates to:
  /// **'發問'**
  String get tabAsk;

  /// No description provided for @tabDevotion.
  ///
  /// In zh_Hant, this message translates to:
  /// **'靈修默想'**
  String get tabDevotion;

  /// No description provided for @navbarStyle.
  ///
  /// In zh_Hant, this message translates to:
  /// **'導覽列樣式'**
  String get navbarStyle;

  /// No description provided for @navbarStyleLiquidGlass.
  ///
  /// In zh_Hant, this message translates to:
  /// **'Liquid Glass'**
  String get navbarStyleLiquidGlass;

  /// No description provided for @navbarStyleSolid.
  ///
  /// In zh_Hant, this message translates to:
  /// **'淨色'**
  String get navbarStyleSolid;

  /// No description provided for @navbarStyleBlur.
  ///
  /// In zh_Hant, this message translates to:
  /// **'模糊'**
  String get navbarStyleBlur;

  /// No description provided for @liquidGlassUnsupportedHint.
  ///
  /// In zh_Hant, this message translates to:
  /// **'呢部機用唔到 Liquid Glass（要 Android 13 或以上），轉咗用模糊樣式。'**
  String get liquidGlassUnsupportedHint;

  /// No description provided for @glassPerfFallbackNotice.
  ///
  /// In zh_Hant, this message translates to:
  /// **'為咗順啲，自動轉咗用模糊樣式；可以喺設定重新開返 Liquid Glass。'**
  String get glassPerfFallbackNotice;

  /// No description provided for @devotionLoading.
  ///
  /// In zh_Hant, this message translates to:
  /// **'載入緊靈修…'**
  String get devotionLoading;

  /// No description provided for @devotionLoadFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'靈修暫時載入唔到'**
  String get devotionLoadFailed;

  /// No description provided for @devotionRetry.
  ///
  /// In zh_Hant, this message translates to:
  /// **'再試'**
  String get devotionRetry;

  /// No description provided for @devotionRefresh.
  ///
  /// In zh_Hant, this message translates to:
  /// **'更新'**
  String get devotionRefresh;

  /// No description provided for @devotionOpenInBrowser.
  ///
  /// In zh_Hant, this message translates to:
  /// **'喺瀏覽器開啟'**
  String get devotionOpenInBrowser;

  /// No description provided for @devotionOpenWebReader.
  ///
  /// In zh_Hant, this message translates to:
  /// **'網頁版'**
  String get devotionOpenWebReader;

  /// No description provided for @devotionWebLoadFailed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'網頁載入唔到'**
  String get devotionWebLoadFailed;

  /// No description provided for @devotionOpenEmbed.
  ///
  /// In zh_Hant, this message translates to:
  /// **'開嚟聽'**
  String get devotionOpenEmbed;

  /// No description provided for @devotionWatchVideo.
  ///
  /// In zh_Hant, this message translates to:
  /// **'睇片'**
  String get devotionWatchVideo;

  /// No description provided for @devotionCopyArticle.
  ///
  /// In zh_Hant, this message translates to:
  /// **'複製全文'**
  String get devotionCopyArticle;

  /// No description provided for @devotionCopied.
  ///
  /// In zh_Hant, this message translates to:
  /// **'已複製'**
  String get devotionCopied;

  /// No description provided for @layoutSection.
  ///
  /// In zh_Hant, this message translates to:
  /// **'版面'**
  String get layoutSection;

  /// No description provided for @navbarVisibility.
  ///
  /// In zh_Hant, this message translates to:
  /// **'導覽列'**
  String get navbarVisibility;

  /// No description provided for @devotionVisibility.
  ///
  /// In zh_Hant, this message translates to:
  /// **'靈修默想'**
  String get devotionVisibility;

  /// No description provided for @optionShow.
  ///
  /// In zh_Hant, this message translates to:
  /// **'開'**
  String get optionShow;

  /// No description provided for @optionHide.
  ///
  /// In zh_Hant, this message translates to:
  /// **'閂'**
  String get optionHide;

  /// No description provided for @appVersion.
  ///
  /// In zh_Hant, this message translates to:
  /// **'版本'**
  String get appVersion;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when language+script codes are specified.
  switch (locale.languageCode) {
    case 'zh':
      {
        switch (locale.scriptCode) {
          case 'Hant':
            return AppLocalizationsZhHant();
        }
        break;
      }
  }

  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
