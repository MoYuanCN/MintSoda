# 插件开发指南

本文说明如何为 ANI-RSS-Yuri 编写、打包、发布与更新插件。插件系统面向“扩展而不侵入”：Java 插件以独立 JAR 加载，JavaScript 插件以脚本目录解释执行，两者共用同一套扩展点——通知、订阅过滤、订阅事件、周期任务、设置界面、模板变量、下载拦截与自定义 HTTP 接口。

不熟悉 Java 时优先看 [JavaScript 插件](#javascript-插件)，它不需要编译，改完脚本重新打包即可。

> 安全边界见 [安全说明](SECURITY.md)。插件与服务同权限运行，**只安装你信任的插件**。

## 概念与架构

| 组件 | 说明 |
| --- | --- |
| `ani-rss-plugin-api` | 稳定的插件接口模块，Java 插件以 `provided` 依赖编译。 |
| `PluginSource` | 插件来源抽象，`JavaPluginSource` 走类加载器，`ScriptPluginSource` 走脚本引擎。 |
| `PluginManager` | 负责加载、启用、停用、重载与卸载插件，只依赖 `PluginSource`。 |
| `PluginContext` | 宿主传给插件的能力入口：注册扩展点、读写插件配置与日志。 |
| `PluginHook` | 宿主内部桥接，把匹配规则之后的条目交给插件过滤器。 |
| 插件市场 | 可配置的 JSON 索引，支持列出、安装与更新插件。 |

- 插件接口版本由 `PluginApi.VERSION` 表示，当前为 `2`。宿主只加载 `apiVersion` 不大于自身版本的插件，破坏性变更时提升该值。
- 版本 2 新增模板变量、下载拦截与自定义接口三个扩展点；均为新增能力，不改变既有扩展点语义。
- 描述文件统一叫 `plugin.json`；为兼容常见习惯，包内只提供 `manifest.json` 也接受，安装后会改名为 `plugin.json`。
- `runtime` 决定插件形态：`JAVA`（默认）是单个 JAR，根目录包含 `plugin.json` 与编译后的类；`JAVASCRIPT` 是 ZIP，根目录包含 `plugin.json` 与入口脚本。
- Java 插件每个独立类加载器，`ani.rss.plugin.api.*` 与 JDK 类始终由宿主提供，避免类型不一致。
- JavaScript 插件运行在沙箱里：看不到任何 Java 类，单次调用超过 5 秒会被中断。
- 插件加载、启用或运行失败只影响该插件，主程序继续运行。

## 目录布局

```text
<配置目录>/plugins/
├── installed.json          # 启用状态、来源、sha256 与插件配置
└── <plugin-id>/
    ├── plugin.jar          # Java 插件包（脚本插件没有此文件）
    ├── plugin.json         # 接口版本、运行时、入口等
    ├── main.js             # 脚本插件入口（示例）
    └── data/               # 插件私有数据目录，覆盖安装时保留
```

所有插件路径都会被规范化并限制在 `plugins/` 之内；插件标识只允许 `A-Z a-z 0-9 . _ -`，长度 1-64。

## 最小插件

### 1. `plugin.json`

```json
{
  "id": "my-plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "apiVersion": 1,
  "mainClass": "com.example.MyPlugin",
  "author": "you",
  "description": "一句话说明插件作用",
  "homepage": "https://example.com",
  "updateUrl": "",
  "permissions": ["notification"],
  "minAppVersion": "",
  "runtime": "JAVA",
  "entry": ""
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 唯一标识，同时作为目录名；发布后不要修改 |
| `name` | 是 | 显示名称 |
| `version` | 是 | 插件版本，市场与更新比较时使用 |
| `apiVersion` | 是 | 编译时依赖的接口版本 |
| `mainClass` | 是 | `Plugin` 实现类全限定名 |
| `author` | 否 | 作者 |
| `description` | 否 | 简介 |
| `homepage` | 否 | 项目主页 |
| `updateUrl` | 否 | 插件自身的更新索引地址 |
| `permissions` | 否 | 声明使用的能力，仅用于展示与审计 |
| `minAppVersion` | 否 | 最低宿主版本 |
| `runtime` | 否 | `JAVA` 或 `JAVASCRIPT`，缺省 `JAVA` |
| `entry` | 否 | 脚本入口文件，缺省 `main.js`；Java 插件忽略 |

### 2. 入口类

```java
package com.example;

import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginContext;

public class MyPlugin implements Plugin {

    @Override
    public void onLoad(PluginContext context) {
        context.log("loaded");
    }

    @Override
    public void onEnable(PluginContext context) {
        // 在这里注册扩展点
    }

    @Override
    public void onDisable(PluginContext context) {
        // 释放线程、连接等资源
    }
}
```

生命周期：

1. `onLoad`：插件被识别时调用一次，此时可能尚未启用；不要在构造函数里访问宿主能力。
2. `onEnable`：启用时调用，用于注册扩展点；停用后注册项会被清理。
3. `onDisable`：停用或重载前调用。
4. `onUnload`：卸载前调用。

## 扩展点

### 通知渠道

```java
context.registerNotificationChannel(new NotificationChannel() {
    @Override
    public String id() {
        return "my-channel";
    }

    @Override
    public String name() {
        return "我的通知渠道";
    }

    @Override
    public List<ConfigField> fields() {
        return List.of(
                new ConfigField("endpoint", "接收地址", ConfigField.FieldType.TEXT,
                        "https://example.com/hook", true, List.of()),
                new ConfigField("token", "令牌", ConfigField.FieldType.PASSWORD)
        );
    }

    @Override
    public void send(NotificationPayload payload, Map<String, String> options) throws Exception {
        // 使用 options 中的字段投递 payload
    }
});
```

- `id` 全局唯一，建议加插件前缀，避免与其他插件冲突。
- 通知正文已由宿主按模板渲染，插件只需投递；同一渠道可复用 `请选择消息模板` 的文本模板。
- `test(payload, options)` 默认复用 `send`，需要区分时可覆盖。
- 字段类型支持 `TEXT`、`PASSWORD`、`NUMBER`、`SWITCH`、`SELECT`；值统一以字符串传递。

`NotificationPayload` 字段：`status`、`statusLabel`、`title`、`subgroup`、`text`、`episode`、`coverUrl`、`tags`。

### 订阅过滤器

```java
context.registerItemFilter(new ItemFilter() {
    @Override
    public String id() {
        return "my-filter";
    }

    @Override
    public int order() {
        return 100; // 数值越小越先执行
    }

    @Override
    public Decision decide(SubscriptionSnapshot ani, ItemSnapshot item, Map<String, Object> ctx) {
        return item.title().contains("1080p") ? Decision.REJECT : Decision.ABSTAIN;
    }
});
```

- 过滤发生在宿主匹配规则之后，顺序固定为：反匹配 > 全局匹配 > 排除 > 插件过滤。
- `REJECT` 会拒绝条目；`ABSTAIN` / `ACCEPT` 不改变宿主结论。
- RSS 下载、预览与合集预览共用同一插件过滤逻辑。
- 插件异常会被记录日志并放行，不会中断主流程。

### 订阅事件

```java
context.registerSubscriptionListener(new SubscriptionListener() {
    @Override
    public String id() {
        return "my-listener";
    }

    @Override
    public void onCreated(ItemFilter.SubscriptionSnapshot subscription) {
    }

    @Override
    public void onUpdated(ItemFilter.SubscriptionSnapshot subscription) {
    }

    @Override
    public void onDeleted(ItemFilter.SubscriptionSnapshot subscription) {
    }
});
```

### 周期任务

```java
context.registerScheduledTask(new ScheduledPluginTask() {
    @Override
    public String id() {
        return "my-task";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(30);
    }

    @Override
    public void run(PluginContext context) throws Exception {
        // 宿主使用独立守护线程调度，异常不会影响其他插件
    }
});
```

### 设置区块

```java
context.registerSettingsSection(new SettingsSection(
        "my-settings",
        "我的插件设置",
        "这些字段会以 JSON 形式保存。",
        List.of(
                new ConfigField("enabled", "启用", ConfigField.FieldType.SWITCH),
                new ConfigField("mode", "模式", ConfigField.FieldType.SELECT,
                        "", false, List.of("A", "B"))
        )
));
```

插件页按声明自动渲染表单，值通过 `PluginContext#getConfig` / `saveConfig` 读写。

### 模板变量

为通知、重命名与下载路径模板追加占位符：

```java
context.registerTemplateVariableProvider(new TemplateVariableProvider() {
    @Override
    public String id() {
        return "my-variables";
    }

    @Override
    public Map<String, String> variables(ItemFilter.SubscriptionSnapshot ani) {
        return Map.of("myField", "value");
    }
});
```

- 之后模板里可直接写 `${myField}`，三类模板都会生效。
- 与内置字段同名时插件值优先；返回 `null` 字段会被忽略。
- 宿主内置字段包括 `${title}`、`${subgroup}`、`${season}`、`${seasonFormat}`、`${tmdbid}`、`${year}`、`${month}`、`${date}`、`${jpTitle}`、`${bgmId}` 等；`${jpTitle}` 与 `${bgmId}` 只在模板引用时才联网解析。
- 宿主对同一订阅的变量有 5 秒缓存，避免逐条重命名时重复请求；不要在这里做耗时过长或带副作用的操作。

### 下载拦截

在种子提交给下载器之前调整任务或取消下载：

```java
context.registerDownloadInterceptor(new DownloadInterceptor() {
    @Override
    public String id() {
        return "my-download";
    }

    @Override
    public int order() {
        return 100; // 数值越小越先执行
    }

    @Override
    public DownloadCandidate intercept(DownloadCandidate candidate, Map<String, Object> ctx) {
        if (candidate.size() != null && candidate.size() > 8L * 1024 * 1024 * 1024) {
            return null; // 取消本次下载
        }
        return candidate.withSavePath(candidate.savePath() + "/" + candidate.subgroup());
    }
});
```

- 返回 `null` 取消下载，宿主会删除已下载的种子文件，下次轮询按原有重试逻辑处理。
- 用 `withSavePath` / `withName` 返回修改后的任务，其余字段只读。
- 任一拦截器异常只记录日志并继续，不会中断下载流程。
- 拦截发生在订阅过滤之后、提交给下载器之前。

### 自定义 HTTP 接口

插件可以对外提供接口，宿主负责鉴权与分发：

```java
context.registerEndpoint(new PluginEndpoint() {
    @Override
    public String path() {
        return "status";
    }

    @Override
    public Set<String> methods() {
        return Set.of("GET", "POST");
    }

    @Override
    public String handle(EndpointRequest request) {
        return "{\"ok\":true}";
    }
});
```

- 完整路径为 `/api/plugin/api/{pluginId}/{path}`，例如上面的例子是 `/api/plugin/api/my-plugin/status`。
- 所有请求**都要求登录**，鉴权方式与插件管理接口一致（`@Auth`）；不需要额外实现鉴权。
- `methods()` 默认只允许 `POST`，返回空集合表示不限制方法。
- 请求体上限 256KB，超过会直接拒绝。
- 返回值按字符串原样放进 `Result.data`；需要返回 JSON 时自行拼接或序列化。
- 不要在接口里返回令牌、密码或完整配置。

## JavaScript 插件

不需要 Java 或 Maven，一个目录、两个文件即可：`plugin.json` 与入口脚本（默认 `main.js`），打成 ZIP 上传。

### 1. `plugin.json`

```json
{
  "id": "js-demo-plugin",
  "name": "JavaScript 示例插件",
  "version": "1.0.0",
  "apiVersion": 2,
  "runtime": "JAVASCRIPT",
  "entry": "main.js",
  "author": "you",
  "description": "一句话说明插件作用",
  "permissions": ["notification"]
}
```

`runtime` 必须是 `JAVASCRIPT`，`entry` 指向包内脚本；安装时会校验该文件存在。

### 2. 入口脚本

脚本可以直接使用三个全局对象：

| 全局 | 说明 |
| --- | --- |
| `__api` | 宿主能力：日志、配置、数据目录与全部注册方法 |
| `__descriptor` | 插件描述：`id`、`name`、`version`、`apiVersion`、`author`、`description` |
| `JSON` 等 | Rhino 提供 ES6 标准库；**没有** `require`、`fetch`、`setTimeout` 与任何 Java 类 |

```javascript
function onEnable() {
    __api.notificationChannel({
        id: 'my-channel',
        name: '我的渠道',
        fields: [
            {key: 'endpoint', label: '接收地址', type: 'TEXT', required: true},
            {key: 'token', label: '令牌', type: 'PASSWORD'}
        ],
        send: function (payload, options) {
            __api.log('发送通知 ' + payload.title + ' -> ' + options.endpoint);
        }
    });
}
```

生命周期函数 `onLoad`、`onEnable`、`onDisable`、`onUnload` 都是可选的；扩展点必须在 `onEnable` 里注册，停用时宿主自动清理。

### 3. `__api` 参考

| 方法 | 说明 |
| --- | --- |
| `log(message)` | 写日志，自动带插件标识 |
| `getConfig()` / `saveConfig(json)` | 读写插件配置（JSON 字符串） |
| `appVersion()` | 宿主版本 |
| `dataDir()` | 插件私有数据目录的绝对路径 |
| `notificationChannel(obj)` | 注册通知渠道 |
| `itemFilter(obj)` | 注册订阅过滤器 |
| `subscriptionListener(obj)` | 注册订阅事件 |
| `scheduledTask(obj)` | 注册周期任务 |
| `settingsSection(obj)` | 注册设置区块 |
| `templateVariables(obj)` | 注册模板变量 |
| `downloadInterceptor(obj)` | 注册下载拦截 |
| `endpoint(obj)` | 注册自定义 HTTP 接口 |

注册对象的字段：

```javascript
// 通知渠道
{id, name, fields: [{key, label, type, placeholder, required, options}],
 send(payload, options) {}}
// payload: {status, statusLabel, title, subgroup, text, episode, coverUrl, tags}

// 订阅过滤器：返回 'REJECT' / 'ACCEPT' / 'ABSTAIN'
{id, order, decide(subscription, item, context) {}}
// subscription: {id, title, group, season, weekLabel, currentEpisode, totalEpisode, tags}
// item: {title, rename, subgroup, episode, master, size, magnet}

// 订阅事件
{id, onCreated(subscription) {}, onUpdated(subscription) {}, onDeleted(subscription) {}}

// 周期任务：intervalMinutes 最小 1，initialDelayMinutes 可选
{id, intervalMinutes, initialDelayMinutes, run(pluginId) {}}

// 设置区块
{id, title, description, fields: [...]}

// 模板变量：返回字段名到值的映射，模板里写 ${name}
{id, variables(subscription) { return {name: 'value'}; }}

// 下载拦截：返回 {savePath, name} 修改任务，返回 null 取消下载
{id, order, intercept(candidate, context) { return candidate; }}
// candidate: {hash, name, title, subgroup, episode, savePath, tags, magnet, size, subscription}

// 自定义接口：挂载在 /api/plugin/api/{插件id}/{path}
{id, path, methods: ['GET', 'POST'], handle(request) { return 'JSON 字符串'; }}
// request: {method, path, query, body, contentType}
```

### 4. 沙箱限制

- 看不到任何 Java 类，也不能用 `Packages`、`java`、`Java.type` 访问宿主。
- 单次脚本调用超过 5 秒会被中止并记录错误，避免死循环占满线程。
- 没有网络与定时器 API；需要周期行为时用 `scheduledTask`，需要外部请求时由宿主侧能力（通知渠道、下载拦截）触发。
- 脚本异常只影响当前调用，宿主会记录日志并继续主流程。

### 5. 打包

```bash
cd examples/plugins/js-plugin
./build.sh          # 生成 plugin.zip
```

也可以用任意工具打成 ZIP，要求 `plugin.json` 与入口脚本在压缩包根目录。安装时选择该 ZIP 即可；覆盖安装会保留 `data/` 目录。

完整示例见 `examples/plugins/js-plugin/`。

## 编译与打包

插件需要以 `provided` 依赖接口模块：

```xml
<dependency>
    <groupId>ani.rss</groupId>
    <artifactId>ani-rss-plugin-api</artifactId>
    <version>3.3.0</version>
    <scope>provided</scope>
</dependency>
```

打包要点：

- `plugin.json` 必须位于 JAR 根目录（`manifest.json` 亦可，安装后会规范化）。
- 不要把 `ani-rss-plugin-api` 打进插件包。
- 示例见 `examples/plugins/hello-plugin/`，构建脚本会把 `plugin.json` 加入 JAR：

```bash
cd examples/plugins/hello-plugin
./build.sh
# 产物：target/plugin.jar
```

## 安装与更新

安装方式：

1. **上传安装**：插件页“安装插件”，选择 `plugin.jar`（Java）或 `plugin.zip`（脚本）。
2. **链接安装**：填写 JAR 地址，可同时提供 `sha256` 与字节数。
3. **市场安装**：填写市场索引地址后，从插件市场列表安装。

校验要求：

- 地址必须是公网 `http`/`https`，回环与内网字面地址会被拒绝。
- 提供 `sha256` 时严格比对；提供字节数时严格比对大小。
- 安装先写入临时目录并校验，通过后替换正式目录，避免留下半成品。
- 脚本插件按 ZIP 解包，条目路径必须留在插件目录内；覆盖安装保留 `data/`。
- 安装不会自动启用插件，需在插件页手动启用。

## 插件市场

市场是一个 JSON 索引：

```json
{
  "name": "示例市场",
  "updatedAt": "2026-01-01",
  "plugins": [
    {
      "id": "my-plugin",
      "name": "我的插件",
      "version": "1.0.0",
      "description": "简介",
      "author": "you",
      "homepage": "https://example.com",
      "downloadUrl": "https://example.com/plugins/my-plugin-1.0.0.jar",
      "sha256": "<jar 的 sha256>",
      "size": 12345,
      "apiVersion": 1,
      "permissions": ["notification"],
      "updateUrl": ""
    }
  ]
}
```

- `downloadUrl` 指向 `plugin.jar` 或脚本 `plugin.zip`，宿主按包内描述文件的 `runtime` 自动识别。
- 市场地址保存在配置项 `pluginMarketUrl`，可在插件页修改。
- 检查更新时优先使用市场条目；没有市场条目时回退到插件自带的 `updateUrl`，该地址返回单个同上结构的条目。
- 版本号不同即视为有更新；建议使用语义化版本。
- 示例索引见 `examples/plugins/market.json`。

## 选择 Java 还是 JavaScript

| 场景 | 建议 |
| --- | --- |
| 只想改模板、加通知渠道、简单过滤 | JavaScript |
| 需要访问宿主内部能力、复用第三方 Java 库、做复杂解析 | Java |
| 需要长期维护、分发到市场 | 两者皆可；Java 包更小启动更快，JavaScript 无需编译 |

两种运行时共享同一套扩展点与快照字段，迁移时只需把注册对象换成接口实现。

## 接口兼容策略

- 新增可选方法或字段：不提升 `PluginApi.VERSION`，插件保持兼容。
- 删除或改变既有语义：提升 `PluginApi.VERSION`，旧插件在插件页显示“不兼容”。
- 宿主核心类不属于接口，插件不得依赖 `ani.rss.*`（`plugin.api` 除外）；跨版本使用时宿主实现可能变化。
- 插件应自行兜底空值：快照字段可能为空字符串或 `null`。

## 最佳实践

- 在 `onEnable` 中注册、在 `onDisable` 中释放资源；不要依赖 `onUnload` 做关键清理。
- 不要在静态字段中缓存宿主对象，重载后可能失效。
- 网络请求设置超时，失败时抛异常交由宿主记录与重试，不要静默吞掉。
- 模板变量提供方与下载拦截器会被主流程同步调用，保持轻量；耗时任务放到周期任务里。
- 日志不要输出令牌、密码或完整响应体。
- 周期任务保持幂等，避免在任务里做长时间阻塞操作。
- 需要持久化时使用 `dataDir()` 或 `saveConfig`，不要在插件目录外写文件。
