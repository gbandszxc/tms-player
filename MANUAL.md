# Android TV (arm64) 项目手册

## 已完成事项（截至 2026-03-14）

1. 初始化 Android TV 项目骨架并接入 Gradle Wrapper（8.7）。
2. 完成 TV 首页浏览框架（当前为 `Fragment + RecyclerView` 分区面板模式）。
3. 落地 SMB 浏览领域模型与仓库接口（已在 Phase 1 切换为 `jcifs-ng` 真实仓库）。
4. 增加歌词 LRC 解析器基础实现。
5. 构建参数固定为 JDK 17，ABI 限定 `arm64-v8a`。
6. 已验证 `assembleDebug` 可成功打包。
7. 已接入 release 签名配置并产出已签名 `app-release.apk`。
8. 已增加 product flavors：`dev`（开发联调）与 `tv`（电视约束）。
9. 已完成首轮中文化：主界面与 SMB 配置弹窗文案改为中文（保留 `SMB` 等专有名词）。
10. 已接入内嵌 MiSans 字体（Regular/Medium/Bold）并在主题与列表项中启用。
11. 已接入 SMB 配置持久化（DataStore Preferences），应用重启后自动恢复配置。
12. 已增加 SMB 错误分级映射与中文提示（认证失败、服务器不可达、共享名不存在、路径无效、超时）。
13. 已新增 SMB1 兼容开关（默认关闭，默认走 SMB2/3，可手动开启 SMB1）。
14. 已补充首批单元测试：`SmbConfigTest`、`SmbFailureMapperTest`。
15. 已完成 Phase 2（播放核心）：接入 Media3 ExoPlayer + MediaSessionService。
16. 已支持文件点击单曲播放与“整目录顺序/随机播放”临时播放列表。
17. 已具备后台播放与系统媒体通知能力（`PlaybackService`）。
18. 已新增播放队列构建单元测试 `PlaybackQueueBuilderTest`。
19. 已推进 Phase 3（首版）：播放队列构建时补齐标题/艺术家/专辑元数据。
20. 已接入封面回退链路（同目录 `folder.jpg` / `cover.jpg` / `front.jpg`）。
21. 播放前元数据与封面解析在 IO 线程执行，避免主线程阻塞。
22. 已推进 Phase 4（首版）：支持同名同目录外挂 LRC 匹配与加载。
23. 已增加内嵌歌词读取能力（通过标签字段 `LYRICS` 读取）。
24. `LrcParser` 已支持 `offset` 标签与当前行二分查找。
25. 已推进 Phase 5（首版）：增加遥控器快捷键支持（`Back` 返回上级目录，`Menu` 打开 SMB 配置）。
26. 已增加错误态快速重试入口（列表操作区出现“重试连接”）。
27. SMB 目录读取加入弱网重试策略（超时/不可达场景自动重试一次）。
28. 已完成 Phase 6（交付收敛）：新增发布验收清单 `spec/release-checklist.md`。
29. SMB 浏览页已改为纵向单列列表（`VerticalGridSupportFragment`），文件项按竖向排列并保持左对齐。
30. SMB 配置支持共享名留空；留空时可浏览服务器暴露的共享根目录。
31. SMB 配置支持保存多个连接并在页面内快速切换激活连接。
32. 已统一页面可见文案为中文（保留 `SMB` 等专有名词）。
33. 连接管理区与文件浏览区已按 `spec/design.md` 分区重构（管理区独立，文件区纵向浏览）。
34. 视觉风格已调亮并取消分区外暗框，按连接管理/文件浏览/播放控制三段式布局。
35. 连接管理区仅在根目录显示，进入子目录后默认从文件浏览区开始。
36. 浏览页已按设计图改为左对齐卡片式 UI，并修复焦点高亮越界问题。
37. 播放控制按钮文案简化为“顺序播放/随机播放”，并保持按钮内单行不换行。
38. 浏览页改为整页可滚动，文件浏览区域高度自适应，避免列表被截断不可见。
39. 已按 55 寸 4K 电视约 2.5 米观距下调字号，提升整体观看舒适度。
40. 文件列表前缀已改为 emoji 图标：目录 `📁`，音频 `🎵`。
41. 页面左上角新增常驻“返回上一级”按钮（可通过方向键聚焦并按确认触发）。
42. 已新增 `PlaybackActivity` 播放页面，并在点击音频/顺序播放/随机播放后自动跳转到播放页。
43. 已将 `PlaybackService` 的会话入口调整为播放页（媒体通知点击后直达播放页）。
44. 已为 ExoPlayer 接入基于 `jcifs-ng` 的 SMB 自定义 `DataSource`，支持直接读取 `smb://` 音频流。
45. 播放页已补齐歌曲元信息（歌曲/艺术家/专辑）、实时进度文本与水平进度条。
46. 播放页已接入歌词时间轴展示（上一行/当前高亮/下一行）并随播放位置同步刷新。
47. 播放页已支持封面显示（沿用元数据 `artworkUri`，失败时回退默认图）。
48. 浏览页播放控制区已新增“回到当前播放”按钮，并按当前会话状态动态显示。
49. 点击当前正在播放的同一首歌时，已改为续播并保留进度，不再重建队列重置到 0 秒。
50. 已修复外置歌词在“配置共享名 + 子目录”场景的路径拼接问题，恢复 `.lrc` 命中率。
51. 播放页封面加载已增强同目录封面探测策略（基于 SMB 父目录对象），提升封面显示成功率。
52. 播放页进度条聚焦时支持 `OK/Enter` 切换播放/暂停（保留左右键快进快退）。
53. 播放页封面加载顺序已优化为“元数据封面URI -> 内嵌封面 -> 同目录封面”，减少封面出现滞后。
54. 播放页歌词读取增加短退避重试，并在内存态配置缺失时从 DataStore 回补 SMB 配置，降低误判“暂无歌词”。
55. 已新增 `Track9` 样本单测，验证歌词时间轴解析与内嵌封面读取。
56. 已抽离 `SmbPathResolver` 并补充单测，统一外置歌词路径构造逻辑（share/子目录/uri 回退场景）。
57. 播放页新增 `PlaybackLyricsCache`，同曲重复进入优先命中缓存，减少歌词重复请求与“暂无歌词”误判。
58. 播放页新增 `PlaybackArtworkCache`，返回文件页后回到当前播放可直接命中封面缓存，缩短展示延迟。
59. 外置歌词查找已升级为“`streamUri` 同名 `.lrc` 优先 + 路径解析回退”双策略，提升 Track9 类场景命中率。
60. 歌词文本解码新增 UTF-16 BOM 识别，兼容更多 LRC 编码来源。
61. 播放页缓存键已统一为 `uri` 优先，回到当前播放时歌词与封面更易命中缓存。
62. 已新增图标批处理脚本 `spec/icon/generate_android_icons.ps1`，可将 `spec/icon/icon_raw.png` 一键生成并覆盖 Android `mipmap-*` 启动图标资源。
63. 已知问题：`sample/Track9` 在部分实测场景仍偶发”歌词加载中 -> 暂无歌词”，封面缓存已稳定但歌词链路待下一轮继续优化。
64. 已统一应用包名与源码包路径：`com.example.tvmediaplayer` -> `com.github.gbandszxc.tvmediaplayer`。
65. 已修复目录切换后的焦点保底逻辑：进入/退出目录后优先落在首个文件项，无文件时回退到”返回上一级”按钮，避免遥控器操作出现光标丢失。
66. 浏览页”播放控制”区已按问题单调整：`回到当前播放` 移至 `随机播放` 右侧并占用剩余宽度，按钮改为浅黄色并启用单行循环滚动文案。
67. 已将应用版本定版为 `1.0.0`（`versionCode=1`），并产出首版 `tvRelease` 安装包；后续迭代按发布策略递增版本号。
68. 已优化内嵌封面加载速度：MP3 文件封面提取改为只下载 ID3v2 tag 区域（文件开头部分，通常 << 1MB），跳过后续音频数据，大幅降低内嵌封面加载时间。
69. 已修复外置歌词偶发无法加载问题：`loadExternalLrc` 对每个候选路径加独立 `runCatching`，单个路径异常不再中断整个加载链路；`load` 方法对 `loadExternalLrc` 整体加 `runCatching`，确保外置失败后仍能尝试内嵌歌词。
70. 已修复外置歌词文件名全角/半角不一致导致无法匹配的问题：精确路径命中失败时，遍历父目录并通过 `normalizeWidth`（全角 ASCII → 半角）+ 大小写不敏感做模糊匹配，兼容语音识别工具混用 `！`/`!`、`？`/`?` 等字符的场景。
71. 版本升至 `1.0.1`（`versionCode=2`），包含封面与歌词加载修复。
72. 已统一 APK 输出命名规则为 `tms-player-<buildType>-<versionName>.apk`（例如 `tms-player-debug-1.0.1.apk`、`tms-player-release-1.0.1.apk`）。
73. 已补充 `.gitignore` 仓库忽略增强项：`*.pem/*.p12/*.crt/*.key`、`.env*`、常见系统/编辑器临时文件与本地 `.agent_prompt/` 工具目录，降低敏感信息与无关文件误提交风险。
74. 已移除 product flavors（`dev`/`tv`），合并为标准 `debug`/`release` 两个构建变体；`debug` 包名后缀 `.debug`（支持与 release 并行安装），`android.software.leanback` 统一为 `required=false`，兼容 TV 与手机/平板侧载调试。
75. 已优化多尺寸设备 UI 兼容性：SMB 配置弹窗内容区包裹 `ScrollView`（小屏可滚动填写全部字段）；浏览页和播放页的按钮栏改用 `HorizontalScrollView` 防溢出；播放页封面区由固定 340dp 改为权重 30% 自适应布局，适配手机横屏场景。
76. 已新增 `SettingsActivity`：左侧分类面板 + 右侧详情面板的两级菜单设置页，遥控器方向键/OK键可自然操作；入口为浏览页右上角"设置"按钮；菜单结构包含"显示设置/播放设置/应用设置/关于"，"关于"页点击可跳转 GitHub 项目主页；其余条目暂为占位（功能开发中）。
77. 已补齐设置页核心功能：全局缩放改为预设档位轮换（`90%/95%/100%/105%/110%`，默认 `100%`），通过统一 UI 设置存储持久化并在页面返回后生效。
78. 已补齐播放设置：新增“播放页歌词字号/全屏歌词字号”手动输入能力（范围 `14sp-56sp`），默认值分别与当前页面默认字号保持一致（播放页 `20sp`、全屏歌词 `28sp`），并接入播放页与全屏歌词页实时应用。
79. 已补齐应用休眠设置：新增“应用休眠保护”开关并默认开启；开启时应用窗口保持 `FLAG_KEEP_SCREEN_ON`，避免运行中触发休眠/息屏；关闭后恢复系统默认行为。
80. 已补齐关于页内容：新增项目描述“一款适配安卓TV，基于遥控器操作的本地SMB网络音乐播放器”，并将项目主页入口改为 GitHub 图标按钮（隐藏裸链接文本）。
81. 播放页歌词默认字号已从 `17sp` 调整为 `20sp`，并与设置页“播放页歌词字号”默认值保持一致。
82. 播放页信息区已按 8pt 节奏重排间距（歌曲信息/进度条/歌词标题/歌词内容/底部按钮区），修正层级留白不均导致的视觉割裂。
83. 全局缩放在 `105%/110%` 档位已改为容器内自适应缩放：放大时动态收敛内容测量尺寸，避免页面元素超出可视容器边界。
84. 全局缩放进一步修正：缩小档位（如 `90%/95%`）同样采用反向测量尺寸适配，消除缩小时右侧/底部出现的粗边距问题。

## 1. 当前项目定位

当前工程是“可编译、可侧载、可在 TV 上浏览骨架 UI”的第一阶段版本。

- 包名：`com.github.gbandszxc.tvmediaplayer`
- `minSdk`：`21`
- `targetSdk`：`34`
- `compileSdk`：`34`
- ABI：`arm64-v8a`
- UI：Leanback

## 2. 关键文件结构（当前）

```text
tv-media-player/
├─ MANUAL.md
├─ settings.gradle
├─ build.gradle
├─ gradle.properties
├─ local.properties
├─ gradlew
├─ gradlew.bat
├─ gradle/
│  └─ wrapper/
│     ├─ gradle-wrapper.jar
│     └─ gradle-wrapper.properties
├─ app/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/github/gbandszxc/tvmediaplayer/
│     │  ├─ MainActivity.kt
│     │  ├─ data/repo/{FakeSmbRepository.kt,JcifsSmbRepository.kt,SmbConfigStore.kt,SmbFailureMapper.kt}
│     │  ├─ domain/model/{SmbConfig.kt,SmbEntry.kt,SavedSmbConnection.kt}
│     │  ├─ domain/repo/SmbRepository.kt
│     │  ├─ lyrics/{LrcParser.kt,SmbLyricsRepository.kt}
│     │  ├─ playback/{PlaybackService.kt,PlaybackQueueBuilder.kt,SmbMediaItemFactory.kt}
│     │  └─ ui/
│     │     ├─ TvBrowseFragment.kt
│     │     ├─ TvBrowserViewModel.kt
│     │     └─ AppFonts.kt
│     └─ res/
│        ├─ layout/activity_main.xml
│        ├─ font/{misans_regular.ttf,misans_medium.ttf,misans_bold.ttf,misans_family.xml}
│        ├─ values/{strings.xml,colors.xml,themes.xml}
│        ├─ drawable/{ic_launcher_foreground.xml,tv_banner.xml}
│        └─ mipmap-anydpi-v26/{ic_launcher.xml,ic_launcher_round.xml}
└─ spec/
   ├─ plan.md
   ├─ next-steps.md
   ├─ design.md
   └─ release-checklist.md
```

## 3. 常用命令

在项目根目录执行（Windows PowerShell）：

```powershell
# Debug（TV 与手机/平板均可侧载，包名后缀 .debug）
.\gradlew.bat assembleDebug

# Release（签名包，正式发布用）
.\gradlew.bat assembleRelease

# Release（规避 lintVital 依赖下载问题）
.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease

# 清理
.\gradlew.bat clean
```

若当前终端未切到 JDK 17，可临时指定：

```powershell
cmd /c "set JAVA_HOME=C:\D\Develop\Java\jdk-17.0.16+8&& set PATH=%JAVA_HOME%\bin;%PATH%&& .\gradlew.bat assembleDebug"
```

## 4. 产物位置

```text
Debug:   app\build\outputs\apk\debug\tms-player-debug-<versionName>.apk
Release: app\build\outputs\apk\release\tms-player-release-<versionName>.apk
```

## 5. Release 命令差异说明

1. `.\gradlew.bat assembleRelease`
直接执行 release 构建，沿用当前缓存；会包含 `lintVitalAnalyzeRelease`。

2. `.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease`
先清理再全量构建，且跳过 `lintVitalAnalyzeRelease` 任务。适合当前机器偶发的 TLS 握手失败场景（下载 lint 依赖中断）时使用。

3. 什么时候用哪条
- 网络正常时优先用标准命令：`assembleRelease`
- 出现 `lintVitalAnalyzeRelease` 依赖下载失败时，用 `-x lintVitalAnalyzeRelease` 兜底打包

## 6. 构建变体说明

项目只有 `debug` 和 `release` 两个构建变体，不再区分 flavor：

1. `debug`
- 包名：`com.github.gbandszxc.tvmediaplayer.debug`（带 `.debug` 后缀，可与 release 并行安装）
- `android.software.leanback required=false`，可在手机/平板安装侧载调试

2. `release`
- 包名：`com.github.gbandszxc.tvmediaplayer`（正式包名）
- 使用签名配置，`required=false` 同样兼容多设备安装
## 7. 签名配置与安全

1. 项目已在 `app/build.gradle` 配置 `signingConfigs.release`。
2. 本地使用根目录 `key.properties` 读取签名参数。
3. `key.properties` 已加入 `.gitignore`，不会被提交。

## 8. 当前实现边界

1. 已完成 Media3 播放与后台通知主链路（含 SMB 流读取），并已落地首版播放页元信息/进度/歌词/封面展示。
2. 当前 SMB 浏览按目录逐级访问，尚未实现全库递归扫描聚合能力。
3. 当前播放能力为“临时队列”，尚未实现长期播放列表管理与播放历史。
4. 内嵌封面（APIC/FLAC PICTURE）与精确标签解析尚未接入专用解析器。
5. 歌词 UI 当前为三行窗口（上一行/当前行高亮/下一行），尚未实现整段滚动歌词与全屏歌词模式。
6. 播放页与设计图相比仍缺少更完整的大屏信息层级（如歌词全屏、更多遥控器快捷交互）。
7. 已可产出已签名 release，但仍建议补充签名校验与安装回归测试流程。



