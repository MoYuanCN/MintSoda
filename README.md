<p align="center">
  <img src="ani-rss-ui/public/icon.png" width="128" height="128" alt="ANI-RSS-Yuri"/>
</p>

<h1 align="center">ANI-RSS-Yuri</h1>

<p align="center">
</p>

<p align="center">
  <img alt="java" src="https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white">
  <img alt="vue" src="https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white">
  <img alt="platform" src="https://img.shields.io/badge/platform-Windows%20%7C%20Linux-4b5563?logo=linux&logoColor=white">
  <a href="LICENSE"><img alt="license" src="https://img.shields.io/badge/license-GPL--2.0-blue"></a>
</p>

<p align="center">
  <a href="#-快速开始">快速开始</a> ·
  <a href="#-功能特性">功能特性</a> ·
  <a href="#-外观与主题">外观与主题</a> ·
  <a href="docs/USER_GUIDE.md">使用指南</a> ·
  <a href="docs/DEVELOPMENT.md">开发指南</a> ·
  <a href="docs/SPECIAL_FEATURE.md">分支特性</a>
</p>

---

## 项目简介

**ANI-RSS-Yuri** 是 [ANI-RSS](https://github.com/wushuo894/ani-rss) 的 分支项目，基于本分支开发者个人需求进行了深度魔改

## 🚀 快速开始

| 平台 | 方式 |
| --- | --- |
| 🪟 Windows | 下载 `ani-rss.exe` 直接运行，配置保存在 EXE 同目录的 `config/` |
| 🐧 Linux | 一条命令安装到当前目录并注册 systemd 服务 |
| ☕ 通用 | 下载 `ani-rss.jar`，使用 Java 25 运行 |

**Linux 一键安装**

```bash
curl -fsSL https://raw.githubusercontent.com/MoYuanCN/ani-rss-Yuri/main/linux/install-yuri.sh | sudo bash
```

安装时可追加参数：

```bash
--username <用户名>   # 预设登录账号
--password <密码>     # 预设登录密码，留空则在首次启动时生成或引导设置
--port <端口>         # 默认 17789
--dir <目录>          # 默认安装到执行命令时所在目录
--setup              # 强制在 WebUI 完成首次账号初始化
```

安装完成后运行 `assyuri` 打开管理菜单，也可以直接使用子命令：

```bash
assyuri status     # 运行状态
assyuri version    # 版本与安装目录
assyuri update     # 更新到最新版本
assyuri log        # 实时查看日志
assyuri restart    # 重启服务
assyuri uninstall  # 卸载服务
```

> 默认端口 **17789**，首次访问会按平台规则生成凭据或进入账号设置引导。

## ✨ 本分支功能特性

### 📡 订阅与下载

- 支持 **Mikan**、**AniBT** 等 RSS 源，多订阅统一管理。
- 仅保留 **qBittorrent**，支持 API Key 与用户名/密码两种认证，兼容没有API Key的 qBittorrent版本。
- RSS 刷新间隔以**秒**为单位。

### 🎯 匹配规则

- 本地匹配 + 全局匹配 + 全局排除 + 反匹配，设置页内置可视化规则测试。
- 优先级固定为 **反匹配 > 全局匹配 > 排除**；RSS 下载、RSS 预览与合集预览共用同一判定实现。
- 反匹配未命中时默认跳过，可按订阅选择继续普通匹配或直接放行。
- 规则语法支持 `{{字幕组}}:正则` 限定来源。

### 📨 通知

- 保留 **Telegram**、**自定义 HTTP 请求** 与 **媒体库刷新**。
- 多套可命名模板，每个模板独立选择**文本**或 **JSON**。
- 自定义请求的 Body 可手写（支持 `${title}`、`${subgroup}`、`${text}` 等变量），也可直接从模板库选择 JSON。
- 新增通知必须确认后才写入，避免留下空配置。

### 🔀 订阅排序

订阅页顶部可切换排序：

| 排序 | 说明 |
| --- | --- |
| 评分 | 按 Bangumi 评分 |
| 拼音 | 按标题拼音 |
| 标题 | 按标题字符顺序 |
| 更新时间 | 按上次更新时间 |
| 更新星期 | 按每周放送日轮转 |
| 备用 RSS 数量 | 备用源多的订阅靠前 |

## 🎨 外观与主题

- **主题色**自由选择，或开启「从背景图动态取色」，自动提取可读的强调色。
- 卡片、导航栏、弹窗支持不透明度与模糊度。
- 内置多套预设。
- 可自定义应用/登录背景、应用/登录 Logo、圆角、内容宽度与启动页。
- 支持自定义 CSS/JS 注入，以及 WebUI 在线更新。

## 🛠️ 本地构建

需要 **Java 25**。Maven 前端插件会把 Node.js 与 pnpm 下载到 `ani-rss-ui/node/`，依赖缓存写入 `ani-rss-ui/.tools/pnpm-store/`，无需系统级 Node 环境。

```bash
# 仅构建 JAR（含前端资源）
mvn -B package -DskipTests --file pom.xml

# 发布用 JAR + Windows EXE
bash ./package.sh
```

产物位于 `ani-rss-application/target/`：`ani-rss.jar` 与 `ani-rss.exe`。

## 📦 目录结构

| 路径 | 说明 |
| --- | --- |
| `ani-rss-application/` | Spring Boot 后端：订阅、匹配、下载、通知与配置 |
| `ani-rss-ui/` | Vue 3 + Vite + Element Plus 前端 |
| `linux/` | Linux 安装、控制与卸载脚本 |
| `docs/` | 用户指南、开发指南、分支特性、贡献规范与安全说明 |

## 📄 许可

本项目沿用上游的 **GPL-2.0** 许可证，详见 [LICENSE](LICENSE)。

<p align="center">
  <sub>如果这个项目对你有帮助，欢迎点个 ⭐ Star</sub>
</p>
