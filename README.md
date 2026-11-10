# ANI-RSS-Yuri

ANI-RSS-Yuri 是 [ANI-RSS](https://github.com/wushuo894/ani-rss) 的 Fork，保留 RSS 订阅与自动下载流程，并针对本项目的使用目标调整了匹配、通知和 WebUI 功能。

## 本 Fork 的功能

- 下载器仅支持 qBittorrent。
- RSS 刷新间隔可精确到秒，旧配置的分钟值会自动迁移。
- 每个订阅可开启反匹配：用标题关键词或正则表达式筛选泛 RSS 条目，并在订阅编辑页粘贴标题测试规则。
- 设置页提供反匹配规则集中管理与标题测试，也支持全局匹配和全局排除。
- WebUI 可自定义登录与应用背景、Logo、卡片透明度、模糊度和圆角。
- 通知支持可复用的 Telegram 与自定义 HTTP JSON 模板；单条通知可选用不同模板。
- 开发者选项支持自定义种子获取地址，并按 info_hash 请求种子文件。
- 通知保留 Telegram、媒体库刷新和自定义 HTTP 请求。自定义请求支持方法、请求头、Content-Type，以及包含占位符的 JSON 请求体。
- 本地功能不再受爱发电订单或捐赠状态限制。

本 Fork 不包含邮件、QQ、Server 酱、Bark 等通知、AnimeGarden 适配、OpenList 上传、Aria2/Transmission 下载器、集成的 qBittorrent 任务进度页面、内置视频播放、刮削或 Bangumi 自动点格子功能。

## 下载与部署

- [ANI-RSS-Yuri Releases](https://github.com/MoYuanCN/ani-rss-Yuri/releases)
- [Docker 镜像](https://github.com/MoYuanCN/ani-rss-Yuri/pkgs/container/ani-rss-yuri)
- [上游使用文档](https://docs.wushuo.top/)（ANI-RSS-Yuri 特有功能请以本仓库说明和界面为准）

## 本地构建

需要 Java 25。Maven 前端插件会在项目目录下安装所需的 Node.js 与 pnpm。

```bash
mvn -B package -DskipTests --file pom.xml
```

Windows 与 macOS 发布构建使用：

```bash
bash ./package.sh
```

## 许可

本项目沿用上游的 GPL-2.0 许可证，详见 [LICENSE](LICENSE)。
