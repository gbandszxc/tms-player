# Android TV (arm64) 项目手册

## 已完成事项（截至 2026-03-14）

1. 初始化 Android TV 项目骨架并接入 Gradle Wrapper（8.7）。
2. 完成 Leanback 首页浏览框架（`BrowseSupportFragment`）。
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
│     │  ├─ domain/model/{SmbConfig.kt,SmbEntry.kt}
│     │  ├─ domain/repo/SmbRepository.kt
│     │  ├─ lyrics/LrcParser.kt
│     │  └─ ui/
│     │     ├─ TvBrowseFragment.kt
│     │     ├─ TvBrowserViewModel.kt
│     │     └─ presenter/SimpleTextPresenter.kt
│     └─ res/
│        ├─ layout/activity_main.xml
│        ├─ font/{misans_regular.ttf,misans_medium.ttf,misans_bold.ttf,misans_family.xml}
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
3. 已可产出已签名 release，但仍建议补充签名校验与安装回归测试流程。
