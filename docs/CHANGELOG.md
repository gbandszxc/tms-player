# 更新日志

## 已完成事项（截至 2026-03-14，终态归并）

1. 完成 Android TV 工程基础搭建与构建链路落地：Gradle Wrapper 8.7、JDK 17、`arm64-v8a`，`debug/release` 可稳定打包，release 已接入签名。
2. 构建变体收敛为标准 `debug/release`（移除 `dev/tv` flavors），`debug` 使用 `.debug` 包名后缀，支持与 release 并行安装。
3. 应用包名与源码包路径统一为 `com.github.gbandszxc.tvmediaplayer`，APK 命名统一为 `tsm-player-<buildType>-<versionName>.apk`。
4. SMB 架构完成领域模型 + 仓库接口落地，并切换到 `jcifs-ng` 实现，支持直接浏览 `smb://` 目录。
5. SMB 配置完成 DataStore 持久化，支持多连接保存与快速切换，支持共享名留空浏览共享根目录，并保留 SMB1 兼容开关（默认关闭）。
6. SMB 访问链路补齐可用性能力：错误分级中文提示、弱网重试、错误态快速重试入口。
7. 浏览页终态为"连接管理 + 文件浏览 + 播放控制"三段式布局：文件区纵向单列、整页可滚动、根目录显示连接管理区、子目录聚焦文件区。
8. 浏览页交互终态包含：常驻"返回上一级"、目录切换焦点保底、`Back/Menu` 遥控器快捷操作、播放控制区动态"回到当前播放"。
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
24. SMB 封面/标签提速扩展到多格式：`PlaybackActivity` 新增"按扩展名快速探测 + 失败自动回退全量读取"机制；`mp3` 继续走 ID3v2 区域拷贝，`flac` 读取 STREAMINFO/PICTURE 等元数据块，`m4a/mp4/m4b/aac/alac` 与 `ogg/opus` 先读取限定前缀字节探测，若未解析到标签或封面则自动回退全量读取，保证兼容性。
25. 歌词加载性能优化（2026-03-27）：新增 `SmbAudioMetadataProbe` 统一 SMB 元数据快速探测（标题/艺术家/专辑/封面/内嵌歌词）并带并发去重，`PlaybackActivity` 的标签/封面改为复用该探测结果；`SmbLyricsRepository` 改为"外置 `.lrc` 先发起 + 内嵌延迟并发，先成功先返回"；歌词重试改为"仅异常重试、未命中不重试"；`PlaybackLyricsCache` 新增无歌词负缓存（内存+磁盘短 TTL），避免反复打开同一无歌词文件重复走 SMB 慢路径。
