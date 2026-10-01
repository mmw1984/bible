
# Bible 1.6.3 → 原生 Android (Kotlin + Jetpack Compose) 重構計劃

---

## 0. 現況速覽（Skim 結果）

| 項目 | 現況 |
|---|---|
| 程式碼 | `lib/` 約 17,800 行 Dart（35 個檔案），`test/` 12 個測試檔約 2,500 行 |
| 版本 | `pubspec.yaml` → `1.6.3+203`；`android/app/build.gradle.kts` → `applicationId com.marcow.bible`, `targetSdk 36`, `minSdk = flutter.minSdkVersion`, JVM/Java 11 |
| 經文資料 | `assets/bible/{cuv,web}/<BOOK_ID>.json`（66 卷 × 2 版本 + `manifest.json`），結構 `{ "id","name","translation","chapters": { "1": ["經文","經文"] } }`，共 66 卷 / 1189 章 / 7.4 MB |
| 字型 | `assets/fonts/` 共 18 MB：`exposure_variable.otf`、`openrunde_regular.otf`、`openrunde_medium.otf`、`noto_serif_tc_variable.ttf` |
| 圖示 | `assets/icon.png` / `icon.svg` + `android/app/src/main/res/mipmap-*/ic_launcher.png` + `mipmap-anydpi-v26/ic_launcher.xml` + `drawable-*/ic_launcher_foreground.png` |
| l10n | `l10n.yaml` → `arb-dir: lib/l10n`、`template-arb-file: app_zh_Hant.arb`、`output-class: AppLocalizations`；3 個 arb（`app_en` / `app_zh` / `app_zh_Hant`），各 127 行、約 120 個 key |
| 主要依賴 | `app_links`、`flutter_secure_storage`、`fog_edge_blur`、`lucide_icons_flutter`、`webview_flutter`、`youtube_player_flutter`、`url_launcher`、`crypto`、`http`、`path_provider`、`shared_preferences` |
| 原生 Kotlin（已存在） | `MainActivity.kt`（MethodChannel `bible/android`：`openAiChatActivity` / `setAiShortcutEnabled` / `getDeviceRoundedCorners`；deep link `bible://openrouter/callback`）、`AiChatActivity.kt`（獨立 Flutter engine + Channel `bible/ai_chat_launch`）、dynamic shortcut `bible_ai_chat` |
| CI | `.github/workflows/release-apk.yml`（tag `v*` → flutter test → `flutter build apk --release`，簽章靠 `ANDROID_KEYSTORE_BASE64` secret）；另有 `deploy-web.yml`（Vercel） |
| 工具 | `tool/download_bible.mjs`（bible-api.com 抓資料）、`tool/verify_bible.mjs`（66 卷 / 1189 章校驗）、`tool/devotion_probe.dart` |
| 測試素材 | `test/fixtures/`、`test/goldens/` |

---

## 1. Feature Inventory（全部畫面 / 功能）

### 1.1 Shell- `BibleApp`（`lib/main.dart:79`）：`MaterialApp` + `AppSettingsScope`，light/dark theme、locale（`zh-Hant` / `en`）
- `BibleHome`（`lib/main.dart:199`）：`WidgetBindingObserver`、edge-to-edge insets、lazy mount tab（首次進入 mount，之後 offstage 保留）
- `AppNavBar`（`lib/app_navbar.dart:69`）：浮動 pill 底部導覽列，`AppNavTab { bible, ask, devotion }`，可拖曳白色指示器，`kAppNavBarHeight 58` / `kAppNavBarBottomGap 12` / `kAppNavBarHorizontalInset 16` / `kAppNavBarMaxWidth 260`
  - 兩種樣式：`AppNavBarStyle.material`（實心） / `.materialBlur`（`fog_edge_blur` 液態玻璃，預設）
  - `glassPerfBlocked`：玻璃捕捉管線量測到持續 jank 會 fallback 到實心，使用者可強制再開
  - `showNavbar = false` 時，讀經頁 top bar 顯示 Ask / Devotion 快捷鈕

### 1.2 Bible 讀經（Tab 1）

- 閱讀模式 `ReadingMode { chinese, english, bilingual }`（`lib/main.dart:33`）
- `_Reader`（`lib/main.dart:1063`）：逐節渲染、skeleton 載入動畫（`_VerseSkeleton`）、scroll-aware entrance（`_ScrollAwareEntrance`）
- `_ChapterPickerBubble`（`lib/main.dart:1385`）：章節氣泡選單、上一章 / 下一章
- `_VerseRow`（`lib/main.dart:1523`）：節號 + 可選取文字 + 長選動作列 - `複製經文`（`Clipboard`）
  - `問 AI` → 帶經文 payload 開 `AiChatActivity`
  - `解釋經文` → 同上，帶解釋模式
- `_Sidebar`（`lib/main.dart:1729`）：書卷抽屜列表 + `_ChapterLink`
- `_LibraryRoute` / `_LibraryPanel`（`lib/main.dart:1882` / `:1944`）：獨立 PageRoute 書卷面板，`_TestamentSegmented`（舊約39 卷 / 新約 27 卷）
- 閱讀進度持久化：`reader_book` / `reader_chapter` / `reader_mode` + 每 book/chapter 獨立 scroll offset

### 1.3 搜尋（`_SearchDialog`, `lib/main.dart:2109`）

- `_SearchMode { traditional, ai }`（`lib/main.dart:2578`）
- Traditional：`BibleRepository.search()`，全書大小寫不敏感 `contains`，`limit = 80`，回 `ScriptureHit { book, chapter, verse }`
- AI 模式：兩個 request **並行**（overview + references）
  - 概覽：prompt marker `BIBLE_SEARCH_OVERVIEW`，`reasoning: { enabled: false, exclude: true }`，`max_tokens 1200`
  - 引文：prompt marker `BIBLE_SEARCH_REFERENCES_JSON`，`response_format: json_schema`（strict），`max_tokens 2400`，schema 含 `scriptures[] { bookId, chapter, verseStart, verseEnd, reason }`（maxItems 16）+ `suggestedQuestions[]`
  - UI：`_AiSearchLoading`（搜尋概覽 / 搜尋經文）、`_SearchHitTile`、`_ActionTile`、結果數量標籤、失敗狀態（`_ReferenceFailure { request, verses }`）

### 1.4 Bible AI（Tab 2 / 獨立 Activity）

- `AiChatPage`（`lib/ai_chat_page.dart`，1923 行）
  - 訊息氣泡 + Markdown渲染（`AppMarkdown`, `lib/app_markdown.dart` 444 行自製 renderer）
  - 思考內容（reasoning）摺疊 / 展開  - `回覆已停止或尚未完整`（finish_reason = length 的 incomplete 標記）
  - 經文附件（`scriptureReference`）與移除、follow-up 提示  - 輸入列：送出 / 停止、placeholder（`questionHint` / `followUpHint(ref)`）
  - `重新生成`、`複製回覆`、`前往最新回覆`（自動捲動）、清空對話（確認對話框）
  - 空白狀態快捷：問 / 解釋經文 / 首次登入引導
-設定頁（同檔 `_SettingsSection` 起）
  - 外觀：Light / Dark
  - 導覽列樣式：實心 / 模糊
  - App 語言：中文 / English
  - 版面：顯示 /隱藏 navbar、顯示 / 隱藏 Devotion
  - OpenRouter 連線狀態 + 登入 / 登出、錯誤面板
  - `modelId` 文字輸入（hint `openrouter/free`，`saveModel`）
  - 危險區：清除對話（資料只存在本機）
  - 版本號顯示
- `BibleAiController`（`lib/ai_service.dart:259`）
  - `AiAvailability { checking, ... }`、`generating`、`generationError`、`openRouterSignedIn`、`modelId`（預設 `openrouter/free`）
  - `responseLocale` → 系統提示詞語言（`natural Traditional Chinese` / `natural English`）
  - `get_scripture` 偽工具迴圈：模型輸出 `{"tool":"get_scripture",...}` → App 執行查經 → 最多 3 輪 → 最終強制作答
  - web search：首次帶 `openrouter:web_search`（`engine auto`、`search_context_size low`、`tool_choice auto`）；若模型同時不支援 reasoning/tools，**fallback 一次不帶 tool 的請求**（避免整個回答被丟掉）
- `OpenRouterAuth`（`lib/openrouter_service.dart:15`）
  - PKCE（method `plain`）、`Random.secure()` 64 bytes verifier
  - `https://openrouter.ai/auth?callback_url=bible://openrouter/callback&code_challenge=…&code_challenge_method=plain`
  - `POST https://openrouter.ai/api/v1/auth/keys` → `{ key }` 存入 secure storage
  - pending code 落盤 + `retryPendingExchange()`（跨程序恢復）
  - keys：`openrouter_api_key`、`openrouter_pkce_verifier`、`openrouter_pkce_method`、`openrouter_pending_code`
- `OpenRouterClient`（`lib/openrouter_service.dart:219`）：`generate` / `generateStream`（SSE，含 reasoning delta、finish_reason、web citations 清理）/ `research`（forced web search → `WebResearchSource[]`）
- `AiMemoryStore`（`lib/ai_memory_store_io.dart`）：app documents 目錄下 `memory.md`（分節：User preferences / Important events / Explained scripture / Search history）+ `transcript.jsonl`（每行一則 `{role,text,scripture,kind,timestamp}`）；prompt 注入上限 7000（chat）/ 3500（search）字元，逾越取尾段

### 1.5 靈修默想 Devotion（Tab 3，`lib/devotion_page.dart` 685 行）

- 來源：WordPress REST `https://devotion.wkphc.org/wp-json/wp/v2/posts`，RSS `https://devotion.wkphc.org/feed` 作為 fallback（`test/devotion_site_fallback_test.dart`）
- 內容 parser（`lib/devotion_content.dart`，1167 行）：自製 HTML tokenizer → block 模型
  - `DevotionParagraph`、`DevotionHeading`、`DevotionQuote`、`DevotionImage`、`DevotionVideo`、`DevotionEmbed`、`DevotionSection`
  - 標題日期解析（`parseDevotionTitleDate`）、英文月份格式表、cache encode/decode
- 播放 / 顯示：
  - `devotion_youtube_player.dart`（387 行，含 seek邏輯，有 `test/youtube_seek_test.dart`）
  - `devotion_soundcloud_player.dart`（202 行）
  - `devotion_web_reader_io.dart`（256 行，WebView 後備閱讀器）
  - `devotion_image_io.dart`（40 行，圖片磁碟快取）
- UX：列表 / 文章頁、pull-to-refresh + stale 自動更新、複製全文、開外部連結、錯誤態
- Cache：`devotion_cache_v1`（存在 SharedPreferences，存 **raw post** 而非解析後 block，令 parser 改進可以重用舊 cache）

### 1.6 設定持久化總表（遷移依據）

| Key | 來源 | 型別 | 用途 |
|---|---|---|---|
| `app_theme_mode` | SharedPreferences | String(`light`/`dark`) | 主題 |
| `appearance_dark` | SharedPreferences | Bool | 舊版相容欄位（仍同步寫入） |
| `app_locale` | SharedPreferences | String(`zh-Hant`/`en`) | App 語言 |
| `navbar_style` | SharedPreferences | String(`material`/`materialBlur`) | 導覽列樣式 |
| `glass_perf_blocked` | SharedPreferences | Bool | 玻璃效能 fallback旗標 |
| `show_navbar` | SharedPreferences | Bool | 導覽列可見性 |
| `show_devotion` | SharedPreferences | Bool | Devotion tab 可見性 |
| `reader_book` / `reader_chapter` / `reader_mode` | SharedPreferences | String/Int/String | 閱讀位置 |
| scroll offset keys | SharedPreferences | Double | 每 book/chapter 偏移 |
| `devotion_cache_v1` | SharedPreferences | String(JSON) | 靈修快取 |
| `openrouter_model` | flutter_secure_storage | String | AI model id |
| `openrouter_api_key` / `..._verifier` / `..._method` / `..._pending_code` | flutter_secure_storage | String | OAuth |
| `memory.md` / `transcript.jsonl` | app files dir | File | AI 記憶 |

---

## 2. 建議原生架構

### 2.1 模組切分

多模組 Gradle 專案（repo root 即 Gradle root），Flutter 專案整個搬到 `legacy/flutter/` 凍結維護，直到原生版GA：

```
bible/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ gradle/libs.versions.toml
├─ app/ # com.marcow.bible — Application、MainActivity、AiChatActivity、DI入口
├─ core/
│  ├─ design-system/                 # AppTheme / AppColors / AppRadii / AppTypography / AppNavBar / IconButton / Segmented / TextInput / Markdown
│  ├─ model/                         # 純 Kotlin domain model（無 Android 依賴）
│  ├─ common/                        # Dispatchers qualifier、Result、logger
│  ├─ database/                      # Room DB、entities、DAOs、migrations、prepackaged asset
│  ├─ datastore/                     # SettingsDataStore（Proto）+ legacy SharedPreferences importer
│  ├─ network/                       # OkHttp/Retrofit、SSE parser、OpenRouter API、Devotion WP API
│  └─ legacy-migration/              # 讀舊 Flutter SharedPreferences / files / secure storage
├─ feature/
│  ├─ reader/                        # 讀經
│  ├─ library/                       # 書卷面板 / 舊約新約分段
│  ├─ search/                        # traditional + AI search
│  ├─ aichat/                        # AI 對話 + AI 設定
│  ├─ devotion/                      # 靈修列表 / 文章 / 播放器
│  └─ settings/                      # App 設定頁
└─ legacy/flutter/                   # 現有 Flutter 專案（凍結）
```

依賴方向：`app → feature/* → core/*`，`feature` 之間**不互相依賴**（共用地圖冊傳資料型別）。

### 2.2 分層（每個 feature module 內）

```
feature/aichat/
├─ ui/           Compose螢幕 + ViewModel (androidx.lifecycle.ViewModel + StateFlow)
│  ├─ AiChatScreen.kt, AiChatViewModel.kt, AiSettingsSection.kt
│  ├─ components/ MessageBubble.kt, ReasoningBlock.kt, ComposerBar.kt, ScriptureAttachmentChip.kt
│  └─ navigation/ AiChatNav.kt (type-safe routes)
├─ domain/        UseCase: AskQuestionUseCase, RegenerateAnswerUseCase, SearchScriptureUseCase
└─ (data 由 core:network + core:database 提供，不放 feature 內)
```

分層規則：
- **UI**：只識 `StateFlow<UiState>` / `UiEvent`，唔識 OkHttp、Room- **Domain**：純 Kotlin suspend function，無 Android 依賴 → 可 JVM unit test
- **Data**：Repository impl，Room / Retrofit / DataStore

### 2.3 技術選型

**建置 / 語言**

| 用途 | 選擇 |
|---|---|
| Gradle | AGP 8.9+，Kotlin DSL，Gradle 8.11+ wrapper |
| Kotlin | 2.1.x（K2） |
| Compose Compiler | `org.jetbrains.kotlin.plugin.compose`（跟 Kotlin 版本） |
| JDK | 17（CI 已用 temurin 17；現行 Flutter 專案係11，直接升17） |
| Version catalog | `gradle/libs.versions.toml` 單一來源 |
| minSdk | **26**（見 §6 風險）；`targetSdk 36`、`compileSdk 36` |

**AndroidX / Jetpack**

| 用途 | 選擇 |
|---|---|
| UI | `androidx.compose:compose-bom`、`material3`、`material-icons-extended`、`foundation`、`animation`、`ui-tooling-preview` |
| Navigation | `androidx.navigation:navigation-compose` 2.9.x + **type-safe routes**（kotlin serialization 產生 `AiChatDestination`） |
| ViewModel | `androidx.lifecycle:lifecycle-viewmodel-compose` 2.9.x + `lifecycle-runtime-compose`（`collectAsStateWithLifecycle`） |
| 依賴注入 | **Hilt 2.56** + `hilt-navigation-compose`（`hiltViewModel()`）、KSP |
| 資料庫 | **Room 2.7** + KSP，含 **FTS4**（見 §3.3） |
| 偏好設定 | **DataStore 1.1** Preferences（遷移成本低）或 Proto（型別安全，建議 Proto） |
| 網路 | **OkHttp 4.12** + **Retrofit 2.11** + `kotlinx-serialization` converter、`okhttp-sse`（自訂 `EventSource` 更貼合 OpenRouter 格式） |
| 序列化 | `kotlinx.serialization-json` 1.8 |
| 圖片 | **Coil 3**（`coil-compose` + `coil-network-okhttp`） |
| WebView | `androidx.webkit:webkit`（`WebViewCompat`）+ `AndroidView` 嵌入 Compose |
| Custom Tabs | `androidx.browser:browser`（OAuth） |
| 媒體 | `androidx.media3:media3-exoplayer`（SoundCloud 直串音訊）；YouTube 走 WebView iframe（保持現況） |
| 安全儲存 | `androidx.security:security-crypto`（`MasterKey` + `EncryptedSharedPreferences`）或 Keystore-wrapped DataStore |
| 模糊效果 | `Modifier.blur`（API 31+ `RenderEffect`）+ API 26–30 fallback（半透明 scrim），取代 `fog_edge_blur` |
| HTML 解析 | **Jsoup 1.18**（取代1167 行自製 tokenizer） |
| 工具 | `kotlinx-coroutines`、`androidx.core:core-splashscreen`、`androidx.startup`（Hilt WorkManager 若需要） |

**測試**

| 層 | 工具 |
|---|---|
| 單元 / UseCase | JUnit5 (`junit-jupiter`) + `kotlinx-coroutines-test` + **MockK** + **Turbine**（若堅持 JUnit4 + `kotlin-test` 亦可，但 JUnit5 較好） |
| Room | `androidx.room:room-testing` + Robolectric in-memory DB |
| Repository (Retrofit) | `okhttp3:mockwebserver`（`test/openrouter_service_test.dart` 已係 MockWebServer 風格，容易對齊） |
| Compose UI | `androidx.compose.ui:ui-test-junit4` + `createAndroidComposeRule` |
| Golden | **Paparazzi 1.3**（取代 `test/goldens/`） |
| HTML parser | 直接移植 `test/devotion_parser_test.dart` + `test/devotion_fixtures_test.dart` fixture 到 Jsoup 斷言 |

### 2.4 核心元件對照表

| Flutter 檔案 | 原生對應 |
|---|---|
| `lib/app_theme.dart` | `core/design-system/theme/AppTheme.kt`、`AppColors.kt`、`AppRadii.kt`、`AppTypography.kt`、`Shape.kt`（`springCurve = Cubic(0.16,1,0.3,1)` → `SpringSpec`/`CubicEasing`） |
| `lib/app_ui.dart`（1090 行） | `core/design-system/components/` — `AppButton`、`AppSegmented`、`AppTextInput`、`AppIconControl`、`ErrorPanel`、`Sheet` |
| `lib/app_navbar.dart` | `core/design-system/nav/AppFloatingNavBar.kt`（含 `draggable indicator` 用 `Animatable` + `offset` state） |
| `lib/app_markdown.dart` | `core/design-system/markdown/MarkdownText.kt`，後端用 `org.commonmark:commonmark` + 自製 Compose renderer（保持 bold / code / list / heading / link / blockquote 外觀一致） |
| `lib/localization.dart` | `androidx.compose.ui.res.stringResource`；`app_zh_Hant.arb` → `values-zh-rTW/strings.xml`，`app_en.arb` → `values/strings.xml`（見 §3.4） |
| `lib/app_settings.dart` | `core/datastore/SettingsRepository.kt` + `UserPreferences`（`StateFlow<AppSettings>`） |
| `lib/bible_data.dart` | `core/model/BibleBook.kt`、`Verse.kt`、`ScriptureHit.kt` + `core/database/BibleDao.kt` |
| `lib/ai_service.dart` | `feature/aichat/domain/` use cases + `core/network/openrouter/` |
| `lib/openrouter_service.dart` | `core/network/openrouter/OpenRouterAuthManager.kt`、`OpenRouterApi.kt`、`SseChatParser.kt` |
| `lib/ai_memory_store_io.dart` | `core/database/AiMemoryDao.kt` |
| `lib/devotion_content.dart` | `core/network/devotion/` + `feature/devotion/domain/HtmlToBlocks.kt`（Jsoup） |
| `lib/devotion_*.dart` | `feature/devotion/ui/`（YouTube/SoundCloud/WebView/Image loader） |

---

## 3. 資料遷移

### 3.1 經文資料（CUV / WEB）

**現況**：132 個 JSON 檔（每檔一卷，含 `chapters` map），總 7.4 MB，`rootBundle.loadString` 整個 parse進記憶體 cache（`_assetCache`）。

**建議：改用 pre-packaged Room（SQLite）**

1. 建 `core/database/BibleDb.kt`，`version = 1`，`exportSchema = true`
2. 離線建庫工具（Gradle task 或 `tool/` 內 Kotlin/Node script）讀 `legacy/flutter/assets/bible/{cuv,web}/*.json`，寫出 `app/src/main/assets/databases/bible.db`
3. `Room.databaseBuilder(...).createFromAsset("databases/bible.db")`（**唔好** 用 `fallbackToDestructiveMigration`，經文係靜態資料）
4. `android { assetPacks }` / AAB `androidAsset` 自動壓縮（SQLite 文字內容壓縮率一般，預期 DB 約 6–7 MB，與現況持平）
5. `tool/verify_bible.mjs` 邏輯移植成 `core/database` 的 Room instrumentation test（66 卷 / 1189 章 / 每章非空）

**Schema**

```sql
CREATE TABLE books (
  id         TEXT PRIMARY KEY,      -- 'GEN' … 'REV'
  ordinal    INTEGER NOT NULL,      -- 1..66
  name_zh    TEXT NOT NULL,
  name_en    TEXT NOT NULL,
  chapters   INTEGER NOT NULL,
  testament  INTEGER NOT NULL       -- 0=舊約 1=新約
);

CREATE TABLE verses (
  book_id  TEXT    NOT NULL REFERENCES books(id),
  chapter  INTEGER NOT NULL,
  verse    INTEGER NOT NULL,
  text_cuv TEXT,                    -- CUV 缺節時為 NULL
  text_web TEXT,                    -- WEB 缺節時為 NULL
  PRIMARY KEY (book_id, chapter, verse)
) WITHOUT ROWID;

CREATE TABLE reading_progress (
  book_id      TEXT PRIMARY KEY REFERENCES books(id),
  chapter      INTEGER NOT NULL,
  verse        INTEGER,
  scroll_ratio REAL NOT NULL DEFAULT 0, -- 正規化 offset，避開不同螢幕高度問題
  mode         TEXT NOT NULL, -- 'chinese' | 'english' | 'bilingual'
  updated_at   INTEGER NOT NULL
);
```

> 現況是每 (book, chapter) 存**絕對像素** offset（`lib/main.dart:321`）。原生版改存 `scroll_ratio = offset / maxScrollExtent`，跨裝置/字體縮放更穩。若要無縫延續舊 offset，第一次讀到舊值時換算成 ratio。

**Dao（`BibleDao.kt`）**

- `@Query("SELECT * FROM books ORDER BY ordinal") suspend fun books(): List<BookEntity>`
- `@Query("SELECT * FROM verses WHERE book_id = :book AND chapter = :chapter ORDER BY verse") suspend fun chapter(book, chapter): List<VerseEntity>` → domain mapper 補齊兩版本（`VersePair(number, zh, en)` 對齊邏輯完全照 `BibleRepository.chapter()`：取 `max(cuv.size, web.size)`）
- `@Query("SELECT v.* FROM verses v JOIN books b ON b.id = v.book_id WHERE b.testament = :t ORDER BY b.ordinal") suspend fun indexByTestament(t)`（書卷面板 / 抽屜）
- `@Query("UPDATE reading_progress …")` / `@Query("SELECT * FROM reading_progress")`

### 3.2 全文檢索（FTS）

現況 `BibleRepository.search()` 每次掃全書 31,102×2 節 `contains`。原生要兩條路：

**主線：FTS4 virtual table（中文 trigram問題要處理）**

```sql
CREATE VIRTUAL TABLE verses_fts USING fts4(
  text_cuv, text_web,
  book_id NOT NULL, chapter NOT NULL, verse NOT NULL,
  tokenize = trigram          -- SQLite >= 3.34
);
```

問題：Android 內建 SQLite 版本隨 OS 變動，`trigram` tokenizer未必存在。**對策**：

1. 執行期能力檢測：讀 `SELECT sqlite_version()`；>= 3.34 用 FTS4 trigram
2. 不支援時 fallback：維護 `verses_ngram` 索引（每節切 3-gram 寫入 `ngram_index(term, book_id, chapter, verse)`），中文 substring 查詢走這個
3. 英文大小寫不敏感 + 詞首比對交由 FTS 處理如果想簡單一點、避免兩套：**先出 Phase 3 用 `LIKE` + `Dispatchers.IO` + 背景預建 `verses_search`（每節 text 併一欄，加 UNINDEXED rowid 快取）**，量測後再決定升 FTS。31K 節的 `LIKE '%x%'` 在 SQLite 上約 100–200 ms，配上 debounce + 進度條已經夠用 —— 我傾向**先 LIKE，FTS 係 Phase 6 優化項**。

### 3.3 字型（18 MB → 目標< 6 MB）

| 字型 | 處理 |
|---|---|
| `openrunde_regular.otf` / `openrunde_medium.otf` | 直接搬去 `res/font/`（`Font(R.font.openrunde_regular)`），對應 weight 400 / 500 |
| `exposure_variable.otf` | Variable font。Compose 用 `Font(res/font/exposure_variable.ttf, weight, style, variationSettings = FontVariation.Settings(FontVariation.weight(w)))`，**需要 minSdk 26+**（API 26 以下 variable font 會 fallback 到 default instance） |
| `noto_serif_tc_variable.ttf` | 呢個係大頭（CJK 可到 8–12 MB）。用 `fonttools` **subset**：pyftsubset 只保留實際用到的 CJK 字元（可用 `assets/bible/cuv/*` + `web/*` + arb 字串 + devotion 快取做字元集合，估計 4,000–6,000 個漢字）+ ASCII + 標點 → 預期 1.5–3 MB |
| AAB 分發 | 若仍嫌大，改用 Play **Asset Delivery**（`assetPacks` / `dynamic-delivery`），或乾脆出多個 density/size variant |

### 3.4 l10n（arb → Android resources）

`l10n.yaml` 定義：`template = app_zh_Hant.arb`，所以 `AppLocalizations` 的 key 以中文版為準。

步驟：
1. 寫 `tool/arb_to_strings.mjs`：讀 `app_zh_Hant.arb` → `res/values-zh-rTW/strings.xml`；讀 `app_en.arb` → `res/values/strings.xml`（放喺 `core/design-system/src/main/res/`：`android.nonTransitiveRClass=true`之下，宣告喺 `app` 嘅 string 係 `feature/*` 睇唔到嘅）
2. Key命名：**保留 arb 的 camelCase key 不變**（例如 `selectBook`、`tabAsk`、`followUpHint`），但 Android 慣例是 snake_case。建議一次性轉換 `selectBook` → `select_book`，並在 script 內建 map，重跑穩定
3. **具名參數**：跟 `context.l10n.chapterNumber(value)`、`followUpHint(ref)`、`selectChapterCurrent(ch)`、`traditionalResultCount(n)`、`aiResultCount(n)` → Android `stringResource(R.string.chapter_number, n)`，佔位符用 `%1$s` / `%1$d`
4. `app_zh.arb`（簡體）目前無對應原生 locale：`values-zh-rCN/strings.xml` 可留作將來，Phase 1 先只出 `values/` + `values-zh-rTW/`
5. 語言切換：`AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-Hant"))` 或 per-app locale（AGP `androidResources.localeFilters += "zh-rTW"` + `resourceConfigurations`），Compose 端用 `LocalConfiguration.current.locales`
6. 新增 lint 防回歸：CI 加一條「兩個 arb key set 必須完全一致」檢查

### 3.5 圖示 /啟動畫面

- `assets/icon.png` → `flutter_launcher_icons` 產生嘅 `res/mipmap-*/ic_launcher.png` + `drawable-*/ic_launcher_foreground.png` **已經存在**，直接沿用（毋須重新生成）
- `mipmap-anydpi-v26/ic_launcher.xml` 改為標準 adaptive icon 寫法- 需新增：`ic_launcher_round`、monochrome icon（Android 13+ themed icon，用 `assets/icon.svg` 轉 monochrome layer）、`ic_shortcut_ai_chat`（dynamic shortcut 專用，唔好再借用 app icon —— 現況 `MainActivity.kt` `setAiShortcut` 用 `R.mipmap.ic_launcher`，原生版改用獨立 shortcut icon 更靚）
- `res/values/styles.xml` / `values-v31` / `values-night` 換成 `Theme.Bible.Splash`（`core-splashscreen`）→ `Theme.Bible`

### 3.6 舊使用者資料遷移（in-place upgrade）

因為 `applicationId` 保持 `com.marcow.bible`、簽章用**同一把 keystore**（CI secret 已存在），原生版可覆蓋安裝 → 需要在首啟時搬舊資料。

**SharedPreferences**：Flutter 寫入 `<dataDir>/shared_prefs/FlutterSharedPreferences.xml`，key 全部有 `flutter.` 前綴。

`core/legacy-migration/LegacyPrefsImporter.kt`：

```
val prefs = context.getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
fun s(k: String) = prefs.getString("flutter.$k", null)
fun b(k: String) = prefs.getBoolean("flutter.$k", false)
```

對應搬到 DataStore（`settings.preferences_pb`）：

| 舊 key | 新欄位 |
|---|---|
| `app_theme_mode` | `Settings.theme_mode`（`LIGHT`/`DARK`；冇就 fallback `appearance_dark` → 再 fallback系統 brightness） |
| `app_locale` | `Settings.locale_tag`（`zh-Hant`/`en`） |
| `navbar_style` | `Settings.navbar_style` |
| `glass_perf_blocked` | `Settings.glass_perf_blocked`（原生版改用 `Modifier.blur`，此旗標改為「使用者強制關閉模糊」語意，預設 false） |
| `show_navbar` / `show_devotion` | 同名欄位 |
| `reader_book` / `reader_chapter` / `reader_mode` | `reading_progress` 表（並轉換 scroll offset → ratio） |
| scroll offset keys | 需要 Flutter 端 key 命名規則；`lib/main.dart:321` 的 `_scrollKey` → 直接照抄命名規則讀取 |
| `devotion_cache_v1` | `DevotionCacheEntity`（先照抄 JSON，Parser 升級後自然 re-parse，同現況設計一致） |

**AI 記憶檔**：`context.filesDir/memory.md` + `transcript.jsonl` → 首次啟動時讀入 Room `ai_memory` / `ai_messages`，成功後 rename 成 `.migrated`（保留 rollback）。呢個成本低、體感好，**必做**。

**flutter_secure_storage**：Android端係 `EncryptedSharedPreferences`，檔名 `FlutterSecureStorage.xml`，用 **Tink**（`__androidx_security_crypto_encrypted_prefs_*`）加密，master key 存在 `shared_prefs/__androidx_security_crypto_encrypted_prefs_key_keyset__`。理論上可用同一個 Tink `AndroidKeysetManager` + `MasterKeys` 讀取，但**依賴 `flutter_secure_storage` 的 encryptor 設定細節 + keyset 格式**，脆弱。

**決策（見 §6 Q1）**：Phase 4 先做「嘗試讀取 → 失敗就 report、要求重新登入」。最壞情況係使用者要重新按一次 OpenRouter 登入，可接受。

---

## 4. Bible AI 原生重寫

### 4.1 分層

```
core/network/openrouter/
├─ OpenRouterAuthManager.kt     # PKCE + Custom Tabs + deep link + pending code
├─ OpenRouterApi.kt             # Retrofit interface├─ dto/ChatCompletionRequest.kt # 全部欄位 optional（不同模型支援度不同）
├─ dto/ChatCompletionChunk.kt   # SSE delta: content / reasoning / finish_reason / annotations
├─ ChatEvent.kt                 # sealed: ContentDelta, ReasoningDelta, FinishReason, WebCitation, Failure
└─ SseChatSource.kt             # Flow<ChatEvent>（OkHttp ResponseBody → SSE frame parser）
```

**請求 DTO 對照現況**（`lib/openrouter_service.dart:335` `_send`）

| OpenRouter 欄位 | 值 |
|---|---|
| `model` | 使用者 `openrouter_model`，預設 `openrouter/free` |
| `messages` | 單一 user message（`{role:"user", content: prompt}`） |
| `temperature` | `0.2` |
| `max_tokens` | structuredSearch `2400` / shortSearchOverview `1200` / 否則 `options.maxTokens` |
| `stream` | 依呼叫路徑 |
| `reasoning` | 一般 `OpenRouterRequestOptions.reasoning` → `{max_tokens, exclude:false}`；overview → `{enabled:false, exclude:true}`；structured → `{max_tokens:32, exclude:true}` |
| `tools` | `[{type:"openrouter:web_search", parameters:{engine:"auto", max_results, max_uses, max_total_results, search_context_size}}]` |
| `tool_choice` | forced → `{type:"openrouter:web_search"}`，否則 `"auto"` |
| `max_tool_calls` | `options.maxToolCalls` |
| `response_format` | structuredSearch → `json_schema`（`strict:true`，properties 見 §1.3） |
| `stream_options` | 加 `{include_usage:true}`，用嚟顯示 token 用量（可選 enhancement） |

### 4.2 Streaming

`OkHttp` `Call.enqueue` → `response.body!!.source()`，`SseChatSource.kt` 逐行讀 `data: {...}`，`kotlinx.serialization` parse 成 `ChatCompletionChunk`，emit `ChatEvent`。Compose端 `ViewModel` 用 `MutableStateFlow<String>` **逐 token append**（buffer + `conflate`），UI 用 `SnapshotStateList` 避免整個 recomposition。

三種 delta 要分流（對應 `ChatEvent`）：
- `choices[0].delta.reasoning` → 思考區塊（可摺疊）
- `choices[0].delta.content` → 正文 Markdown
- `choices[0].finish_reason == "length"` → `AiMessage.incomplete = true` → UI 顯示「回覆已停止或尚未完整」

### 4.3 Prompt / Use case

**Prompt 一律 verbatim搬運**，唔好改寫 ——呢啲係經過調校嘅 magic prompt，改一個字行為就會變。用 Kotlin `raw string`（`"""..."""`）放喺 `core/network/openrouter/prompts/` 或 `feature/aichat/domain/Prompts.kt`。

| Prompt marker | 用途 | 對應 UseCase |
|---|---|---|
| `BIBLE_SEARCH_OVERVIEW` | 搜尋概覽（禁 reasoning、`max_tokens 1200`） | `SearchOverviewUseCase` |
| `BIBLE_SEARCH_REFERENCES_JSON` | 搜尋引文（json_schema、`max_tokens 2400`） | `SearchReferencesUseCase` |
| 對話 system prompt | 語言 = `natural Traditional Chinese` / `natural English`（`AppLocale.aiLanguage`） | `AskQuestionUseCase` |

**structuredSearch 偵測**：現況係 `prompt.contains("BIBLE_SEARCH_REFERENCES_JSON")` 或 `contains("\"overview\"") && contains("\"scriptures\"") && contains("Return ONLY valid JSON")` —— 呢個係 smell，native 版改成明確 enum：

```kotlin
enum class PromptKind { Chat, SearchOverview, SearchReferences }
sealed interface AiRequest {
  data class Chat(val prompt: String, val reasoning: Boolean = true) : AiRequest
  data object SearchOverview : AiRequest
  data object SearchReferences : AiRequest
}
```

`AiRequest → OpenRouterRequestOptions` 係 pure function，unit test 覆蓋 4 種組合（呢個直接對應 `test/openrouter_service_test.dart`）。

### 4.4 `get_scripture` 偽工具迴圈

現況（`lib/ai_service.dart:471-502`）：模型可能回 `{"tool":"get_scripture","bookId":..,"chapter":..,"verseStart":..,"verseEnd":..}`，App 執行本地查經，把結果 append 到 prompt 再送，最多 3 輪；仍回 tool → 最後一次強制「用權威結果作答」。

原生：`AskQuestionUseCase` 用一個 `repeat(3)` + `while` 結構，`ScriptureToolRunner` 依賴 `BibleDao`。保持同一個 fallback 語句（英文原文照抄）。

### 4.5 Web search fallback

現況關鍵行為：某些 routed model **唔支援同 reasoning + tools 並用**，第一次請求失敗會 retry一次不帶 tool 的請求。呢個 fallback **一定要保留**，否則用 `openrouter/free` router 會大量失敗。實作：

```kotlin
suspend fun ask(req: AiRequest): AiResponse = try {
    api.chat(toOpenRouterRequest(req, withWebSearch = true))
} catch (e: OpenRouterUnsupportedToolException) {
    api.chat(toOpenRouterRequest(req, withWebSearch = false))  // 保留同一段 reasoning 語義
}
```

`research()`（forced web search、`max_tool_calls 1`、`search_context_size "low"`、`max_tokens 8192`）→ parse `message.annotations` → `WebResearchSource[]`，並檢查 `usage.server_tool_use.web_search_requests >= 1`，否則拋錯（照 `lib/openrouter_service.dart:265`）。

### 4.6 AI 記憶

| 現況 | 原生 |
|---|---|
| `filesDir/memory.md`（分節 Markdown） | Room `ai_memory(id, section, content, sort_order, updated_at)`；匯出成 Markdown 給 prompt（`promptMemory(maxCharacters)` 逾越取尾段） |
| `filesDir/transcript.jsonl` | Room `ai_messages(id, role, text, reasoning, scripture, kind, web_search, incomplete, created_at)` |
| `transcript(limit: 24)` | `@Query("SELECT … ORDER BY created_at DESC LIMIT :limit")` |
| web 版用 `localStorage` | 已無需（唔再支援 web） |

清除對話 = `@Query("DELETE FROM ai_messages")` + 清空 memory 特定 section。

### 4.7 OAuth（`OpenRouterAuthManager`）

```
beginSignIn()
  → 產生64 bytes Random.secure() → base64Url no-padding = code_verifier
  → 寫 EncryptedSharedPreferences: openrouter_pkce_verifier / openrouter_pkce_method("plain")
  → 刪 openrouter_pending_code
  → CustomTabsIntent 開 https://openrouter.ai/auth?callback_url=bible://openrouter/callback
        &code_challenge=<verifier>&code_challenge_method=plain
onNewIntent(intent) [scheme=bible, host=openrouter, path=/callback]
  → error/error_description → 顯示錯誤
  → code 為空 → 錯誤
  → code 已處理過（_handledCodes）→ 忽略
  → 寫 openrouter_pending_code → retryPendingExchange()
POST https://openrouter.ai/api/v1/auth/keys { code, code_verifier, code_challenge_method }
  → 2xx 且有 "key" → 寫 openrouter_api_key，刪 verifier/method/pending_code
  → 否則拋錯（截斷 240 字元，與現況一致）
```

`pending_code` 落盤係為了處理「App 被殺後回來」—— 原生保留，但改用 `MainActivity.onCreate` + `onNewIntent` 兩處都觸發一次 retry（去重）。

### 4.8 獨立 Activity 與 Shortcut

現況 `AiChatActivity` 存在意義：從長按 launcher icon / dynamic shortcut / home screen widget 直接開 AI 對話，獨立 task、独立 initial route、`EXTRA_LAUNCH_PAYLOAD` 帶經文附件。

原生保留：
- `app/src/main/kotlin/com/marcow/bible/AiChatActivity.kt`，`android:exported="false"`、`launchMode="singleTask"`、`taskAffinity` 獨立
- `MainActivity` 的 `forwardOAuthToChat()` 邏輯保留（`AiChatActivity.isAlive` static flag）
- `ShortcutManager.addDynamicShortcuts(shortcutId = "bible_ai_chat")` 保留 id（避免已安裝用戶見到舊 shortcut 消失），改用專屬 `ic_shortcut_ai_chat`
- `Intent` 傳經文附件：唔再用 Base64 JSON，改為 typed `Parcelable`（`ScriptureRefParcelable`）；同時保留讀舊 base64 格式以防 shortcut殘留

### 4.9 內嵌與 fallback

`AiChatScreen` 內嵌 `AndroidView { WebView }`，用於：
- 未來擴充的 richer content（若需要）
- `WebViewCompat.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`
- 記得處理 `onPause`/`onResume`/`onDestroy` 避免 WebView leak（記於 §6 風險）

---

## 5. Phased Migration 策略

### Phase 0 — 地基（1週）

**交付**：全新 Gradle 多模組專案跑得起来，`app` 顯示靜態殼- 建 `settings.gradle.kts` / `gradle/libs.versions.toml` / `gradle.properties`（開 `android.nonTransitiveRClass`、`android.nonFinalResIds`）
- 8 個 `core/*` + 6 個 `feature/*` + `app` 骨架
- Hilt + KSP + Compose compiler 接通
- `app` 簽章設定照抄 `android/app/build.gradle.kts`（env keystore，alias 預設 `bible`）
- `namespace = "com.marcow.bible"`、`applicationId` 不變
- 把現有 Flutter 專案整個搬到 `legacy/flutter/`
- **CI**：新 `.github/workflows/android-ci.yml`（PR 上跑 ktlint + detekt + unitTest + lintDebug + assembleDebug）；`release-apk.yml` 先**保持現有 Flutter 版不動**，Phase 6 才切

**Exit criteria**：`./gradlew :app:assembleDebug` 出得 APK，Hilt injection 正常，CI綠。

---

### Phase 1 — 資料層 + Design System +設定（2 週）

**交付**：App 開得出主題正確嘅設定頁，資料庫已建好

1. **資料庫**：`BibleDb` + entities + Dao + pre-packaged `bible.db` + `verify` 測試（66 卷 / 1189 章）
2. **Design System**：`AppTheme`（light/dark + `AppColors`/`AppRadii` theme extensions 移植自 `app_theme.dart`）、字型 subset（§3.3）、圖示
3. **l10n**：`tool/arb_to_strings.mjs` 產出 `values/` + `values-zh-rTW/`，~120 個 string
4. **DataStore**：`SettingsRepository` + Proto + `LegacyPrefsImporter`
5. **`feature/settings`**：外觀 / 語言 / navbar 樣式 / navbar 可見性 / devotion 可見性 / 版本號
6. **Migration**：舊 SharedPreferences 一次性匯入（含 `appearance_dark` legacy fallback）

**Exit criteria**：用 Flutter 版改過設定的裝置，覆蓋安裝原生版後設定100% 延續。

---

### Phase 2 — 讀經（Tab1）（2 週）

**交付**：讀經功能 100% parity

- `feature/reader`：`ReaderScreen`、`ReaderViewModel`、`BibleRepository`
- `ReadingMode { chinese, english, bilingual }` segmented
- 逐節渲染、skeleton 載入、scroll進度寫 Room（含舊 offset → ratio 轉換）
- 節號 + `SelectableText`，長選動作列：複製經文 / 問 AI / 解釋經文
- `ChapterPickerBubble`（Compose `Popup`）、上一章 / 下一章
- `feature/library`：`_Sidebar` 抽屜 + `LibraryRoute`（`navigation-compose`）+ 舊約新約 segmented
- `feature/navigation`：底部 pill nav（暫時只有 Bible tab）+ blur/solid 兩樣式（`Modifier.blur` API 31+ / scrim fallback）+ `showNavbar=false` 時 top bar 顯示 Ask/Devotion 快捷
- Paparazzi golden：對齊 `test/goldens/` 現有 golden

**Exit criteria**：`test/widget_test.dart` + `test/app_ui_test.dart` 覆蓋的行為在原生版有對應測試通過。

---

### Phase 3 — 搜尋（1 週）

**交付**：傳統搜尋 + AI 搜尋（含 overview / references 並行）

- `feature/search`：`SearchSheet`（底部 sheet 或 dialog）、`_SearchModeSegmented`
- Traditional：`BibleDao.searchContains()`（LIKE）+ debounce + `limit 80` + loading/error
- AI 模式：
  - `SearchOverviewUseCase` / `SearchReferencesUseCase` → `kotlinx.coroutines.async` **並行**（對應 `Future.wait`）
  - `response_format.json_schema` schema 用 `buildJsonObject` 手寫（與 `openrouter_service.dart` 逐字一致）
  - UI：`AiSearchLoading`（概覽 / 引文兩條獨立進度）、結果數量 label、`SearchHitTile` 點擊跳讀經頁
- FTS4 優化列為 Phase 6 選項

**Exit criteria**：兩種搜尋模式行為一致，AI 搜尋失敗時顯示明確錯誤面板（`ReferenceFailure { request, verses }` 對應兩個 error state）。

---

### Phase 4 — Bible AI（2.5 週）

**交付**：AI 對話 100% parity

**AI provider（硬性要求，唔係二選一）**：native 版必須同時支援兩個 provider ——

| Provider | 實作 | 定位 |
|---|---|---|
| **OpenRouter**（**預設**） | `core/network/openrouter/`（§4.1–§4.7 原樣保留） | 需要登入 + API key；能力最全（web search、reasoning、structured search） |
| **Gemini Nano**（on-device） | 新增 `core/network/aicore/`，用 Google Play services for AI Edge（AICore） | 免登入、離線可用；device / hardware 閘門要反映落 `AiAvailability` |

- `AiProvider`（`OpenRouter` / `GeminiNano`）係 domain 層依賴嘅 abstraction：`AiRequest` / `AiResponse` / `ChatEvent`（§4.1、§4.3）對兩個 provider 都要成立，`PromptKind` 同 prompt 原文共用 —— **唔准為咗遷就 on-device 模型而改 prompt**（§4.3 magic prompt 警告）。
- **Provider 選擇 UX**：放喺 AI 設定區（§1.4 `_SettingsSection` 同一位置），同 Flutter 版一致 —— 單一「AI provider」選擇器，下面只顯示當前 provider 嘅相關設定（OpenRouter 先顯示連線狀態 / 登入登出 / `modelId`；Gemini Nano 先顯示裝置支援狀態）。
  - ⚠️ 現況 Flutter 版（`lib/ai_chat_page.dart`）**只有 OpenRouter，根本冇 provider selector**，所以呢度係新設計而唔係照抄；準則係沿用 Flutter 版 AI 設定區嘅版面、錯誤面板同 empty state 處理，令用戶睇落係同一個 settings screen。
- **預設 = OpenRouter**：`ai_provider` 缺值時 fallback 去 `OpenRouter`，確保 Phase 4 收尾時既有 OpenRouter 用戶行為 100% 不變。
- Provider 差異要收埋喺 `AiRequest → provider request` mapping 層，唔好漏落 UI：`get_scripture` 偽工具迴圈、web search fallback（§4.5）、reasoning、incomplete 標記對兩個 provider 都要有定義清楚嘅行為（Gemini Nano 冇 web search tool 就直接走無 tool 路徑，並喺 UI 隱藏 web search 相關 affordance）。

1. `OpenRouterAuthManager` + Custom Tabs + deep link（`bible://openrouter/callback` intent-filter 原樣保留）
2. `OpenRouterApi` + `SseChatSource` → `Flow<ChatEvent>`
3. `AiChatViewModel`：訊息列表、streaming、stop、regenerate、copy、clear、go-to-latest 自動捲動
4. **Markdown 渲染**：commonmark + 自製 Compose renderer（對齊 `AppMarkdown`）
5. 思考內容摺疊 / incomplete 標記
6. `get_scripture` 偽工具迴圈 + web search fallback
7. 經文附件（從讀經頁帶 payload 過來）+ follow-up hint
8. AI 設定區：連線狀態 / 登入登出 / `modelId` 輸入 /清除對話
9. `AiChatActivity` + dynamic shortcut + `ic_shortcut_ai_chat`
10. `memory.md` + `transcript.jsonl` → Room 遷移
11. **安全儲存遷移**：嘗試讀 `FlutterSecureStorage.xml`，失敗則要求重新登入（§6 Q1）

**Exit criteria**：`test/ai_service_test.dart`（586 行）+ `test/openrouter_service_test.dart`（233 行）+ `test/openrouter_oauth_test.dart`（97 行）的行為全部在 Kotlin 有對應測試；`test/ai_chat_ui_test.dart`（616 行）覆蓋的 UI 狀態齊。

---

### Phase 5 — Devotion（2 週）

**交付**：靈修默想 100% parity

- `core/network/devotion/`：WP REST client（OkHttp + kotlinx.serialization）+ RSS fallback（用 `xmlpullparser` 或 Jsoup XML mode）
- `feature/devotion/domain/HtmlToBlocks.kt`：**Jsoup 移植版 parser**，輸出 `DevotionBlock` sealed（`Paragraph`/`Heading`/`Quote`/`Image`/`Video`/`Embed`/`Section`）
  - 對齊 `devotion_content.dart` 的 `_kDevotionHeaders`、`_kContentClassNames`、`_voidElements`、標題日期解析、英文月份
  - 測試：**直接把 `test/fixtures/` 的 fixture 拿來跑**，確保行為一致（`devotion_parser_test.dart` 270 行 + `devotion_fixtures_test.dart` 228 行 + `devotion_site_fallback_test.dart` 128 行）
- UI：列表（`LazyColumn`）+ 文章頁（`LazyColumn` of blocks）、pull-to-refresh、stale 自動更新、複製全文、外鏈
- 媒體：
  - YouTube → `AndroidView(WebView)` + iframe embed（保留 `test/youtube_seek_test.dart` 的 seek邏輯，冇對應 library 就自己寫時間計算）
  - SoundCloud → `Media3 ExoPlayer`（若 embed 只得 iframe 就同樣走 WebView；見 §6 Q4）
  - 圖片 → Coil 3 + disk cache（對應 `devotion_image_io.dart`）
  - fallback閱讀器 → `AndroidView(WebView)`（對應 `devotion_web_reader_io.dart`）
- Cache：`devotion_cache_v1` → Room `devotion_cache`（存 raw post JSON，語意同現況：parser 改進自動 re-parse）

**Exit criteria**：靈修頁 fixture 解析結果與 Flutter 版逐節點一致。

---

### Phase 6 — Polish / Release（1.5 週）

- **FTS4 trigram** 或 n-gram 索引（若 Phase 3 的 LIKE 不夠快）
- Perf：baseline profile / Macrobenchmark（啟動時間、捲動 jank —— 特別係 blur navbar）
- 模糊效果 fallback全面驗證（API 26/30/31+ 三檔）
- 圖標 monochrome layer、splash screen、`predictive back`（`android:enableOnBackInvokedCallback="true"` 已在 manifest，Compose 要接 `BackHandler`）
- 全面 `ktlint` + `detekt` + Android Lint 清零
- **CI 切換**：`release-apk.yml` 改成 `./gradlew :app:bundleRelease` / `assembleRelease`，同樣用 `ANDROID_KEYSTORE_*` secrets，同樣輸出 `bible-<version>-release.apk` + `.sha256`
- APK/AAB 大小檢查（目標 ≤ 40 MB，理想 ≤ 25 MB）
- 移除 `android:name="${applicationName}"` / flutter embedding meta-data / `EnableImpeller=false`
- Web 版決策（§6 Q2）

**總計約 12 週**（單人全職），若部分內容並行可壓到 9–10 週。

---

## 6. 風險與待確認問題

### 高風險

| # | 風險 | 影響 | 緩解 |
|---|---|---|---|
| R1 | **`flutter_secure_storage` 資料搬唔到**（Tink keyset 格式 + encryptor 細節耦合） | 現有使用者要重新登入 OpenRouter | ① 實作讀取器 + 失敗 fallback 到要求重登 ② Phase 4 前先做 spike 驗證可行性 ③ 就算失敗體驗也只是多按一次登入 |
| R2 | **簽章 / applicationId 必須一致** | 唔一致 = 用戶要重新下載 + 資料全失 | `applicationId` 保持 `com.marcow.bible`、CI 用同一組 `ANDROID_KEYSTORE_*` secret；Phase 0 就要用真 keystore 做一次 smoke install 驗證 |
| R3 | **Variable font 在 minSdk < 26 無法用 `FontVariation`** | `exposure_variable.otf` / `noto_serif_tc_variable.ttf` 版面走樣 | minSdk 直接訂 **26**；並準備 static instance 靜態 fallback（用 fonttools `instancer` 產生 weight 400/500 兩份 static） |
| R4 | **Jsoup parser 行為 ≠ 自製 tokenizer** | Devotion 文章渲染差異（少段落、圖片消失、表格爆版） | 用 `test/fixtures/` 逐個 fixture 做 golden 對比；保留自製 tokenizer 作為 fallback 直到測試全綠 |
| R5 | **Scroll offset 語意變更（像素 → ratio）** | 覆蓋安裝後跳到唔同位置 | Phase 2 加一次性轉換：讀舊像素值時除以當時的 `maxScrollExtent`（需記錄該值，見 Q5） |
| R6 | **OpenRouter free router行為不穩**（工具支援度、JSON嚴格模式） | AI 功能隨時壞 | web search fallback + incomplete 標記 + 明確錯誤面板；prompt 與 fallback邏輯 verbatim 搬運，唔重寫 |

### 中風險

| # | 風險 | 緩解 |
|---|---|---|
| R7 | **模糊效果（`fog_edge_blur` → `Modifier.blur`）外觀差異** | 三個 API level 人工驗證；26–30 提供 scrim fallback；`glassPerfBlocked` 旗標改語意為「使用者強制關閉模糊」 |
| R8 | **WebView 洩漏 /崩潰**（YouTube、SoundCloud、fallback 閱讀器） | `AndroidView` 內正確 `onPause`/`onResume`/`onDestroy` + `remember { }` 持有；WebView 數量用 `key` 控制生命週期；`WebViewCompat` 更新 |
| R9 | **YouTube / SoundCloud 第三方 embed 政策變動** | 抽象成 `DevotionMediaPlayer` interface，WebView / ExoPlayer 兩種 impl 可換 |
| R10 | **APK膨脹**（18 MB 字型 + 7.4 MB 經文 + Compose + Hilt） | fonttools subset + pre-packaged SQLite + AAB + Asset Delivery（見 §3.3）；設定 CI size gate |
| R11 | **Compose全面 recomposition**（長經文章節176 節 × 雙語） | `LazyColumn` + `key(verse)` + 穩定 data class + `derivedStateOf`；用 Macrobenchmark 量測 |
| R12 | **測試基礎設施要全部重建**（Flutter test → JVM/Compose/Paparazzi） | 逐階段 port，每個 Phase 的 exit criteria 都包含對應測試綠燈；fixture / golden 直接複用 |
| R13 | **單人知識集中在一人身上**（17.8k 行 Dart 隱含規則） | Phase 0 先寫 `docs/`：prompt 逐字備份、AI request 選項決策表、parser 規則表 |

### 待確認問題（Open Questions）

1. **Q1 — AI 資料遷移 vs 強制重登？** `flutter_secure_storage` 的 `FlutterSecureStorage.xml` 值得做 spike 嗎？定係接受「首次啟動要求重新登入 OpenRouter」？（影響 Phase 4 約 0.5 週）
2. **Q2 — Web 版去留？** 現況有 `deploy-web.yml` + Vercel 部署 + `localStorage` 版 memory store（`ai_memory_store_web.dart`、`oauth_callback
