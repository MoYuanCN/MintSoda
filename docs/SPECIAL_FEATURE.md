# 本分支特性说明

本文档说明 **ANI-RSS-Yuri** 相对上游 [ANI-RSS](https://github.com/wushuo894/ani-rss) 的实际差异，内容依据本仓库的提交记录整理。

- 上游基线：`wushuo894/ani-rss` 的 `3.2.61`。
- 本分支版本：`3.3.0`。
- 定位：面向 Windows 与 Linux 的自托管版本，按本分支目标“做减法”——单一下载器、可预期的匹配规则、更自由的外观与更轻的部署方式。常规订阅与下载流程尽量与上游保持一致。

> 功能范围说明见 [使用指南](USER_GUIDE.md)，构建与协作见 [开发指南](DEVELOPMENT.md) 与 [贡献指南](CONTRIBUTING.md)，安全边界见 [安全说明](SECURITY.md)。

## 功能边界总览

| 领域 | 上游 | 本分支 |
| --- | --- | --- |
| 下载器 | qBittorrent、Aria2、Transmission | 仅 qBittorrent |
| 订阅来源 | Mikan、AniBT、AnimeGarden、手动 RSS | Mikan、AniBT、手动 RSS |
| 通知渠道 | 邮件、Server 酱、Bark、Shell、系统通知、文件移动、OpenList、AstrBot、Telegram、WebHook、媒体库刷新 | Telegram、自定义 HTTP 请求、媒体库刷新 |
| 通知模板 | 渠道内置模板 + 单一 Body | 多套可命名模板，文本 / JSON，模板即 Body |
| 匹配 | 本地匹配、本地排除、全局排除 | 增加反匹配与全局匹配，统一判定入口 |
| 播放与刮削 | 内置播放、刮削、NFO 生成 | 已移除 |
| 任务进度 | 内置 qBittorrent 任务页面 | 已移除，请使用 qBittorrent 自身界面 |
| 部署 | Docker、macOS、Windows、Linux | Windows、Linux |
| 账号 | 内置 `admin/admin` | 首次启动按平台生成或引导设置 |
| 默认端口 | 7789 | 17789 |

## 订阅与下载

### 仅保留 qBittorrent

- 删除 Aria2、Transmission 及其 RPC 实体、方法枚举与配置项。
- 新增 `qbAuthType` 区分 **API Key** 与 **用户名/密码** 两种认证；`qbUsername`、`qbPassword` 独立保存，兼容未提供 API Key 的 qBittorrent。
- 旧配置在加载时归一化为 qBittorrent，并按密码是否以 `qbt_` 开头推断认证方式。

### 移除 AnimeGarden

- 删除浏览页、`AnimeGardenService`、`AnimeGardenController`、实体、图标与对应的 MCP 工具。
- 添加订阅只保留 Mikan、AniBT、手动 RSS 三种来源；批量添加不再为 AnimeGarden 单独拼接 `bgmUrl` 与字幕组。

### RSS 刷新

- 刷新间隔单位由分钟改为**秒**（`rssSleepSeconds`，默认 900），旧 `rssSleepMinutes` 会换算并在保存时继续写入以兼容。
- 新增 `rssConcurrency`（1-16，默认 4）并发预取，`rssTimeout` 范围放宽到 1-120 秒。
- 每轮按固定间隔起跑并扣除本轮耗时，短间隔下不漂移、不空转。
- 跨轮使用 **ETag 条件请求**，`304` 时复用上次内容；请求失败时退避并沿用最近一次成功内容。
- 单轮内复用下载器任务列表、本地目录与下载路径，删除文件后主动失效。

### 订阅进度写入

- 刷新期间 `AniUtil.beginDeferredSync` 只记录待保存，整轮结束写一次 `ani.v2.json`；请求线程的即时保存行为不变。
- 备用 RSS 复制订阅改为属性拷贝，避免逐条深拷贝整份订阅。
- 缺集检测改用集数集合，条件请求与退避记录有上限，避免无界增长。

### 合集保存路径

- 合集开始下载时按当前标题、TMDB、季重新计算保存位置，修正切换条目后仍沿用旧路径导致目录丢失的问题。
- 新增 `collectionDownloadPathTemplate`，留空表示沿用原行为；仅在使用 `${bgmId}` 时才解析 BGM ID。
- 新增 `/api/collectionDownloadPath` 供合集弹窗显示实际路径。

### 订阅排序

在上游 SCORE / PINYIN / DOWNLOAD_TIME 之外新增：

| 枚举 | 说明 |
| --- | --- |
| `TITLE` | 按标题字符顺序 |
| `UPDATE_WEEK` | 按每周放送日轮转 |
| `STANDBY_RSS` | 按备用 RSS 数量降序 |

- `/api/listAni` 支持 `?sort=` 临时指定排序，未知值回退到配置中的默认排序。
- 订阅页顶部提供排序下拉，仅影响当前浏览，不修改默认设置。

## 匹配规则

### 新增反匹配与全局匹配

- 订阅新增 `reverseMatchEnabled`、`reverseMatchRules`、`globalReverseMatch`、`reverseMatchMissBehavior`，以及继承用的 `globalMatch`。
- 规则由标题正则与目标字幕组组成；命中后条目归入指定字幕组，不要求原 RSS 已带有该字幕组。
- 新增 `MatchRuleUtil#evaluate` 作为**唯一判定入口**，RSS 下载、RSS 预览与合集预览共用，避免界面显示与实际下载分歧。

### 优先级

固定为 **反匹配 > 全局匹配 > 排除**，本地匹配是普通路径的必要条件：

1. 本地反匹配命中 → 直接接收，使用规则指定的字幕组。
2. 全局反匹配命中 → 同上（需订阅启用）。
3. 反匹配已启用但未命中 → 按 `reverseMatchMissBehavior`：`SKIP`（默认）、`NORMAL`、`ALLOW`。
4. 本地匹配规则 → 每条都必须命中。
5. 全局匹配 → 每条适用规则都必须命中，通过后优先于排除。
6. 本地与全局排除 → 命中即拒绝。

### 新订阅默认值

- 新增 `enabledGlobalMatch` / `importGlobalMatch` 与 `enabledGlobalReverseMatch` / `importGlobalReverseMatch`：新订阅可选择默认启用全局规则，或把当前规则复制为本地规则，二者互斥。
- 旧全局反匹配字符串会迁移为结构化的 `globalReverseMatchRules`；订阅内的旧 `reverseMatchKeywords` 迁移为 `reverseMatchRules` 后清空。

## 通知

### 渠道收敛

只保留 `TELEGRAM`、`CUSTOM_REQUEST`、`EMBY_REFRESH`。删除邮件、Server 酱、Bark、Shell、系统通知、文件移动、OpenList 上传与 AstrBot 的实现、界面与枚举值。

- 旧 `WEB_HOOK` 仅用于读取旧配置，加载时迁移为 `CUSTOM_REQUEST`。
- 已移除类型的旧通知在加载时丢弃。

### 模板

- 模板实体为 `id / name / type / content`，`type` 为 `TEXT` 或 `JSON`，可维护多套并按名称选择。
- 模板直接渲染 `${title}`、`${subgroup}`、`${text}` 等字段变量，不再使用 `${notification}` 嵌套整段消息；旧模板中的该占位符会在迁移时展开，并保证 JSON 结构合法。
- JSON 模板要求解析为合法 JSON，变量只放在字符串值内，渲染后用 Gson 序列化，避免转义问题。

### 自定义请求的 Body

Body 只有两种来源，语义明确：

- **手写 Body**：按 Content-Type 发送，文本或 JSON 均可，占位符照常替换。
- **模板作为 Body**：从模板库选择任意模板，由服务端渲染后发送；选中模板后忽略手写内容，Content-Type 跟随模板类型。

为此新增 `CustomRequestNotification`，统一 Body 取值入口。

### 新增前确认

通知弹窗改为编辑副本，点击“确定”才写入列表；新增通知不再先插入一条空记录，编辑整体替换原项，取消或关闭不留副作用。

## 认证与安全

### 不再内置默认账号

- 删除 `DefaultConfigFactory` 中的 `admin/admin` 默认值，新增 `loginConfigured` 标记初始化状态。
- 新增 `CredentialUtils`：用户名 3-32 位字母/数字/下划线/连字符；密码至少 8 位，且包含大写字母、小写字母、数字、符号中的至少两类；同时负责随机凭据生成（20 位、覆盖四类字符）。
- 新增 `LoginBootstrapUtil` 在启动时处理首次凭据：
  - **Windows**：标记为待初始化并自动打开浏览器，显示 WebUI 初始化页面。
  - **Linux**：生成随机用户名与强密码，只在首次启动日志输出一次。
  - **环境变量**：`ANI_RSS_USERNAME` / `ANI_RSS_PASSWORD` 可强制覆盖，每次启动生效；不合规时拒绝启动并说明原因。
  - **`--setup`**：仅在尚未配置账号时进入 WebUI 初始化引导，不生成随机凭据；环境变量优先于该参数，账号已配置时该参数完全失效。
- 新增 `/api/setupStatus` 与 `/api/setup`；后者仅在未初始化、且请求来自本机或内网时可用，服务端校验强度后只保存摘要。
- 旧配置按已有凭据自动标记为已配置，避免升级后无法登录；仍是 `admin/admin` 的配置会在日志中提示尽快修改。

### 凭据变更

- 设置页保存新用户名或密码时同样校验强度。
- 密码字段留空表示“不修改”，服务端保留原摘要。
- 用户名或密码变更后轮换 `tokenId`，使所有已登录会话失效。
- `/api/setup` 与登录页所需的外观设置（`/api/publicSettings`）等公开接口保持无敏感信息。

### 开发者选项

- 从下载设置末尾拆为独立的一级设置页；开启前需输入当前登录账号与密码验证。
- 关闭后自定义种子地址**保留但不再使用**，点击“保存设置”后生效。
- `customTorrentUrl` 支持 `<info_hash>` 或 `${info_hash}`，未写占位符时自动追加哈希；仅在开发者选项启用时由 `TorrentUtil` 使用。
- 旧配置若已填写自定义地址，迁移时默认启用，避免升级后静默失效。

### 其他安全处理

- 默认保持 `allowCors=false`、`MCP_ENABLED=false`、`SWAGGER_ENABLED=false`；`innerIP`、`limitLoginAttempts` 行为不变。
- 新增 `ui-assets` 目录与 `/api/uiAsset`：文件名为相对路径，拒绝绝对路径与 `..`，只允许 png/jpg/webp/gif，并校验文件头魔数。
- 外观图片上传（`/api/uiAsset/upload`）只接受 png/jpg/jpeg/webp/gif，并校验文件头魔数后才落盘；`/api/file` 仅允许图片格式，移除了视频与字幕的 Range 读取逻辑（与播放功能一并移除）。
- `/api/custom.js` 与 `/api/custom.css` 仍视为管理员可信输入。
- 更新包继续做 `size` + `sha256` 双重校验，WebUI 在线更新同样校验，未放宽。

## 外观与主题

- 新增主题强调色 `accentColor`，留空时沿用用户上次保存的强调色，再回退到默认蓝色。
- 新增 `themeDynamicColor`：从已配置的应用或登录背景图提取一个可读的强调色（先提饱和再限制亮度）。
- 应用与登录背景分别支持**浅色/深色**两套图片（`appBackgroundImageDark`、`loginBackgroundImageDark`，留空沿用浅色），背景层自身提供 `backgroundBlur` / `backgroundOpacity`，模糊只作用于背景图。
- 卡片、导航栏、弹窗分别可调**不透明度**与**模糊度**（`cardOpacity`/`cardBlur`、`navOpacity`/`navBlur`、`dialogOpacity`/`dialogBlur`），默认值即为半透明磨砂；页面、表格、设置区块与登录表单同样使用亚克力质感，而非纯色块。
- 侧栏默认在右侧，菜单项均分侧栏高度；窄屏改为底部导航。
- 提供浅色预设：蓝白玻璃、樱花粉白、薄荷青白、磨砂雾面、清透极简。预设只是写回上述已有字段，不生成主题文件或额外资源。
- 支持应用/登录背景、应用/登录 Logo 上传与选择，默认 Logo 使用项目图标 `/icon.png`。

`/api/publicSettings` 只返回渲染登录页所需的无敏感字段；新增的导航栏与弹窗参数目前随完整配置下发。

## 插件系统

- 新增稳定的接口模块 `ani-rss-plugin-api`，插件以独立 JAR 加载，每个插件一个类加载器；接口类与 JDK 类始终由宿主提供。
- 支持 JavaScript 插件：`runtime` 为 `JAVASCRIPT` 的 ZIP 包由 Rhino 沙箱解释执行，无需编译；与 Java 插件共用同一套扩展点。
- 插件来源抽象为 `PluginSource`（`JavaPluginSource` / `ScriptPluginSource`），管理器不再区分具体语言；描述文件兼容 `manifest.json`，安装时统一为 `plugin.json`。
- 插件生命周期为加载、启用、停用、重载与卸载，失败只影响该插件。
- 扩展点包括通知渠道、订阅过滤器、订阅事件、周期任务、设置区块、模板变量、下载拦截与插件自定义接口；订阅事件在增删改订阅时触发，插件过滤在匹配规则之后执行。
- 模板变量由 `TemplateVariables` 统一应用，通知、重命名与下载路径共用同一批字段；插件可通过 `TemplateVariableProvider` 追加或覆盖字段。
- 下载拦截在种子提交给下载器之前执行，可改写保存位置与名称，或返回 `null` 取消任务。
- 插件自定义接口挂在 `/api/plugin/api/{pluginId}/{path}`，由 `PluginEndpointController` 统一鉴权与分发，请求体上限 256KB。
- 新增通知类型 `PLUGIN`，插件渠道的字段与值保存在 `NotificationConfig` 的 `pluginChannelId` 与 `pluginOptions`。
- 新增 `/api/plugin/*` 接口：列表、详情、启用/停用/重载/卸载、配置读写、上传与链接安装、市场获取、市场安装、单条与全部更新检查、更新与市场地址管理。全部接口都要求登录。
- 插件状态保存在配置目录的 `plugins/installed.json`；插件目录为 `plugins/{id}`，内含 `plugin.jar`、`plugin.json` 与 `data/`。
- 安装与更新同时校验 `size` 与 `sha256`，下载地址经 `URLUtils#verify` 校验；先写临时目录校验通过后再替换，避免半成品。
- 新增配置项 `pluginMarketUrl`（默认空）。市场为 JSON 索引，插件也可通过 `updateUrl` 提供自身更新条目。
- 前端新增侧栏“插件”页与通知编辑中的“插件渠道”选择；文档新增 `docs/PLUGIN_DEVELOPMENT.md`，示例见 `examples/plugins/`。

## 缓存自动清理

- 新增 `cacheAutoClear`（默认关闭）、`cacheAutoClearHours`（默认 72，范围 1-720）、`cacheLastClearTime`。
- 新增 `CacheClearTask`，每 10 分钟检查一次，到达间隔才清理未被引用的封面与预览缓存。
- 手动清理同样记录时间戳，避免紧接着又自动清理一次。

## 性能与前端

- 标题拼音按上限缓存；首字母判断只取单个字符。
- 删除未被引用的 `PersistCacheUtils`、`RenameCacheUtil`、`CacheObject` 与不再使用的评分查询。
- 路由页面改为按需加载；新增 `icons.js` 只登记模板中以名称使用的图标，不再全量导入 `@element-plus/icons-vue`，图标分包由约 168KB 降至约 33KB。
- 设置页改为“编辑副本 + 逐键比较”：显示保存状态、逐页改动标记，支持 `Ctrl + S` 保存、撤销修改与离开确认；保存前校验间隔、并发与 JSON 模板。
- 日志等级统计改为单次遍历。

## 数据与接口

- 新增 `/api/setupStatus`、`/api/setup`、`/api/verifyDeveloperCredentials`、`/api/setDeveloperSettings`、`/api/collectionDownloadPath`、`/api/uiAsset` 与 `/api/uiAsset/upload`。
- 删除 `/api/trackersUpdate`、`/api/verifyNo`（捐赠）、`/api/embyWebHook`、`/api/rate`、`/api/setRate`，以及播放、刮削、AnimeGarden、任务进度相关的控制器。
- `/api/publicSettings` 为新增的公开接口，只返回登录页渲染所需的外观字段。
- `/api/file` 只允许图片；上传类接口保留原有行为，外观图片走独立白名单校验。

## 部署

- 默认端口改为 **17789**，同步开发代理、安装脚本与文档。
- 新增 `linux/install-yuri.sh`：一条命令安装到**执行命令时所在目录**（不新建子目录）并注册 systemd 服务，安装时在终端交互询问用户名与两次密码（沿用与后端一致的强度校验），也可用 `--username`、`--password` 直接传入；另有 `--port`、`--dir`、`--setup`、`--yes`。重新安装检测到已有配置时默认沿用原账号。凭据写入仅 root 可读的环境文件。
- 新增 `linux/assyuri`：数字菜单与子命令（`status`、`version`、`update`、`log`、`start/stop/restart`、`enable/disable`、`uninstall`）；无 `unzip` 时用 Python 读取版本。
- `linux/install-ani-rss.sh` 保留为安装到 `/opt/ani-rss` 的旧方式。
- Windows EXE 使用便携配置目录：配置保存在 EXE 同目录的 `config/`，并在首次启动时复制旧用户目录配置。

## 移除的功能

- Aria2、Transmission 下载器与集成任务进度页。
- 内置播放、刮削、NFO 生成与 Bangumi 点格子（`rate` / `setRate`）。
- OpenList / AList 适配与上传。
- AnimeGarden 订阅来源。
- 摸鱼检测、Trackers 自动更新设置。
- 邮件、Server 酱、Bark、Shell、系统通知、文件移动、AstrBot 通知。
- 捐赠订单校验（本地功能默认全部可用）。
- Docker 镜像、容器发布与 macOS 构建。

## 配置兼容

新增或变更字段时，同时处理以下位置：

1. 实体：`entity/Config.java`、`entity/Ani.java`。
2. 默认值：`config/DefaultConfigFactory.java`、`config/DefaultAniFactory.java`。
3. 旧配置补齐与迁移：`util/other/ConfigUtil.java` 的 `format` 及迁移方法。
4. 前端默认对象：`ani-rss-ui/src/js/config.js`、`src/js/ani.js`。
5. 设置界面与文档。

具体迁移行为：

- `format` 只补默认值、做显式迁移，不静默丢弃用户数据。
- 反匹配、通知模板、凭据状态、qBittorrent 认证、秒级间隔、合集路径与外观字段都有各自的补齐或迁移分支。
- 无法无损迁移时保留旧字段并写迁移逻辑；已移除渠道的旧配置会被丢弃。
- 需要保留的旧字段（如 `rssSleepMinutes`、`notificationTemplate`、`telegramTemplate`、`customRequestBody`、`reverseMatchKeywords`）继续写入或保留，仅作兼容用途。

## 与上游同步的注意事项

- 上游新增的 AnimeGarden、AstrBot 与其他已移除能力不要直接合并回来。
- 合并上游时优先检查 `MatchRuleUtil`、`CustomRequestNotification`、`LoginBootstrapUtil`、`ConfigUtil#format` 是否被覆盖。
- 上游若新增下载器或通知渠道，需要同时决定是否在本分支保留，并更新本文档。
