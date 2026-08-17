# nSearch — 本地全文文件搜索器

> 一款 Android 应用，基于 Apache Lucene 对设备上的本地文件做**多语言全文索引与搜索**，类似 Google 的「包含全部/任一关键词」式检索。

## 1. 功能对照（需求 → 实现）

| 需求 | 实现位置 |
| --- | --- |
| 搜索本地文件（txt 等） | `FileScanner` 扫描主外部存储 + SAF 添加的文件夹；`TextExtractor` 抽取文本 |
| 内置 Lucene | `LuceneManager` 封装 `org.apache.lucene:lucene-core:8.11.3` |
| 支持多种语言 | `MultiLangAnalyzer` 使用 `ICUTokenizer`（ICU 分词），中/日/韩/英/欧语统一处理 |
| 类似谷歌的全文搜索 | `SearchEngine`：每个词在「内容 + 文件名（加权）」双字段匹配，按模式设定 `minimumShouldMatch` |
| 索引时同时进行搜索 | `IndexWriter` + `SearcherManager` 近实时（NRT）搜索；索引线程写、搜索线程读，互不影响 |
| 清晰的搜索/索引进度 | 主页进度卡片（已索引 X/Y、当前文件）+ 前台服务通知（每 500ms 刷新） |
| 保存进度、重开增量同步 | `IndexDatabase` 记录每个文件的 size/lastModified；重开时未变更文件直接跳过，已删除文件自动清理 |
| 支持 IN / OR 等 | 主界面「与(AND)/或(OR)」芯片；严格=全部包含，宽松=任一包含 |
| 默认搜索模式：严格 / 中等 / 宽松 | 设置项 `search_mode`；中等=通常全部匹配但词数>2 时允许少匹配 1 个 |
| 同义词模式 | 设置项 `synonym_enabled`；`res/raw/synonyms.txt` 经 `SolrSynonymParser` 构建 `SynonymMap`，查询时叠加 `SynonymGraphFilter` |
| 触发索引 / 删除索引 | 设置项 `trigger_index` / `delete_index`；主界面菜单「立即索引」亦可触发 |
| 文件扫描历史 | `scan_history` 表 + `HistoryActivity` 列表展示每次扫描的文件数/成功/失败/跳过/耗时 |
| 文件索引内容选择（pdf/csv/md/txt/excel） | 设置项 `file_types`（多选）；抽取分别由 `TextExtractor`/PDFBox/jxl/自研 XLSX 解析完成 |
| 单文件索引字数上限 | 设置项 `char_limit`：200K / 500K / 1M（默认）/ 5M / 无限制 |

## 2. 架构

```
NSearchApp (Application)

IndexController (单例, 应用级)
  ├─ ExecutorService 后台线程：扫描 + 建索引 + 增量同步 + 写扫描历史
  ├─ IndexingService (前台服务)：常驻通知 + Wakelock，状态在单例中→重开续传
  └─ Listener 回调 → 主线程更新 UI

LuceneManager (单例)
  ├─ IndexWriter  （长期打开，索引/搜索并发）
  └─ SearcherManager（NRT，索引过程中 refresh 即被搜到）

FileScanner → ScanItem(File / SAF) → TextExtractor → Document → IndexWriter
SearchEngine：用户查询 → 分词 → BooleanQuery → 近实时检索 → 高分片段高亮
```

## 3. 文件类型与抽取

| 类型 | 抽取方式 |
| --- | --- |
| txt / md / csv | 按 UTF-8 读取（受字数上限约束） |
| pdf | `org.apache.pdfbox:pdfbox:2.0.27`（桌面版，**仅作临时方案**，见 §6） |
| xls | `net.sourceforge.jexcelapi:jxl` |
| xlsx | 自研 zip + XML 解析（`XlsxTextExtractor`，无需 POI） |

## 4. 权限

- 存储权限（全盘扫描）：Android 11+ 需要 `MANAGE_EXTERNAL_STORAGE`，应用检测到未授权时引导跳转系统设置页开启「所有文件访问」（该权限无法用运行时弹窗直接申请）；低版本运行时弹窗申请 `READ_EXTERNAL_STORAGE`。
- 索引为前台服务（`dataSync` 类型）+ `POST_NOTIFICATIONS`、`WAKE_LOCK`。
- 打开结果文件用 `FileProvider`（本地）或持久化 SAF Uri（添加的文件夹）。

## 5. 构建

与仓库内其它 Android 子项目一致（compileSdk 36 / **minSdk 26** / Java 11 + desugaring）：

```bash
cd nsearch
./gradlew :app:assembleDebug      # debug 包（~23MB）
./gradlew :app:assembleRelease    # release 包（~17MB，开启 R8 混淆，已配 proguard 规则）
```

> **minSdk = 26 的原因**：Lucene 8 的 `AttributeFactory` / `MMapDirectory` 以及
> `lucene-analyzers-common` 内的 Snowball 词干器使用了 `MethodHandle.invoke/invokeExact`，
> D8 仅在 `minSdk >= 26` 时支持，且该类调用无法被 coreLibraryDesugaring 回退，故最低 26。
>
> 索引目录位于应用私有存储 `getDir("lucene_index")`，随应用卸载清除；
> 重新打开应用会自动做增量同步，无需重建全量索引。

## 6. 已知边界

- **PDF 文本抽取为临时方案**：当前用桌面版 `org.apache.pdfbox:pdfbox:2.0.27`，其内部引用
  `java.awt`（Android 上不存在），对多数 PDF 执行 `PDFTextStripper.getText()` 会抛
  `NoClassDefFoundError`，该异常已在 `IndexController.indexOne` 中被捕获并标记为「索引失败」，
  **不会**导致 App 崩溃，其余文件类型与搜索功能不受影响。要获得完整的 Android PDF 抽取能力，
  应改用 `com.tom_roush:pdfbox-android`（AAR）——当前 JitPack/Maven Central 均无可用构建产物，
  待其可解析后，将 `app/build.gradle` 中 pdfbox 依赖替换为该 AAR、`TextExtractor` 的 import
  换回 `com.tom_rouh.pdfbox.android` 包即可，无需改动业务逻辑。
- 大文件仅索引前 N 字（默认 1M），超出部分不可被搜到但文件名仍可匹配。
- 高亮摘要取自文档头部（≤200K 字符），匹配点若超出该范围则只显示片段、不高亮。
- SAF 添加的文件夹依赖「持久化 Uri 权限」，撤销权限后该范围失效。
