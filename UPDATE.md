# ANI-RSS-Yuri 3.3.0

## 增加与调整

- 支持订阅级和全局反匹配规则，并提供规则编辑与标题测试界面。
- 明确过滤优先级：反匹配 > 全局匹配 > 排除。反匹配未命中默认跳过，可为单条订阅设置继续普通规则或放行。
- RSS 下载和合集预览共用规则判定，减少预览与实际下载结果不一致的情况。
- RSS 刷新间隔支持秒级配置，并在同一刷新批次内复用相同地址的 RSS 响应。
- qBittorrent 支持 API Key 与用户名密码认证；下载器仅保留 qBittorrent。
- 通知模板支持多个 Telegram 文本与自定义 JSON 请求体模板，并允许逐条通知选择模板。
- 增加 WebUI 背景、Logo、卡片透明度、模糊度和圆角自定义。
- 增加需验证登录凭据的开发者选项，以及按 info_hash 请求种子的自定义地址。
- 修复合集关联 TMDB 后保存路径未自动刷新的问题。
- Windows EXE 配置保存在程序同目录的便携目录；Linux 通过 JAR 运行并由 systemd 脚本部署。
- 爱发电功能不再校验捐赠状态。

## 移除的功能

- 邮件、QQ、Server 酱、Bark 等通知方式；Aria2、Transmission 下载器。
- 集成的 qBittorrent 任务进度页、内置视频播放、刮削和自动刮削。
- OpenList 上传、AnimeGarden 适配、Bangumi 点格子、摸鱼检测和 trackers 更新设置。
- Docker 镜像、容器发布流水线与容器部署文件。
- macOS 应用、DMG 构建、启动项和更新支持。本项目仅支持 Linux 与 Windows。

使用说明见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)，构建说明见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。
