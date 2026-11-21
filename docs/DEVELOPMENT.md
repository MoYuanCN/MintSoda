# MintSoda 开发指南

本指南面向在本仓库提交代码的开发者，说明模块划分、构建调试、代码约定与发布流程。CI、插件、接口与多语言各有独立文档，见文末链接。

## 本文目录

- [技术栈](#技术栈)
- [目录](#目录)
- [后端模块划分](#后端模块划分)
- [环境与构建](#环境与构建)
- [代码约定](#代码约定)
- [配置变更清单](#配置变更清单)
- [测试与自检](#测试与自检)
- [匹配规则维护](#匹配规则维护)
- [CI 与发布](#ci-与发布)

## 技术栈

- Java 25、Spring Boot 与 Maven：后端服务、定时 RSS 任务、qBittorrent 接入和本地配置。
- Vue 3、Vite、Element Plus 与 pnpm：WebUI。
- 配置以 JSON 文件保存，并通过实体对象向前后端传递。增加配置字段时同时考虑缺省值、已有配置升级和 UI 默认模型。

## 目录

- `mint-soda-application/`：Java 应用、服务、任务、接口和打包配置。
- `mint-soda-ui/`：Vue 页面和前端资源。
- `mint-soda-plugin-api/`：稳定的插件接口模块，插件以 `provided` 依赖。
- `examples/plugins/`：示例插件与市场索引，不参与正式构建。
- `linux/`：Linux JAR 的 systemd 安装、控制与卸载脚本。
- `.github/workflows/`：构建检查、Release 发布与旧版本清理。
- `docs/`：文档索引与用户、开发、插件、API、安全、多语言文档。

本项目只支持 Linux 与 Windows，不维护 Docker 或 macOS 构建。

接口清单与调用示例见 [API 参考](API.md)，插件开发与市场格式见 [插件开发指南](PLUGIN_DEVELOPMENT.md)，多语言与词条维护见 [多语言与翻译贡献](I18N.md)。协作与安全要求见 [贡献指南](CONTRIBUTING.md) 与 [安全说明](SECURITY.md)；面向 AI 编码代理的完整仓库约定见根目录 `AGENTS.md`。完整文档索引见 [文档导航](README.md)。

## 后端模块划分

下载链路按职责拆分为若干可独立替换的单元，新增能力时优先扩展这些单元而不是继续给大服务加方法：

| 单元 | 位置 | 职责 |
| --- | --- | --- |
| `DownloadService` | `service/` | 下载调度：判断是否需要下载、调用下载器、失败重试 |
| `DownloadPathResolver` | `service/support/` | 下载位置模板解析（剧场版、自定义位置、季度与年份替换） |
| `DownloadNotifier` | `service/support/` | 下载完成通知、字幕组反查与完结迁移 |
| `DownloadPass` | `service/support/` | 单轮刷新内的目录与路径缓存，降低机械硬盘唤醒 |
| `ItemsUtil` | `util/other/` | RSS 条目解析、过滤与订阅判断 |
| `RssFetcher` | `util/other/` | RSS 请求、条件缓存、失败退避与旧内容兜底 |
| `BgmUtil` | `util/other/` | Bangumi 作品与剧集查询 |
| `BgmAuth` | `util/other/` | Bangumi 账号、Token 注入、有效期与自动刷新 |
| `PluginManager` | `plugin/` | 插件生命周期、调度与扩展点分发 |
| `PluginSource` | `plugin/runtime/` | 运行时抽象，Java / JavaScript / Lua 各自实现 |

约定：

- 大服务只做编排，具体规则放到 `support` 或独立工具类；旧入口保留为委托方法，避免一次性改动所有调用方。
- 拆分类时不要改变公开方法签名与静态入口，先委托再逐步迁移。
- 新增缓存要说明生命周期（单轮、进程内、跨轮次），跨线程共享必须使用并发结构。

## 环境与构建

### 本地调试

前端可以脱离 Maven 单独调试：

```bash
cd mint-soda-ui
./node/pnpm.cmd install     # Windows；Linux 使用 ./node/pnpm
./node/pnpm.cmd dev         # Vite 端口 37789，/api 代理到 127.0.0.1:17789
```

需要指向其它后端时设置 `SERVER_HOST`，例如 `SERVER_HOST=http://192.168.1.10:17789`。生产构建使用 `./node/pnpm.cmd build`，产物由 Maven 打入 JAR，不需要单独部署。

后端调试常用开关：

| 方式 | 作用 |
| --- | --- |
| `CONFIG=/path/to/config` 或 `--config=/path/to/config` | 指定配置目录 |
| `--setup` | 尚未配置账号时强制进入 WebUI 初始化，已配置时无效 |
| `SWAGGER_ENABLED=true` | 临时开启 Swagger，默认关闭 |
| `MCP_ENABLED=true` | 临时开启 MCP 端点，默认关闭 |
| `MINT_SODA_USERNAME` / `MINT_SODA_PASSWORD` | 覆盖登录凭据，每次启动生效 |
| `SERVER_PORT` | 覆盖端口，默认 `17789` |

### 构建

需要 Java 25。Maven 前端插件会把 Node.js 和 pnpm 下载到 `mint-soda-ui/node/`，pnpm 依赖缓存写入 `mint-soda-ui/.tools/pnpm-store/`；两处都由 `.gitignore` 排除，不需要安装到系统目录。前端依赖使用 `pnpm-lock.yaml` 锁定。

只构建 JAR：

```bash
mvn -B package -DskipTests --file pom.xml
```

构建发布用 JAR 和 Windows EXE：

```bash
bash ./package.sh
```

`package.sh` 使用 Maven `windows` profile 生成 EXE，并保留 JAR。CI 的 `build-test.yml` 执行相同打包脚本；`build.yml` 将 JAR 与 EXE 附加到 GitHub Release，不发布容器镜像。

Linux 安装脚本使用 Java 直接运行 JAR，并将服务配置目录指向安装目录。手动运行时可通过 `CONFIG=/path/to/config java -jar MintSoda.jar` 指定配置目录。

## 代码约定

- Java 使用现有包结构和 Lombok 实体风格；前端使用 Vue 3 `<script setup>` 与现有组件样式。
- 界面文案统一通过 `mint-soda-ui/src/i18n/` 的 `$t` / `t` 获取，新增文案需同步 `locales` 下三种语言；流程见[多语言与翻译贡献](I18N.md)。
- 配置字段需覆盖六处：实体类、后端默认值、旧配置补齐与迁移、前端默认对象、对应设置界面、用户指南；清单见 [配置变更清单](#配置变更清单)。
- 一轮 RSS 刷新用 `AniUtil.beginDeferredSync` / `endDeferredSync` 包裹，期间 `AniUtil.sync` 只标记待保存，结束时写一次 `ani.v2.json`；新增的刷新入口要保持同样包裹，否则会退化为逐条写盘。
- 复用通知模板使用 `id / name / type / content` 字段；`type` 为 `TEXT` 或 `JSON`。更改持久化格式时，保留旧字段读取能力并在 `ConfigUtil.format` 中迁移已有配置。
- 旧版 `Config.notificationTemplate` 是完整消息正文，迁移时应并入默认文本通知模板；模板应直接渲染 `${title}`、`${subgroup}`、`${text}` 等字段，不再嵌套 `${notification}`。
- 开发者设置由 `developerOptionsEnabled` 控制是否显示；通过关于页的凭据验证后，先确认并保存整份当前配置，再启用该标记并刷新页面。
- 过滤规则或业务条件有多个调用点时抽取共享逻辑。RSS 下载、RSS 预览和合集预览应保持一致。
- 合集保存位置统一由 `CollectionService#resolveDownloadPath` 计算：优先使用 `collectionDownloadPathTemplate`，其次为会话中的自定义位置，最后回落到普通保存位置；新增合集入口不要自行读取 `Ani#customDownloadPathTemplate`。
- RSS 读取统一走 `ItemsUtil#getRss`：同一轮内复用响应、跨轮使用 `ETag` 条件请求、失败时回退到上次成功内容。需要新增 RSS 请求入口时复用该方法，不要自行发 HTTP 请求。
- 短间隔轮询依赖单轮缓存：`ItemsUtil#beginRssBatch`/`endRssBatch`、`TorrentUtil#beginPass`/`endPass`、`DownloadService#beginPass`/`endPass` 分别缓存 RSS 响应、下载器任务列表和本地目录列表。新增轮次入口时要成对调用，避免使用过期数据。
- 下载路径计算较重，单轮内会按订阅字段做记忆化；修改路径模板相关字段时同步检查缓存键。
- 修改配置语义时更新用户指南；修改发布流程时同步检查 workflow、安装脚本、README 和更新说明。
- 保持配置字段向后兼容。已有 JSON 中缺少新字段时应采用安全默认值，不要求用户手工重建配置。
- 提交遵循 Conventional Commits，例如 `feat(match): add global reverse rules`、`docs: document fork-specific behavior`、`build: remove container publishing`。

## 配置变更清单

任何配置字段或语义变更必须同时处理：

1. 实体：`entity/Config.java`（或对应实体）。
2. 默认值：`config/DefaultConfigFactory.java`。
3. 旧配置补齐与迁移：`util/other/ConfigUtil.java` 的 `format` 及相关方法。
4. 前端默认对象：`mint-soda-ui/src/js/config.js`。
5. 设置界面：`mint-soda-ui/src/view/config/*.vue`。
6. 文档：`docs/USER_GUIDE.md` 与 `docs/CONFIG.md` 的字段表，必要时同步本文件与 `docs/SPECIAL_FEATURE.md`。

规则：

- 旧 JSON 缺少新字段时必须安全降级，不能让用户重建配置。
- `format` 只补默认值、做显式迁移，不静默丢弃用户已有数据；无法无损迁移时保留旧字段并写迁移逻辑。
- 密码类字段在前端传空表示“不修改”，后端 `ConfigService#setConfig` 据此保留旧值。
- 兼容字段（`rssSleepMinutes`、`notificationTemplate`、`telegramTemplate`、`customRequestBody`）继续写入，仅作兼容用途。

## 测试与自检

- 现有自动化测试只有 `mint-soda-application/src/test/java/mint/soda/MintSodaApplicationTests.java`，会访问 Bangumi 接口，依赖网络。
- 用户未要求测试时不要主动新增测试；需要验证时优先选择可离线覆盖的点：配置迁移、模板渲染、匹配优先级、插件权限解析。
- 提交前至少运行 `mvn -B package -DskipTests --file pom.xml`；改动了前端则先执行一次 `pnpm build`。改动工作流时用 YAML 校验工具检查 `.github/workflows/*.yml`。

## 匹配规则维护

当前规则入口由 `MatchRuleUtil` 统一处理，RSS 下载与合集预览调用同一方法。反匹配规则由标题正则和目标字幕组组成；规则命中时使用目标字幕组。规则优先级和反匹配未命中行为定义见[用户指南](USER_GUIDE.md#标题匹配规则)。若改变判定顺序、配置迁移方式或规则格式，请同时更新该文档和两个界面的标题测试行为。

## CI 与发布

- `build-test.yml`：手动触发，运行 Java 25 Maven 打包并生成 Linux 可构建的 JAR 与 Windows EXE。
- `build.yml`：手动触发，构建后创建 GitHub Release，发布 `MintSoda.jar` 和 `MintSoda.exe`。
- 变更 Maven profile 或构建工具版本时，确认 JAR 与 EXE 都仍包含前端资源；不要恢复 Docker 镜像或 macOS 打包步骤。
- 工作流权限按实际操作设置最小范围：构建检查只需读取仓库，发布流程需要 `contents: write`。
- `delete-old-releases.yml`：手动触发，按脚本中的保留策略清理旧 Release。

## 延伸阅读

- [API 参考](API.md)：接口清单、鉴权与统一返回体。
- [配置参考](CONFIG.md)：`Config` 全字段、默认值、取值约束与迁移。
- [插件开发指南](PLUGIN_DEVELOPMENT.md)：扩展点、权限与三种运行时。
- [多语言与翻译贡献](I18N.md)：词条结构与新增语言流程。
- [贡献指南](CONTRIBUTING.md) · [安全说明](SECURITY.md) · [文档导航](README.md)。
