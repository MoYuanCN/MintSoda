<p align="center">
  <img src="mint-soda-ui/public/icon.png" width="128" height="128" alt="MintSoda"/>
</p>

<h1 align="center">MintSoda</h1>

<p align="center">
  <img alt="java" src="https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white">
  <img alt="vue" src="https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white">
  <img alt="platform" src="https://img.shields.io/badge/platform-Windows%20%7C%20Linux-4b5563?logo=linux&logoColor=white">
  <a href="LICENSE"><img alt="license" src="https://img.shields.io/badge/license-GPL--2.0-blue"></a>
</p>

<p align="center">
  <a href="#-快速开始">快速开始</a> ·
  <a href="#-功能">功能</a> ·
  <a href="#-插件系统">插件系统</a> ·
  <a href="#-本地构建">本地构建</a> ·
  <a href="docs/README.md">文档导航</a> ·
  <a href="docs/USER_GUIDE.md">使用指南</a> ·
  <a href="docs/API.md">API 参考</a>
</p>

---

## 项目简介

**MintSoda** 是番剧订阅与下载服务：通过 RSS 订阅番剧，按匹配规则筛选条目后交给 qBittorrent 下载，并提供 Vue WebUI 管理订阅、规则、通知与主题。

## 🚀 快速开始

| 平台 | 方式 |
| --- | --- |
| 🪟 Windows | 下载 `MintSoda.exe` 直接运行，配置保存在 EXE 同目录的 `config/` |
| 🐧 Linux | 一条命令安装到当前目录并注册 systemd 服务 |
| ☕ 通用 | 下载 `MintSoda.jar`，使用 Java 25 运行 |

**Linux 一键安装**

```bash
curl -fsSL https://raw.githubusercontent.com/MoYuanCN/MintSoda/main/linux/install.sh | sudo bash
```

安装过程中会在终端询问登录用户名与密码（直接回车则自动生成）；也可追加参数跳过询问：

```bash
--username <用户名>   # 预设登录账号
--password <密码>     # 预设登录密码，留空则在首次启动时生成或引导设置
--port <端口>         # 默认 17789
--dir <目录>          # 默认安装到执行命令时所在目录
--setup              # 未配置账号时强制在 WebUI 完成首次初始化（已配置则忽略）
```

安装完成后运行 `mintsoda` 打开管理菜单，也可以直接使用子命令：

```bash
mintsoda status     # 运行状态
mintsoda version    # 版本与安装目录
mintsoda update     # 更新到最新版本
mintsoda log        # 实时查看日志
mintsoda restart    # 重启服务
mintsoda uninstall  # 卸载服务
```

> 默认端口 **17789**，首次访问会按平台规则生成凭据或进入账号设置引导。

## ✨ 功能

- **订阅**：Mikan、AniBT 与手动 RSS，支持批量添加、备用 RSS、完结迁移与集数偏移。
- **匹配规则**：本地匹配、本地排除、全局排除、全局匹配与反匹配，优先级固定为反匹配 > 全局匹配 > 排除；下载、RSS 预览与合集预览共用同一判定。
- **下载**：qBittorrent，支持 API Key 与用户名/密码认证；可设并发、延迟、清理规则、优先保留与保存路径模板。
- **通知**：Telegram、自定义 HTTP 请求、媒体库刷新与插件渠道；多套文本 / JSON 模板，可直接作为请求 Body。
- **重命名**：模板化命名与保存路径，支持单条订阅覆盖。
- **外观**：浅色 / 深色、动态或固定强调色、浅深两套背景与 Logo、卡片 / 导航 / 弹窗的透明度与模糊度、圆角，以及五套预设；设置页实时预览。
- **插件**：Java、JavaScript 与 Lua 三种运行时，扩展通知渠道、订阅过滤、下载拦截、条目改写、界面页面与自定义接口，权限按插件逐项授权。
- **多语言**：简体中文、繁體中文与 English。
- **平台**：Windows（EXE / JAR）与 Linux（JAR + systemd）。

功能范围与实现约定见 [功能与实现](docs/SPECIAL_FEATURE.md)。

## 🛠️ 本地构建

需要 **Java 25**。Maven 前端插件会把 Node.js 与 pnpm 下载到 `mint-soda-ui/node/`，依赖缓存写入 `mint-soda-ui/.tools/pnpm-store/`，无需系统级 Node 环境。

```bash
# 仅构建 JAR（含前端资源）
mvn -B package -DskipTests --file pom.xml

# 发布用 JAR + Windows EXE
bash ./package.sh
```

产物位于 `mint-soda-application/target/`：`MintSoda.jar` 与 `MintSoda.exe`。

## 📦 目录结构

| 路径 | 说明 |
| --- | --- |
| `mint-soda-application/` | Spring Boot 后端：订阅、匹配、下载、通知与配置 |
| `mint-soda-ui/` | Vue 3 + Vite + Element Plus 前端，含三种语言的词条 |
| `mint-soda-plugin-api/` | 稳定的插件接口模块，插件以 `provided` 依赖 |
| `examples/plugins/` | 三种运行时的示例插件与市场索引，不参与正式构建 |
| `linux/` | Linux 安装、管理菜单与控制脚本 |
| `docs/` | 使用、开发、插件、API、配置、安全与多语言文档 |
| `.github/workflows/` | 手动触发的构建检查与 Release 发布 |

## 📚 文档导航

全部文档的索引见 [README.md](docs/README.md)。

## 📄 许可

本项目采用 **GPL-2.0** 许可证，详见 [LICENSE](LICENSE)。

<p align="center">
  <sub>Issue 与 PR 请提交到本仓库</sub>
</p>
