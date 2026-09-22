// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => '聖經';

  @override
  String get settings => '設定';

  @override
  String get back => '返去';

  @override
  String get close => '閂';

  @override
  String get save => '儲存';

  @override
  String get delete => '刪除';

  @override
  String get clear => '清除';

  @override
  String get cancel => '取消';

  @override
  String get options => '選項';

  @override
  String get retry => '再試';

  @override
  String get search => '搵';

  @override
  String get bibleAi => 'Bible AI';

  @override
  String get bibleAiConversation => 'Bible AI 傾偈';

  @override
  String get clearConversation => '清除傾偈';

  @override
  String get clearConversationQuestion => '清除傾偈？';

  @override
  String get clearConversationBody => '所有 Bible AI 傾偈記錄都會喺呢部機度刪走。';

  @override
  String get noConversation => '重未有傾偈';

  @override
  String get answerIncomplete => '回覆停咗或者未完整';

  @override
  String get askAi => '問 AI';

  @override
  String get explainScripture => '解經';

  @override
  String get copyScripture => '複製經文';

  @override
  String get copyAnswer => '複製回覆';

  @override
  String get removeScriptureAttachment => '移除經文附件';

  @override
  String get regenerate => '再生成';

  @override
  String get goToLatest => '跳去最新回覆';

  @override
  String get thinking => '諗緊…';

  @override
  String get thinkingContent => '思考過程';

  @override
  String get expandThinking => '睇下點諗';

  @override
  String get collapseThinking => '收埋諗法';

  @override
  String get questionHint => '輸入你嘅問題';

  @override
  String followUpHint(String reference) {
    return '追問 $reference';
  }

  @override
  String get send => '送出';

  @override
  String get stop => '停';

  @override
  String get aiInitializingFailed => 'Bible AI 開唔到，請再試過。';

  @override
  String get answerFailed => '回覆完成唔到，內容留番喺度，遲啲再試。';

  @override
  String get regenerateFailed => '再生成唔到，遲啲再試。';

  @override
  String get openRouterNotConnected => 'OpenRouter 未連線';

  @override
  String get openRouterConnectBody => '登入咗就可以用 Bible AI。';

  @override
  String get login => '登入';

  @override
  String get logout => '登出';

  @override
  String get connectedSecurely => '已經安全連線';

  @override
  String get notSignedIn => '重未登入';

  @override
  String get openRouterConnection => 'OpenRouter 連線';

  @override
  String get modelId => '模型 ID';

  @override
  String get modelHelper =>
      '預設用 Free Models Router，亦可以入第二個 OpenRouter model ID。';

  @override
  String get saveModel => '儲存模型';

  @override
  String get appearance => '外觀';

  @override
  String get theme => '主題';

  @override
  String get light => '淺色';

  @override
  String get dark => '深色';

  @override
  String get appLanguage => 'App 語言';

  @override
  String get chinese => '廣東話';

  @override
  String get english => '英文';

  @override
  String get bilingual => '中英對照';

  @override
  String get selectBook => '揀書卷';

  @override
  String get selectChapter => '揀章';

  @override
  String selectChapterCurrent(int chapter) {
    return '揀章，而家係第 $chapter 章';
  }

  @override
  String chapterNumber(int chapter) {
    return '第 $chapter 章';
  }

  @override
  String chapterCount(int count) {
    return '$count 章';
  }

  @override
  String get readingLanguage => '閱讀文字';

  @override
  String get currentlyReading => '而家睇緊';

  @override
  String get previousChapter => '上一章';

  @override
  String get nextChapter => '下一章';

  @override
  String get scriptureLoadFailed => '經文暫時載入唔到';

  @override
  String get closeSearch => '閂搵嘢';

  @override
  String get closeChapterPicker => '閂揀章';

  @override
  String get closeLibrary => '閂書卷';

  @override
  String get closeScriptureActions => '閂經文選項';

  @override
  String explainScripturePrompt(String reference) {
    return '請解釋 $reference，並說明上下文、主旨及今日可以點樣理解。請用廣東話（繁體）回答。';
  }

  @override
  String get searchWholeBible => '搵遍全本聖經';

  @override
  String get searchHintBody => '打低字詞、人物、事件或者主題';

  @override
  String get traditionalSearch => '全文直搵';

  @override
  String get aiSearch => 'AI 搵';

  @override
  String get aiOverview => 'AI 總覽';

  @override
  String get aiScriptureResults => 'AI 搵到嘅經文';

  @override
  String get searchStatus => '搵嘢進度';

  @override
  String get searchingOverview => '整理緊總覽';

  @override
  String get searchingScripture => '對緊經文';

  @override
  String get noResults => '搵唔到相關經文';

  @override
  String get searchFailed => '搵唔到嘢，請再試過。';

  @override
  String get overviewFailed => 'AI 總覽整唔到。';

  @override
  String get referencesFailed => 'AI 搵經文整唔到。';

  @override
  String get verseResultsFailed => '經文結果載入唔到。';

  @override
  String get loginToSearch => '登入咗 OpenRouter 就會自動繼續幫你 AI 搵；全文直搵唔使登入。';

  @override
  String traditionalResultCount(String count) {
    return '全文結果 · $count';
  }

  @override
  String aiResultCount(int count) {
    return 'AI 結果 · $count';
  }

  @override
  String get oldTestamentCount => '舊約 · 39';

  @override
  String get newTestamentCount => '新約 · 27';

  @override
  String get returnToSearchOrigin => '返去啱先嗰版';

  @override
  String get aiSettings => '設定';

  @override
  String get openRouterLoginFailed => '開唔到 OpenRouter 登入。';

  @override
  String get tabBible => '聖經';

  @override
  String get tabAsk => '發問';

  @override
  String get tabDevotion => '靈修默想';

  @override
  String get navbarStyle => '導覽列樣式';

  @override
  String get navbarStyleLiquidGlass => 'Liquid Glass';

  @override
  String get navbarStyleSolid => '淨色';

  @override
  String get navbarStyleBlur => '模糊';

  @override
  String get liquidGlassUnsupportedHint =>
      '呢部機用唔到 Liquid Glass（要 Android 13 或以上），轉咗用模糊樣式。';

  @override
  String get glassPerfFallbackNotice =>
      '為咗順啲，自動轉咗用模糊樣式；可以喺設定重新開返 Liquid Glass。';

  @override
  String get devotionLoading => '載入緊靈修…';

  @override
  String get devotionLoadFailed => '靈修暫時載入唔到';

  @override
  String get devotionRetry => '再試';

  @override
  String get devotionRefresh => '更新';

  @override
  String get devotionOpenInBrowser => '喺瀏覽器開啟';

  @override
  String get devotionOpenWebReader => '網頁版';

  @override
  String get devotionWebLoadFailed => '網頁載入唔到';

  @override
  String get devotionOpenEmbed => '開嚟聽';

  @override
  String get devotionWatchVideo => '睇片';

  @override
  String get devotionCopyArticle => '複製全文';

  @override
  String get devotionCopied => '已複製';

  @override
  String get layoutSection => '版面';

  @override
  String get navbarVisibility => '導覽列';

  @override
  String get devotionVisibility => '靈修默想';

  @override
  String get optionShow => '開';

  @override
  String get optionHide => '閂';

  @override
  String get appVersion => '版本';
}

/// The translations for Chinese, using the Han script (`zh_Hant`).
class AppLocalizationsZhHant extends AppLocalizationsZh {
  AppLocalizationsZhHant() : super('zh_Hant');

  @override
  String get appTitle => '聖經';

  @override
  String get settings => '設定';

  @override
  String get back => '返去';

  @override
  String get close => '閂';

  @override
  String get save => '儲存';

  @override
  String get delete => '刪除';

  @override
  String get clear => '清除';

  @override
  String get cancel => '取消';

  @override
  String get options => '選項';

  @override
  String get retry => '再試';

  @override
  String get search => '搵';

  @override
  String get bibleAi => 'Bible AI';

  @override
  String get bibleAiConversation => 'Bible AI 傾偈';

  @override
  String get clearConversation => '清除傾偈';

  @override
  String get clearConversationQuestion => '清除傾偈？';

  @override
  String get clearConversationBody => '所有 Bible AI 傾偈記錄都會喺呢部機度刪走。';

  @override
  String get noConversation => '重未有傾偈';

  @override
  String get answerIncomplete => '回覆停咗或者未完整';

  @override
  String get askAi => '問 AI';

  @override
  String get explainScripture => '解經';

  @override
  String get copyScripture => '複製經文';

  @override
  String get copyAnswer => '複製回覆';

  @override
  String get removeScriptureAttachment => '移除經文附件';

  @override
  String get regenerate => '再生成';

  @override
  String get goToLatest => '跳去最新回覆';

  @override
  String get thinking => '諗緊…';

  @override
  String get thinkingContent => '思考過程';

  @override
  String get expandThinking => '睇下點諗';

  @override
  String get collapseThinking => '收埋諗法';

  @override
  String get questionHint => '輸入你嘅問題';

  @override
  String followUpHint(String reference) {
    return '追問 $reference';
  }

  @override
  String get send => '送出';

  @override
  String get stop => '停';

  @override
  String get aiInitializingFailed => 'Bible AI 開唔到，請再試過。';

  @override
  String get answerFailed => '回覆完成唔到，內容留番喺度，遲啲再試。';

  @override
  String get regenerateFailed => '再生成唔到，遲啲再試。';

  @override
  String get openRouterNotConnected => 'OpenRouter 未連線';

  @override
  String get openRouterConnectBody => '登入咗就可以用 Bible AI。';

  @override
  String get login => '登入';

  @override
  String get logout => '登出';

  @override
  String get connectedSecurely => '已經安全連線';

  @override
  String get notSignedIn => '重未登入';

  @override
  String get openRouterConnection => 'OpenRouter 連線';

  @override
  String get modelId => '模型 ID';

  @override
  String get modelHelper =>
      '預設用 Free Models Router，亦可以入第二個 OpenRouter model ID。';

  @override
  String get saveModel => '儲存模型';

  @override
  String get appearance => '外觀';

  @override
  String get theme => '主題';

  @override
  String get light => '淺色';

  @override
  String get dark => '深色';

  @override
  String get appLanguage => 'App 語言';

  @override
  String get chinese => '廣東話';

  @override
  String get english => '英文';

  @override
  String get bilingual => '中英對照';

  @override
  String get selectBook => '揀書卷';

  @override
  String get selectChapter => '揀章';

  @override
  String selectChapterCurrent(int chapter) {
    return '揀章，而家係第 $chapter 章';
  }

  @override
  String chapterNumber(int chapter) {
    return '第 $chapter 章';
  }

  @override
  String chapterCount(int count) {
    return '$count 章';
  }

  @override
  String get readingLanguage => '閱讀文字';

  @override
  String get currentlyReading => '而家睇緊';

  @override
  String get previousChapter => '上一章';

  @override
  String get nextChapter => '下一章';

  @override
  String get scriptureLoadFailed => '經文暫時載入唔到';

  @override
  String get closeSearch => '閂搵嘢';

  @override
  String get closeChapterPicker => '閂揀章';

  @override
  String get closeLibrary => '閂書卷';

  @override
  String get closeScriptureActions => '閂經文選項';

  @override
  String explainScripturePrompt(String reference) {
    return '請解釋 $reference，並說明上下文、主旨及今日可以點樣理解。請用廣東話（繁體）回答。';
  }

  @override
  String get searchWholeBible => '搵遍全本聖經';

  @override
  String get searchHintBody => '打低字詞、人物、事件或者主題';

  @override
  String get traditionalSearch => '全文直搵';

  @override
  String get aiSearch => 'AI 搵';

  @override
  String get aiOverview => 'AI 總覽';

  @override
  String get aiScriptureResults => 'AI 搵到嘅經文';

  @override
  String get searchStatus => '搵嘢進度';

  @override
  String get searchingOverview => '整理緊總覽';

  @override
  String get searchingScripture => '對緊經文';

  @override
  String get noResults => '搵唔到相關經文';

  @override
  String get searchFailed => '搵唔到嘢，請再試過。';

  @override
  String get overviewFailed => 'AI 總覽整唔到。';

  @override
  String get referencesFailed => 'AI 搵經文整唔到。';

  @override
  String get verseResultsFailed => '經文結果載入唔到。';

  @override
  String get loginToSearch => '登入咗 OpenRouter 就會自動繼續幫你 AI 搵；全文直搵唔使登入。';

  @override
  String traditionalResultCount(String count) {
    return '全文結果 · $count';
  }

  @override
  String aiResultCount(int count) {
    return 'AI 結果 · $count';
  }

  @override
  String get oldTestamentCount => '舊約 · 39';

  @override
  String get newTestamentCount => '新約 · 27';

  @override
  String get returnToSearchOrigin => '返去啱先嗰版';

  @override
  String get aiSettings => '設定';

  @override
  String get openRouterLoginFailed => '開唔到 OpenRouter 登入。';

  @override
  String get tabBible => '聖經';

  @override
  String get tabAsk => '發問';

  @override
  String get tabDevotion => '靈修默想';

  @override
  String get navbarStyle => '導覽列樣式';

  @override
  String get navbarStyleLiquidGlass => 'Liquid Glass';

  @override
  String get navbarStyleSolid => '淨色';

  @override
  String get navbarStyleBlur => '模糊';

  @override
  String get liquidGlassUnsupportedHint =>
      '呢部機用唔到 Liquid Glass（要 Android 13 或以上），轉咗用模糊樣式。';

  @override
  String get glassPerfFallbackNotice =>
      '為咗順啲，自動轉咗用模糊樣式；可以喺設定重新開返 Liquid Glass。';

  @override
  String get devotionLoading => '載入緊靈修…';

  @override
  String get devotionLoadFailed => '靈修暫時載入唔到';

  @override
  String get devotionRetry => '再試';

  @override
  String get devotionRefresh => '更新';

  @override
  String get devotionOpenInBrowser => '喺瀏覽器開啟';

  @override
  String get devotionOpenWebReader => '網頁版';

  @override
  String get devotionWebLoadFailed => '網頁載入唔到';

  @override
  String get devotionOpenEmbed => '開嚟聽';

  @override
  String get devotionWatchVideo => '睇片';

  @override
  String get devotionCopyArticle => '複製全文';

  @override
  String get devotionCopied => '已複製';

  @override
  String get layoutSection => '版面';

  @override
  String get navbarVisibility => '導覽列';

  @override
  String get devotionVisibility => '靈修默想';

  @override
  String get optionShow => '開';

  @override
  String get optionHide => '閂';

  @override
  String get appVersion => '版本';
}
