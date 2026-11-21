# 功能与实现说明

本文说明 MintSoda 的功能范围与实现约定。功能用法见[使用指南](USER_GUIDE.md)，接口清单见 [API 参考](API.md)，字段与迁移见 [配置参考](CONFIG.md)，构建与协作见 [开发指南](DEVELOPMENT.md) 与 [贡献指南](CONTRIBUTING.md)，多语言见[多语言与翻译贡献](I18N.md)，安全边界见[安全说明](SECURITY.md)。

当前版本：`3.3.0`。

## 本文目录

- [功能范围](#功能范围)
- [订阅与下载](#订阅与下载)
- [匹配规则](#匹配规则)
- [通知](#通知)
- [认证与安全](#认证与安全)
- [外观与主题](#外观与主题)
- [插件系统](#插件系统)
- [多语言](#多语言)
- [缓存与运行维护](#缓存与运行维护)
- [性能与前端](#性能与前端)
- [数据与接口](#数据与接口)
- [标识](#标识)
- [部署](#部署)
- [配置字段的处理约定](#配置字段的处理约定)

## 功能范围

| 领域 | 能力 |
| --- | --- |
| 下载器 | qBittorrent，支持 API Key 与用户名/密码两种认证 |
| 订阅来源 | Mikan、AniBT、手动 RSS |
| 通知 | Telegram、自定义 HTTP 请求、媒体库刷新、插件渠道 |
| 通知模板 | 多套可命名模板，文本 / JSON，模板可直接作为请求 Body |
| 匹配 | 本地匹配、本地排除、全局排除、全局匹配、反匹配，统一判定入口 |
| 界面 | Vue 3 WebUI，分组设置页、插件页、三种语言、可调主题与质感 |
| 插件 | Java、JavaScript、Lua 三种运行时，统一扩展点与权限 |
| 平台 | Windows（EXE / JAR）、Linux（JAR + systemd） |
| 默认端口 | 17789 |
| 账号 | 首次启动按平台生成或引导设置 |

## 订阅与下载

### qBittorrent

- 下载器为 qBittorrent。`qbAuthType` 区分 **API Key** 与 **用户名/密码** 两种认证，`qbUsername`、`qbPassword` 独立保存。
- 旧配置加载时归一化为 qBittorrent，并按密码是否以 `qbt_` 开头推断认证方式。
- 分类与标签统一由 `MintSoda` 写入。

### 订阅来源

- 添加订阅支持 Mikan、AniBT 与手动 RSS 三种来源。
- 批量添加按来源读取标题、字幕组与详情链接。

### RSS 刷新

- 刷新间隔以**秒**为单位（`rssSleepSeconds`，默认 900），`rssSleepMinutes` 在保存时继续写入以兼容旧版本。
- `rssConcurrency`（1-16，默认 4）控制并发预取，`rssTimeout` 范围 1-120 秒。
- 每轮按固定间隔起跑并扣除本轮耗时，短间隔下不漂移、不空转。
- 跨轮使用 **ETag 条件请求**，`304` 时复用上次内容；请求失败时退避并沿用最近一次成功内容。
- 单轮内复用下载器任务列表、本地目录与下载路径，删除文件后主动失效。对应入口为 `ItemsUtil#beginRssBatch` / `endRssBatch`、`TorrentUtil#beginPass` / `endPass` 与 `DownloadService#beginPass` / `endPass`。

### 订阅进度写入

- 刷新期间 `AniUtil.beginDeferredSync` 只记录待保存，整轮结束写一次 `data/subscriptions.json`；请求线程的即时保存行为不变。
- 备用 RSS 复制订阅为属性拷贝。
- 缺集检测使用集数集合，条件请求与退避记录有上限。

### 合集保存路径

- 合集开始下载时按当前标题、TMDB、季重新计算保存位置。
- `collectionDownloadPathTemplate` 留空表示沿用普通保存位置；仅在使用 `${bgmId}` 时才解析 BGM ID。
- `/api/collectionDownloadPath` 供合集弹窗显示实际路径。

### 订阅排序

| 枚举 | 说明 |
| --- | --- |
| `SCORE` | 评分 |
| `PINYIN` | 拼音 |
| `DOWNLOAD_TIME` | 更新时间 |
| `TITLE` | 标题字符顺序 |
| `UPDATE_WEEK` | 每周放送日轮转 |
| `STANDBY_RSS` | 备用 RSS 数量降序 |

- `/api/listAni` 支持 `?sort=` 临时指定排序，未知值回退到配置中的默认排序。
- 订阅页顶部提供排序下拉，仅影响当前浏览，不修改默认设置。

## 匹配规则

### 三类规则

- **匹配**：条目必须命中；订阅配置多条时每条都要命中。
- **排除**：命中即跳过；分为订阅本地与全局两套。
- **反匹配**：由标题正则与目标字幕组组成，命中后条目归入指定字幕组，不要求原 RSS 已带有该字幕组。

规则语法支持 `{{字幕组}}:正则` 限定来源字幕组。

### 优先级

固定为 **反匹配 > 全局匹配 > 排除**，本地匹配是普通路径的必要条件。`MatchRuleUtil#evaluate` 是唯一判定入口，RSS 下载、RSS 预览与合集预览共用。

1. 本地反匹配命中 → 直接接收，使用规则指定的字幕组。
2. 全局反匹配命中 → 同上（需订阅启用全局反匹配，或字幕组填为 `反匹配` 标记）。
3. 反匹配已启用但未命中 → 按 `reverseMatchMissBehavior`：`SKIP`（默认）、`NORMAL`、`ALLOW`；订阅留空时跟随设置中的全局默认。
4. 本地匹配规则 → 每条都必须命中。
5. 全局匹配 → 每条适用规则都必须命中，通过后优先于排除。
6. 本地与全局排除 → 命中即拒绝。

### 新订阅默认值

- `enabledGlobalMatch` / `importGlobalMatch` 与 `enabledGlobalReverseMatch` / `importGlobalReverseMatch`：新订阅可选择默认启用全局规则，或把当前规则复制为本地规则，二者互斥。
- 旧全局反匹配字符串迁移为结构化的 `globalReverseMatchRules`；订阅内的旧 `reverseMatchKeywords` 迁移为 `reverseMatchRules` 后清空。

### 字幕组标记与路由记录

- 把订阅字幕组填为 `反匹配`（或 `REVERSE_MATCH`）即可进入反匹配模式；该标识只作开关，不写入标签、重命名或保存路径。
- 命中规则的字幕组按集数记录在 `reverseMatchAssignments`（`episode`、`subgroup`、`title`、`updatedAt`），刷新结束后持久化；同一集数再次命中时覆盖，超过 500 条时淘汰最旧记录。
- `reverseMatchMissBehavior` 可作为全局默认，订阅留空表示跟随全局。
- `reverseMatchSubgroupOffsets` 按字幕组设置独立集数偏移，叠加在订阅的 `offset` 之上，只作用于该字幕组的条目；订阅的「反匹配」页列出已命中的字幕组与集数范围。

## 通知

### 渠道

通知类型为 `TELEGRAM`、`CUSTOM_REQUEST`、`EMBY_REFRESH` 与插件提供的 `PLUGIN`。旧 `WEB_HOOK` 配置加载时迁移为 `CUSTOM_REQUEST`。

### 状态文案

- `Config.notificationStatusStyles` 按 `DOWNLOAD_START`、`DOWNLOAD_END`、`OMIT`、`ERROR`、`COMPLETED` 保存表情、状态名称与事件模板。
- 事件模板支持 `${name}`（资源名或集数）与 `${source}`（备用 RSS 标记），并可继续使用订阅字段；留空时回落到默认文案。
- 旧配置缺少该字段时由 `ConfigUtil.format` 逐项补齐，不覆盖用户已调整的值。

### 模板

- 模板实体为 `id / name / type / content`，`type` 为 `TEXT` 或 `JSON`，可维护多套并按名称选择。
- 模板直接渲染 `${title}`、`${subgroup}`、`${text}` 等字段变量；旧模板中的 `${notification}` 在迁移时展开，并保证 JSON 结构合法。
- JSON 模板要求解析为合法 JSON，变量只放在字符串值内，渲染后用 Gson 序列化。
- 模板删除后，引用它的通知回落到备用模板。

### 自定义请求的 Body

Body 有两种来源：

- **手写 Body**：按 Content-Type 发送，文本或 JSON 均可，占位符照常替换。
- **模板作为 Body**：从模板库选择任意模板，由服务端渲染后发送；选中模板后忽略手写内容，Content-Type 跟随模板类型。

取值入口统一为 `CustomRequestNotification`。

### 新增与编辑

通知弹窗编辑副本，点击“确定”才写入列表；编辑整体替换原项，取消或关闭不留副作用。

## 认证与安全

### 首次启动与凭据

- `DefaultConfigFactory` 不提供默认账号，`loginConfigured` 标记初始化状态。
- `CredentialUtils` 定义凭据强度：用户名 3-32 位字母/数字/下划线/连字符；密码至少 8 位，且包含大写字母、小写字母、数字、符号中的至少两类；同时负责随机凭据生成（20 位、覆盖四类字符）。
- `LoginBootstrapUtil` 在启动时处理首次凭据：
  - **Windows**：标记为待初始化并自动打开浏览器，显示 WebUI 初始化页面。
  - **Linux**：生成随机用户名与强密码，只在首次启动日志输出一次。
  - **环境变量**：`MINT_SODA_USERNAME` / `MINT_SODA_PASSWORD` 可强制覆盖，每次启动生效；不合规时拒绝启动并说明原因。
  - **`--setup`**：仅在尚未配置账号时进入 WebUI 初始化引导，不生成随机凭据；环境变量优先于该参数，账号已配置时该参数无效。
- `/api/setupStatus` 与 `/api/setup`：后者仅在未初始化、且请求来自本机或内网时可用，服务端校验强度后只保存摘要。
- 已有凭据的旧配置自动标记为已配置；仍是 `admin/admin` 的配置会在日志中提示尽快修改。

### 凭据变更

- 设置页保存新用户名或密码时同样校验强度。
- 密码字段留空表示“不修改”，服务端保留原摘要。
- 用户名或密码变更后轮换 `tokenId`，使所有已登录会话失效。
- `/api/setup` 与登录页所需的外观设置（`/api/publicSettings`）等公开接口不包含敏感信息。

### 开发者选项

- 是独立设置页；开启前需输入当前登录账号与密码验证。
- 关闭后自定义种子地址保留但不再使用，点击“保存设置”后生效。
- `customTorrentUrl` 支持 `<info_hash>` 或 `${info_hash}`，未写占位符时自动追加哈希；仅在开发者选项启用时由 `TorrentUtil` 使用。
- 已填写自定义地址的旧配置迁移时默认启用。

### 默认安全开关

- `allowCors=false`、`MCP_ENABLED=false`、`SWAGGER_ENABLED=false` 默认关闭；`innerIP` 与 `limitLoginAttempts` 生效。
- `data/images/assets` 与 `/api/uiAsset`：文件名为相对路径，拒绝绝对路径与 `..`，只允许 png/jpg/webp/gif，并校验文件头魔数。
- 外观图片上传（`/api/uiAsset/upload`）同样按白名单与文件头校验；`/api/file` 只允许图片。
- `/api/custom.js` 与 `/api/custom.css` 视为管理员可信输入。
- 更新包做 `size` + `sha256` 双重校验，WebUI 在线更新同样校验。

## 外观与主题

- 强调色 `accentColor` 可显式设置；`themeDynamicColor` 从已配置的应用或登录背景图提取可读的强调色（先提饱和再限制亮度）。
- 应用与登录背景分别支持**浅色/深色**两套图片（`appBackgroundImageDark`、`loginBackgroundImageDark`，留空沿用浅色），背景层自身提供 `backgroundBlur` / `backgroundOpacity`，模糊只作用于背景图。
- 卡片、导航栏、弹窗分别可调**不透明度**与**模糊度**（`cardOpacity`/`cardBlur`、`navOpacity`/`navBlur`、`dialogOpacity`/`dialogBlur`），默认即为半透明磨砂；页面、表格、设置区块与登录表单同样使用亚克力质感。
- 侧栏默认在右侧，菜单项均分侧栏高度；窄屏改为底部导航。
- 预设：蓝白玻璃、樱花粉白、薄荷青白、磨砂雾面、清透极简。预设只写回上述字段，不生成主题文件或额外资源。
- 支持应用/登录背景、应用/登录 Logo 的上传与选择，默认 Logo 使用项目图标 `/icon.png`。
- 设置页“外观与主题”右侧常驻主题预览，直接读取根节点上的主题变量，滑杆、配色、预设与图片改动与真实界面同步生效。
- `/api/publicSettings` 只返回渲染登录页所需的无敏感外观字段。

## 插件系统

- 稳定的接口模块 `mint-soda-plugin-api`，插件以独立 JAR 加载，每个插件一个类加载器；接口类与 JDK 类始终由宿主提供。
- 运行时：
  - `JAVA`：独立 JAR。
  - `JAVASCRIPT`：ZIP 包由 Rhino 沙箱解释执行，无需编译。
  - `LUA`：ZIP 包由 LuaJ 解释执行，只装载基础库，单次调用 5 秒超时。
- 插件来源抽象为 `PluginSource`（`JavaPluginSource` / `ScriptPluginSource` / `LuaPluginSource`）；描述文件兼容 `manifest.json`，安装时统一为 `plugin.json`。
- 权限由 `PluginPermission` 枚举并按能力分组，读与写分开授权（`CONFIG_READ`/`CONFIG_WRITE`、`DATA_READ`/`DATA_WRITE`），取消下载单独用 `DOWNLOAD_CANCEL`；按插件保存在 `PluginRecord.grantedPermissions`，用户在插件页逐项开关，注册扩展点时由 `PluginContextImpl` 强制校验，保存后已启用插件立即重载。旧的整体标识 `CONFIG`、`DATA_DIR` 在保存时展开为读写两项。
- 插件私有目录 `plugins/{id}/data` 在升级与重装时保留，卸载时删除；读写统一走 `PluginContext` 的按路径方法，路径经 `PluginPaths.dataFile` 规范化并限制在数据目录内，拒绝符号链接。
- 设置备份包含整个 `plugins/` 目录但不含 `installed.json`；导入时逐条校验路径并在临时目录完成后整体替换，导入后 `PluginManager.rescan()` 按目录重建安装状态，插件默认停用。
- 插件生命周期为加载、启用、停用、重载与卸载，单个插件失败不影响其他插件。
- 扩展点包括通知渠道、订阅过滤器、订阅事件、周期任务、设置区块、模板变量、下载拦截、下载事件、条目改写、界面页面与插件自定义接口；订阅事件在增删改订阅时触发，插件过滤在匹配规则之后、条目改写之前执行。
- 插件联动：`PluginBus` 提供按主题收发消息的总线，支持广播、请求应答、订阅与提供方注册；主题名匹配 `^[a-z0-9][a-z0-9._-]{0,63}$`，载荷上限 64KB，请求超时 3 秒，嵌套调用最多 4 层；对应权限为 `PLUGIN_DISCOVERY`、`PLUGIN_MESSAGE_SEND`、`PLUGIN_MESSAGE_RECEIVE`。
- 模板变量由 `TemplateVariables` 统一应用，通知、重命名与下载路径共用同一批字段；插件可通过 `TemplateVariableProvider` 追加或覆盖字段。
- 下载拦截在种子提交给下载器之前执行，可改写保存位置与名称，或返回 `null` 取消任务。
- 下载事件：`DownloadListener` 在任务提交成功、下载完成与提交失败时回调，只读快照不可修改任务；权限 `DOWNLOAD_EVENT`（低风险）。
- 条目改写：`ItemTransformer` 在过滤之后、重命名之前调整标题或字幕组；权限 `ITEM_TRANSFORM`（中风险）。
- 界面页面：`PluginPage` 用白名单区块（文本、表单、表格、操作、指标卡片、分隔线）注册只读页面，页面调用插件接口取数；权限 `CUSTOM_PAGE`（低风险）。插件内容不按 HTML 渲染，也不执行脚本。
- 插件接口版本为 `5`，包名与 Maven 坐标为 `mint.soda`；较早版本新增的注册方法都有默认实现。
- 插件自定义接口挂在 `/api/plugin/api/{pluginId}/{path}`，由 `PluginEndpointController` 统一鉴权与分发，请求体上限 256KB。
- 插件渠道的字段与值保存在 `NotificationConfig` 的 `pluginChannelId` 与 `pluginOptions`。
- `/api/plugin/*` 提供列表、运行概览、详情、启用/停用/重载/卸载、配置读写、上传与链接安装、市场获取、市场安装、单条与全部更新检查、更新与市场地址管理。全部接口都要求登录；请求体使用 `entity/dto` 下带类型的 DTO。
- 插件运行概览（`/api/plugin/stats`）汇总插件数量、启用/错误数量与各类扩展点数量。
- 插件状态保存在配置目录的 `plugins/installed.json`；插件目录为 `plugins/{id}`，内含 `plugin.jar`、`plugin.json` 与 `data/`。
- 安装与更新同时校验 `size` 与 `sha256`，下载地址经 `URLUtils#verify` 校验；先写临时目录校验通过后再替换。
- `pluginMarketUrl` 默认为空。市场为 JSON 索引，插件也可通过 `updateUrl` 提供自身更新条目。
- 侧栏“插件”页管理插件与权限；通知编辑中可选择“插件渠道”。

## 多语言

- WebUI 使用 `vue-i18n` 管理文案，内置 `zh-CN`、`zh-TW`、`en-US`，词条位于 `mint-soda-ui/src/i18n/locales/`，`zh-CN` 为唯一事实来源。
- 语言选择保存在浏览器本地，首次访问按浏览器语言自动匹配；切换语言刷新页面以重建模块级常量，同时切换 Element Plus 与 dayjs 语言包。
- 侧栏底部、登录页与初始化页右上角提供语言选择器；切换只影响界面文案，不写入服务端配置。
- 新增或修改文案的完整流程见[多语言与翻译贡献](I18N.md)。

## 缓存与运行维护

- `cacheAutoClear`（默认关闭）、`cacheAutoClearHours`（默认 72，范围 1-720）、`cacheLastClearTime`。
- `CacheClearTask` 每 10 分钟检查一次，到达间隔才清理未被引用的封面与预览缓存。
- 手动清理同样记录时间戳。
- `/api/system/status` 返回版本、运行时长、订阅数量、堆内存与线程数等聚合数据，要求登录，不包含路径与凭据。

## 性能与前端

- 标题拼音按上限缓存；首字母判断只取单个字符。
- 路由页面按需加载；`icons.js` 只登记模板中以名称使用的图标，其余图标显式导入。
- 设置页采用“编辑副本 + 逐键比较”：显示保存状态、逐页改动标记，支持 `Ctrl + S` 保存、撤销修改与离开确认；保存前校验间隔、并发与 JSON 模板。
- 日志等级统计为单次遍历。
- 进程内缓存 `CacheUtils` 为无锁并发实现：命中路径不加全表锁，按写入顺序淘汰，读取时清理过期项。
- 插件扩展点使用索引快照：启用状态变化时一次性重建各类索引，分发路径只做一次 volatile 读取；`plugin.json` 按修改时间与大小缓存解析结果。
- RSS 预取复用固定大小的共享线程池，单轮并发由信号量限制。
- 插件模板变量缓存按提供方集合变化失效，容量超限时先清理过期项。
- 前端资源开启内容哈希长缓存与 gzip 压缩：入口页按 `no-cache` 返回，静态资源命中浏览器缓存。
- 首屏占位、登录/初始化/主界面切换与路由切换使用轻量过渡；设置页加载时显示骨架屏，切页带轻微淡入；`prefers-reduced-motion` 下全部关闭。
- 插件页面的 `STATS` 指标区块用自适应网格展示 `label / value / hint`。

## 数据与接口

- 设置文件为 `config/config.json`，其余数据位于配置目录同级的 `data/`，目录布局由 `StoragePaths` 统一定义。
- 接口统一挂在 `/api` 前缀下，业务接口使用 `POST`；文件、图片、日志压缩包、备份导出与日历使用 `GET`。接口清单见 [API 参考](API.md)。
- 插件接口统一挂在 `/api/plugin/api/{pluginId}/**`。
- 公开接口仅限登录与初始化页面渲染所需的无敏感字段。

## 标识

- 出站请求的 User-Agent 为 `MintSoda/<版本>`，可在开发者选项中覆盖。
- 构建产物为 `MintSoda.jar` 与 `MintSoda.exe`；更新器为 `MintSoda-update.exe`。
- 程序目录下存在 `config/` 或 `data/` 时直接使用程序目录，便于容器与隔离环境挂载配置与数据；Windows 非 exe 运行且两者都不存在时默认配置目录为 `MintSoda/`，便携运行（EXE）始终为 EXE 同目录的 `config/` 与 `data/`。
- qBittorrent 的分类与标签统一为 `MintSoda`。
- 日志为 `data/logs/MintSoda.log`，备份文件为 `MintSoda.backup.<版本>.zip`，ICS 文件名与日历名称同样使用 `MintSoda`。
- Linux 服务名与系统用户为 `mint-soda`，控制脚本为 `linux/mint-soda.sh`。

## 部署

- 默认端口 **17789**。
- `linux/install.sh` 安装到**执行命令时所在目录**（不新建子目录）并注册 systemd 服务，安装时在终端交互询问用户名与两次密码（沿用与后端一致的强度校验），也可用 `--username`、`--password` 直接传入；另有 `--port`、`--dir`、`--setup`、`--yes`。重新安装检测到已有配置时默认沿用原账号。凭据写入仅 root 可读的环境文件。
- `linux/mintsoda` 提供数字菜单与子命令（`status`、`version`、`update`、`log`、`start/stop/restart`、`enable/disable`、`uninstall`）；无 `unzip` 时用 Python 读取版本。
- `linux/install-legacy.sh` 安装到 `/opt/mint-soda`。
- Windows EXE 使用便携配置目录：配置与数据保存在 EXE 同目录的 `config/`、`data/`，并在首次启动时复制旧用户目录配置。
- 容器或隔离环境可直接把 `config/` 与 `data/` 挂载到程序目录；启动时只校验可写，不会改动挂载目录的属主或内容。

## 配置字段的处理约定

新增或变更字段时，同时处理以下位置：

1. 实体：`entity/Config.java`、`entity/Ani.java`。
2. 默认值：`config/DefaultConfigFactory.java`、`config/DefaultAniFactory.java`。
3. 旧配置补齐与迁移：`util/other/ConfigUtil.java` 的 `format` 及迁移方法。
4. 前端默认对象：`mint-soda-ui/src/js/config.js`、`src/js/ani.js`。
5. 设置界面与文档。

迁移规则：

- `format` 只补默认值、做显式迁移，不静默丢弃用户数据。
- 反匹配、通知模板、凭据状态、qBittorrent 认证、秒级间隔、合集路径与外观字段都有各自的补齐或迁移分支。
- 无法无损迁移时保留旧字段并写迁移逻辑。
- 兼容字段（如 `rssSleepMinutes`、`notificationTemplate`、`telegramTemplate`、`customRequestBody`、`reverseMatchKeywords`）继续写入或保留。
- 代码统一使用 `mint.soda` 命名空间与 `mint.soda:mint-soda-*` 坐标；引入外部实现时按本仓库的包名与命名调整后再合入。
- 调整订阅、通知或配置逻辑时，优先检查 `MatchRuleUtil`、`CustomRequestNotification`、`LoginBootstrapUtil` 与 `ConfigUtil#format` 的既有行为。
