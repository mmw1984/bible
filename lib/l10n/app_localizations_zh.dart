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
  String get back => '返回';

  @override
  String get close => '關閉';

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
  String get retry => '重試';

  @override
  String get search => '搜尋';

  @override
  String get bibleAi => 'Bible AI';

  @override
  String get bibleAiConversation => 'Bible AI 對話';

  @override
  String get clearConversation => '清除對話';

  @override
  String get clearConversationQuestion => '清除對話？';

  @override
  String get clearConversationBody => '所有 Bible AI 對話記錄都會從這部裝置移除。';

  @override
  String get noConversation => '尚未有對話';

  @override
  String get answerIncomplete => '回覆已停止或尚未完整';

  @override
  String get askAi => '問 AI';

  @override
  String get explainScripture => '解釋經文';

  @override
  String get copyScripture => '複製經文';

  @override
  String get copyAnswer => '複製回覆';

  @override
  String get removeScriptureAttachment => '移除經文附件';

  @override
  String get regenerate => '重新生成';

  @override
  String get goToLatest => '前往最新回覆';

  @override
  String get thinking => '正在思考';

  @override
  String get thinkingContent => '思考內容';

  @override
  String get expandThinking => '展開思考內容';

  @override
  String get collapseThinking => '收起思考內容';

  @override
  String get questionHint => '輸入你的問題';

  @override
  String followUpHint(String reference) {
    return '追問 $reference';
  }

  @override
  String get send => '送出';

  @override
  String get stop => '停止生成';

  @override
  String get aiInitializingFailed => 'Bible AI 初始化失敗，請重試。';

  @override
  String get answerFailed => '未能完成回覆，內容已保留，請稍後再試。';

  @override
  String get regenerateFailed => '未能重新生成回覆，請稍後再試。';

  @override
  String get openRouterNotConnected => 'OpenRouter 尚未連接';

  @override
  String get openRouterConnectBody => '登入後即可使用 Bible AI。';

  @override
  String get login => '登入';

  @override
  String get logout => '登出';

  @override
  String get connectedSecurely => '已安全連接';

  @override
  String get notSignedIn => '尚未登入';

  @override
  String get openRouterConnection => 'OpenRouter 連線';

  @override
  String get modelId => '模型 ID';

  @override
  String get modelHelper =>
      '預設使用 Free Models Router，也可輸入其他 OpenRouter model ID。';

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
  String get appLanguage => '應用程式語言';

  @override
  String get chinese => '中文';

  @override
  String get english => '英文';

  @override
  String get bilingual => '雙語';

  @override
  String get selectBook => '選擇書卷';

  @override
  String get selectChapter => '選擇章節';

  @override
  String selectChapterCurrent(int chapter) {
    return '選擇章節，目前第 $chapter 章';
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
  String get readingLanguage => '閱讀語言';

  @override
  String get currentlyReading => '正在閱讀';

  @override
  String get previousChapter => '上一章';

  @override
  String get nextChapter => '下一章';

  @override
  String get scriptureLoadFailed => '經文暫時無法載入';

  @override
  String get closeSearch => '關閉搜尋';

  @override
  String get closeChapterPicker => '關閉章節選單';

  @override
  String get closeLibrary => '關閉書卷';

  @override
  String get closeScriptureActions => '關閉經文操作';

  @override
  String explainScripturePrompt(String reference) {
    return '請解釋 $reference，並說明上下文、主旨及今天可以如何理解。';
  }

  @override
  String get searchWholeBible => '搜尋全本聖經';

  @override
  String get searchHintBody => '輸入字詞、人物、事件或主題';

  @override
  String get traditionalSearch => '傳統搜尋';

  @override
  String get aiSearch => 'AI 搜尋';

  @override
  String get aiOverview => 'AI Overview';

  @override
  String get aiScriptureResults => 'AI 經文結果';

  @override
  String get searchStatus => '搜尋狀態';

  @override
  String get searchingOverview => '正在整理概覽';

  @override
  String get searchingScripture => '正在比對經文';

  @override
  String get noResults => '找不到相符經文';

  @override
  String get searchFailed => '搜尋暫時未能完成，請再試一次。';

  @override
  String get overviewFailed => 'AI Overview 暫時未能完成。';

  @override
  String get referencesFailed => 'AI 經文搜尋暫時未能完成。';

  @override
  String get verseResultsFailed => '經文結果暫時未能載入。';

  @override
  String get loginToSearch => '完成 OpenRouter 登入後會自動繼續 AI 搜尋；傳統全文結果毋須登入。';

  @override
  String traditionalResultCount(String count) {
    return '傳統全文結果 · $count';
  }

  @override
  String aiResultCount(int count) {
    return 'AI 經文結果 · $count';
  }

  @override
  String get oldTestamentCount => '舊約 · 39';

  @override
  String get newTestamentCount => '新約 · 27';

  @override
  String get returnToSearchOrigin => '返回搜尋前位置';

  @override
  String get aiSettings => '設定';

  @override
  String get openRouterLoginFailed => '未能開啟 OpenRouter 登入。';

  @override
  String get tabBible => '圣经';

  @override
  String get tabAsk => 'Ask';

  @override
  String get tabDevotion => '灵修默想';

  @override
  String get navbarStyle => '导航栏样式';

  @override
  String get navbarStyleLiquidGlass => 'Liquid Glass';

  @override
  String get navbarStyleSolid => '实色';

  @override
  String get navbarStyleBlur => '模糊';

  @override
  String get liquidGlassUnsupportedHint =>
      '此设备不支持 Liquid Glass（需要 Android 13 或以上），已改用模糊样式。';

  @override
  String get glassPerfFallbackNotice =>
      '为保持流畅，已自动改用模糊样式；你可以在设置重新开启 Liquid Glass。';

  @override
  String get devotionLoading => '正在加载灵修…';

  @override
  String get devotionLoadFailed => '灵修暂时无法加载';

  @override
  String get devotionRetry => '重试';

  @override
  String get devotionRefresh => '重新整理';

  @override
  String get devotionOpenInBrowser => '在浏览器打开';

  @override
  String get devotionOpenWebReader => '网页版';

  @override
  String get devotionWebLoadFailed => '网页无法加载';

  @override
  String get devotionOpenEmbed => '打开嵌入音频';

  @override
  String get devotionWatchVideo => '播放影片';

  @override
  String get layoutSection => '版面';

  @override
  String get navbarVisibility => '导航栏';

  @override
  String get devotionVisibility => '灵修默想';

  @override
  String get optionShow => '显示';

  @override
  String get optionHide => '隐藏';

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
  String get back => '返回';

  @override
  String get close => '關閉';

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
  String get retry => '重試';

  @override
  String get search => '搜尋';

  @override
  String get bibleAi => 'Bible AI';

  @override
  String get bibleAiConversation => 'Bible AI 對話';

  @override
  String get clearConversation => '清除對話';

  @override
  String get clearConversationQuestion => '清除對話？';

  @override
  String get clearConversationBody => '所有 Bible AI 對話記錄都會從這部裝置移除。';

  @override
  String get noConversation => '尚未有對話';

  @override
  String get answerIncomplete => '回覆已停止或尚未完整';

  @override
  String get askAi => '問 AI';

  @override
  String get explainScripture => '解釋經文';

  @override
  String get copyScripture => '複製經文';

  @override
  String get copyAnswer => '複製回覆';

  @override
  String get removeScriptureAttachment => '移除經文附件';

  @override
  String get regenerate => '重新生成';

  @override
  String get goToLatest => '前往最新回覆';

  @override
  String get thinking => '正在思考';

  @override
  String get thinkingContent => '思考內容';

  @override
  String get expandThinking => '展開思考內容';

  @override
  String get collapseThinking => '收起思考內容';

  @override
  String get questionHint => '輸入你的問題';

  @override
  String followUpHint(String reference) {
    return '追問 $reference';
  }

  @override
  String get send => '送出';

  @override
  String get stop => '停止生成';

  @override
  String get aiInitializingFailed => 'Bible AI 初始化失敗，請重試。';

  @override
  String get answerFailed => '未能完成回覆，內容已保留，請稍後再試。';

  @override
  String get regenerateFailed => '未能重新生成回覆，請稍後再試。';

  @override
  String get openRouterNotConnected => 'OpenRouter 尚未連接';

  @override
  String get openRouterConnectBody => '登入後即可使用 Bible AI。';

  @override
  String get login => '登入';

  @override
  String get logout => '登出';

  @override
  String get connectedSecurely => '已安全連接';

  @override
  String get notSignedIn => '尚未登入';

  @override
  String get openRouterConnection => 'OpenRouter 連線';

  @override
  String get modelId => '模型 ID';

  @override
  String get modelHelper =>
      '預設使用 Free Models Router，也可輸入其他 OpenRouter model ID。';

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
  String get appLanguage => '應用程式語言';

  @override
  String get chinese => '中文';

  @override
  String get english => '英文';

  @override
  String get bilingual => '雙語';

  @override
  String get selectBook => '選擇書卷';

  @override
  String get selectChapter => '選擇章節';

  @override
  String selectChapterCurrent(int chapter) {
    return '選擇章節，目前第 $chapter 章';
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
  String get readingLanguage => '閱讀語言';

  @override
  String get currentlyReading => '正在閱讀';

  @override
  String get previousChapter => '上一章';

  @override
  String get nextChapter => '下一章';

  @override
  String get scriptureLoadFailed => '經文暫時無法載入';

  @override
  String get closeSearch => '關閉搜尋';

  @override
  String get closeChapterPicker => '關閉章節選單';

  @override
  String get closeLibrary => '關閉書卷';

  @override
  String get closeScriptureActions => '關閉經文操作';

  @override
  String explainScripturePrompt(String reference) {
    return '請解釋 $reference，並說明上下文、主旨及今天可以如何理解。';
  }

  @override
  String get searchWholeBible => '搜尋全本聖經';

  @override
  String get searchHintBody => '輸入字詞、人物、事件或主題';

  @override
  String get traditionalSearch => '傳統搜尋';

  @override
  String get aiSearch => 'AI 搜尋';

  @override
  String get aiOverview => 'AI Overview';

  @override
  String get aiScriptureResults => 'AI 經文結果';

  @override
  String get searchStatus => '搜尋狀態';

  @override
  String get searchingOverview => '正在整理概覽';

  @override
  String get searchingScripture => '正在比對經文';

  @override
  String get noResults => '找不到相符經文';

  @override
  String get searchFailed => '搜尋暫時未能完成，請再試一次。';

  @override
  String get overviewFailed => 'AI Overview 暫時未能完成。';

  @override
  String get referencesFailed => 'AI 經文搜尋暫時未能完成。';

  @override
  String get verseResultsFailed => '經文結果暫時未能載入。';

  @override
  String get loginToSearch => '完成 OpenRouter 登入後會自動繼續 AI 搜尋；傳統全文結果毋須登入。';

  @override
  String traditionalResultCount(String count) {
    return '傳統全文結果 · $count';
  }

  @override
  String aiResultCount(int count) {
    return 'AI 經文結果 · $count';
  }

  @override
  String get oldTestamentCount => '舊約 · 39';

  @override
  String get newTestamentCount => '新約 · 27';

  @override
  String get returnToSearchOrigin => '返回搜尋前位置';

  @override
  String get aiSettings => '設定';

  @override
  String get openRouterLoginFailed => '未能開啟 OpenRouter 登入。';

  @override
  String get tabBible => '聖經';

  @override
  String get tabAsk => 'Ask';

  @override
  String get tabDevotion => '靈修默想';

  @override
  String get navbarStyle => '導航欄樣式';

  @override
  String get navbarStyleLiquidGlass => 'Liquid Glass';

  @override
  String get navbarStyleSolid => '實色';

  @override
  String get navbarStyleBlur => '模糊';

  @override
  String get liquidGlassUnsupportedHint =>
      '此裝置不支援 Liquid Glass（需要 Android 13 或以上），已改用模糊樣式。';

  @override
  String get glassPerfFallbackNotice =>
      '為保持流暢，已自動改用模糊樣式；你可以在設定重新開啟 Liquid Glass。';

  @override
  String get devotionLoading => '正在載入靈修…';

  @override
  String get devotionLoadFailed => '靈修暫時無法載入';

  @override
  String get devotionRetry => '重試';

  @override
  String get devotionRefresh => '重新整理';

  @override
  String get devotionOpenInBrowser => '在瀏覽器開啟';

  @override
  String get devotionOpenWebReader => '網頁版';

  @override
  String get devotionWebLoadFailed => '網頁無法載入';

  @override
  String get devotionOpenEmbed => '開啟嵌入音訊';

  @override
  String get devotionWatchVideo => '播放影片';

  @override
  String get layoutSection => '版面';

  @override
  String get navbarVisibility => '導航欄';

  @override
  String get devotionVisibility => '靈修默想';

  @override
  String get optionShow => '顯示';

  @override
  String get optionHide => '隱藏';

  @override
  String get appVersion => '版本';
}
