# 插件开发指南

本文说明如何为 ANI-RSS-Yuri 编写、打包、发布与更新插件。插件系统面向“扩展而不侵入”：Java 插件以独立 JAR 加载，JavaScript 与 Lua 插件以脚本目录解释执行，三者共用同一套扩展点——通知、订阅过滤、订阅事件、周期任务、设置界面、模板变量、下载拦截、下载事件与自定义 HTTP 接口。

不熟悉 Java 时优先看 [JavaScript 插件](#javascript-插件) 或 [Lua 插件](#lua-插件)：两者都不需要编译，改完脚本重新打包即可。

插件接口当前版本为 `3`，在版本 2 的基础上新增了[条目改写](#条目改写)与[界面页面](#界面页面)。只使用旧能力的插件继续声明 `apiVersion: 2` 即可，无需改动。

> 安全边界见 [安全说明](SECURITY.md)。插件与服务同权限运行，**只安装你信任的插件**。用户视角的插件页说明见[使用指南 · 插件](USER_GUIDE.md#插件)，宿主接口清单见 [API 参考 · 插件](API.md#插件)。

## 本文目录

- [概念与架构](#概念与架构)
- [目录布局](#目录布局)
- [最小插件](#最小插件)
- [扩展点](#扩展点)
- [JavaScript 插件](#javascript-插件)
- [权限管理](#权限管理)
- [插件数据](#插件数据)
- [Lua 插件](#lua-插件)
- [编译与打包](#编译与打包)
- [安装与更新](#安装与更新)
- [插件市场](#插件市场)
- [选择运行时](#选择运行时)
- [接口兼容策略](#接口兼容策略)
- [最佳实践](#最佳实践)

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
- 下载事件（`registerDownloadListener`）是在版本 2 之上追加的可选能力：接口方法与注册入口都提供默认实现，未升级的插件无需改动即可继续使用。
- 描述文件统一叫 `plugin.json`；为兼容常见习惯，包内只提供 `manifest.json` 也接受，安装后会改名为 `plugin.json`。
- `runtime` 决定插件形态：`JAVA`（默认）是单个 JAR，根目录包含 `plugin.json` 与编译后的类；`JAVASCRIPT` 是 ZIP，根目录包含 `plugin.json` 与入口脚本。
- Java 插件每个独立类加载器，`ani.rss.plugin.api.*` 与 JDK 类始终由宿主提供，避免类型不一致。
- JavaScript 与 Lua 插件运行在沙箱里：看不到任何 Java 类，也没有文件、网络与模块加载能力，单次调用超过 5 秒会被中断。
- 每个插件的能力都要经过权限开关，详见 [权限管理](#权限管理)。
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

### 下载事件

只观察结果、不修改任务：

```java
context.registerDownloadListener(new DownloadListener() {
    @Override
    public String id() {
        return "my-download-events";
    }

    @Override
    public void onAdded(DownloadSnapshot task, Map<String, Object> ctx) {
    }

    @Override
    public void onCompleted(DownloadSnapshot task, Map<String, Object> ctx) {
    }

    @Override
    public void onFailed(DownloadSnapshot task, Map<String, Object> ctx) {
    }
});
```

- `onAdded` 在种子成功提交给下载器后触发，`onFailed` 在重试用尽后触发，`onCompleted` 在下载器报告完成并发出完成通知后触发。
- `DownloadSnapshot` 字段：`hash`、`name`、`title`、`subgroup`、`episode`、`savePath`、`tags`、`size`、`subscription`。
- 与下载拦截不同，监听器拿到的是最终任务，不能修改任何字段；需要改动请使用 `DownloadInterceptor`。
- 单个监听器异常只记录日志，不影响下载与后续监听器。

### 条目改写

在过滤之后、重命名之前调整条目：

```java
context.registerItemTransformer(new ItemTransformer() {
    @Override
    public String id() {
        return "my-transformer";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Item transform(ItemFilter.SubscriptionSnapshot ani, ItemFilter.ItemSnapshot item,
                          Map<String, Object> ctx) {
        if (item.title().contains("[简日]")) {
            return Item.subgroup("简日双语");
        }
        return null; // 不修改
    }
});
```

- 只能修改标题与字幕组；返回 `null` 表示不修改，其余字段只读。
- 改写字幕组会影响重命名、下载标签与保存路径，但不会修改订阅本身的字幕组设置。
- 执行顺序在 `MatchRuleUtil` 与插件过滤器之后，不能绕过反匹配 > 全局匹配 > 排除的优先级。
- 任一改写器异常只记录日志，沿用已有结果。

### 界面页面

插件可以在主程序界面注册自己的页面，宿主按白名单区块渲染：

```java
context.registerPage(new PluginPage(
        "dashboard", "我的看板", "DataLine", "展示插件采集的数据",
        List.of(
                PluginPage.Block.text("说明", "下面的表格由插件接口提供数据。"),
                PluginPage.Block.table("最近记录", "records", List.of(
                        new PluginPage.Block.Column("time", "时间"),
                        new PluginPage.Block.Column("title", "标题"))),
                PluginPage.Block.form("设置", "save", List.of(
                        new ConfigField("endpoint", "接口地址", ConfigField.FieldType.TEXT))),
                PluginPage.Block.actions("操作", List.of(
                        PluginPage.Block.Action.confirm("rescan", "重新扫描", "rescan", "确定要重新扫描吗？"))),
                PluginPage.Block.divider()
        )
));
```

- 页面出现在插件卡片的「插件页面」按钮组里，点击进入 `#/plugins/{插件Id}/{页面Id}`。
- 区块类型仅 `TEXT`、`FORM`、`TABLE`、`ACTIONS`、`DIVIDER`；插件不提供 HTML，也不执行脚本，宿主不会把插件内容当作页面标记注入。
- `TABLE` 的 `source` 指向插件接口，宿主以 `GET` 调用并按列渲染返回的数组（或 `{items:[...]}`）。
- `FORM` 提交时把字段值以 JSON `POST` 到 `source`；字段类型与设置区块相同。
- `ACTIONS` 的每个按钮调用自己的 `source`，`confirm=true` 时先弹确认框。
- 需要 `CUSTOM_PAGE` 权限；页面读取接口本身还需要 `HTTP_ENDPOINT`。

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
| `hasPermission(id)` | 查询用户是否授予某项权限，可按权限分支 |
| `listDataFiles(dir)` / `readDataFile(path)` | 列出或读取 `data/` 下的文件，需要 `DATA_READ` |
| `writeDataFile(path, text)` / `deleteDataFile(path)` | 写入或删除 `data/` 下的文件，需要 `DATA_WRITE` |
| `notificationChannel(obj)` | 注册通知渠道 |
| `itemFilter(obj)` | 注册订阅过滤器 |
| `subscriptionListener(obj)` | 注册订阅事件 |
| `scheduledTask(obj)` | 注册周期任务 |
| `settingsSection(obj)` | 注册设置区块 |
| `templateVariables(obj)` | 注册模板变量 |
| `downloadInterceptor(obj)` | 注册下载拦截 |
| `downloadListener(obj)` | 注册下载事件 |
| `itemTransformer(obj)` | 注册条目改写 |
| `page(obj)` | 注册界面页面 |
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

// 下载事件：只观察结果，不能修改任务
{id, onAdded(task, context) {}, onCompleted(task, context) {}, onFailed(task, context) {}}
// task: {hash, name, title, subgroup, episode, savePath, tags, size, subscription}

// 条目改写：返回 {title, subgroup}，返回 null 表示不修改
{id, order, transform(subscription, item, context) { return {subgroup: '简日双语'}; }}

// 界面页面：区块类型 TEXT / FORM / TABLE / ACTIONS / DIVIDER
{id, title, icon, description, blocks: [
  {type: 'TEXT', title: '说明', text: '...'},
  {type: 'TABLE', title: '记录', source: 'records', columns: [{key: 'time', label: '时间'}]},
  {type: 'FORM', title: '设置', source: 'save', fields: [{key: 'endpoint', label: '接口地址', type: 'TEXT'}]},
  {type: 'ACTIONS', title: '操作', actions: [
    {id: 'rescan', label: '重新扫描', source: 'rescan', method: 'POST', confirm: true, confirmText: '确定？'}
  ]},
  {type: 'DIVIDER'}
]}

// 自定义接口：挂载在 /api/plugin/api/{插件id}/{path}
{id, path, methods: ['GET', 'POST'], handle(request) { return 'JSON 字符串'; }}
// request: {method, path, query, body, contentType}
```

### 4. 沙箱限制

- 看不到任何 Java 类，也不能用 `Packages`、`java`、`Java.type` 访问宿主。
- 单次脚本调用超过 5 秒会被中止并记录错误，避免死循环占满线程。
- 文件只能通过 `data/` 相关方法读写，路径由宿主校验；脚本没有直接的文件系统 API。
- 没有网络与定时器 API；需要周期行为时用 `scheduledTask`，需要外部请求时由宿主侧能力（通知渠道、下载拦截）触发。
- 脚本异常只影响当前调用，宿主会记录日志并继续主流程。

### 5. 打包

```bash
cd examples/plugins/js-plugin
./build.sh          # 生成 plugin.zip
```

也可以用任意工具打成 ZIP，要求 `plugin.json` 与入口脚本在压缩包根目录。安装时选择该 ZIP 即可；覆盖安装会保留 `data/` 目录。

完整示例见 `examples/plugins/js-plugin/`。

## 权限管理

插件声明的能力在安装后由用户在插件页「权限」里逐项开关，保存即生效：已启用的插件会立即重载，按新权限重新注册扩展点。关闭某项权限后，插件对该扩展点的注册会被跳过并记录一条警告，插件的其余部分继续运行。

权限按能力分组，读与写分开授权：

| 分组 | 标识 | 名称 | 作用 | 风险 | 宿主强制 |
| --- | --- | --- | --- | --- | --- |
| 订阅 | `ITEM_FILTER` | 过滤条目 | 在匹配规则之后接受或拒绝资源 | 中 | 是 |
| 订阅 | `SUBSCRIPTION_EVENTS` | 订阅事件 | 接收订阅增删改事件 | 低 | 是 |
| 订阅 | `TEMPLATE_VARIABLE` | 模板变量 | 向模板注入占位符 | 低 | 是 |
| 订阅 | `ITEM_TRANSFORM` | 改写条目 | 在过滤之后改写标题或字幕组 | 中 | 是 |
| 下载 | `NOTIFICATION` | 发送通知 | 注册通知渠道并投递 | 中 | 是 |
| 下载 | `DOWNLOAD_INTERCEPT` | 下载拦截 | 修改保存位置或名称 | 高 | 是 |
| 下载 | `DOWNLOAD_CANCEL` | 取消下载 | 直接取消下载任务 | 高 | 是 |
| 下载 | `DOWNLOAD_EVENT` | 下载事件 | 接收任务提交、完成与失败事件 | 低 | 是 |
| 任务 | `SCHEDULED_TASK` | 周期任务 | 后台定时执行 | 低 | 是 |
| 接口 | `HTTP_ENDPOINT` | 自定义接口 | 提供 `/api/plugin/api/{id}/**` | 中 | 是 |
| 界面 | `CUSTOM_PAGE` | 界面页面 | 在主程序界面注册只读页面 | 低 | 是 |
| 配置 | `CONFIG_READ` | 读取配置 | 读取插件设置 | 低 | 是 |
| 配置 | `CONFIG_WRITE` | 写入配置 | 保存插件设置 | 中 | 是 |
| 数据 | `DATA_READ` | 读取数据 | 读取 `plugins/{id}/data` | 低 | 是 |
| 数据 | `DATA_WRITE` | 写入数据 | 写、覆盖、删除 `data/` 下的文件 | 高 | 是 |
| 系统 | `LOG` | 写日志 | 输出带插件标识的日志 | 低 | 是 |
| 系统 | `NETWORK` | 出站网络 | 发起网络请求 | 高 | 否 |
| 系统 | `FILE_SYSTEM` | 文件系统 | 读写应用目录外的文件 | 高 | 否 |

规则：

- 标识大小写不敏感，`item-filter` 与 `ITEM_FILTER` 等价；`endpoint`、`download-interceptor`、`template-variables`、`data-dir` 等历史写法同样接受，未知标识在保存时被丢弃。
- 旧版本的整体标识会展开：`CONFIG` → `CONFIG_READ` + `CONFIG_WRITE`，`DATA_DIR` / `DATA` → `DATA_READ` + `DATA_WRITE`。这是单向迁移，保存一次后记录里只有拆分后的标识。
- 读与写互不隐含。只授予 `DATA_READ` 时 `writeDataFile` 与 `deleteDataFile` 会抛出异常；只授予 `DATA_WRITE` 时 `readDataFile` 与 `listDataFiles` 会抛出异常，`dataDir()` 也要求至少持有其中一项。
- 缺少权限时这些方法会抛出异常而不是静默返回空值，便于排查；插件应在调用前用 `hasPermission` 判断，避免中断 `onEnable` 里后续的注册。
- 取消下载单独授权。拦截器返回 `null` 但缺少 `DOWNLOAD_CANCEL` 时，宿主会把这次取消当作「不做修改」并记录警告，不会中断下载。
- 首次安装按 `plugin.json` 初始化权限；升级插件不会重置用户的调整；导入备份会恢复备份中的插件目录，权限调整仍按本机记录为准（备份不含安装记录）。
- 未声明权限的插件仍可加载，但只能做纯计算。
- `NETWORK` 与 `FILE_SYSTEM` 无法技术限制：Java 插件与服务同进程，脚本插件也没有对应 API，这两项只用于审计与提示，界面上标记为「仅提示」。
- 权限列表通过 `POST /api/plugin/permissions` 获取，调整通过 `POST /api/plugin/setPermissions`；保存后已启用插件立即重载。
- 脚本可以调用 `__api.hasPermission(id)`（Lua 为 `api.has_permission(id)`）判断权限后再决定行为，避免依赖注册被静默跳过。

## 插件数据

插件私有目录位于 `plugins/{id}/data`，用于保存缓存、游标、状态等需要跨重启保留的数据。宿主只读传递 `File`，不解析其内容，格式由插件自行约定。

- **升级与重装保留**：安装与更新只替换插件目录内的代码文件，`data/` 始终保留，任何运行时都不例外。
- **卸载删除**：卸载插件会连同 `data/` 一起删除；需要保留时先在「设置 → 导出设置」备份。
- **备份与恢复**：导出的设置备份包含整个 `plugins/` 目录（含 `data/`），但不含 `installed.json`；导入后按目录重建安装记录，插件默认保持停用状态。
- **路径限制**：所有读写都经过 `PluginPaths.dataFile`，先 `normalize()` 再校验位于数据目录内，拒绝 `..` 与绝对路径；符号链接不会被跟随。

推荐的读写方式（Java）：

```java
// 读取：不存在时返回空串
String text = context.readDataFile("state/cursor.json");
// 写入：自动创建父目录
context.writeDataFile("state/cursor.json", json);
// 列出：返回相对路径
List<String> files = context.listDataFiles("state");
// 删除：返回是否删除了文件
boolean deleted = context.deleteDataFile("state/cursor.json");
```

脚本插件提供同名方法：

| JavaScript | Lua |
| --- | --- |
| `__api.readDataFile(path)` | `api.read_data_file(path)` |
| `__api.writeDataFile(path, text)` | `api.write_data_file(path, text)` |
| `__api.listDataFiles(dir)` | `api.list_data_files(dir)` |
| `__api.deleteDataFile(path)` | `api.delete_data_file(path)` |

注意：

- 只支持 UTF-8 文本；二进制数据请自行编码后再写入。
- 目录列表不返回符号链接，也不会递归到数据目录之外。
- 写入没有体积上限，但备份会一并打包，避免在 `data/` 里堆积大文件。
- 直接使用 `dataDir()` 返回的 `File` 时，插件要自行约束子路径；按路径读写的方法已经做了校验，优先使用它们。

### 有状态插件的写法

以「记录上次处理到的条目」为例，把状态放进一个 JSON 文件，读写都通过上下文方法：

```java
String readCursor() {
    String text = context.readDataFile("state/cursor.json");
    return StrUtil.isBlank(text) ? "{}" : text;
}

void saveCursor(Map<String, Object> state) {
    context.writeDataFile("state/cursor.json", GsonStatic.toJson(state));
}
```

要点：

- 写入是「覆盖整个文件」，不是追加；一次写完整内容，避免读出半截 JSON。
- 状态丢失时应能降级到默认值，不要因为缺少文件而拒绝加载。
- 状态文件按用途命名并放进子目录（如 `state/`、`cache/`），便于用户定位与清理。
- 不要缓存 `dataDir()` 的绝对路径到配置文件里，不同平台路径不同。
- 需要多文件一致性时，先写数据文件、最后写索引文件，读取时以索引为准。

## Lua 插件

Lua 插件和 JavaScript 插件结构一致：`plugin.json` 加一个入口脚本，打成 ZIP 上传。区别在于宿主注入的是全局 `api` 表，生命周期函数用下划线命名。

### 1. `plugin.json`

```json
{
  "id": "lua-demo-plugin",
  "name": "Lua 示例插件",
  "version": "1.0.0",
  "apiVersion": 2,
  "runtime": "LUA",
  "entry": "main.lua",
  "permissions": ["notification", "item-filter"]
}
```

`runtime` 必须是 `LUA`，`entry` 缺省为 `main.lua`；安装时会校验该文件存在。

### 2. 入口脚本

```lua
function on_load()
    api.log("插件加载完成")
end

function on_enable()
    api.register_notification_channel({
        id = "lua-channel",
        name = "Lua 渠道",
        fields = {
            {key = "endpoint", label = "接收地址", type = "TEXT", required = true}
        },
        send = function(payload, options)
            api.log("发送通知 " .. tostring(payload.title))
        end
    })
end

function on_disable()
    api.log("插件已停用")
end
```

生命周期函数 `on_load`、`on_enable`、`on_disable`、`on_unload` 都可选；扩展点只在 `on_enable` 里注册。

### 3. `api` 参考

| 方法 | 说明 |
| --- | --- |
| `api.log(message)` | 写日志 |
| `api.logDebug(message)` / `api.logWarn(message)` / `api.logError(message)` | 按级别写日志，Lua 版对应 `api.log_debug` / `api.log_warn` / `api.log_error` |
| `api.get_config()` / `api.save_config(json)` | 读写插件配置 |
| `api.app_version()` | 宿主版本 |
| `api.data_dir()` | 插件私有数据目录 |
| `api.has_permission(id)` | 查询用户是否授予某项权限，可按权限分支 |
| `api.list_data_files(dir)` / `api.read_data_file(path)` | 列出或读取 `data/` 下的文件，需要 `DATA_READ` |
| `api.write_data_file(path, text)` / `api.delete_data_file(path)` | 写入或删除 `data/` 下的文件，需要 `DATA_WRITE` |
| `api.register_notification_channel(table)` | 注册通知渠道 |
| `api.register_item_filter(table)` | 注册订阅过滤器 |
| `api.register_subscription_listener(table)` | 注册订阅事件 |
| `api.register_scheduled_task(table)` | 注册周期任务 |
| `api.register_settings_section(table)` | 注册设置区块 |
| `api.register_template_variables(table)` | 注册模板变量 |
| `api.register_download_interceptor(table)` | 注册下载拦截 |
| `api.register_download_listener(table)` | 注册下载事件 |
| `api.register_item_transformer(table)` | 注册条目改写 |
| `api.register_page(table)` | 注册界面页面 |
| `api.register_endpoint(table)` | 注册自定义 HTTP 接口 |

注册表字段与 JavaScript 版一致，命名改用下划线：

```lua
-- 订阅过滤器：返回 "REJECT" / "ACCEPT" / "ABSTAIN"
api.register_item_filter({
    id = "demo-filter",
    order = 300,
    decide = function(subscription, item)
        if string.find(item.title, "sample", 1, true) then
            return "REJECT"
        end
        return "ABSTAIN"
    end
})

-- 周期任务：interval_minutes 最小 1
api.register_scheduled_task({id = "demo-task", interval_minutes = 30, run = function(pluginId) end})

-- 模板变量：返回字段名到值的映射
api.register_template_variables({
    id = "demo-vars",
    variables = function(subscription) return {demoTag = "v1"} end
})

-- 下载拦截：返回 {name, savePath}，返回 nil 表示取消
api.register_download_interceptor({
    id = "demo-download",
    order = 300,
    intercept = function(candidate)
        return {name = candidate.name, savePath = candidate.savePath .. "/lua"}
    end
})

-- 下载事件：只观察结果，不能修改任务
api.register_download_listener({
    id = "demo-events",
    onAdded = function(task) end,
    onCompleted = function(task) end,
    onFailed = function(task) end
})

-- 条目改写：返回 {title, subgroup}，返回 nil 不修改
api.register_item_transformer({
    id = "demo-transform",
    order = 300,
    transform = function(subscription, item, context)
        if string.find(item.title, "[简日]", 1, true) then
            return {subgroup = "简日双语"}
        end
        return nil
    end
})

-- 界面页面
api.register_page({
    id = "dashboard",
    title = "Lua 看板",
    blocks = {
        {type = "TEXT", title = "说明", text = "数据来自插件接口"},
        {type = "TABLE", title = "记录", source = "records",
            columns = {{key = "time", label = "时间"}}},
        {type = "ACTIONS", title = "操作", actions = {
            {id = "rescan", label = "重新扫描", source = "rescan", method = "POST", confirm = true}
        }}
    }
})

-- 自定义接口
api.register_endpoint({
    path = "status",
    methods = {"GET"},
    handle = function(request) return '{"ok":true}' end
})
```

参数里的 table 都是普通 Lua 表：数组是 1 起始的列表，对象是字符串键的映射。

### 4. 沙箱限制

- 只装载基础库：`string`、`table`、`math`、`bit32`、`coroutine` 与语言内建函数；没有 `io`、`os`、`debug`、`luajava`。
- `dofile`、`loadfile`、`require`、`collectgarbage` 与 `package.loadlib` 被移除，`package.path` 为空，无法从磁盘加载代码。
- 单次调用超过 5 秒会被中断，避免死循环。
- 脚本异常只影响当前调用，宿主记录日志后继续主流程。

### 5. 打包

```bash
cd examples/plugins/lua-plugin
./build.sh          # 生成 plugin.zip
```

要求 `plugin.json` 与入口脚本位于 ZIP 根目录。完整示例见 `examples/plugins/lua-plugin/`。

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

## 选择运行时

| 场景 | 建议 |
| --- | --- |
| 只想改模板、加通知渠道、简单过滤 | JavaScript 或 Lua |
| 团队更熟悉 Lua、想复用现成 Lua 脚本 | Lua |
| 需要访问宿主内部能力、复用第三方 Java 库、做复杂解析 | Java |
| 需要长期维护、分发到市场 | 都可以；Java 包更小启动更快，脚本插件无需编译 |

三种运行时共享同一套扩展点与快照字段，迁移时只需把注册对象换成目标语言的写法。

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
