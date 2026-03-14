# Android TV (arm64) 项目手册

## 已完成事项（截至 2026-03-14）

1. 初始化 Android TV 项目骨架并接入 Gradle Wrapper（8.7）。
2. 完成 Leanback 首页浏览框架（`BrowseSupportFragment`）。
3. 落地 SMB 浏览领域模型与仓库接口（当前为 Fake 仓库实现）。
4. 增加歌词 LRC 解析器基础实现。
5. 构建参数固定为 JDK 17，ABI 限定 `arm64-v8a`。
6. 已验证 `assembleDebug` 可成功打包。
7. 已接入 release 签名配置并产出已签名 `app-release.apk`。

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
│     │  ├─ data/repo/FakeSmbRepository.kt
│     │  ├─ domain/model/{SmbConfig.kt,SmbEntry.kt}
│     │  ├─ domain/repo/SmbRepository.kt
│     │  ├─ lyrics/LrcParser.kt
│     │  └─ ui/
│     │     ├─ TvBrowseFragment.kt
│     │     ├─ TvBrowserViewModel.kt
│     │     └─ presenter/SimpleTextPresenter.kt
│     └─ res/
│        ├─ layout/activity_main.xml
│        ├─ values/{strings.xml,colors.xml,themes.xml}
│        ├─ drawable/{ic_launcher_foreground.xml,tv_banner.xml}
│        └─ mipmap-anydpi-v26/{ic_launcher.xml,ic_launcher_round.xml}
└─ spec/
   ├─ plan.md
   └─ design.md
```

## 3. 常用命令

在项目根目录执行（Windows PowerShell）：

```powershell
# Debug 包
.\gradlew.bat assembleDebug

# Release 包（标准）
.\gradlew.bat assembleRelease

# Release 包（规避 lintVital 依赖下载问题）
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
Debug:   app\build\outputs\apk\debug\app-debug.apk
Release: app\build\outputs\apk\release\app-release.apk
```

## 5. Release 命令差异说明

1. `.\gradlew.bat assembleRelease`
直接执行 release 构建，沿用当前缓存，不主动清理；会包含 `lintVitalAnalyzeRelease`。

2. `.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease`
先清理再全量构建，且跳过 `lintVitalAnalyzeRelease` 任务。适合你当前机器偶发的 TLS 握手失败场景（下载 lint 依赖中断）时使用。

3. 什么时候用哪条
- 网络正常时优先用标准命令：`assembleRelease`
- 出现 `lintVitalAnalyzeRelease` 依赖下载失败时，用 `-x lintVitalAnalyzeRelease` 兜底打包

## 6. 签名配置与安全

1. 项目已在 `app/build.gradle` 配置 `signingConfigs.release`。
2. 本地使用根目录 `key.properties` 读取签名参数。
3. `key.properties` 已加入 `.gitignore`，不会被提交。

## 7. 当前实现边界

1. SMB 目前是 Fake 仓库（用于演示与跑通交互流程），未接真实 NAS。
2. 播放器、后台播放、通知控制、封面与歌词联动尚未接入真实 Media3 流程。
3. 已可产出已签名 release，但仍建议补充签名校验与安装回归测试流程。
