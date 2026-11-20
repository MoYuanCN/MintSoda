# API 参考

ANI-RSS-Yuri 的后端对外只有一个 HTTP 入口：默认监听 `17789` 端口，所有业务接口挂在 `/api` 前缀下。本文说明鉴权方式、统一返回体与接口清单，便于编写自动化脚本或对接第三方工具。

> 本文以当前代码为准。插件可以通过 `PluginEndpoint` 注册自己的接口，统一挂载在 `/api/plugin/api/{插件标识}/**`，详见[插件开发指南](PLUGIN_DEVELOPMENT.md#自定义-http-接口)。配置字段的含义与默认值见[配置参考](CONFIG.md)。

## 基本约定

- **协议**：默认明文 HTTP，建议置于 HTTPS 反向代理之后。
- **前缀**：所有接口形如 `http(s)://<主机>:17789/api/<路径>`。
- **方法**：业务接口统一使用 `POST`；文件、图片、日志压缩包、备份导出与日历使用 `GET`。参数放在 JSON 请求体或查询参数中，两者可同时使用。
- **编码**：请求与响应均为 UTF-8，`Content-Type: application/json`（文件上传除外）。
- **大小限制**：上传请求体上限 200MB；插件自定义接口的请求体上限 256KB。
- **路径匹配**：控制器路径不区分大小写。

## 鉴权

除公开接口外，所有 `/api` 接口都要求鉴权，可通过 `@Auth` 支持的四种方式之一通过：

| 方式 | 传递位置 | 说明 |
| --- | --- | --- |
| JWT | 请求头 `Authorization` | 登录返回的令牌，前端默认使用该方式 |
| 表单 / 查询参数 | `s` 参数 | 便于浏览器直接打开或脚本拼接 |
| API Key | 请求头 `api-key`、`x-api-key`、`s`，或同名参数 | 需先在设置中生成 API Key；为空时不生效 |
| IP 白名单 | 来源 IP | 开启后按单 IP、通配符、CIDR 或区间匹配 |

JWT 使用 HS256 签名，有效期由 `loginEffectiveHours` 控制（`0` 表示不过期，默认 3 小时）。修改用户名或密码、或在开启 `multiLoginForbidden` 时重新登录，都会使旧令牌失效。鉴权失败返回业务码 `403`，前端据此清空令牌并回到登录页。

### 登录

```bash
# 1. 查询是否需要首次初始化
curl -s -X POST http://127.0.0.1:17789/api/setupStatus

# 2. 登录（password 为前端计算的口令摘要）
curl -s -X POST http://127.0.0.1:17789/api/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<用户名>","password":"<口令摘要>"}'

# 3. 携带返回的 data 作为令牌调用其它接口
curl -s -X POST http://127.0.0.1:17789/api/listAni \
  -H 'Authorization: <令牌>'
```

> 登录与开发者验证提交的是口令摘要而非明文，相当于口令本身可用于重放；务必配合 HTTPS 使用。

## 统一返回体

所有业务接口返回 `Result<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "t": 1700000000000
}
```

| 字段 | 说明 |
| --- | --- |
| `code` | 业务状态码，与 HTTP 状态码语义一致：`200` 成功、`403` 未登录或令牌失效、`404` 资源或方法不存在、`500` 业务或内部错误 |
| `message` | 提示文案，失败时用于前端提示；不包含凭据等敏感信息 |
| `data` | 业务数据，类型由接口决定，可能为空 |
| `t` | 服务器当前时间戳（毫秒），前端用于检测本地时间偏差 |

注意：

- **HTTP 状态码始终是 200**（文件下载与静态资源除外），是否成功以 `code` 为准。
- 业务异常由 `CustomExceptionHandler` 统一转换为 `Result`，不要在脚本里依赖异常栈。
- 前端在收到 `code === 403` 时会清空本地令牌，脚本调用时需自行处理重新登录。

## 公开接口

以下接口不需要登录，改动时需评估信息暴露：

| 接口 | 说明 |
| --- | --- |
| `POST /api/login` | 登录 |
| `POST /api/setupStatus` | 首次初始化状态 |
| `POST /api/setup` | 首次设置账号，仅未初始化且来源为本机或内网时可用 |
| `POST /api/publicSettings` | 登录页所需的外观字段 |
| `GET /api/custom.js` / `GET /api/custom.css` | 管理员配置的自定义资源，会注入页面 |
| `GET /api/uiAsset` | 外观图片，限定在 `ui-assets` 目录 |
| `POST /api/testIpWhitelist` | 当前来源 IP 是否命中白名单 |
| `POST /api/ping` | 存活测试 |

## 接口清单

### 状态与关于

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/system/status` | POST | 运行概览：版本、运行时长、订阅数、堆内存、线程数、负载 |
| `/api/about` | POST | 关于信息与可用更新 |
| `/api/update` | POST | 下载并应用更新 |
| `/api/stop` | POST | 重启（`status=0`）或关闭（`status=1`）服务 |

### 配置

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/config` | POST | 获取设置；返回前清空登录密码、下载器密码、`jwtKey` 与 `outTradeNo` |
| `/api/setConfig` | POST | 保存设置；密码类字段留空表示不修改 |
| `/api/clearCache` | POST | 清理未被引用的封面与预览缓存 |
| `/api/testProxy` | POST | 代理连通性测试（`?url=` + 设置对象） |
| `/api/downloadLoginTest` | POST | 用给定设置测试 qBittorrent 登录 |
| `/api/verifyDeveloperCredentials` | POST | 校验开发者选项凭据 |
| `/api/setDeveloperSettings` | POST | 保存开发者选项（自定义种子地址、User-Agent、请求日志） |
| `/api/devNetworkCheck` | POST | 出站网络自检 |

### 订阅

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/listAni` | POST | 订阅列表；`?sort=` 可临时指定排序 |
| `/api/addAni` | POST | 添加订阅；`?override=` 控制同名冲突 |
| `/api/setAni` | POST | 修改订阅 |
| `/api/deleteAni` | POST | 删除订阅；`?deleteFiles=` 同时删除文件与任务 |
| `/api/batchEnable` | POST | 批量启用 / 停用；`?value=` |
| `/api/refreshAll` | POST | 刷新全部订阅 |
| `/api/refreshAni` | POST | 刷新单条订阅，请求体 `{"id":"..."}` |
| `/api/updateTotalEpisodeNumber` | POST | 更新总集数；`?force=` |
| `/api/rssToAni` | POST | 将 RSS 解析为订阅草稿 |
| `/api/previewAni` | POST | 预览订阅的条目 |
| `/api/downloadPath` | POST | 计算订阅的下载位置 |
| `/api/importAni` | POST | 导入订阅数据 |
| `/api/refreshCover` | POST | 刷新封面 |

排序值：`SCORE`（评分）、`PINYIN`（拼音）、`DOWNLOAD_TIME`（最后下载时间）、`UPDATE_WEEK`（更新星期）、`STANDBY_RSS`（备用 RSS 数量）、`TITLE`（标题）。未知值回退到配置中的默认排序。

### 订阅来源

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/mikan` | POST | Mikan 番剧列表（`?text=` + 季度对象） |
| `/api/mikanGroup` | POST | Mikan 字幕组列表（`?url=`） |
| `/api/aniBT` | POST | AniBT 番剧列表（请求体 `season`、`bgmUrl`、`title`） |
| `/api/aniBTGroup` | POST | AniBT 字幕组列表（`?bgmId=`） |
| `/api/searchBgm` | POST | 搜索 Bangumi 条目（`?name=`） |
| `/api/getAniBySubjectId` | POST | 按 Bangumi 条目 ID 生成订阅（`?id=`） |
| `/api/getBgmTitle` | POST | 获取 Bangumi 标题 |
| `/api/meBgm` | POST | 当前 Bangumi 账号信息与 Token 有效期 |
| `/api/bgm/oauth/callback` | POST | Bangumi 授权回调（`?code=`） |
| `/api/getThemoviedbName` | POST | 查询 TMDB 标题（请求体 `tmdbId` / `title` / `ova`） |
| `/api/getThemoviedbGroup` | POST | 查询 TMDB 剧集组 |

### 合集与下载

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/startCollection` | POST | 开始下载合集 |
| `/api/previewCollection` | POST | 合集预览 |
| `/api/collectionDownloadPath` | POST | 计算合集下载位置 |
| `/api/getCollectionSubgroup` | POST | 推断合集字幕组 |
| `/api/deleteTorrent` | POST | 删除缓存种子（`?id=` + `?hash=`，多个哈希用逗号分隔） |

### 通知

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/testNotification` | POST | 用给定通知配置发送测试消息 |
| `/api/newNotification` | POST | 生成一条带默认模板的通知配置 |
| `/api/getTgUpdates` | POST | 获取 Telegram 最近会话，便于选择目标 |
| `/api/getEmbyViews` | POST | 获取 Emby 媒体库列表 |

### 日志

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/logs` | POST | 当前会话日志缓存 |
| `/api/clearLogs` | POST | 清空日志缓存（不影响磁盘日志） |
| `/api/downloadLogs` | GET | 下载日志压缩包 |

### 备份与 WebUI

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/exportBackup` | GET | 导出设置备份（含插件与插件私有数据，不含 `installed.json`） |
| `/api/importBackup` | POST | 导入备份（`multipart/form-data`，字段 `file`） |
| `/api/webui/upload` | POST | 上传自定义 WebUI 包 |
| `/api/webui/delete` | POST | 删除自定义 WebUI |
| `/api/webui/getUpdate` | POST | 检查 WebUI 更新 |
| `/api/webui/update` | POST | 应用 WebUI 更新（校验 `size` 与 `sha256`） |

### 文件与上传

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/file` | GET | 读取文件，`filename` 为 Base64 编码路径，仅允许图片 |
| `/api/uiAsset` | GET | 读取外观图片，路径限定在 `ui-assets` 目录 |
| `/api/uiAsset/upload` | POST | 上传外观图片，白名单扩展名并校验文件头 |
| `/api/upload` | POST | 通用上传，按内容摘要命名 |
| `/api/uploadAndRead` | POST | 上传并返回 UTF-8 文本 |
| `/api/uploadAndReadToBase64` | POST | 上传并返回 Base64 |
| `/api/proxyImage` | GET | 图片代理，`imgUrl` 为 Base64 编码地址，经 SSRF 校验 |
| `/api/calendar.ics` | GET | 订阅日历 |

### 插件

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/plugin/list` | POST | 插件列表 |
| `/api/plugin/stats` | POST | 运行概览：插件数量与各类扩展点数量 |
| `/api/plugin/info` | POST | 插件详情，请求体 `{"id":"..."}` |
| `/api/plugin/enable` / `disable` / `reload` / `uninstall` | POST | 启停、重载与卸载，请求体 `{"id":"..."}` |
| `/api/plugin/saveConfig` | POST | 保存插件配置，请求体 `{"id":"...","config":"<JSON 字符串>"}` |
| `/api/plugin/upload` | POST | 上传安装插件（`multipart/form-data`，可选 `sha256`） |
| `/api/plugin/install` | POST | 链接安装，请求体 `url`、`sha256`、`size` |
| `/api/plugin/permissions` | POST | 可授予的权限清单 |
| `/api/plugin/setPermissions` | POST | 保存权限，请求体 `{"id":"...","permissions":["ITEM_FILTER", ...]}` |
| `/api/plugin/market` | POST | 获取市场索引，请求体 `{"url":"..."}`，留空使用已保存地址 |
| `/api/plugin/installFromMarket` | POST | 从市场条目安装 |
| `/api/plugin/checkUpdate` / `update` | POST | 检查 / 应用单个插件更新，请求体 `id`、`marketUrl` |
| `/api/plugin/checkAll` | POST | 检查全部插件更新 |
| `/api/plugin/marketUrl` / `setMarketUrl` | POST | 读取 / 保存市场地址 |
| `/api/plugin/api/{pluginId}/**` | 任意 | 插件自定义接口，方法与路径由插件声明；插件页面的表格、表单与操作也走这里 |

插件接口的返回值是插件自己生成的字符串，原样放进 `Result.data`；不是所有插件都返回 JSON。

### 开发者与调试

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/mcp` | 任意 | MCP 端点，仅 `MCP_ENABLED=true` 时启用，只接受 API Key |
| `/swagger-ui/**`、`/v3/api-docs/**` | GET | 接口文档，仅 `SWAGGER_ENABLED=true` 时启用 |

`SWAGGER_ENABLED` 与 `MCP_ENABLED` 默认关闭，需要调试时用环境变量临时开启，不要在公网实例上常驻。

## 调用示例

### 查询订阅并刷新

```bash
TOKEN='<登录返回的令牌>'
BASE='http://127.0.0.1:17789/api'

# 列出订阅
curl -s -X POST "$BASE/listAni" -H "Authorization: $TOKEN"

# 刷新某条订阅
curl -s -X POST "$BASE/refreshAni" \
  -H "Authorization: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":"<订阅 ID>"}'
```

### 用 API Key 调用

```bash
curl -s -X POST 'http://127.0.0.1:17789/api/system/status' \
  -H 'api-key: <设置中生成的 API Key>'
```

### 安装插件并启用

```bash
curl -s -X POST "$BASE/plugin/install" \
  -H "Authorization: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/plugin.jar","sha256":"<64 位十六进制>","size":"12345"}'

curl -s -X POST "$BASE/plugin/enable" \
  -H "Authorization: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":"my-plugin"}'
```

## 扩展与变更

- 新增接口放在对应的 `*Controller`，返回 `Result<T>` 并补 `@Operation` 说明；默认加 `@Auth`。
- 请求体优先使用 `entity/dto` 下带类型的 DTO，避免直接解析 `Map<String, String>`。
- 新增配置字段时同步更新[配置参考](CONFIG.md) 与[开发指南 · 配置变更清单](DEVELOPMENT.md#配置变更清单)。
- 公开接口需在评审中说明“不含敏感信息”的理由，并同步更新[安全说明](SECURITY.md#公开接口)与本文档。
- 删除或改变接口语义时，同步更新本文档与用户指南中的相关描述。
