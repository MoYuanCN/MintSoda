# ANI-RSS-Yuri

ANI-RSS-Yuri 是 [ANI-RSS](https://github.com/wushuo894/ani-rss) 的 Fork，保留上游 RSS 订阅、番剧管理与自动下载流程，并按本分支的使用目标调整功能。未特别说明时，常规订阅和下载流程沿用 ANI-RSS 的行为。

## 增加的功能

- 仅支持 qBittorrent，并支持 API Key 或用户名密码认证，兼容没有 API Key 的 qBittorrent 服务端。
- RSS 刷新间隔使用秒，可设置短于上游限制的间隔；同一轮刷新会复用相同 RSS 地址的响应。
- 增加本地反匹配和全局反匹配。可用正则规则从泛 RSS 中挑选条目，并归入指定订阅和字幕组；设置页提供规则测试界面。
- 匹配优先级为反匹配 > 全局匹配 > 排除。反匹配未命中时默认跳过，可按订阅设置为继续普通规则或放行。
- 支持全局匹配和全局排除，可设置新订阅默认启用或导入规则。
- 通知仅保留 Telegram、媒体库刷新和自定义 HTTP 请求；上游新增的 AstrBot 渠道未引入。支持多个可命名模板；每个模板单独选择普通文本或 JSON，并可在通知配置中选择。Telegram 使用普通文本，自定义请求支持两种格式。
- WebUI 可自定义应用和登录背景、登录及应用 Logo、卡片透明度、模糊度和圆角。
- 开发者选项需在“关于”页连续点击七次并验证登录凭据后开启；其中可设置按 info_hash 获取种子的自定义地址。
- 爱发电捐赠功能不再校验订单或捐赠状态。
- Windows EXE 使用便携配置目录，配置保存在 EXE 同目录的 `config` 文件夹中。

## 移除的功能

- 邮件、QQ、Server 酱、Bark 等通知方式。
- Aria2、Transmission 等下载器，仅保留 qBittorrent。
- 集成的 qBittorrent 任务和进度查看页面。
- 内置视频播放、刮削与自动刮削。
- OpenList 上传、AnimeGarden 适配、Bangumi 点格子、摸鱼检测和 trackers 更新设置。
- Docker 镜像、容器构建流程与容器部署支持。
- macOS 应用、DMG 打包和相关更新支持。本项目仅考虑 Linux 与 Windows。

## 下载与部署

- [ANI-RSS-Yuri Releases](https://github.com/MoYuanCN/ani-rss-Yuri/releases)
- [使用指南](docs/USER_GUIDE.md)
- [开发指南](docs/DEVELOPMENT.md)
- [贡献指南](docs/CONTRIBUTING.md)
- [安全说明](docs/SECURITY.md)
- [AGENTS.md](AGENTS.md)（面向 AI 编码代理与协作者的仓库约定）

Windows 可使用发布的 EXE，也可使用 JAR。Linux 使用 JAR。运行 JAR 需要 Java 25。

Linux 也可使用仓库中的 systemd 安装脚本：

```bash
sudo bash linux/install-ani-rss.sh
```

## 本地构建

需要 Java 25。Maven 前端插件会将 Node.js 与 pnpm 下载到项目目录下的 `ani-rss-ui/node/`，并将 pnpm 依赖缓存写入 `ani-rss-ui/.tools/pnpm-store/`；这些目录已加入 `.gitignore`。

构建 JAR：

```bash
mvn -B package -DskipTests --file pom.xml
```

构建 JAR 和 Windows EXE：

```bash
bash ./package.sh
```

## 许可

本项目沿用上游的 GPL-2.0 许可证，详见 [LICENSE](LICENSE)。
