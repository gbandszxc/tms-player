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

## 1. 当前项目定位

当前工程是“可编译、可侧载、可在 TV 上浏览骨架 UI”的第一阶段版本。

- 包名：`com.example.tvmediaplayer`
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
│     ├─ java/com/example/tvmediaplayer/
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
# Dev Debug（推荐：手机/普通模拟器可见）
.\gradlew.bat assembleDevDebug

# TV Debug（电视调试）
.\gradlew.bat assembleTvDebug

# TV Release（标准）
.\gradlew.bat assembleTvRelease

# TV Release（规避 lintVital 依赖下载问题）
.\gradlew.bat clean assembleTvRelease -x lintVitalAnalyzeRelease

# 清理
.\gradlew.bat clean
```

若当前终端未切到 JDK 17，可临时指定：

```powershell
cmd /c "set JAVA_HOME=C:\D\Develop\Java\jdk-17.0.16+8&& set PATH=%JAVA_HOME%\bin;%PATH%&& .\gradlew.bat assembleDebug"
```

## 4. 产物位置

```text
Dev Debug: app\build\outputs\apk\dev\debug\app-dev-debug.apk
TV Debug:  app\build\outputs\apk\tv\debug\app-tv-debug.apk
TV Release: app\build\outputs\apk\tv\release\app-tv-release.apk
```

## 5. Release 命令差异说明

1. `.\gradlew.bat assembleTvRelease`
直接执行 TV release 构建，沿用当前缓存，不主动清理；会包含 `lintVitalAnalyzeRelease`。

2. `.\gradlew.bat clean assembleTvRelease -x lintVitalAnalyzeRelease`
先清理再全量构建，且跳过 `lintVitalAnalyzeRelease` 任务。适合你当前机器偶发的 TLS 握手失败场景（下载 lint 依赖中断）时使用。

3. 什么时候用哪条
- 网络正常时优先用标准命令：`assembleTvRelease`
- 出现 `lintVitalAnalyzeRelease` 依赖下载失败时，用 `-x lintVitalAnalyzeRelease` 兜底打包

## 6. Flavor 说明

1. `dev` flavor
- 用于开发联调，`android.software.leanback` 在主清单中为 `required=false`，便于手机和普通模拟器显示图标。

2. `tv` flavor
- `app/src/tv/AndroidManifest.xml` 覆盖为 `android.software.leanback required=true`，保持 TV 设备约束。
- 发布请使用 `tvRelease` 变体。
## 7. 签名配置与安全

1. 项目已在 `app/build.gradle` 配置 `signingConfigs.release`。
2. 本地使用根目录 `key.properties` 读取签名参数。
3. `key.properties` 已加入 `.gitignore`，不会被提交。

## 8. 当前实现边界

1. 播放器、后台播放、通知控制、封面与歌词联动尚未接入真实 Media3 流程。
2. 当前 SMB 浏览按目录逐级访问，尚未实现全库递归扫描聚合能力。
3. 当前播放能力为“临时队列”，尚未实现长期播放列表管理与播放历史。
4. 内嵌封面（APIC/FLAC PICTURE）与精确标签解析尚未接入专用解析器。
5. 歌词 UI（滚动/高亮）尚未接入播放页，仅完成歌词数据读取与时间轴能力。
6. 播放页 UI 与大屏信息层级细化尚未完成（当前以列表页 + 通知播放为主）。
7. 已可产出已签名 release，但仍建议补充签名校验与安装回归测试流程。



