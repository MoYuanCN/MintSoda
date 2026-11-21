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
  <a href="#-功能特色">功能特色</a> ·
  <a href="#-插件系统">插件系统</a> ·
  <a href="#-本地构建">本地构建</a> ·
  <a href="docs/README.md">文档导航</a> ·
  <a href="docs/USER_GUIDE.md">使用指南</a> ·
  <a href="docs/API.md">API 参考</a>
</p>

---

## 项目简介

**MintSoda** 面向自托管场景：只保留 qBittorrent 下载器，收敛通知渠道，重写匹配规则与通知模板，并内置插件系统与多语言 WebUI。项目最初派生自 [ANI-RSS](https://github.com/wushuo894/ani-rss)，现已独立演进，代码命名空间、产物与文档均使用 MintSoda。

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

## ✨ 功能特色

| 能力 | 说明 |
| --- | --- |
| 匹配规则 | 反匹配 > 全局匹配 > 排除，本地匹配为普通路径必要条件；下载、预览、合集预览共用同一判定 |
| 下载 | 仅 qBittorrent，支持 API Key 与用户名密码两种认证，备用 RSS 与洗版可预期 |
| 通知 | Telegram、自定义 HTTP 请求、媒体库刷新，多套命名模板直接渲染字段变量 |
| 插件 | Java / JavaScript / Lua 三种运行时，细粒度权限、私有数据、市场安装 |
| 外观 | 主题色与动态取色、亚克力与模糊、自定义背景与 Logo、自定义 CSS/JS |
| 部署 | Windows EXE 便携配置，Linux 一键安装并注册 systemd |
| 多语言 | 简体中文、繁体中文与英文，切换不影响服务端配置 |

完整的功能清单、配置说明与实现约定见 [功能与实现](docs/SPECIAL_FEATURE.md)；日常使用见 [使用指南](docs/USER_GUIDE.md)；对接脚本见 [API 参考](docs/API.md)。

## 🎨 外观与主题

- 主题色自由选择，或开启「从背景图动态取色」。
- 卡片、导航栏、弹窗支持不透明度与模糊度，内置多套预设。
- 可自定义应用/登录背景与 Logo、圆角、内容宽度与启动页。
- 支持自定义 CSS/JS 注入，以及 WebUI 在线更新。

## 🧩 插件系统

- 独立接口模块 `mint-soda-plugin-api`，插件以单独 JAR 加载，宿主与插件只共享稳定接口。
- Java 插件以 JAR 分发；**JavaScript / Lua 插件**只需一个 `plugin.json` 加一个脚本打成 ZIP。
- 扩展点：通知渠道、订阅过滤器、订阅事件、周期任务、设置区块、模板变量、下载拦截、下载事件、自定义 HTTP 接口。
- 每个插件的能力按组逐项授权，可随时调整并立即生效；插件可在私有目录持久化数据，升级不受影响。
- 支持插件市场：填写 JSON 索引地址即可浏览、安装与检查更新。
- 安装与更新校验 `sha256` 与文件大小，地址限公网 `http`/`https`。
- 详见 [插件开发指南](docs/PLUGIN_DEVELOPMENT.md)，示例见 `examples/plugins/`。

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

| 我想… | 看这里 |
| --- | --- |
| 安装并跑起来 | [使用指南 · 运行与配置文件](docs/USER_GUIDE.md#运行与配置文件) |
| 了解功能边界与实现 | [功能与实现](docs/SPECIAL_FEATURE.md) |
| 写脚本调用接口 | [API 参考](docs/API.md) |
| 排查或迁移配置 | [配置参考](docs/CONFIG.md) |
| 编写插件 | [插件开发指南](docs/PLUGIN_DEVELOPMENT.md) |
| 参与开发 | [开发指南](docs/DEVELOPMENT.md) · [贡献指南](docs/CONTRIBUTING.md) |
| 翻译界面 | [多语言与翻译贡献](docs/I18N.md) |
| 部署安全与已知限制 | [安全说明](docs/SECURITY.md) |

全部文档的索引见 [docs/README.md](docs/README.md)。

## 📄 许可

本项目采用 **GPL-2.0** 许可证，详见 [LICENSE](LICENSE)。

<p align="center">
  <sub>欢迎提交 Issue 与 PR，也欢迎点个 ⭐ Star</sub>
</p>
