# 配置参考

设置保存在 `config/config.json`，订阅保存在 `data/subscriptions.json`，两者都由程序读写，不建议在服务运行时手工编辑。本文列出 `Config` 的全部字段、类型与默认值，便于备份比对、迁移排查和二次开发。

> 面向使用者的界面说明见[使用指南](USER_GUIDE.md)，接口清单见 [API 参考](API.md)，改动字段时必须遵守的流程见[开发指南 · 配置变更清单](DEVELOPMENT.md#配置变更清单)。

## 本文目录

- [文件与加载](#文件与加载)
- [字段清单](#字段清单)
- [取值约束](#取值约束)
- [兼容与迁移](#兼容与迁移)
- [安全相关字段](#安全相关字段)
- [新增字段的流程](#新增字段的流程)

## 文件与加载

| 文件 | 内容 |
| --- | --- |
| `config/config.json` | 设置（本文的 `Config`） |
| `data/subscriptions.json` | 订阅列表 |
| `data/backups/YYYY-MM-DD.zip` | 开启“设置备份”后每日生成的压缩包，保留 `configBackupDay` 天 |
| `data/plugins/installed.json` | 插件安装记录，不进入备份 |

加载顺序：先由 `DefaultConfigFactory` 生成完整默认值，再把磁盘上的 JSON 覆盖上去（`null` 不覆盖），最后由 `ConfigUtil#format` 补齐缺失字段、执行迁移与范围校正。因此旧配置缺字段不会导致启动失败。

自动备份的内容是 `data/images/covers/`、`data/torrents/`、`data/images/assets/`、`data/subscriptions.json`、`config/config.json` 与整个插件目录（含私有 `data/`，不含 `installed.json`），可用 `/api/importBackup` 或设置页导入恢复。旧版备份中的 `ani.v2.json`、`config.v2.json`、`files/`、`img/`、`ui-assets/` 等条目在导入时自动映射到新位置。

## 字段清单

### 站点与元数据

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `mikanHost` | `String` | `https://mikanani.me` | Mikan Host |
| `tmdbApi` | `String` | `https://api.themoviedb.org` | TMDB API |
| `tmdbApiKey` | `String` | 空 | TMDB API Key |
| `tmdbImage` | `String` | `https://image.tmdb.org` | TMDB IMAGE |
| `tmdbAnime` | `Boolean` | `true` | 仅获取动漫 |
| `tmdb` | `Boolean` | `true` | 启用 TMDB |
| `tmdbId` | `Boolean` | `false` | 标题带 TMDB ID |
| `tmdbIdPlexMode` | `Boolean` | `false` | Plex 命名方式 |
| `tmdbLanguage` | `String` | `zh-CN` | TMDB 语言 |
| `tmdbRomaji` | `Boolean` | `false` | 获取罗马音 |
| `tmdbOriginalName` | `Boolean` | `false` | 标题使用原始语言名称 |
| `bgmApi` | `String` | `https://api.bgm.tv` | bgmApi |
| `bgmJpName` | `Boolean` | `false` | BGM日语标题 |
| `bgmImageSize` | `String` | `medium` | BGM 封面图片质量 |

### 下载器

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `downloadToolType` | `String` | `qBittorrent` | 下载工具类型 |
| `downloadToolHost` | `String` | 读环境变量 `DOWNLOAD_TOOL_HOST` / `DOWNLOAD_TOOL_PASSWORD`，默认空 | 下载工具地址 |
| `downloadToolPassword` | `String` | 读环境变量 `DOWNLOAD_TOOL_HOST` / `DOWNLOAD_TOOL_PASSWORD`，默认空 | 下载工具密码 |
| `qbAuthType` | `String` | `API_KEY` | qBittorrent 认证方式：`API_KEY` 或 `USER_PASSWORD` |
| `qbUsername` | `String` | 空 | qBittorrent 用户名（`USER_PASSWORD` 时使用） |
| `qbPassword` | `String` | 空 | qBittorrent 密码（`USER_PASSWORD` 时使用） |
| `qbUseDownloadPath` | `Boolean` | `false` | 使用 qb 自身保存路径 |
| `qbContentLayout` | `String` | `Original` | qb 内容布局 |
| `ratioLimit` | `Integer` | `-2` | 分享率 |
| `seedingTimeLimit` | `Integer` | `-2` | 总做种时长 |
| `inactiveSeedingTimeLimit` | `Integer` | `-2` | 非活跃时长 |
| `upLimit` | `Long` | `0L` | 上传速度限制 |
| `dlLimit` | `Long` | `0L` | 下载速度限制 |
| `downloadRetry` | `Integer` | `3` | 下载重试次数 |

### 下载与路径

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `downloadPathTemplate` | `String` | `{下载目录}/${title}` | 下载路径模版 |
| `ovaDownloadPathTemplate` | `String` | `{下载目录}/${title}` | 剧场版下载路径模版 |
| `collectionDownloadPathTemplate` | `String` | 空 | 合集下载路径模版 |
| `completedPathTemplate` | `String` | `{已完结目录}/${title}/Season ${season}` | 番剧完结迁移位置 |
| `completed` | `Boolean` | `false` | 番剧完结迁移 |
| `downloadCount` | `Integer` | `0` | 同时下载数量限制 |
| `delayedDownload` | `Integer` | `0` | 延迟下载(分钟) |
| `fileExist` | `Boolean` | `false` | 文件已下载自动跳过 |
| `awaitStalledUP` | `Boolean` | `true` | 等待做种完毕 |
| `delete` | `Boolean` | `false` | 自动删除已完成任务 |
| `deleteStandbyRSSOnly` | `Boolean` | `false` | 主RSS更新后删除备用RSS |
| `downloadNew` | `Boolean` | `false` | 只下载最新集 |
| `skip5` | `Boolean` | `true` | 自动跳过 x.5 集数 |
| `customTags` | `List<String>` | `[]` | 自定义标签 |
| `priorityKeywordsEnable` | `Boolean` | `false` | 优先保留开关 |
| `priorityKeywords` | `List<String>` | `[]` | 优先保留关键词列表 |

### 刷新、偏移与重命名

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `rss` | `Boolean` | `true` | RSS 开关 |
| `rssSleepSeconds` | `Integer` | `900`（15 分钟） | RSS 间隔(秒) |
| `rssSleepMinutes` | `Integer` | `15`（由 `rssSleepSeconds` 回写） | 旧版 RSS 间隔（分钟），仅作兼容，保存时由 `rssSleepSeconds` 回写 |
| `rssTimeout` | `Integer` | `20` | RSS 超时时间(秒) |
| `rssConcurrency` | `Integer` | `4` | RSS 并发数 |
| `rename` | `Boolean` | `true` | 自动重命名 |
| `renameSleepSeconds` | `Integer` | `10` | 重命名间隔(秒) |
| `offset` | `Boolean` | `false` | 自动推断剧集偏移 |
| `offsetType` | `OffsetTypeEnum` | `AUTO_OFFSET` | 剧集偏移推断方式：`AUTO_OFFSET` 或 `BGM_OFFSET` |
| `omit` | `Boolean` | `true` | 检测遗漏集数 |
| `followDay` | `Integer` | `14` | 追更天数 |
| `autoDisabled` | `Boolean` | `false` | 自动禁用已完结番剧订阅 |
| `debug` | `Boolean` | `false` | DEBUG |
| `logsMax` | `Integer` | `128` | 最大日志条数 |
| `renameTemplate` | `String` | `[${subgroup}] ${title} S${seasonFormat}E${episodeFormat}` | 重命名模版 |
| `renameDelYear` | `Boolean` | `true` | 重命名剔除年份 |
| `renameDelTmdbId` | `Boolean` | `true` | 重命名剔除 TMDB ID |
| `titleYear` | `Boolean` | `true` | 获取标题时带上年份 |
| `maxFileNameLength` | `Integer` | `0` | 最大文件名长度(不含后缀) |
| `customEpisode` | `Boolean` | `false` | 自定义集数获取规则 |
| `customEpisodeStr` | `String` | 内置集数正则 | 自定义集数获取规则表达式 |
| `customEpisodeGroupIndex` | `Integer` | `2` | 自定义集数获取规则 groupIndex |
| `bangumiIniEnabled` | `Boolean` | `false` | bangumi.ini |

### 匹配与字幕组

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `exclude` | `List<String>` | `[720[Pp], \\d-\\d, 合集, 特别篇]` | 全局排除 |
| `enabledExclude` | `Boolean` | `false` | 默认启用全局排除 |
| `importExclude` | `Boolean` | `false` | 默认导入全局排除 |
| `globalMatch` | `List<String>` | `[]` | 全局匹配规则，格式 `{{字幕组}}:正则` |
| `enabledGlobalMatch` | `Boolean` | `false` | 添加订阅时默认启用全局匹配 |
| `importGlobalMatch` | `Boolean` | `false` | 添加订阅时默认导入全局匹配 |
| `globalReverseMatch` | `List<String>` | `[]` | 旧版全局反匹配列表，仅用于迁移到 `globalReverseMatchRules` |
| `globalReverseMatchRules` | `List<ReverseMatchRule>` | `[]` | 全局反匹配规则，每项含 `id`、`pattern`、`subgroup` |
| `enabledGlobalReverseMatch` | `Boolean` | `false` | 添加订阅时默认启用全局反匹配 |
| `importGlobalReverseMatch` | `Boolean` | `false` | 添加订阅时默认导入全局反匹配 |
| `reverseMatchMissBehavior` | `String` | `SKIP` | 反匹配未命中默认行为：`SKIP` 跳过、`NORMAL` 继续普通匹配、`ALLOW` 直接放行；订阅未单独指定时生效 |
| `standbyRss` | `Boolean` | `false` | 备用RSS |
| `coexist` | `Boolean` | `false` | 多字幕组共存模式 |
| `subtitleIndependentFolderEnabled` | `Boolean` | `false` | 启用 字幕独立文件夹 |
| `subtitleIndependentFolderName` | `String` | `Subs` | 字幕独立文件夹 |

### 通知

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `notificationConfigList` | `List<NotificationConfig>` | `[]` | 通知配置列表 |
| `notificationTemplateList` | `List<NotificationTemplate>` | 内置“默认文本”“默认 JSON” | 共享通知模板，字段为 `id / name / type / content` |
| `notificationStatusStyles` | `Map<String, NotificationStatusStyle>` | 五种状态的默认表情、名称与事件模板 | 通知状态样式，键为 `DOWNLOAD_START`、`DOWNLOAD_END`、`OMIT`、`ERROR`、`COMPLETED`；字段为 `emoji / label / template` |
| `notificationTemplate` | `String` | 空 | 旧版消息模板，仅用于迁移 |

### 认证与安全

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `login` | `Login` | 空，首次启动按平台生成或引导设置 | 用户名与口令摘要 |
| `loginConfigured` | `Boolean` | 按已有凭据推断 | 是否已完成首次登录凭据配置 |
| `multiLoginForbidden` | `Boolean` | `true` | 禁止多端登录 |
| `loginEffectiveHours` | `Integer` | `3` | 登录有效时间(小时) |
| `apiKey` | `String` | 随机 64 位字符串 | API Key |
| `verifyLoginIp` | `Boolean` | `false` | 校验登录 IP |
| `ipWhitelist` | `Boolean` | `false` | 开启 IP 白名单 |
| `ipWhitelistStr` | `String` | 空 | IP 白名单 |
| `innerIP` | `Boolean` | `false` | 仅允许内网 IP 访问 |
| `limitLoginAttempts` | `Boolean` | `true` | 限制尝试次数 |
| `reverseProxyTrustIpListEnabled` | `Boolean` | `false` | 启用 受信任的反向代理IP |
| `reverseProxyTrustIpList` | `List<String>` | `[127.0.0.1]` | 受信任的反向代理IP |
| `allowCors` | `Boolean` | `false` | 允许跨域 |
| `jwtKey` | `String` | 随机 32 字节（Base64） | JwtKey |
| `tokenId` | `String` | 随机 UUID | tokenId |
| `uuid` | `String` | 随机 UUID | 唯一ID |

### Bangumi 账号

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `bgmTokenType` | `BgmTokenTypeEnum` | `INPUT` | BGM Token 类型 |
| `bgmToken` | `String` | 空 | BGM Token |
| `bgmAppID` | `String` | 空 | BGM App ID |
| `bgmAppSecret` | `String` | 空 | BGM App Secret |
| `bgmRefreshToken` | `String` | 空 | BGM Refresh Token |
| `bgmRedirectUri` | `String` | 空 | BGM Redirect URI |

### 代理

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `proxy` | `Boolean` | `false` | 代理是否开启 |
| `proxyHost` | `String` | 空 | 代理 host |
| `proxyPort` | `Integer` | `8080` | 代理端口 |
| `proxyUsername` | `String` | 空 | 代理用户名 |
| `proxyPassword` | `String` | 空 | 代理密码 |
| `proxyList` | `String` | 内置常用站点列表 | 代理列表 |

### 外观

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `accentColor` | `String` | 空 | 主题强调色 |
| `themeDynamicColor` | `Boolean` | `false` | 从背景图动态取色 |
| `appBackgroundImage` | `String` | 空 | 应用背景图文件名，位于 `data/images/assets/` |
| `appBackgroundImageDark` | `String` | 空 | 深色模式应用背景图 |
| `loginBackgroundImage` | `String` | 空 | 登录页背景图文件名，位于 `data/images/assets/` |
| `loginBackgroundImageDark` | `String` | 空 | 深色模式登录背景图 |
| `backgroundBlur` | `Integer` | `10` | 背景图模糊度 |
| `backgroundOpacity` | `Integer` | `100` | 背景图不透明度 |
| `appLogoImage` | `String` | 空 | 应用 Logo 文件名，位于 `data/images/assets/` |
| `loginLogoImage` | `String` | 空 | 登录页 Logo 文件名，位于 `data/images/assets/` |
| `cardOpacity` | `Integer` | `80` | 卡片不透明度（0–100） |
| `cardBlur` | `Integer` | `14` | 卡片模糊度 |
| `navOpacity` | `Integer` | `78` | 导航栏不透明度 |
| `navBlur` | `Integer` | `18` | 导航栏模糊度 |
| `dialogOpacity` | `Integer` | `84` | 弹窗不透明度 |
| `dialogBlur` | `Integer` | `16` | 弹窗模糊度 |
| `appCornerRadius` | `Integer` | `12` | 全局圆角（px） |
| `customCss` | `String` | 空 | 自定义 CSS |
| `customJs` | `String` | 空 | 自定义 JS |

### 缓存、备份与更新

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `cacheAutoClear` | `Boolean` | `false` | 自动清理缓存 |
| `cacheAutoClearHours` | `Integer` | `72` | 自动清理缓存间隔(小时) |
| `cacheLastClearTime` | `Long` | `0L` | 上次清理缓存时间 |
| `configBackup` | `Boolean` | `false` | 设置备份 |
| `configBackupDay` | `Integer` | `7` | 备份天数 |
| `autoUpdate` | `Boolean` | `false` | 自动更新 |
| `updateTotalEpisodeNumber` | `Boolean` | `false` | 自动更新总集数信息 |
| `forceUpdateTotalEpisodeNumber` | `Boolean` | `false` | 强制更新总集数信息 |
| `version` | `String` | 空 | 版本 |

### 插件与开发者

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pluginMarketUrl` | `String` | 空 | 插件市场地址 |
| `developerOptionsEnabled` | `Boolean` | `false` | 开发者选项是否已解锁 |
| `customTorrentUrl` | `String` | 空 | 自定义种子下载地址，留空使用来源站默认 |
| `customUserAgent` | `String` | 空 | 自定义 User-Agent |
| `devHttpTrace` | `Boolean` | `false` | 记录出站请求日志 |
| `githubToken` | `String` | 空 | GitHub Token，用于提高 GitHub 接口限额 |

### 其他

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `sortType` | `AniSortTypeEnum` | `SCORE` | 排序方式 |
| `autoStart` | `Boolean` | `false` | 开机自启动 |
| `outTradeNo` | `String` | 空 | 旧版缓存标识，现仅为兼容保留，不参与逻辑 |
| `gitInfo` | `GitInfo` | 不落盘 | 读取时由构建信息填充，仅出现在接口响应中 |
## 取值约束

以下字段在 `ConfigUtil#format` 中会被强制校正，超出的值不会保留：

| 字段 | 约束 |
| --- | --- |
| `rssConcurrency` | 1–16 |
| `rssSleepSeconds` | 不小于 1，保存时同步回写 `rssSleepMinutes` |
| `backgroundBlur` | 0–40 |
| `backgroundOpacity` | 0–100 |
| `cacheAutoClearHours` | 1–720 |
| `customUserAgent` | 去除换行与控制字符，最长 256 |

`downloadToolType` 会被强制归一化为 `qBittorrent`；`downloadToolPassword` 以 `qbt_` 开头时 `qbAuthType` 视为 `API_KEY`，否则为 `USER_PASSWORD`。

## 兼容与迁移

以下字段只为读取旧配置而保留，新代码不应依赖：

| 字段 | 说明 |
| --- | --- |
| `rssSleepMinutes` | 旧版 RSS 间隔（分钟），由 `rssSleepSeconds` 回写 |
| `notificationTemplate` | 旧版整段消息正文，加载时并入默认文本模板 |
| `globalReverseMatch` | 旧版全局反匹配列表，迁移到 `globalReverseMatchRules` |

迁移原则：只补默认值与做显式转换，不静默丢弃用户数据；无法无损迁移时保留旧字段并写迁移逻辑。

通知侧还有两类旧数据会就地迁移：

- 通知类型 `WEB_HOOK` 转换为 `CUSTOM_REQUEST`，URL、请求头与请求体中的旧占位符一并替换。
- `NotificationConfig` 上的 `notificationTemplate`、`telegramTemplate`、`customRequestBody` 属于渠道级旧字段，加载时并入共享模板（`notificationTemplateList`），之后只写共享模板。

## 安全相关字段

| 字段 | 注意事项 |
| --- | --- |
| `login.password` | 口令摘要，不返回前端；留空表示不修改 |
| `apiKey` | 生成后可用于接口鉴权，视为敏感值 |
| `jwtKey`、`tokenId` | 签名与会话标识，修改密码或 API Key 后必须轮换 |
| `qbPassword`、`bgmAppSecret`、`bgmRefreshToken`、`githubToken` | 第三方凭据，不写入日志与错误响应 |
| `ipWhitelist`、`ipWhitelistStr`、`innerIP` | 访问控制，默认关闭，启用后行为不得削弱 |
| `allowCors` | 默认 `false`，不要为方便调试长期开启 |

`GET /api/config` 返回前会把 `login.password`、`downloadToolPassword`、`qbPassword`、`jwtKey`、`outTradeNo` 置空，并附带运行时的 `version` 与 `gitInfo`；`apiKey` 与 `bgmToken` 等需要回显的字段仍会返回，因此该接口必须保持登录鉴权。保存时密码类字段传空表示“不修改”。详见[安全说明](SECURITY.md)。

## 新增字段的流程

1. `entity/Config.java` 增加字段与 `@Schema` 说明。
2. `config/DefaultConfigFactory.java` 给出默认值。
3. `util/other/ConfigUtil.java#format` 补齐旧配置并做范围校正。
4. `mint-soda-ui/src/js/config.js` 同步默认对象。
5. 在对应 `mint-soda-ui/src/view/config/*.vue` 增加控件与词条。
6. 更新本文档与[使用指南](USER_GUIDE.md)。

密码类字段保持“前端传空表示不修改”的语义；`format` 只补默认值与迁移，不丢弃已有数据。
