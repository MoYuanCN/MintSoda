# AGENTS.md

本文件面向在本仓库工作的 AI 编码代理与协作者，说明项目定位、构建方式与必须遵守的约定。文档总索引见 `docs/README.md`；用户功能说明见 `docs/USER_GUIDE.md`，接口清单见 `docs/API.md`，配置字段清单见 `docs/CONFIG.md`，开发与提交流程见 `docs/DEVELOPMENT.md`、`docs/CONTRIBUTING.md`，安全要求见 `docs/SECURITY.md`，相对上游的差异见 `docs/SPECIAL_FEATURE.md`，多语言流程见 `docs/I18N.md`。

## 项目定位

ANI-RSS-Yuri 是 ANI-RSS 的 Fork：通过 RSS 订阅番剧，统一匹配规则后调用 qBittorrent 下载，并提供 Vue WebUI 管理订阅、规则与通知。

- 运行平台：Linux 与 Windows（JAR；Windows 另有 EXE）。
- 不维护 Docker、容器发布与 macOS 打包，不要重新引入。
- 默认订阅与下载流程尽量与上游 ANI-RSS 保持一致；Fork 的差异集中在匹配规则、通知模板、qBittorrent 认证、外观设置与 Windows 便携配置。

## 技术栈与目录

| 位置 | 说明 |
| --- | --- |
| `pom.xml` | Maven reactor，Java 25，Spring Boot 父工程 |
| `ani-rss-application/` | 后端：`controller`、`service`、`task`、`download`、`notification`、`entity`、`util`、`config` |
| `ani-rss-ui/` | 前端：Vue 3 + Vite + Element Plus，`src/view` 页面、`src/js` 工具与默认配置 |
| `linux/` | Linux 安装、控制、卸载脚本 |
| `ani-rss-plugin-api/` | 稳定的插件接口模块，插件以 `provided` 依赖，不应引入宿主内部类 |
| `examples/plugins/` | 示例插件与市场索引，不参与正式构建 |
| `docs/` | 文档索引、用户指南、API 参考、配置参考、开发指南、分支特性、贡献规范、安全说明、插件开发指南、多语言说明 |
| `.github/workflows/` | 手动触发的构建检查与 Release 发布 |

后端入口为 `ani-rss-application/src/main/java/ani/rss/AniRssApplication.java`；默认端口 17789，接口统一挂在 `/api` 前缀下。

## 构建与运行

依赖 Java 25。Maven 前端插件会把 Node.js 与 pnpm 安装到 `ani-rss-ui/node/`，依赖缓存写入 `ani-rss-ui/.tools/pnpm-store/`，两者均已被忽略，不需要系统级 Node 环境。

```bash
# 只构建 JAR（含前端资源）
mvn -B package -DskipTests --file pom.xml

# 发布用 JAR + Windows EXE
bash ./package.sh
```

- `package.sh` 使用 Maven `windows` profile，同时产出 `ani-rss-application/target/ani-rss-Yuri.jar` 与 `ani-rss-application/target/ani-rss-Yuri.exe`。
- `linux/install-yuri.sh` 是一键安装入口（安装到当前目录并注册 systemd），`linux/assyuri` 是配套的交互菜单脚本；`linux/install-yuri-legacy.sh` 保留为安装到 `/opt/ani-rss-yuri` 的旧方式。
- Windows profile 的 `generate-resources` 阶段需要 `bash` 与 `curl`；CI 在 Ubuntu 上执行同一脚本。
- 前端单独调试：在 `ani-rss-ui/` 下用 `pnpm install` 与 `pnpm dev`（可使用 Maven 下载到 `ani-rss-ui/node/` 的 pnpm），Vite 端口 37789，并将 `/api` 代理到本地后端。
- 运行时可用的环境变量/参数：`CONFIG`/`--config=` 指定配置目录；`MCP_ENABLED`/`--mcp-enabled=` 与 `SWAGGER_ENABLED`/`--swagger-enabled=` 打开默认关闭的 MCP 与 Swagger；`--setup` 仅在尚未配置账号时强制在 WebUI 完成首次初始化（已配置时完全失效）；`ANI_RSS_USERNAME`/`ANI_RSS_PASSWORD` 覆盖登录凭据；`SERVER_PORT` 等 Spring 标准配置可覆盖端口（默认 17789）。

不要改动默认的 `SWAGGER_ENABLED=false`、`MCP_ENABLED=false`；需要调试时通过环境变量临时开启。

## 代码约定

### 后端（Java）

- 保持现有包结构与 Lombok 风格：实体用 `@Data`/`@Accessors(chain = true)`，日志用 `@Slf4j`，依赖注入优先 `@Resource`/构造注入。
- 配置、常量、工具类集中在既有位置，不新建并行的工具层；已有工具能复用时不复制实现。
- 新增 REST 接口放在对应的 `*Controller`，返回 `Result<T>`，并加 `@Operation` 说明。
- 业务异常用 `IllegalArgumentException`/`IllegalStateException`/`ResultException`，由 `handle/CustomExceptionHandler` 统一转成 `Result`；不要在控制器里手写响应体。
- 时间、文件、URL、正则等通用处理优先使用项目已有的 Hutool 工具与 `commons` 下的封装。
- 不要在源码中添加与实现无关的注释；只在复杂规则（如匹配优先级、迁移兼容）处写简短说明，语言与现有代码一致（中文）。

### 前端（Vue / JavaScript）

- 页面使用 Vue 3 `<script setup>`，组件与 Element Plus 用法沿用现有页面。
- `.vue` 模板与脚本缩进为 2 空格；`src/js/config.js` 等配置文件沿用 4 空格。
- 请求统一走 `src/js/api.js` / `src/js/http.js`，不要在组件里直接拼 `fetch`。
- 新增设置项时同步更新 `src/js/config.js` 的默认对象，否则 `setConfig` 会缺失字段。
- 界面文案统一通过 `vue-i18n` 的 `$t` / `t` 获取，禁止在模板与脚本中硬编码中文；词条按 `zh-CN` 为源写入 `ani-rss-ui/src/i18n/locales/`，新增或修改流程见 `docs/I18N.md`。

## 配置变更清单

配置以 JSON 持久化（默认文件名 `config.v2.json`，位于配置目录）。任何配置字段或语义变更必须同时处理：

1. 实体：`entity/Config.java`（或对应实体）。
2. 默认值：`config/DefaultConfigFactory.java`。
3. 旧配置补齐与迁移：`util/other/ConfigUtil.java` 的 `format` 及相关迁移方法。
4. 前端默认对象：`ani-rss-ui/src/js/config.js`。
5. 设置界面：对应的 `ani-rss-ui/src/view/config/*.vue`。
6. 文档：`docs/USER_GUIDE.md` 与 `docs/CONFIG.md` 的字段表，必要时 `docs/DEVELOPMENT.md`。

规则：

- 旧 JSON 缺少新字段时必须安全降级，不能让用户重建配置。
- `format` 只补默认值、做显式迁移，不静默丢弃用户已有数据；无法无损迁移时保留旧字段并写迁移逻辑。
- 保存配置的入口是 `ConfigService#setConfig`；密码类字段在前端传空表示"不修改"，后端据此保留旧值，改动时要维持该语义。
- 需要保留的旧字段（如 `rssSleepMinutes`、`notificationTemplate`、`telegramTemplate`、`customRequestBody`）继续写入，仅作为兼容用途。

## 匹配规则

- 唯一判定入口是 `util/other/MatchRuleUtil#evaluate`；RSS 下载、RSS 预览、合集预览必须走同一实现，不要在别处复制判定逻辑。
- 优先级固定为：反匹配 > 全局匹配 > 排除；本地匹配是普通路径的必要条件。
- 反匹配命中时使用规则指定的字幕组；启用反匹配但未命中时按订阅的 `reverseMatchMissBehavior`（`SKIP` 默认、`NORMAL`、`ALLOW`）处理。
- 规则语法：`{{字幕组}}:正则` 限定来源字幕组；反匹配规则由 `pattern` + `subgroup` 组成。
- 改动判定顺序、迁移方式或规则格式时，同步更新 `docs/USER_GUIDE.md` 与两个前端测试区（`ReverseMatchView.vue`、`GlobalReverseMatchView.vue`）。

## 通知

- 通知类型仅保留 `TELEGRAM`、`CUSTOM_REQUEST`、`EMBY_REFRESH`；`WEB_HOOK` 只用于读取旧配置，加载时迁移为 `CUSTOM_REQUEST`。
- 模板实体字段为 `id / name / type / content`，`type` 为 `TEXT` 或 `JSON`。
- 模板直接渲染 `${title}`、`${subgroup}`、`${text}` 等字段变量；新界面与文档中不得再使用 `${notification}` 嵌套整段消息。
- JSON 模板必须解析为合法 JSON，变量只放在字符串值内，再用 Gson 序列化，避免转义问题。
- 模板删除后，引用它的通知要回落到备用模板；新增渠道或模板行为时保持该兜底逻辑。

## 安全硬性要求

完整说明见 `docs/SECURITY.md`，以下为不可协商的下限：

- 不提交任何凭据、令牌、真实配置、备份、日志或上传资源；示例值与文档中只使用占位符。
- 新增接口默认加 `@Auth`。只有不含敏感信息、且登录页渲染所必需的接口可以公开（例如 `/api/publicSettings`），新增公开接口需在评审中说明理由。
- 任何日志、异常信息、错误响应都不得包含密码、密码摘要、API Key、JWT、Telegram Bot Token、qBittorrent 凭据或完整认证头。
- 访问用户提供的 URL 前必须经过 `commons/URLUtils#verify` 一类校验，拒绝回环与内网字面地址；不要把校验放宽为仅检查 `http` 前缀。
- 文件读写必须基于固定根目录：先 `normalize()`，再做 `startsWith(root)` 校验；上传只接受白名单扩展名并校验文件头（参考 `UploadController`、`FileController#uiAsset`）。
- 保持更新包的 `size` + `sha256` 双重校验；WebUI 更新同样校验，不要跳过。
- 默认安全开关不能被放松：`allowCors=false`、`innerIP` 默认关闭但启用时行为不得削弱、`limitLoginAttempts` 保持生效、密码/API Key 变更后必须轮换 `tokenId` 与 JWT 会话。
- 不要恢复内置的 `admin/admin` 默认凭据：`LoginBootstrapUtil` 负责首次启动（Windows 引导设置、Linux 生成随机强密码只输出一次、`ANI_RSS_USERNAME`/`ANI_RSS_PASSWORD` 覆盖），凭据强度规则在 `commons/CredentialUtils`，前后端需保持一致。
- 主题相关设置（`accentColor`、`themeDynamicColor`、导航栏/弹窗的模糊度与不透明度）只用于 CSS 变量与强调色，不得写入任何主题样式文件或独立资源；预设仅写回已有配置字段。
- 前端图标只在模板中以名称使用（`icon="Search"` 或无 import 的 `<Search/>`）时才需要加入 `ani-rss-ui/src/js/icons.js` 的全局注册表；显式 `import` 的图标不要重复注册，也不要恢复 `@element-plus/icons-vue` 的全量导入。
- 前端 `v-html` 只能用于可信内容；`AboutView` 渲染的 Release 说明来自 GitHub API，属于外部内容，改动渲染链路时要考虑净化。`/api/custom.js`、`/api/custom.css` 会注入页面，视为管理员可信输入。

## 插件系统

- 唯一接口模块是 `ani-rss-plugin-api`；接口破坏性变更必须提升 `PluginApi.VERSION`，不得在插件 API 中引用 `ani.rss.application` 的内部类型。
- 插件运行时通过 `plugin/runtime/PluginSource` 抽象；新增语言只加一个 `PluginSource` 实现与 `PluginSources` 分支，不要改 `PluginManager` 的加载逻辑。
- JavaScript 插件运行在 `ScriptSandbox`：必须保持 `ClassShutter` 全拒绝、`initSafeStandardObjects` 与调用超时，不得为兼容放开 Java 类访问；传给脚本的宿主数据必须先转成原生对象。
- Lua 插件运行在 `LuaPlugin` 自建的安全 `Globals`：只装基础库，不装 `IoLib`/`OsLib`/`luajava`，并移除 `dofile`/`loadfile`/`require`/`package.loadlib`；新增库或加载入口前必须确认不引入文件与进程访问。
- 扩展点由 `PluginManager` 统一加载与分发：通知渠道、订阅过滤器、订阅事件、周期任务、设置区块、模板变量、下载拦截、下载事件、插件自定义接口。扩展点列表在启用状态变化时重建索引，分发路径只读快照；新增扩展点时同步更新 `docs/PLUGIN_DEVELOPMENT.md`。
- 模板占位符统一走 `util/other/TemplateVariables`，不要在通知、重命名、下载路径里再各写一份替换逻辑。
- 插件自定义接口必须经 `/api/plugin/api/{pluginId}/**` 分发并保持 `@Auth`，不要为插件单独开放免鉴权路由。
- 插件过滤器在 `MatchRuleUtil` 之后执行，不得绕过反匹配 > 全局匹配 > 排除的既有优先级；RSS 下载、预览与合集预览必须共用同一插件过滤路径。
- 插件路径一律经 `PluginPaths` 规范化并限制在 `plugins/` 内；插件标识仅允许 `A-Z a-z 0-9 . _ -`。
- 安装与更新必须校验 `size` 与 `sha256`，远程地址经 `commons/URLUtils#verify`；先写临时目录校验通过后再替换正式目录。
- 插件权限由 `PluginPermission` 定义、按插件持久化在 `PluginRecord.grantedPermissions`，注册扩展点时在 `PluginContextImpl` 强制校验；新增扩展点必须同时定义对应权限，不得把校验放到插件侧。读与写权限分开（`*_READ` / `*_WRITE`），破坏性操作（取消下载、写数据）使用独立的高风险权限。
- 插件私有数据只能经 `PluginPaths.dataFile` 访问，规范化后必须位于 `plugins/{id}/data` 内；升级与重装必须保留 `data/`，卸载才删除。备份导出排除 `installed.json`，导入必须逐条校验路径并在临时目录完成后整体替换。
- 权限调整入口是 `PluginService#setPermissions`（`POST /api/plugin/setPermissions`），保存后对已启用插件立即重载；升级安装不得重置用户已调整的权限。
- 插件与服务同权限运行；新增插件接口默认加 `@Auth`，新增公开插件接口需在评审中说明理由。
- `PLUGIN` 通知类型必须保留在 `ConfigUtil.format` 的允许列表内；删除插件后引用它的通知应静默跳过而不是报错。
- 前端插件字段只按白名单类型渲染，不得把插件提供的内容当作 HTML 注入。

## 不要做的事

- 对外可见的标识统一使用 `ani-rss-Yuri`（产物名、User-Agent、日志、服务名、标签、备份与 ICS 名称），不要改回 `ani-rss`；涉及历史数据的过滤（如 qBittorrent 分类与标签）必须同时兼容旧值。
- 不要恢复已移除的功能：邮件/QQ/Server 酱/Bark 通知、Aria2/Transmission 下载器、qBittorrent 任务进度页、内置播放与刮削、OpenList 上传、AnimeGarden、Bangumi 点格子、摸鱼检测、trackers 更新、Docker、macOS。
- 不要把下载器默认值或配置迁移方向改为 qBittorrent 之外的类型；`ConfigUtil.format` 会强制归一化为 qBittorrent。
- 不要重新引入 `UPDATE.md` 依赖，Release 说明由 GitHub 自动生成。
- 不要在关于页或文档中指向非本 Fork 的仓库与社群链接。
- 不要修改构建工具版本（Java 25、Node v26.12.1、pnpm 12.15.0）而不验证 JAR 与 EXE 同时可用。
- 不要提交本地工具目录、忽略文件或本机环境相关内容。

## 测试

- 现有测试仅 `ani-rss-application/src/test/java/ani/rss/AniRssApplicationTests.java`，会访问 Bangumi 接口，依赖网络。
- 用户未要求测试时，不要主动新增或运行测试；需要验证时优先选择配置迁移、模板渲染、匹配优先级这类可离线覆盖的点。
- 提交前至少保证 `mvn -B package -DskipTests` 通过；改动了工作流则同时做 YAML 校验。

## 提交与交付

- 使用 Conventional Commits：`<type>(<scope>): <中文摘要>`；type 与 scope 保持英文小写，摘要与正文使用中文，不要在摘要里用英文祈使句。常用 scope：`match`、`notification`、`config`、`security`、`ui`、`download`、`plugin`、`build`、`release`、`docs`。
- 一次提交只做一件事；配置迁移与功能改动尽量分开，方便回溯。
- 交付前自检：配置字段清单是否齐全、旧配置是否仍可加载、下载/预览是否共用判定、日志是否泄露敏感值、文档是否同步、构建是否通过。
