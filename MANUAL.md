# Android TV (arm64) 项目手册

## 已完成事项（截至 2026-03-14，终态归并）

1. 完成 Android TV 工程基础搭建与构建链路落地：Gradle Wrapper 8.7、JDK 17、`arm64-v8a`，`debug/release` 可稳定打包，release 已接入签名。
2. 构建变体收敛为标准 `debug/release`（移除 `dev/tv` flavors），`debug` 使用 `.debug` 包名后缀，支持与 release 并行安装。
3. 应用包名与源码包路径统一为 `com.github.gbandszxc.tvmediaplayer`，APK 命名统一为 `tsm-player-<buildType>-<versionName>.apk`。
4. SMB 架构完成领域模型 + 仓库接口落地，并切换到 `jcifs-ng` 实现，支持直接浏览 `smb://` 目录。
5. SMB 配置完成 DataStore 持久化，支持多连接保存与快速切换，支持共享名留空浏览共享根目录，并保留 SMB1 兼容开关（默认关闭）。
6. SMB 访问链路补齐可用性能力：错误分级中文提示、弱网重试、错误态快速重试入口。
7. 浏览页终态为“连接管理 + 文件浏览 + 播放控制”三段式布局：文件区纵向单列、整页可滚动、根目录显示连接管理区、子目录聚焦文件区。
8. 浏览页交互终态包含：常驻“返回上一级”、目录切换焦点保底、`Back/Menu` 遥控器快捷操作、播放控制区动态“回到当前播放”。
9. 播放核心完成 Media3 ExoPlayer + `PlaybackService` + MediaSession 接入，支持后台播放、系统媒体通知与通知回跳播放页。
10. 播放队列支持单曲播放与整目录顺序/随机播放；点击当前播放同曲时可续播并保留进度，不重建队列。
11. ExoPlayer 已接入基于 `jcifs-ng` 的自定义 SMB `DataSource`，支持直接流式播放 SMB 音频。
12. 播放页终态完成歌曲元信息、实时进度与遥控器控制（进度条支持 `OK/Enter` 播放暂停与左右快进快退）。
13. 歌词能力完成外置 + 内嵌双链路：`LrcParser` 支持 `offset` 与时间轴定位，外置歌词支持路径回退、异常隔离、UTF-16 BOM、全半角归一化匹配。
14. 封面能力完成多级回退：元数据 `artworkUri` -> 内嵌封面 -> 同目录封面（`folder/cover/front`），并优化 MP3 仅读取 ID3v2 区域提升速度。
15. 播放态缓存体系已落地：`PlaybackLyricsCache` 与 `PlaybackArtworkCache`，统一 `uri` 优先键，回到当前播放可更快命中歌词/封面。
16. 新增 `SettingsActivity`（左分类 + 右详情），包含显示设置、播放设置、应用设置、关于；支持遥控器方向键与 OK 键操作。
17. 设置能力终态包含：全局缩放档位（`90/95/100/105/110%`）持久化、播放页/全屏歌词字号可配置（`14sp-56sp`）、休眠保护开关（`FLAG_KEEP_SCREEN_ON`）、关于页 GitHub 入口。
18. 全局缩放已完成放大与缩小双向自适应测量修复，解决大档位内容越界与小档位粗边距问题。
19. 多设备 UI 适配已补齐：配置弹窗可滚动、按钮栏横向滚动防溢出、播放页封面区改为自适应比例布局。
20. 质量与交付补充：已新增关键单测（配置/错误映射/队列/歌词路径等）、`Track9` 样本验证、发布验收清单与图标批处理脚本。
21. 版本里程碑：`1.0.0`（首版发布）、`1.0.1`（歌词/封面修复）、`1.0.2`（设置页与全局缩放修复）、`1.0.3`（上次播放记忆、歌词封面磁盘缓存、清理缓存设置、全屏歌词缓存修复）、`1.0.4`（SMB 封面/标签多格式快速探测与回退策略优化）。
22. 新增上次播放记忆功能：`LastPlaybackStore` 持久化队列 URIs/mediaIds/进度/标题；`PlaybackActivity.onStop` 时自动保存；浏览页黄色按钮在无活跃播放时显示"继续上次播放"，点击可恢复队列进度（不自动播放）并跳转播放页；设置-播放设置新增"记忆上次播放"开关（默认开启），播放设置下分【歌词】和【其它】两组。
23. 歌词/封面缓存改为磁盘持久化：`PlaybackLyricsCache` 和 `PlaybackArtworkCache` 新增 `saveAsync/loadFromDisk/clearDisk/diskCacheSize` 接口，JSON 歌词存 `cacheDir/lyrics/`，JPEG 封面存 `cacheDir/artwork/`；PlaybackActivity 的 maybeLoadLyrics 和 maybeLoadArtwork 加入磁盘二级缓存查询；LyricsFullscreenActivity 修复 key 不一致问题（统一为 uri 优先）并接入同一缓存；设置新增"其它设置"分类，包含"清理缓存"条目，显示当前磁盘缓存大小。
24. SMB 封面/标签提速扩展到多格式：`PlaybackActivity` 新增“按扩展名快速探测 + 失败自动回退全量读取”机制；`mp3` 继续走 ID3v2 区域拷贝，`flac` 读取 STREAMINFO/PICTURE 等元数据块，`m4a/mp4/m4b/aac/alac` 与 `ogg/opus` 先读取限定前缀字节探测，若未解析到标签或封面则自动回退全量读取，保证兼容性。

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
tsm-player/
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
Debug:   app\build\outputs\apk\debug\tsm-player-debug-<versionName>.apk
Release: app\build\outputs\apk\release\tsm-player-release-<versionName>.apk
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



