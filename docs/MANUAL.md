# Android TV (arm64) 项目手册

## 已完成事项

详见 [CHANGELOG.md](./CHANGELOG.md)

**请自觉维护**

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
├─ docs/MANUAL.md
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



